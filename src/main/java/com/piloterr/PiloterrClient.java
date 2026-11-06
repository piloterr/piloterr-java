package com.piloterr;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import com.piloterr.params.*;

/**
 * Official Piloterr API client. Create one instance and reuse it; it is thread-safe.
 *
 * <pre>{@code
 * PiloterrClient client = PiloterrClient.fromEnv(); // reads PILOTERR_API_KEY
 * ApiResponse response = client.amazonProduct(AmazonProductParams.builder().query("B09DFCB66S").build());
 * }</pre>
 */
public final class PiloterrClient {
  /** Production API origin. */
  public static final String DEFAULT_BASE_URL = "https://api.piloterr.com";

  /** Default client-side deadline per request. */
  public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(120);

  /** Environment variable read by {@link #fromEnv()}. */
  public static final String API_KEY_ENVIRONMENT_VARIABLE = "PILOTERR_API_KEY";

  /** SDK version sent in the User-Agent header. */
  public static final String VERSION = "0.1.0";

  /** Job statuses {@link #waitForJob} stops on. */
  public static final List<String> JOB_TERMINAL_STATUSES = List.of("CompletedJob", "Error", "NotFound", "BadRequest");

  /** Tasks run at the same time by {@link #batch}. */
  public static final int DEFAULT_CONCURRENCY = 10;

  /** Retries after a network error or a 408, 429, 500, 502, 503 or 504 response. */
  public static final int MAX_RETRIES = 3;

  /** Each retry waits this many times longer than the previous one (a Retry-After header wins). */
  public static final double RETRY_BACKOFF_FACTOR = 1.5;

  /** Delay before the first retry. */
  public static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(1);

  /** Longest delay between two attempts. */
  public static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(30);

  private static final Set<Integer> RETRYABLE_STATUSES = Set.of(408, 500, 502, 503, 504);

  private static final List<String> ERROR_MESSAGE_KEYS = List.of("error", "message", "error_message", "detail");

  private final String apiKey;
  private final String baseUrl;
  private final Duration timeout;
  private final Map<String, String> headers;
  private final PiloterrTransport transport;
  private final int maxRetries;
  private final Duration retryDelay;

  private PiloterrClient(Builder builder) {
    String key = builder.apiKey;
    if (key == null || key.isBlank()) {
      throw new PiloterrConfigurationException(
          "A non-blank Piloterr API key is required. Pass apiKey(...) or set " + API_KEY_ENVIRONMENT_VARIABLE + ".");
    }
    if (key.indexOf('\r') >= 0 || key.indexOf('\n') >= 0) {
      throw new PiloterrConfigurationException("The Piloterr API key cannot be used in an HTTP header.");
    }
    String base = builder.baseUrl.replaceAll("/+$", "");
    URI uri;
    try {
      uri = URI.create(base);
    } catch (IllegalArgumentException e) {
      throw new PiloterrConfigurationException("baseUrl must be an absolute http or https URL.", e);
    }
    if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme())) || uri.getHost() == null) {
      throw new PiloterrConfigurationException("baseUrl must be an absolute http or https URL.");
    }
    if (builder.timeout != null && (builder.timeout.isNegative() || builder.timeout.isZero())) {
      throw new PiloterrConfigurationException("timeout must be positive, or null to disable it.");
    }
    this.apiKey = key;
    this.baseUrl = base;
    this.timeout = builder.timeout;
    this.headers = Map.copyOf(builder.headers);
    if (builder.maxRetries < 0) {
      throw new PiloterrConfigurationException("maxRetries must be zero or more.");
    }
    if (builder.retryDelay == null || builder.retryDelay.isNegative()) {
      throw new PiloterrConfigurationException("retryDelay must be zero or more.");
    }
    this.maxRetries = builder.maxRetries;
    this.retryDelay = builder.retryDelay;
    this.transport = builder.transport != null ? builder.transport : PiloterrTransport.defaultTransport();
  }

  /** Creates a client that reads the API key from {@value #API_KEY_ENVIRONMENT_VARIABLE}. */
  public static PiloterrClient fromEnv() {
    return builder().build();
  }

  /** Creates a client with an explicit API key. */
  public static PiloterrClient of(String apiKey) {
    return builder().apiKey(apiKey).build();
  }

  /** Returns a new builder. The API key defaults to {@value #API_KEY_ENVIRONMENT_VARIABLE}. */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Sends any request, including endpoints released after this SDK version. GET parameters travel
   * in the query string; POST parameters travel in a JSON body.
   */
  public ApiResponse request(String method, String path, Map<String, ?> params) {
    return request(method, path, params, RequestOptions.defaults());
  }

  /** {@link #request(String, String, Map)} with per-request options. */
  public ApiResponse request(String method, String path, Map<String, ?> params, RequestOptions options) {
    String verb = method == null ? "" : method.toUpperCase(Locale.ROOT);
    return send(verb, path, params == null ? Map.of() : params, options, false);
  }

  /**
   * Polls {@code GET /v1/jobs/{id}} until the job reaches a terminal status. The delay starts at {@code
   * interval} (default 3 s) and grows 1.5x per poll up to 60 s; {@code timeout} defaults to 12 hours.
   *
   * @param onProgress called with the job document after every poll, may be null
   */
  public ApiResponse waitForJob(String jobId, Duration interval, Duration timeout, Consumer<JsonNode> onProgress) {
    if (jobId == null || jobId.isBlank()) {
      throw new PiloterrSerializationException("A non-blank job id is required.", "id");
    }
    Duration pause = interval == null ? Duration.ofSeconds(3) : interval;
    Duration deadline = timeout == null ? Duration.ofHours(12) : timeout;
    Duration maxPause = pause.compareTo(Duration.ofMinutes(1)) > 0 ? pause : Duration.ofMinutes(1);
    long started = System.nanoTime();
    while (true) {
      ApiResponse last = request("GET", "/v1/jobs/{id}", Map.of("id", jobId));
      JsonNode job = last.json();
      if (onProgress != null) {
        onProgress.accept(job);
      }
      if (JOB_TERMINAL_STATUSES.contains(job.path("status").asText(""))) {
        return last;
      }
      if (Duration.ofNanos(System.nanoTime() - started).plus(pause).compareTo(deadline) > 0) {
        throw new PiloterrJobTimeoutException(jobId, last);
      }
      try {
        Thread.sleep(pause.toMillis());
        Duration next = Duration.ofMillis(pause.toMillis() * 3 / 2);
        pause = next.compareTo(maxPause) > 0 ? maxPause : next;
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new PiloterrTransportException("waitForJob was interrupted.", e);
      }
    }
  }

  /** Polls a job with the default interval (3 s, growing to 60 s) and timeout (12 h). */
  public ApiResponse waitForJob(String jobId) {
    return waitForJob(jobId, null, null, null);
  }

  /**
   * About You Product. Read one About You product from a product URL or a keyword. A keyword opens the first search hit. Set region when the query does not already name the shop. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/aboutyou/product}
   */
  public ApiResponse aboutyouProduct(AboutyouProductParams params) {
    return aboutyouProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/aboutyou/product} with per-request options. */
  public ApiResponse aboutyouProduct(AboutyouProductParams params, RequestOptions options) {
    return call("aboutyou_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * About You Search. Search an About You shop with a keyword or a search URL. Pick the country with region, or let a full link choose the shop. Cards include brand, price, and the product link. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/aboutyou/search}
   */
  public ApiResponse aboutyouSearch(AboutyouSearchParams params) {
    return aboutyouSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/aboutyou/search} with per-request options. */
  public ApiResponse aboutyouSearch(AboutyouSearchParams params, RequestOptions options) {
    return call("aboutyou_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Action Product. Action Product API: fetch full product page data from a product ID or URL on Action.com. Returns pricing, specs, images, breadcrumbs and availability via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/action/product}
   */
  public ApiResponse actionProduct(ActionProductParams params) {
    return actionProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/action/product} with per-request options. */
  public ApiResponse actionProduct(ActionProductParams params, RequestOptions options) {
    return call("action_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Action Search. Action Search API: search product listings by keyword or URL on Action.com across European locales. Returns prices, images, locale, currency and pagination via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/action/search}
   */
  public ApiResponse actionSearch(ActionSearchParams params) {
    return actionSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/action/search} with per-request options. */
  public ApiResponse actionSearch(ActionSearchParams params, RequestOptions options) {
    return call("action_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Aldi Product. Aldi Product API: fetch full product page data from an Aldi URL or path slug. Returns pricing, images, description, category and cooling flags. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/aldi/product}
   */
  public ApiResponse aldiProduct(AldiProductParams params) {
    return aldiProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/aldi/product} with per-request options. */
  public ApiResponse aldiProduct(AldiProductParams params, RequestOptions options) {
    return call("aldi_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Aldi Search. Aldi Search API: list products from an Aldi category page URL or path across FR, DE, NL and Canarias shops. Returns prices, thumbnails and pagination. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/aldi/search}
   */
  public ApiResponse aldiSearch(AldiSearchParams params) {
    return aldiSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/aldi/search} with per-request options. */
  public ApiResponse aldiSearch(AldiSearchParams params, RequestOptions options) {
    return call("aldi_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Alibaba Product. Alibaba Product API. Fetch a full B2B product page by URL or product ID with optional locale subdomain. Browser rendering returns tiered pricing, seller profile and lead times. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/alibaba/product}
   */
  public ApiResponse alibabaProduct(AlibabaProductParams params) {
    return alibabaProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/alibaba/product} with per-request options. */
  public ApiResponse alibabaProduct(AlibabaProductParams params, RequestOptions options) {
    return call("alibaba_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Alibaba Search. Alibaba Search API. Query B2B product listings by keyword or search URL across 17 locale subdomains. Request mode returns prices, MOQ, seller data and pagination. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/alibaba/search}
   */
  public ApiResponse alibabaSearch(AlibabaSearchParams params) {
    return alibabaSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/alibaba/search} with per-request options. */
  public ApiResponse alibabaSearch(AlibabaSearchParams params, RequestOptions options) {
    return call("alibaba_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * AliExpress Product. AliExpress Product API: scrape live /item/ product pages from a full URL. Get title, images, seller identity, pricing, specs, delivery, and FAQs as JSON via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/aliexpress/product}
   */
  public ApiResponse aliexpressProduct(AliexpressProductParams params) {
    return aliexpressProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/aliexpress/product} with per-request options. */
  public ApiResponse aliexpressProduct(AliexpressProductParams params, RequestOptions options) {
    return call("aliexpress_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * AliExpress Reviews. AliExpress Reviews API: list buyer reviews for a product id or item URL. Each review includes author, star rating, text, date, and photos when the buyer attached them. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/aliexpress/product/reviews}
   */
  public ApiResponse aliexpressProductReviews(AliexpressProductReviewsParams params) {
    return aliexpressProductReviews(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/aliexpress/product/reviews} with per-request options. */
  public ApiResponse aliexpressProductReviews(AliexpressProductReviewsParams params, RequestOptions options) {
    return call("aliexpress_product_reviews", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * AliExpress Search. AliExpress Search API: search by keyword or wholesale URL and get product listings with prices, ratings, sold counts, and pagination as JSON via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/aliexpress/search}
   */
  public ApiResponse aliexpressSearch(AliexpressSearchParams params) {
    return aliexpressSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/aliexpress/search} with per-request options. */
  public ApiResponse aliexpressSearch(AliexpressSearchParams params, RequestOptions options) {
    return call("aliexpress_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * AliExpress Store Product. AliExpress Store Product API: list products from an AliExpress seller store by URL or store ID. Returns store metadata and product cards (~40 SSR items) as JSON via browser rendering. Pagination is not supported. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/aliexpress/store/product}
   */
  public ApiResponse aliexpressStoreProduct(AliexpressStoreProductParams params) {
    return aliexpressStoreProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/aliexpress/store/product} with per-request options. */
  public ApiResponse aliexpressStoreProduct(AliexpressStoreProductParams params, RequestOptions options) {
    return call("aliexpress_store_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Allegro Product. Allegro Product API. Extract full product page data from allegro.pl by URL (/produkt/, /oferta/, /ogloszenie/). Browser rendering, 3 credits per call. Pair with Allegro Search (3 credits) for listing discovery. Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/allegro/product}
   */
  public ApiResponse allegroProduct(AllegroProductParams params) {
    return allegroProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/allegro/product} with per-request options. */
  public ApiResponse allegroProduct(AllegroProductParams params, RequestOptions options) {
    return call("allegro_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Allegro Search. Allegro Search API. Query product listings by keyword, optional page number, or full allegro.pl listing URL. Returns results with prices, sellers, sponsorship flags and pagination. 3 credits per call. Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/allegro/search}
   */
  public ApiResponse allegroSearch(AllegroSearchParams params) {
    return allegroSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/allegro/search} with per-request options. */
  public ApiResponse allegroSearch(AllegroSearchParams params, RequestOptions options) {
    return call("allegro_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Amazon Product. Efficiently extract product details from Amazon for dynamic retail insights, including price, stock, features, and seller data. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/amazon/product}
   */
  public ApiResponse amazonProduct(AmazonProductParams params) {
    return amazonProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/amazon/product} with per-request options. */
  public ApiResponse amazonProduct(AmazonProductParams params, RequestOptions options) {
    return call("amazon_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Amazon Product Offer. Efficiently extract details of third-party seller offers for any Amazon product across multiple marketplaces. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/amazon/product/offer}
   */
  public ApiResponse amazonProductOffer(AmazonProductOfferParams params) {
    return amazonProductOffer(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/amazon/product/offer} with per-request options. */
  public ApiResponse amazonProductOffer(AmazonProductOfferParams params, RequestOptions options) {
    return call("amazon_product_offer", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Amazon Search. Efficiently scrape Amazon search results for real-time product data and insights across multiple Amazon marketplaces. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/amazon/search}
   */
  public ApiResponse amazonSearch(AmazonSearchParams params) {
    return amazonSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/amazon/search} with per-request options. */
  public ApiResponse amazonSearch(AmazonSearchParams params, RequestOptions options) {
    return call("amazon_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Apartments Property. Extract a full Apartments.com rental listing from a property URL, including amenities, gallery, description, geolocation, and unit details via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/apartments/property}
   */
  public ApiResponse apartmentsProperty(ApartmentsPropertyParams params) {
    return apartmentsProperty(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/apartments/property} with per-request options. */
  public ApiResponse apartmentsProperty(ApartmentsPropertyParams params, RequestOptions options) {
    return call("apartments_property", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Apartments Search. Search Apartments.com rentals from a full city or browse URL and retrieve prices, photos, addresses, phone numbers, and pagination via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/apartments/search}
   */
  public ApiResponse apartmentsSearch(ApartmentsSearchParams params) {
    return apartmentsSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/apartments/search} with per-request options. */
  public ApiResponse apartmentsSearch(ApartmentsSearchParams params, RequestOptions options) {
    return call("apartments_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Argenprop Property. Read one Argenprop listing from its URL or id. You get price, address, rooms, expenses, and the agent when the page shows them. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/argenprop/property}
   */
  public ApiResponse argenpropProperty(ArgenpropPropertyParams params) {
    return argenpropProperty(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/argenprop/property} with per-request options. */
  public ApiResponse argenpropProperty(ArgenpropPropertyParams params, RequestOptions options) {
    return call("argenprop_property", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Argenprop Search. Search Argenprop by neighbourhood or city. Set operation to venta or alquiler, and property_type such as departamentos or casas. Page with pagina. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/argenprop/search}
   */
  public ApiResponse argenpropSearch(ArgenpropSearchParams params) {
    return argenpropSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/argenprop/search} with per-request options. */
  public ApiResponse argenpropSearch(ArgenpropSearchParams params, RequestOptions options) {
    return call("argenprop_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Argos Product. Argos Product API: one UK product by id or URL. Browser rendering (2 credits). Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/argos/product}
   */
  public ApiResponse argosProduct(ArgosProductParams params) {
    return argosProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/argos/product} with per-request options. */
  public ApiResponse argosProduct(ArgosProductParams params, RequestOptions options) {
    return call("argos_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Argos Search. Search the Argos UK catalogue by keyword and page the results. Prices are in pounds. A one-word query that Argos turns into a brand page with no product cards returns no results. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/argos/search}
   */
  public ApiResponse argosSearch(ArgosSearchParams params) {
    return argosSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/argos/search} with per-request options. */
  public ApiResponse argosSearch(ArgosSearchParams params, RequestOptions options) {
    return call("argos_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * arXiv Paper. Fetch one arXiv paper by id or by an abs, pdf, or html URL. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/arxiv/paper}
   */
  public ApiResponse arxivPaper(ArxivPaperParams params) {
    return arxivPaper(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/arxiv/paper} with per-request options. */
  public ApiResponse arxivPaper(ArxivPaperParams params, RequestOptions options) {
    return call("arxiv_paper", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * arXiv Search. Search arXiv papers by keyword, author query such as au:einstein, or arXiv id. A paper URL returns 400. Page through results when the match count is large. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/arxiv/search}
   */
  public ApiResponse arxivSearch(ArxivSearchParams params) {
    return arxivSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/arxiv/search} with per-request options. */
  public ApiResponse arxivSearch(ArxivSearchParams params, RequestOptions options) {
    return call("arxiv_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Auchan Product. Effortlessly extract product details from Auchan for dynamic business insights, including nutritional information, ingredients, ratings, and reviews. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/auchan/product}
   */
  public ApiResponse auchanProduct(AuchanProductParams params) {
    return auchanProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/auchan/product} with per-request options. */
  public ApiResponse auchanProduct(AuchanProductParams params, RequestOptions options) {
    return call("auchan_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Auchan Search. Extract Auchan product listings from keyword searches for insightful market analysis and competitive edge. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/auchan/search}
   */
  public ApiResponse auchanSearch(AuchanSearchParams params) {
    return auchanSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/auchan/search} with per-request options. */
  public ApiResponse auchanSearch(AuchanSearchParams params, RequestOptions options) {
    return call("auchan_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Autodoc Product. Scrape Autodoc product pages: price, specs, EAN, images, compatibility, and TecDoc-related vehicle data via the Related endpoint. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/autodoc/product}
   */
  public ApiResponse autodocProduct(AutodocProductParams params) {
    return autodocProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/autodoc/product} with per-request options. */
  public ApiResponse autodocProduct(AutodocProductParams params, RequestOptions options) {
    return call("autodoc_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Autodoc Product Related. Vehicle fitment lines per maker for an Autodoc product. All parameters from Product fetch.params are required. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/autodoc/product/related}
   */
  public ApiResponse autodocProductRelated(AutodocProductRelatedParams params) {
    return autodocProductRelated(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/autodoc/product/related} with per-request options. */
  public ApiResponse autodocProductRelated(AutodocProductRelatedParams params, RequestOptions options) {
    return call("autodoc_product_related", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Autodoc Search. Search Autodoc by URL: keyword pages use /search?keyword=… (e.g. frein on auto-doc.fr). Extract product cards, prices, and pagination. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/autodoc/search}
   */
  public ApiResponse autodocSearch(AutodocSearchParams params) {
    return autodocSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/autodoc/search} with per-request options. */
  public ApiResponse autodocSearch(AutodocSearchParams params, RequestOptions options) {
    return call("autodoc_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Auto Europe Result. Read one vehicle from an Auto Europe quote. Pass the offer id from Search, with the same pickup and dates. You get the supplier, the SIPP code, and the package prices. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/autoeurope/result}
   */
  public ApiResponse autoeuropeResult(AutoeuropeResultParams params) {
    return autoeuropeResult(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/autoeurope/result} with per-request options. */
  public ApiResponse autoeuropeResult(AutoeuropeResultParams params, RequestOptions options) {
    return call("autoeurope_result", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Auto Europe Search. Quote a rental car on Auto Europe. Pass pickup, pickup date, and dropoff date. Region chooses the country site and its currency. The whole quote comes back on one page. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/autoeurope/search}
   */
  public ApiResponse autoeuropeSearch(AutoeuropeSearchParams params) {
    return autoeuropeSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/autoeurope/search} with per-request options. */
  public ApiResponse autoeuropeSearch(AutoeuropeSearchParams params, RequestOptions options) {
    return call("autoeurope_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * AutoScout24 Ad. Read one AutoScout24 listing, including an inactive ad that still exposes the listing. Price, vehicle, seller, and location. (1 credit) Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/autoscout24/ad}
   */
  public ApiResponse autoscout24Ad(Autoscout24AdParams params) {
    return autoscout24Ad(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/autoscout24/ad} with per-request options. */
  public ApiResponse autoscout24Ad(Autoscout24AdParams params, RequestOptions options) {
    return call("autoscout24_ad", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * AutoScout24 Search. Search AutoScout24 listings from a search URL. Each card has make, model, numeric price, mileage, and the listing URL. (1 credit) Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/autoscout24/search}
   */
  public ApiResponse autoscout24Search(Autoscout24SearchParams params) {
    return autoscout24Search(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/autoscout24/search} with per-request options. */
  public ApiResponse autoscout24Search(Autoscout24SearchParams params, RequestOptions options) {
    return call("autoscout24_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Autotrader Ad. Extract full vehicle details from an AutoTrader UK car-details URL, including price, specs, gallery, seller, and description via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/autotrader/ad}
   */
  public ApiResponse autotraderAd(AutotraderAdParams params) {
    return autotraderAd(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/autotrader/ad} with per-request options. */
  public ApiResponse autotraderAd(AutotraderAdParams params, RequestOptions options) {
    return call("autotrader_ad", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Autotrader Search. Search AutoTrader UK listings from a car-search URL and retrieve prices, specs, photos, seller info, and pagination via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/autotrader/search}
   */
  public ApiResponse autotraderSearch(AutotraderSearchParams params) {
    return autotraderSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/autotrader/search} with per-request options. */
  public ApiResponse autotraderSearch(AutotraderSearchParams params, RequestOptions options) {
    return call("autotrader_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Best Buy Product. Get a Best Buy US or Canada product page: title, price, brand, stock, images, categories and specs via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/bestbuy/product}
   */
  public ApiResponse bestbuyProduct(BestbuyProductParams params) {
    return bestbuyProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/bestbuy/product} with per-request options. */
  public ApiResponse bestbuyProduct(BestbuyProductParams params, RequestOptions options) {
    return call("bestbuy_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Best Buy Reviews. Get customer reviews for a Best Buy product URL: rating, review text, author, photos and pagination. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/bestbuy/product/reviews}
   */
  public ApiResponse bestbuyProductReviews(BestbuyProductReviewsParams params) {
    return bestbuyProductReviews(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/bestbuy/product/reviews} with per-request options. */
  public ApiResponse bestbuyProductReviews(BestbuyProductReviewsParams params, RequestOptions options) {
    return call("bestbuy_product_reviews", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Best Buy Search. Search Best Buy US or Canada by keyword or search URL. Returns product listings with price, rating and pagination via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/bestbuy/search}
   */
  public ApiResponse bestbuySearch(BestbuySearchParams params) {
    return bestbuySearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/bestbuy/search} with per-request options. */
  public ApiResponse bestbuySearch(BestbuySearchParams params, RequestOptions options) {
    return call("bestbuy_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * B&amp;H Photo Video Product. Read a B&amp;H Photo Video product from its URL or id. Returns price, rating, specifications, and box contents. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/bhphotovideo/product}
   */
  public ApiResponse bhphotovideoProduct(BhphotovideoProductParams params) {
    return bhphotovideoProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/bhphotovideo/product} with per-request options. */
  public ApiResponse bhphotovideoProduct(BhphotovideoProductParams params, RequestOptions options) {
    return call("bhphotovideo_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * B&amp;H Photo Video Search. Search B&amp;H Photo Video by keyword or search URL. Returns BH numbers, dollar prices, and a next page URL. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/bhphotovideo/search}
   */
  public ApiResponse bhphotovideoSearch(BhphotovideoSearchParams params) {
    return bhphotovideoSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/bhphotovideo/search} with per-request options. */
  public ApiResponse bhphotovideoSearch(BhphotovideoSearchParams params, RequestOptions options) {
    return call("bhphotovideo_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Bing Search. Scrape real-time Bing search results with full control over location, language, and pagination. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/bing/search}
   */
  public ApiResponse bingSearch(BingSearchParams params) {
    return bingSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/bing/search} with per-request options. */
  public ApiResponse bingSearch(BingSearchParams params, RequestOptions options) {
    return call("bing_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Bitiba Product. Read one product from a live Bitiba shop, including the selected variant price and the pack list. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/bitiba/product}
   */
  public ApiResponse bitibaProduct(BitibaProductParams params) {
    return bitibaProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/bitiba/product} with per-request options. */
  public ApiResponse bitibaProduct(BitibaProductParams params, RequestOptions options) {
    return call("bitiba_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Bitiba Search. Search a live Bitiba country shop. Germany is the default. www.bitiba.com is closed and returns 400. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/bitiba/search}
   */
  public ApiResponse bitibaSearch(BitibaSearchParams params) {
    return bitibaSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/bitiba/search} with per-request options. */
  public ApiResponse bitibaSearch(BitibaSearchParams params, RequestOptions options) {
    return call("bitiba_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Bol Product. Read one bol.com product by URL or product id, in nl, be-nl, or be-fr. You get the selling price, the list price, and the seller. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/bol/product}
   */
  public ApiResponse bolProduct(BolProductParams params) {
    return bolProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/bol/product} with per-request options. */
  public ApiResponse bolProduct(BolProductParams params, RequestOptions options) {
    return call("bol_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Bol Search. Search bol.com by keyword. Region is nl, be-nl, or be-fr. Page the product cards. Sponsored ads can repeat across pages. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/bol/search}
   */
  public ApiResponse bolSearch(BolSearchParams params) {
    return bolSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/bol/search} with per-request options. */
  public ApiResponse bolSearch(BolSearchParams params, RequestOptions options) {
    return call("bol_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Boulanger Product. Read one Boulanger product from its URL or product id. You get price, brand, rating, seller, and specifications. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/boulanger/product}
   */
  public ApiResponse boulangerProduct(BoulangerProductParams params) {
    return boulangerProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/boulanger/product} with per-request options. */
  public ApiResponse boulangerProduct(BoulangerProductParams params, RequestOptions options) {
    return call("boulanger_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Boulanger Search. Search Boulanger by keyword and page the product grid. Cards include price, brand, seller, and the product URL. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/boulanger/search}
   */
  public ApiResponse boulangerSearch(BoulangerSearchParams params) {
    return boulangerSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/boulanger/search} with per-request options. */
  public ApiResponse boulangerSearch(BoulangerSearchParams params, RequestOptions options) {
    return call("boulanger_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Brave Search. Access the Brave search engine results directly through our efficient Piloterr service for reliable data scraping. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/brave/search}
   */
  public ApiResponse braveSearch(BraveSearchParams params) {
    return braveSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/brave/search} with per-request options. */
  public ApiResponse braveSearch(BraveSearchParams params, RequestOptions options) {
    return call("brave_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Brico Dépôt Product. Read one Brico Dépôt product from its product URL. You get price, brand, SKU, and availability. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/bricodepot/product}
   */
  public ApiResponse bricodepotProduct(BricodepotProductParams params) {
    return bricodepotProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/bricodepot/product} with per-request options. */
  public ApiResponse bricodepotProduct(BricodepotProductParams params, RequestOptions options) {
    return call("bricodepot_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Brico Dépôt Search. Search Brico Dépôt with a /produits/ listing URL, not a keyword box. Page 2 is the same path with /2 at the end. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/bricodepot/search}
   */
  public ApiResponse bricodepotSearch(BricodepotSearchParams params) {
    return bricodepotSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/bricodepot/search} with per-request options. */
  public ApiResponse bricodepotSearch(BricodepotSearchParams params, RequestOptions options) {
    return call("bricodepot_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Bricorama Product. Read one Bricorama product from its URL. You get price, brand, and the product details. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/bricorama/product}
   */
  public ApiResponse bricoramaProduct(BricoramaProductParams params) {
    return bricoramaProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/bricorama/product} with per-request options. */
  public ApiResponse bricoramaProduct(BricoramaProductParams params, RequestOptions options) {
    return call("bricorama_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Bricorama Search. Search Bricorama by keyword or search URL and page the home-improvement grid. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/bricorama/search}
   */
  public ApiResponse bricoramaSearch(BricoramaSearchParams params) {
    return bricoramaSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/bricorama/search} with per-request options. */
  public ApiResponse bricoramaSearch(BricoramaSearchParams params, RequestOptions options) {
    return call("bricorama_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Carrefour Product. Read one Carrefour.fr product from its page URL. Every product returns the same keys: price, brand, pack size, Nutriscore, categories, unit price, and customer reviews. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/carrefour/product}
   */
  public ApiResponse carrefourProduct(CarrefourProductParams params) {
    return carrefourProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/carrefour/product} with per-request options. */
  public ApiResponse carrefourProduct(CarrefourProductParams params, RequestOptions options) {
    return call("carrefour_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Carrefour Search. Search for products on Carrefour by keyword or URL, returning structured listings with prices, ratings, and pagination. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/carrefour/search}
   */
  public ApiResponse carrefourSearch(CarrefourSearchParams params) {
    return carrefourSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/carrefour/search} with per-request options. */
  public ApiResponse carrefourSearch(CarrefourSearchParams params, RequestOptions options) {
    return call("carrefour_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Carrefour Suggest. Get product suggestions from Carrefour by keyword or EAN, returning matching products with pricing, availability, and product details. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/carrefour/suggest}
   */
  public ApiResponse carrefourSuggest(CarrefourSuggestParams params) {
    return carrefourSuggest(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/carrefour/suggest} with per-request options. */
  public ApiResponse carrefourSuggest(CarrefourSuggestParams params, RequestOptions options) {
    return call("carrefour_suggest", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Castorama Product. Read one Castorama product from its URL. You get price, brand, and specifications. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/castorama/product}
   */
  public ApiResponse castoramaProduct(CastoramaProductParams params) {
    return castoramaProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/castorama/product} with per-request options. */
  public ApiResponse castoramaProduct(CastoramaProductParams params, RequestOptions options) {
    return call("castorama_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Castorama Search. Search Castorama by keyword or search URL and page the DIY listings. Cards include title, price, and product link. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/castorama/search}
   */
  public ApiResponse castoramaSearch(CastoramaSearchParams params) {
    return castoramaSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/castorama/search} with per-request options. */
  public ApiResponse castoramaSearch(CastoramaSearchParams params, RequestOptions options) {
    return call("castorama_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Cdiscount Product. Extract full product details from a Cdiscount product page, including price, seller, ratings, specifications, and offers. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/cdiscount/product}
   */
  public ApiResponse cdiscountProduct(CdiscountProductParams params) {
    return cdiscountProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/cdiscount/product} with per-request options. */
  public ApiResponse cdiscountProduct(CdiscountProductParams params, RequestOptions options) {
    return call("cdiscount_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Cdiscount Product Offer. Retrieve all marketplace seller offers for a Cdiscount product, including prices, seller ratings, shipping costs, and availability. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/cdiscount/product/offer}
   */
  public ApiResponse cdiscountProductOffer(CdiscountProductOfferParams params) {
    return cdiscountProductOffer(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/cdiscount/product/offer} with per-request options. */
  public ApiResponse cdiscountProductOffer(CdiscountProductOfferParams params, RequestOptions options) {
    return call("cdiscount_product_offer", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Cdiscount Search. Extract product listings from Cdiscount search results or category pages for market intelligence and competitive analysis. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/cdiscount/search}
   */
  public ApiResponse cdiscountSearch(CdiscountSearchParams params) {
    return cdiscountSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/cdiscount/search} with per-request options. */
  public ApiResponse cdiscountSearch(CdiscountSearchParams params, RequestOptions options) {
    return call("cdiscount_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Centris Property. Read one Centris property from its URL or listing id. You get price, address, rooms, and the broker. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/centris/property}
   */
  public ApiResponse centrisProperty(CentrisPropertyParams params) {
    return centrisProperty(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/centris/property} with per-request options. */
  public ApiResponse centrisProperty(CentrisPropertyParams params, RequestOptions options) {
    return call("centris_property", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Centris Search. Search Centris listings by city or by a full results URL. Use the English or French path. Page through properties for sale. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/centris/search}
   */
  public ApiResponse centrisSearch(CentrisSearchParams params) {
    return centrisSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/centris/search} with per-request options. */
  public ApiResponse centrisSearch(CentrisSearchParams params, RequestOptions options) {
    return call("centris_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Chewy Product. Read one Chewy product by URL or numeric id. The default mode is expert and returns the historical product cache. simple returns one snake_case product. Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/chewy/product}
   */
  public ApiResponse chewyProduct(ChewyProductParams params) {
    return chewyProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/chewy/product} with per-request options. */
  public ApiResponse chewyProduct(ChewyProductParams params, RequestOptions options) {
    return call("chewy_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Chewy Search. Search Chewy by keyword or listing URL. The default mode is expert and returns the historical listing objects. simple returns short snake_case cards. Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/chewy/search}
   */
  public ApiResponse chewySearch(ChewySearchParams params) {
    return chewySearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/chewy/search} with per-request options. */
  public ApiResponse chewySearch(ChewySearchParams params, RequestOptions options) {
    return call("chewy_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * ClearedJobs Job. Fetch one cleared job by its URL or by a slug that ends in the numeric id. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/clearedjobs/job}
   */
  public ApiResponse clearedjobsJob(ClearedjobsJobParams params) {
    return clearedjobsJob(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/clearedjobs/job} with per-request options. */
  public ApiResponse clearedjobsJob(ClearedjobsJobParams params, RequestOptions options) {
    return call("clearedjobs_job", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * ClearedJobs Search. Search cleared jobs by keyword. Only the first HTML page is available, so pagination.next is null and page 2 returns 404. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/clearedjobs/search}
   */
  public ApiResponse clearedjobsSearch(ClearedjobsSearchParams params) {
    return clearedjobsSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/clearedjobs/search} with per-request options. */
  public ApiResponse clearedjobsSearch(ClearedjobsSearchParams params, RequestOptions options) {
    return call("clearedjobs_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Company Database. Retrieve comprehensive company data including financials, technologies, social profiles, and location by domain name. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/company}
   */
  public ApiResponse company(CompanyParams params) {
    return company(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/company} with per-request options. */
  public ApiResponse company(CompanyParams params, RequestOptions options) {
    return call("company", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Coolblue Product. Read one Coolblue product by article id or product URL, in the region of that shop. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/coolblue/product}
   */
  public ApiResponse coolblueProduct(CoolblueProductParams params) {
    return coolblueProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/coolblue/product} with per-request options. */
  public ApiResponse coolblueProduct(CoolblueProductParams params, RequestOptions options) {
    return call("coolblue_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Coolblue Search. Search Coolblue with a product keyword. Region defaults to Belgium. Also use nl or de. Page through product cards. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/coolblue/search}
   */
  public ApiResponse coolblueSearch(CoolblueSearchParams params) {
    return coolblueSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/coolblue/search} with per-request options. */
  public ApiResponse coolblueSearch(CoolblueSearchParams params, RequestOptions options) {
    return call("coolblue_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Copart Lot. Copart Lot API: retrieve structured auction lot data by lot number or URL. Specs, damage, bids, photos, yard location and sale status across regional Copart sites. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/copart/lot}
   */
  public ApiResponse copartLot(CopartLotParams params) {
    return copartLot(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/copart/lot} with per-request options. */
  public ApiResponse copartLot(CopartLotParams params, RequestOptions options) {
    return call("copart_lot", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Copart Search. Copart Search API: search auction lots by keyword or URL across Copart regional sites. Returns lot listings and pagination. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/copart/search}
   */
  public ApiResponse copartSearch(CopartSearchParams params) {
    return copartSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/copart/search} with per-request options. */
  public ApiResponse copartSearch(CopartSearchParams params, RequestOptions options) {
    return call("copart_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Costco Product. Costco Product API: fetch full product page data from a product ID or URL across regional Costco sites. Returns pricing, availability, ratings, images and breadcrumbs via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/costco/product}
   */
  public ApiResponse costcoProduct(CostcoProductParams params) {
    return costcoProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/costco/product} with per-request options. */
  public ApiResponse costcoProduct(CostcoProductParams params, RequestOptions options) {
    return call("costco_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Costco Search. Costco Search API: search product listings by keyword or URL across 13 regional Costco sites. Returns prices, availability, review counts and pagination via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/costco/search}
   */
  public ApiResponse costcoSearch(CostcoSearchParams params) {
    return costcoSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/costco/search} with per-request options. */
  public ApiResponse costcoSearch(CostcoSearchParams params, RequestOptions options) {
    return call("costco_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Crunchbase Company Info. Convert URLs into detailed Crunchbase profiles for immediate company insights. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/crunchbase/company/info}
   */
  public ApiResponse crunchbaseCompanyInfo(CrunchbaseCompanyInfoParams params) {
    return crunchbaseCompanyInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/crunchbase/company/info} with per-request options. */
  public ApiResponse crunchbaseCompanyInfo(CrunchbaseCompanyInfoParams params, RequestOptions options) {
    return call("crunchbase_company_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Crunchbase Event. Retrieve detailed Crunchbase event profiles with speakers, sponsors, exhibitors, and press timelines. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/crunchbase/event}
   */
  public ApiResponse crunchbaseEvent(CrunchbaseEventParams params) {
    return crunchbaseEvent(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/crunchbase/event} with per-request options. */
  public ApiResponse crunchbaseEvent(CrunchbaseEventParams params, RequestOptions options) {
    return call("crunchbase_event", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Crunchbase Funding Round. Retrieve full details of a specific Crunchbase funding round including investors and press timeline. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/crunchbase/funding_round}
   */
  public ApiResponse crunchbaseFundingRound(CrunchbaseFundingRoundParams params) {
    return crunchbaseFundingRound(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/crunchbase/funding_round} with per-request options. */
  public ApiResponse crunchbaseFundingRound(CrunchbaseFundingRoundParams params, RequestOptions options) {
    return call("crunchbase_funding_round", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Crunchbase Funding Rounds. Monitor funding history and financial milestones of companies with Crunchbase data. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/crunchbase/funding_rounds}
   */
  public ApiResponse crunchbaseFundingRounds(CrunchbaseFundingRoundsParams params) {
    return crunchbaseFundingRounds(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/crunchbase/funding_rounds} with per-request options. */
  public ApiResponse crunchbaseFundingRounds(CrunchbaseFundingRoundsParams params, RequestOptions options) {
    return call("crunchbase_funding_rounds", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Crunchbase People Info. Retrieve detailed Crunchbase profiles for individuals including career history, investments, and board positions. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/crunchbase/people/info}
   */
  public ApiResponse crunchbasePeopleInfo(CrunchbasePeopleInfoParams params) {
    return crunchbasePeopleInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/crunchbase/people/info} with per-request options. */
  public ApiResponse crunchbasePeopleInfo(CrunchbasePeopleInfoParams params, RequestOptions options) {
    return call("crunchbase_people_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Crunchbase Search. Search Crunchbase for companies and people by keyword and retrieve their profiles and Crunchbase identifiers. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/crunchbase/search}
   */
  public ApiResponse crunchbaseSearch(CrunchbaseSearchParams params) {
    return crunchbaseSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/crunchbase/search} with per-request options. */
  public ApiResponse crunchbaseSearch(CrunchbaseSearchParams params, RequestOptions options) {
    return call("crunchbase_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Cultura Product. Read one Cultura product from its URL or SKU. You get price, EAN, seller, condition, and stock wording. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/cultura/product}
   */
  public ApiResponse culturaProduct(CulturaProductParams params) {
    return culturaProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/cultura/product} with per-request options. */
  public ApiResponse culturaProduct(CulturaProductParams params, RequestOptions options) {
    return call("cultura_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Cultura Search. Search Cultura by keyword and page through books, games, and culture products. Cards include title, price, availability, and the product link. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/cultura/search}
   */
  public ApiResponse culturaSearch(CulturaSearchParams params) {
    return culturaSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/cultura/search} with per-request options. */
  public ApiResponse culturaSearch(CulturaSearchParams params, RequestOptions options) {
    return call("cultura_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Darty Product. Read one Darty product sheet from a product URL or a Darty product id. You get price, brand, availability, rating, and specifications. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/darty/product}
   */
  public ApiResponse dartyProduct(DartyProductParams params) {
    return dartyProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/darty/product} with per-request options. */
  public ApiResponse dartyProduct(DartyProductParams params, RequestOptions options) {
    return call("darty_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Darty Search. Search Darty by keyword or search URL and page through the listing. Each card has title, price, brand, seller, and product link. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/darty/search}
   */
  public ApiResponse dartySearch(DartySearchParams params) {
    return dartySearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/darty/search} with per-request options. */
  public ApiResponse dartySearch(DartySearchParams params, RequestOptions options) {
    return call("darty_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Dataset Linkedin Company. Query the LinkedIn company dataset with Elasticsearch Query DSL. Filter by name, domain, industry, headcount, and country. POST-only, 1 credit per row returned. Credit cost: 1 credit per row.
   *
   * <p>{@code POST /v2/datasets/linkedin/company}
   */
  public ApiResponse datasetsLinkedinCompany(DatasetsLinkedinCompanyParams params) {
    return datasetsLinkedinCompany(params, RequestOptions.defaults());
  }

  /** {@code POST /v2/datasets/linkedin/company} with per-request options. */
  public ApiResponse datasetsLinkedinCompany(DatasetsLinkedinCompanyParams params, RequestOptions options) {
    return call("datasets_linkedin_company", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Decathlon Product. Read one Decathlon product from a product URL, a product path, or a keyword. A keyword uses the first search hit. Set region when the query does not already name the shop. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/decathlon/product}
   */
  public ApiResponse decathlonProduct(DecathlonProductParams params) {
    return decathlonProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/decathlon/product} with per-request options. */
  public ApiResponse decathlonProduct(DecathlonProductParams params, RequestOptions options) {
    return call("decathlon_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Decathlon Search. Search a Decathlon shop with a keyword or a search URL. Choose the country with region, or let a full link pick the shop, then page through titles, prices, and product links. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/decathlon/search}
   */
  public ApiResponse decathlonSearch(DecathlonSearchParams params) {
    return decathlonSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/decathlon/search} with per-request options. */
  public ApiResponse decathlonSearch(DecathlonSearchParams params, RequestOptions options) {
    return call("decathlon_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * DigiKey Product. Get a DigiKey product page: part numbers, manufacturer, price tiers, stock, images, datasheet and specs via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/digikey/product}
   */
  public ApiResponse digikeyProduct(DigikeyProductParams params) {
    return digikeyProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/digikey/product} with per-request options. */
  public ApiResponse digikeyProduct(DigikeyProductParams params, RequestOptions options) {
    return call("digikey_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * DigiKey Search. Search DigiKey country sites by keyword or result/filter URL. Returns category branches or product listings with pagination via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/digikey/search}
   */
  public ApiResponse digikeySearch(DigikeySearchParams params) {
    return digikeySearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/digikey/search} with per-request options. */
  public ApiResponse digikeySearch(DigikeySearchParams params, RequestOptions options) {
    return call("digikey_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * DOAJ Article. Fetch one DOAJ article by its id or by a doaj.org article URL. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/doaj/article}
   */
  public ApiResponse doajArticle(DoajArticleParams params) {
    return doajArticle(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/doaj/article} with per-request options. */
  public ApiResponse doajArticle(DoajArticleParams params, RequestOptions options) {
    return call("doaj_article", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * DOAJ Journal. Fetch one DOAJ journal by ISSN, journal id, or a doaj.org toc URL. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/doaj/journal}
   */
  public ApiResponse doajJournal(DoajJournalParams params) {
    return doajJournal(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/doaj/journal} with per-request options. */
  public ApiResponse doajJournal(DoajJournalParams params, RequestOptions options) {
    return call("doaj_journal", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * DOAJ Search. Search DOAJ articles or journals. kind is article (default) or journal. Article ids and journal ISSNs are the detail keys. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/doaj/search}
   */
  public ApiResponse doajSearch(DoajSearchParams params) {
    return doajSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/doaj/search} with per-request options. */
  public ApiResponse doajSearch(DoajSearchParams params, RequestOptions options) {
    return call("doaj_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Domain DNSBL. Check if a domain or IP address is listed in DNS-based blacklists (DNSBL), with detailed provider-level detection and category information. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/domain/dnsbl}
   */
  public ApiResponse domainDnsbl(DomainDnsblParams params) {
    return domainDnsbl(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/domain/dnsbl} with per-request options. */
  public ApiResponse domainDnsbl(DomainDnsblParams params, RequestOptions options) {
    return call("domain_dnsbl", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Domain Malicious. Check if a domain or IP address has been flagged as malicious across multiple threat intelligence feeds, with source attribution. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/domain/malicious}
   */
  public ApiResponse domainMalicious(DomainMaliciousParams params) {
    return domainMalicious(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/domain/malicious} with per-request options. */
  public ApiResponse domainMalicious(DomainMaliciousParams params, RequestOptions options) {
    return call("domain_malicious", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Domain Whois. Retrieve WHOIS registration data for any domain, including registration dates, registrar details, name servers, and contact information. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/domain/whois}
   */
  public ApiResponse domainWhois(DomainWhoisParams params) {
    return domainWhois(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/domain/whois} with per-request options. */
  public ApiResponse domainWhois(DomainWhoisParams params, RequestOptions options) {
    return call("domain_whois", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Domain.com.au Property. Read one Domain.com.au listing from its URL. The response is a flat object with price, address, agency, and photos. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/domaincomau/property}
   */
  public ApiResponse domaincomauProperty(DomaincomauPropertyParams params) {
    return domaincomauProperty(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/domaincomau/property} with per-request options. */
  public ApiResponse domaincomauProperty(DomaincomauPropertyParams params, RequestOptions options) {
    return call("domaincomau_property", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Domain.com.au Search. Search Domain.com.au by suburb. mode is sale, rent, or sold. page starts at 1. Each card has a dollar price and a price label rebuilt from that amount. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/domaincomau/search}
   */
  public ApiResponse domaincomauSearch(DomaincomauSearchParams params) {
    return domaincomauSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/domaincomau/search} with per-request options. */
  public ApiResponse domaincomauSearch(DomaincomauSearchParams params, RequestOptions options) {
    return call("domaincomau_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * DuckDuckGo Search. Query DuckDuckGo via API and retrieve structured organic results with region and language. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/duckduckgo/search}
   */
  public ApiResponse duckduckgoSearch(DuckduckgoSearchParams params) {
    return duckduckgoSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/duckduckgo/search} with per-request options. */
  public ApiResponse duckduckgoSearch(DuckduckgoSearchParams params, RequestOptions options) {
    return call("duckduckgo_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * eBay Product. eBay Product API: one listing on a supported eBay site, by URL or item id (3 credits). Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/ebay/product}
   */
  public ApiResponse ebayProduct(EbayProductParams params) {
    return ebayProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/ebay/product} with per-request options. */
  public ApiResponse ebayProduct(EbayProductParams params, RequestOptions options) {
    return call("ebay_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * eBay Search. eBay Search API: search listings on supported eBay sites. Returns price, seller, and pagination (3 credits). Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/ebay/search}
   */
  public ApiResponse ebaySearch(EbaySearchParams params) {
    return ebaySearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/ebay/search} with per-request options. */
  public ApiResponse ebaySearch(EbaySearchParams params, RequestOptions options) {
    return call("ebay_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * El Corte Inglés Product. El Corte Inglés Product API. Fetch a full product page from elcorteingles.es by URL or product id. Returns pricing, stock, images and identifiers via browser rendering. Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/elcorteingles/product}
   */
  public ApiResponse elcorteinglesProduct(ElcorteinglesProductParams params) {
    return elcorteinglesProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/elcorteingles/product} with per-request options. */
  public ApiResponse elcorteinglesProduct(ElcorteinglesProductParams params, RequestOptions options) {
    return call("elcorteingles_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * El Corte Inglés Search. El Corte Inglés Search API. Search product listings on elcorteingles.es by keyword or URL. Returns prices, brands, images and pagination via webunlocker. Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/elcorteingles/search}
   */
  public ApiResponse elcorteinglesSearch(ElcorteinglesSearchParams params) {
    return elcorteinglesSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/elcorteingles/search} with per-request options. */
  public ApiResponse elcorteinglesSearch(ElcorteinglesSearchParams params, RequestOptions options) {
    return call("elcorteingles_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * E.Leclerc Product. Read one E.Leclerc product by EAN or product URL. Pass store_id to get that store's price and stock. Another store returns a different price and stock. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/eleclerc/product}
   */
  public ApiResponse eleclercProduct(EleclercProductParams params) {
    return eleclercProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/eleclerc/product} with per-request options. */
  public ApiResponse eleclercProduct(EleclercProductParams params, RequestOptions options) {
    return call("eleclerc_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * E.Leclerc Search. Search for products on E.Leclerc by keyword with optional store-specific filtering and pagination. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/eleclerc/search}
   */
  public ApiResponse eleclercSearch(EleclercSearchParams params) {
    return eleclercSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/eleclerc/search} with per-request options. */
  public ApiResponse eleclercSearch(EleclercSearchParams params, RequestOptions options) {
    return call("eleclerc_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * E.Leclerc Suggest. Get instant product autocomplete suggestions from E.Leclerc for a given keyword or EAN. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/eleclerc/suggest}
   */
  public ApiResponse eleclercSuggest(EleclercSuggestParams params) {
    return eleclercSuggest(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/eleclerc/suggest} with per-request options. */
  public ApiResponse eleclercSuggest(EleclercSuggestParams params, RequestOptions options) {
    return call("eleclerc_suggest", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Email Analyzes. Analyze a domain to retrieve its email infrastructure. Microsoft 365 and Google Workspace domains, SPF records, and DNS configurations. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/email/analyzes}
   */
  public ApiResponse emailAnalyzes(EmailAnalyzesParams params) {
    return emailAnalyzes(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/email/analyzes} with per-request options. */
  public ApiResponse emailAnalyzes(EmailAnalyzesParams params, RequestOptions options) {
    return call("email_analyzes", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Email Finder. Find verified professional email addresses by combining a person's full name with their company domain or company name. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/email/finder}
   */
  public ApiResponse emailFinder(EmailFinderParams params) {
    return emailFinder(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/email/finder} with per-request options. */
  public ApiResponse emailFinder(EmailFinderParams params, RequestOptions options) {
    return call("email_finder", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Email Verify. Verify whether an email address is valid and deliverable, returning a clear status to prevent bounces and maintain list hygiene. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/email/verify}
   */
  public ApiResponse emailVerify(EmailVerifyParams params) {
    return emailVerify(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/email/verify} with per-request options. */
  public ApiResponse emailVerify(EmailVerifyParams params, RequestOptions options) {
    return call("email_verify", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Europages Company. Europages company profile with address, supplier type and the products listed on the page (1 credit). Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/europages/company}
   */
  public ApiResponse europagesCompany(EuropagesCompanyParams params) {
    return europagesCompany(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/europages/company} with per-request options. */
  public ApiResponse europagesCompany(EuropagesCompanyParams params, RequestOptions options) {
    return call("europages_company", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Europages Search. Search Europages suppliers by keyword or category URL. Returns company cards and pagination (1 credit). Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/europages/search}
   */
  public ApiResponse europagesSearch(EuropagesSearchParams params) {
    return europagesSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/europages/search} with per-request options. */
  public ApiResponse europagesSearch(EuropagesSearchParams params, RequestOptions options) {
    return call("europages_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Eventbrite Event. Get one Eventbrite event by detail URL or numeric id: venue, organizer, schedule, and description (1 credit). Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/eventbrite/event}
   */
  public ApiResponse eventbriteEvent(EventbriteEventParams params) {
    return eventbriteEvent(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/eventbrite/event} with per-request options. */
  public ApiResponse eventbriteEvent(EventbriteEventParams params, RequestOptions options) {
    return call("eventbrite_event", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Eventbrite Search. Search Eventbrite events by keyword or paste a city/category URL. Returns event cards and pagination (1 credit). Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/eventbrite/search}
   */
  public ApiResponse eventbriteSearch(EventbriteSearchParams params) {
    return eventbriteSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/eventbrite/search} with per-request options. */
  public ApiResponse eventbriteSearch(EventbriteSearchParams params, RequestOptions options) {
    return call("eventbrite_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Expedia Ad. Fetch one Expedia hotel from its information URL: name, postal address, and amenity groups. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/expedia/ad}
   */
  public ApiResponse expediaAd(ExpediaAdParams params) {
    return expediaAd(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/expedia/ad} with per-request options. */
  public ApiResponse expediaAd(ExpediaAdParams params, RequestOptions options) {
    return call("expedia_ad", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Expedia Flight. Search Expedia flights by airport route and dates, or by a flight search URL. Each offer includes the airline, times, stops, and displayed price. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/expedia/flight}
   */
  public ApiResponse expediaFlight(ExpediaFlightParams params) {
    return expediaFlight(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/expedia/flight} with per-request options. */
  public ApiResponse expediaFlight(ExpediaFlightParams params, RequestOptions options) {
    return call("expedia_flight", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Expedia Search. Execute a hotel search on Expedia using a direct search URL and receive structured hotel listings with prices, ratings, and availability. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/expedia/search}
   */
  public ApiResponse expediaSearch(ExpediaSearchParams params) {
    return expediaSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/expedia/search} with per-request options. */
  public ApiResponse expediaSearch(ExpediaSearchParams params, RequestOptions options) {
    return call("expedia_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Fashionphile Product. Load one Fashionphile product from a handle or a product URL. Keywords and search URLs belong on the search endpoint. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/fashionphile/product}
   */
  public ApiResponse fashionphileProduct(FashionphileProductParams params) {
    return fashionphileProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/fashionphile/product} with per-request options. */
  public ApiResponse fashionphileProduct(FashionphileProductParams params, RequestOptions options) {
    return call("fashionphile_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Fashionphile Search. Search Fashionphile by keywords, a search URL, a collection URL, or a products.json URL. A product handle or product URL belongs on the product endpoint. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/fashionphile/search}
   */
  public ApiResponse fashionphileSearch(FashionphileSearchParams params) {
    return fashionphileSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/fashionphile/search} with per-request options. */
  public ApiResponse fashionphileSearch(FashionphileSearchParams params, RequestOptions options) {
    return call("fashionphile_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Chewy Products Finder. Find Chewy product URLs by product name, ID, or any combination of filters to use in product detail and rendering API calls.
   *
   * <p>{@code GET / POST /v2/finder/chewy-products}
   */
  public ApiResponse finderChewyProducts(FinderChewyProductsParams params) {
    return finderChewyProducts(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/finder/chewy-products} with per-request options. */
  public ApiResponse finderChewyProducts(FinderChewyProductsParams params, RequestOptions options) {
    return call("finder_chewy_products", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Eleclerc Stores Finder. Search Eleclerc stores by name or city to retrieve contact details, opening hours, and geolocation.
   *
   * <p>{@code GET / POST /v2/finder/eleclerc-stores}
   */
  public ApiResponse finderEleclercStores(FinderEleclercStoresParams params) {
    return finderEleclercStores(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/finder/eleclerc-stores} with per-request options. */
  public ApiResponse finderEleclercStores(FinderEleclercStoresParams params, RequestOptions options) {
    return call("finder_eleclerc_stores", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Google Countries Finder. Search the list of Google-supported countries by name or country code to use in Google Search API requests.
   *
   * <p>{@code GET / POST /v2/finder/google-countries}
   */
  public ApiResponse finderGoogleCountries(FinderGoogleCountriesParams params) {
    return finderGoogleCountries(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/finder/google-countries} with per-request options. */
  public ApiResponse finderGoogleCountries(FinderGoogleCountriesParams params, RequestOptions options) {
    return call("finder_google_countries", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Google Languages Finder. Search the list of Google-supported languages by name to retrieve language codes for use in Google Search API requests.
   *
   * <p>{@code GET / POST /v2/finder/google-languages}
   */
  public ApiResponse finderGoogleLanguages(FinderGoogleLanguagesParams params) {
    return finderGoogleLanguages(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/finder/google-languages} with per-request options. */
  public ApiResponse finderGoogleLanguages(FinderGoogleLanguagesParams params, RequestOptions options) {
    return call("finder_google_languages", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Google Locations Finder. Search Google-supported locations by name to retrieve location IDs and GPS coordinates for geo-targeted search API calls.
   *
   * <p>{@code GET / POST /v2/finder/google-locations}
   */
  public ApiResponse finderGoogleLocations(FinderGoogleLocationsParams params) {
    return finderGoogleLocations(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/finder/google-locations} with per-request options. */
  public ApiResponse finderGoogleLocations(FinderGoogleLocationsParams params, RequestOptions options) {
    return call("finder_google_locations", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Leroy Merlin Products Finder. Find Leroy Merlin product URLs by product name, ID, or any combination of filters to use in product detail and rendering API calls.
   *
   * <p>{@code GET / POST /v2/finder/leroymerlin-products}
   */
  public ApiResponse finderLeroymerlinProducts(FinderLeroymerlinProductsParams params) {
    return finderLeroymerlinProducts(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/finder/leroymerlin-products} with per-request options. */
  public ApiResponse finderLeroymerlinProducts(FinderLeroymerlinProductsParams params, RequestOptions options) {
    return call("finder_leroymerlin_products", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Leroy Merlin Stores Finder. Search Leroy Merlin stores by name, city or postal code to retrieve their store ID, slug, address and store locator URL.
   *
   * <p>{@code GET / POST /v2/finder/leroymerlin-stores}
   */
  public ApiResponse finderLeroymerlinStores(FinderLeroymerlinStoresParams params) {
    return finderLeroymerlinStores(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/finder/leroymerlin-stores} with per-request options. */
  public ApiResponse finderLeroymerlinStores(FinderLeroymerlinStoresParams params, RequestOptions options) {
    return call("finder_leroymerlin_stores", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * LinkedIn Industries Finder. Search LinkedIn industry taxonomy to retrieve industry IDs and hierarchies for use in LinkedIn API calls and data enrichment.
   *
   * <p>{@code GET / POST /v2/finder/linkedin-industries}
   */
  public ApiResponse finderLinkedinIndustries(FinderLinkedinIndustriesParams params) {
    return finderLinkedinIndustries(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/finder/linkedin-industries} with per-request options. */
  public ApiResponse finderLinkedinIndustries(FinderLinkedinIndustriesParams params, RequestOptions options) {
    return call("finder_linkedin_industries", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Mr.Bricolage Stores Finder. Search Mr.Bricolage stores by name, city or postal code to retrieve their store ID, slug, contact information and geolocation.
   *
   * <p>{@code GET / POST /v2/finder/mrbricolage-stores}
   */
  public ApiResponse finderMrbricolageStores(FinderMrbricolageStoresParams params) {
    return finderMrbricolageStores(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/finder/mrbricolage-stores} with per-request options. */
  public ApiResponse finderMrbricolageStores(FinderMrbricolageStoresParams params, RequestOptions options) {
    return call("finder_mrbricolage_stores", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Fiverr Profile Info. Extract complete public profile data from any Fiverr seller, including gigs, ratings, reviews, skills, portfolio, and consultation pricing. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/fiverr/profile/info}
   */
  public ApiResponse fiverrProfileInfo(FiverrProfileInfoParams params) {
    return fiverrProfileInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/fiverr/profile/info} with per-request options. */
  public ApiResponse fiverrProfileInfo(FiverrProfileInfoParams params, RequestOptions options) {
    return call("fiverr_profile_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Fiverr Search. Search Fiverr gigs using a direct search URL and receive paginated listings with seller information, gig titles, images, and seller levels. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/fiverr/search}
   */
  public ApiResponse fiverrSearch(FiverrSearchParams params) {
    return fiverrSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/fiverr/search} with per-request options. */
  public ApiResponse fiverrSearch(FiverrSearchParams params, RequestOptions options) {
    return call("fiverr_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Fnac Product. Read one Fnac product from its URL or product id. You get the price, brand, EAN, seller, and rating. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/fnac/product}
   */
  public ApiResponse fnacProduct(FnacProductParams params) {
    return fnacProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/fnac/product} with per-request options. */
  public ApiResponse fnacProduct(FnacProductParams params, RequestOptions options) {
    return call("fnac_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Fnac Search. Search Fnac for a keyword or a SearchResult URL. Each card has the product id, name, price, seller, and stock. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/fnac/search}
   */
  public ApiResponse fnacSearch(FnacSearchParams params) {
    return fnacSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/fnac/search} with per-request options. */
  public ApiResponse fnacSearch(FnacSearchParams params, RequestOptions options) {
    return call("fnac_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Fotocasa Property Ad. Get a full Fotocasa listing by URL: price, features, coordinates, energy certificate, images, and agency (browser rendering, 2 credits). Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/fotocasa/ad}
   */
  public ApiResponse fotocasaAd(FotocasaAdParams params) {
    return fotocasaAd(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/fotocasa/ad} with per-request options. */
  public ApiResponse fotocasaAd(FotocasaAdParams params, RequestOptions options) {
    return call("fotocasa_ad", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Fotocasa Property Search. Search Fotocasa listings by city or search URL. Returns prices, rooms, area, coordinates, and agency data via browser rendering (2 credits). Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/fotocasa/search}
   */
  public ApiResponse fotocasaSearch(FotocasaSearchParams params) {
    return fotocasaSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/fotocasa/search} with per-request options. */
  public ApiResponse fotocasaSearch(FotocasaSearchParams params, RequestOptions options) {
    return call("fotocasa_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Franchise Direct Franchise. Read one Franchise Direct profile: cash requirement, franchise fee, investment range, and training. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/franchisedirect/franchise}
   */
  public ApiResponse franchisedirectFranchise(FranchisedirectFranchiseParams params) {
    return franchisedirectFranchise(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/franchisedirect/franchise} with per-request options. */
  public ApiResponse franchisedirectFranchise(FranchisedirectFranchiseParams params, RequestOptions options) {
    return call("franchisedirect_franchise", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Franchise Direct Search. Search Franchise Direct listings by keyword or listing URL. Each card includes a USD cash figure and a numeric minimum. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/franchisedirect/search}
   */
  public ApiResponse franchisedirectSearch(FranchisedirectSearchParams params) {
    return franchisedirectSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/franchisedirect/search} with per-request options. */
  public ApiResponse franchisedirectSearch(FranchisedirectSearchParams params, RequestOptions options) {
    return call("franchisedirect_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Fressnapf Product. Read a Fressnapf Germany product sheet from its URL. Returns sku, GTIN, euro price, unit price, and pack variants. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/fressnapf/product}
   */
  public ApiResponse fressnapfProduct(FressnapfProductParams params) {
    return fressnapfProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/fressnapf/product} with per-request options. */
  public ApiResponse fressnapfProduct(FressnapfProductParams params, RequestOptions options) {
    return call("fressnapf_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Fressnapf Search. Search Fressnapf Germany by keyword or category URL. Returns product cards with euro prices and pagination. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/fressnapf/search}
   */
  public ApiResponse fressnapfSearch(FressnapfSearchParams params) {
    return fressnapfSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/fressnapf/search} with per-request options. */
  public ApiResponse fressnapfSearch(FressnapfSearchParams params, RequestOptions options) {
    return call("fressnapf_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Funda Property. Read one Funda listing from its URL. You get the asking price, living area, and the feature groups. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/funda/property}
   */
  public ApiResponse fundaProperty(FundaPropertyParams params) {
    return fundaProperty(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/funda/property} with per-request options. */
  public ApiResponse fundaProperty(FundaPropertyParams params, RequestOptions options) {
    return call("funda_property", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Funda Search. Search Funda by place. Set offering to koop for sale or huur for rent, then page the listings. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/funda/search}
   */
  public ApiResponse fundaSearch(FundaSearchParams params) {
    return fundaSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/funda/search} with per-request options. */
  public ApiResponse fundaSearch(FundaSearchParams params, RequestOptions options) {
    return call("funda_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * G2 Product Info. Read a G2 product reviews page: rating, pricing, categories, alternatives, screenshots, and the reviews shown on the page. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/g2/product/info}
   */
  public ApiResponse g2ProductInfo(G2ProductInfoParams params) {
    return g2ProductInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/g2/product/info} with per-request options. */
  public ApiResponse g2ProductInfo(G2ProductInfoParams params, RequestOptions options) {
    return call("g2_product_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * GitHub User Info. Retrieve public GitHub user profile data including bio, company, location, follower count, repositories, and associated email addresses. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/github/user/info}
   */
  public ApiResponse githubUserInfo(GithubUserInfoParams params) {
    return githubUserInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/github/user/info} with per-request options. */
  public ApiResponse githubUserInfo(GithubUserInfoParams params, RequestOptions options) {
    return call("github_user_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Glassdoor Company Info. Get one Glassdoor company profile by URL or employer id (2 credits). Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/glassdoor/company/info}
   */
  public ApiResponse glassdoorCompanyInfo(GlassdoorCompanyInfoParams params) {
    return glassdoorCompanyInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/glassdoor/company/info} with per-request options. */
  public ApiResponse glassdoorCompanyInfo(GlassdoorCompanyInfoParams params, RequestOptions options) {
    return call("glassdoor_company_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Glassdoor Company Search. Search Glassdoor employers by keyword across country sites (2 credits). Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/glassdoor/company/search}
   */
  public ApiResponse glassdoorCompanySearch(GlassdoorCompanySearchParams params) {
    return glassdoorCompanySearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/glassdoor/company/search} with per-request options. */
  public ApiResponse glassdoorCompanySearch(GlassdoorCompanySearchParams params, RequestOptions options) {
    return call("glassdoor_company_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Glassdoor Job Info. Get one Glassdoor job listing by URL or jl id (2 credits). Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/glassdoor/job/info}
   */
  public ApiResponse glassdoorJobInfo(GlassdoorJobInfoParams params) {
    return glassdoorJobInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/glassdoor/job/info} with per-request options. */
  public ApiResponse glassdoorJobInfo(GlassdoorJobInfoParams params, RequestOptions options) {
    return call("glassdoor_job_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Glassdoor Job Search. Search Glassdoor jobs by keyword or SEO URL, with domain and filters (2 credits). Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/glassdoor/job/search}
   */
  public ApiResponse glassdoorJobSearch(GlassdoorJobSearchParams params) {
    return glassdoorJobSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/glassdoor/job/search} with per-request options. */
  public ApiResponse glassdoorJobSearch(GlassdoorJobSearchParams params, RequestOptions options) {
    return call("glassdoor_job_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Goodreads Book. Read one Goodreads book by the numeric id from search, such as 44767458 for Dune. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/goodreads/book}
   */
  public ApiResponse goodreadsBook(GoodreadsBookParams params) {
    return goodreadsBook(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/goodreads/book} with per-request options. */
  public ApiResponse goodreadsBook(GoodreadsBookParams params, RequestOptions options) {
    return call("goodreads_book", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Goodreads Search. Search Goodreads books by keyword. pagination.next is a Goodreads search URL for the following page. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/goodreads/search}
   */
  public ApiResponse goodreadsSearch(GoodreadsSearchParams params) {
    return goodreadsSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/goodreads/search} with per-request options. */
  public ApiResponse goodreadsSearch(GoodreadsSearchParams params, RequestOptions options) {
    return call("goodreads_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Google Images. Google Images Scraper API – bringing the power of visual search to your fingertips. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/google/images}
   */
  public ApiResponse googleImages(GoogleImagesParams params) {
    return googleImages(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/google/images} with per-request options. */
  public ApiResponse googleImages(GoogleImagesParams params, RequestOptions options) {
    return call("google_images", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Google Jobs. Extract job listings from Google Careers for enhanced recruitment and market analysis. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/google/jobs}
   */
  public ApiResponse googleJobs(GoogleJobsParams params) {
    return googleJobs(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/google/jobs} with per-request options. */
  public ApiResponse googleJobs(GoogleJobsParams params, RequestOptions options) {
    return call("google_jobs", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Google Maps. Extract comprehensive company data from Google Maps for enhanced business insights. Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/google/maps}
   */
  public ApiResponse googleMaps(GoogleMapsParams params) {
    return googleMaps(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/google/maps} with per-request options. */
  public ApiResponse googleMaps(GoogleMapsParams params, RequestOptions options) {
    return call("google_maps", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Google News. Extract real-time news articles from Google News with advanced filtering by topic, location, language, and pagination. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/google/news}
   */
  public ApiResponse googleNews(GoogleNewsParams params) {
    return googleNews(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/google/news} with per-request options. */
  public ApiResponse googleNews(GoogleNewsParams params, RequestOptions options) {
    return call("google_news", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Google Search. Scrape real-time Google search results with organic listings, knowledge graph, related searches, and advanced filters. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/google/search}
   */
  public ApiResponse googleSearch(GoogleSearchParams params) {
    return googleSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/google/search} with per-request options. */
  public ApiResponse googleSearch(GoogleSearchParams params, RequestOptions options) {
    return call("google_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Google AI Mode. Scrape real-time Google AI Mode answers: text_blocks, references, and shopping_results. Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/google/search/ai}
   */
  public ApiResponse googleSearchAi(GoogleSearchAiParams params) {
    return googleSearchAi(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/google/search/ai} with per-request options. */
  public ApiResponse googleSearchAi(GoogleSearchAiParams params, RequestOptions options) {
    return call("google_search_ai", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Google Search Autocomplete. Retrieve real-time Google Search autocomplete suggestions for any query, with support for language and country localization. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/google/search/autocomplete}
   */
  public ApiResponse googleSearchAutocomplete(GoogleSearchAutocompleteParams params) {
    return googleSearchAutocomplete(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/google/search/autocomplete} with per-request options. */
  public ApiResponse googleSearchAutocomplete(GoogleSearchAutocompleteParams params, RequestOptions options) {
    return call("google_search_autocomplete", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Google Shopping. Search Google Shopping and retrieve structured product listings with prices, ratings, merchants, and product IDs. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/google/shopping}
   */
  public ApiResponse googleShopping(GoogleShoppingParams params) {
    return googleShopping(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/google/shopping} with per-request options. */
  public ApiResponse googleShopping(GoogleShoppingParams params, RequestOptions options) {
    return call("google_shopping", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Google Videos. Scrape video results from Google Videos search including YouTube, Reddit, and other video platforms, with location and language support. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/google/videos}
   */
  public ApiResponse googleVideos(GoogleVideosParams params) {
    return googleVideos(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/google/videos} with per-request options. */
  public ApiResponse googleVideos(GoogleVideosParams params, RequestOptions options) {
    return call("google_videos", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Google Ads Transparency Ad. Get one Google Ads Transparency creative by URL: dates, impressions, platforms, regions, and the text read from the preview image (2 credits). Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/googleadstransparency/ad}
   */
  public ApiResponse googleadstransparencyAd(GoogleadstransparencyAdParams params) {
    return googleadstransparencyAd(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/googleadstransparency/ad} with per-request options. */
  public ApiResponse googleadstransparencyAd(GoogleadstransparencyAdParams params, RequestOptions options) {
    return call("googleadstransparency_ad", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Google Ads Transparency Search. Search the Google Ads Transparency Center by domain, advertiser, region, format, platform, and date. Returns creative cards and pagination (2 credits). Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/googleadstransparency/search}
   */
  public ApiResponse googleadstransparencySearch(GoogleadstransparencySearchParams params) {
    return googleadstransparencySearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/googleadstransparency/search} with per-request options. */
  public ApiResponse googleadstransparencySearch(GoogleadstransparencySearchParams params, RequestOptions options) {
    return call("googleadstransparency_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Grainger Product. Get a Grainger US or Canada product page: SKU, brand, price, images, description and specs via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/grainger/product}
   */
  public ApiResponse graingerProduct(GraingerProductParams params) {
    return graingerProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/grainger/product} with per-request options. */
  public ApiResponse graingerProduct(GraingerProductParams params, RequestOptions options) {
    return call("grainger_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Grainger Search. Search Grainger US or Canada by keyword, category URL or SKU. Returns product listings, category branches and suggestions via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/grainger/search}
   */
  public ApiResponse graingerSearch(GraingerSearchParams params) {
    return graingerSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/grainger/search} with per-request options. */
  public ApiResponse graingerSearch(GraingerSearchParams params, RequestOptions options) {
    return call("grainger_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Greenhouse Job. Fetch one Greenhouse job by board/id, such as discord/8806482002, or by a job-boards.greenhouse.io job URL. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/greenhouse/job}
   */
  public ApiResponse greenhouseJob(GreenhouseJobParams params) {
    return greenhouseJob(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/greenhouse/job} with per-request options. */
  public ApiResponse greenhouseJob(GreenhouseJobParams params, RequestOptions options) {
    return call("greenhouse_job", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Greenhouse Search. List jobs on a Greenhouse board. query is the board token, such as discord. keyword filters title and location. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/greenhouse/search}
   */
  public ApiResponse greenhouseSearch(GreenhouseSearchParams params) {
    return greenhouseSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/greenhouse/search} with per-request options. */
  public ApiResponse greenhouseSearch(GreenhouseSearchParams params, RequestOptions options) {
    return call("greenhouse_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * GSA eLibrary Contract. Read one GSA eLibrary contractor record from the contract URL returned by search. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/gsaelibrary/contract}
   */
  public ApiResponse gsaelibraryContract(GsaelibraryContractParams params) {
    return gsaelibraryContract(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/gsaelibrary/contract} with per-request options. */
  public ApiResponse gsaelibraryContract(GsaelibraryContractParams params, RequestOptions options) {
    return call("gsaelibrary_contract", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * GSA eLibrary Search. Search GSA eLibrary for schedule, SIN, and contract rows by keyword or by a full search URL. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/gsaelibrary/search}
   */
  public ApiResponse gsaelibrarySearch(GsaelibrarySearchParams params) {
    return gsaelibrarySearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/gsaelibrary/search} with per-request options. */
  public ApiResponse gsaelibrarySearch(GsaelibrarySearchParams params, RequestOptions options) {
    return call("gsaelibrary_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Homestra Ad. Retrieve detailed property listing data from Homestra, including price, size, location, features, photos, and nearby properties. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/homestra/ad}
   */
  public ApiResponse homestraAd(HomestraAdParams params) {
    return homestraAd(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/homestra/ad} with per-request options. */
  public ApiResponse homestraAd(HomestraAdParams params, RequestOptions options) {
    return call("homestra_ad", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Homestra Search. Search Homestra property listings using a direct search URL with filters, returning structured results with prices, sizes, locations, and photos. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/homestra/search}
   */
  public ApiResponse homestraSearch(HomestraSearchParams params) {
    return homestraSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/homestra/search} with per-request options. */
  public ApiResponse homestraSearch(HomestraSearchParams params, RequestOptions options) {
    return call("homestra_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Hugging Face Model. Read one public Hugging Face model by an id such as google-bert/bert-base-uncased. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/huggingface/model}
   */
  public ApiResponse huggingfaceModel(HuggingfaceModelParams params) {
    return huggingfaceModel(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/huggingface/model} with per-request options. */
  public ApiResponse huggingfaceModel(HuggingfaceModelParams params, RequestOptions options) {
    return call("huggingface_model", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Hugging Face Search. Search the public Hugging Face models API by keyword and follow pagination.next to the next cursor page. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/huggingface/search}
   */
  public ApiResponse huggingfaceSearch(HuggingfaceSearchParams params) {
    return huggingfaceSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/huggingface/search} with per-request options. */
  public ApiResponse huggingfaceSearch(HuggingfaceSearchParams params, RequestOptions options) {
    return call("huggingface_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Idealista Property. Extract full property details from an Idealista listing URL, including price, specs, photos by room, features, energy rating, and advertiser data. Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/idealista/property}
   */
  public ApiResponse idealistaProperty(IdealistaPropertyParams params) {
    return idealistaProperty(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/idealista/property} with per-request options. */
  public ApiResponse idealistaProperty(IdealistaPropertyParams params, RequestOptions options) {
    return call("idealista_property", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Idealista Provinces. List the 50 Spanish provinces Idealista shows for homes for sale. Each row includes the sale search URL to pass as query to Idealista Search. Madrid is madrid-provincia. Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/idealista/provinces}
   */
  public ApiResponse idealistaProvinces(IdealistaProvincesParams params) {
    return idealistaProvinces(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/idealista/provinces} with per-request options. */
  public ApiResponse idealistaProvinces(IdealistaProvincesParams params, RequestOptions options) {
    return call("idealista_provinces", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Idealista Search. Search Idealista listings from a search URL and retrieve prices, specs, photos, agency info, and pagination. Pass a province URL from /v2/idealista/provinces (results[].url) as query. Madrid is `https://www.idealista.com/venta-vivien… Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/idealista/search}
   */
  public ApiResponse idealistaSearch(IdealistaSearchParams params) {
    return idealistaSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/idealista/search} with per-request options. */
  public ApiResponse idealistaSearch(IdealistaSearchParams params, RequestOptions options) {
    return call("idealista_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Idealo Product. Get an Idealo product from Spain, France, or Germany. URL or product id. Offers, shipping, specs, and images via WebUnlocker (3 credits). Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/idealo/product}
   */
  public ApiResponse idealoProduct(IdealoProductParams params) {
    return idealoProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/idealo/product} with per-request options. */
  public ApiResponse idealoProduct(IdealoProductParams params, RequestOptions options) {
    return call("idealo_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Idealo Search. Search Idealo Spain, France, and Germany. Keyword or search URL. Prices, shipping, ratings, and pagination via WebUnlocker (3 credits). Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/idealo/search}
   */
  public ApiResponse idealoSearch(IdealoSearchParams params) {
    return idealoSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/idealo/search} with per-request options. */
  public ApiResponse idealoSearch(IdealoSearchParams params, RequestOptions options) {
    return call("idealo_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * IKEA Product. Extract full IKEA product details from a product URL, including price, gallery, variants, dimensions, and breadcrumb. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/ikea/product}
   */
  public ApiResponse ikeaProduct(IkeaProductParams params) {
    return ikeaProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/ikea/product} with per-request options. */
  public ApiResponse ikeaProduct(IkeaProductParams params, RequestOptions options) {
    return call("ikea_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * IKEA Search. Search IKEA products from a search URL and retrieve prices, ratings, dimensions, images, and pagination. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/ikea/search}
   */
  public ApiResponse ikeaSearch(IkeaSearchParams params) {
    return ikeaSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/ikea/search} with per-request options. */
  public ApiResponse ikeaSearch(IkeaSearchParams params, RequestOptions options) {
    return call("ikea_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * ImmobilienScout24 Property. Read one ImmobilienScout24 expose by URL or numeric id. You get cold rent or purchase price, address, and area. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/immobilienscout24/property}
   */
  public ApiResponse immobilienscout24Property(Immobilienscout24PropertyParams params) {
    return immobilienscout24Property(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/immobilienscout24/property} with per-request options. */
  public ApiResponse immobilienscout24Property(Immobilienscout24PropertyParams params, RequestOptions options) {
    return call("immobilienscout24_property", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * ImmobilienScout24 Search. Search ImmobilienScout24 by region and property type. real_estate_type covers rent, buy, house, and land. Page with pagenumber. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/immobilienscout24/search}
   */
  public ApiResponse immobilienscout24Search(Immobilienscout24SearchParams params) {
    return immobilienscout24Search(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/immobilienscout24/search} with per-request options. */
  public ApiResponse immobilienscout24Search(Immobilienscout24SearchParams params, RequestOptions options) {
    return call("immobilienscout24_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * ImmoScout24 Switzerland Search. Search ImmoScout24.ch listings by city or search URL and retrieve the CHF price, rooms, living area, and address. Property pages are not available. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/immoscout24ch/search}
   */
  public ApiResponse immoscout24chSearch(Immoscout24chSearchParams params) {
    return immoscout24chSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/immoscout24ch/search} with per-request options. */
  public ApiResponse immoscout24chSearch(Immoscout24chSearchParams params, RequestOptions options) {
    return call("immoscout24ch_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Immowelt Property. Extract full property details from an Immowelt listing URL, including price, specs, photo gallery, energy certificate, location, and contact data. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/immowelt/property}
   */
  public ApiResponse immoweltProperty(ImmoweltPropertyParams params) {
    return immoweltProperty(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/immowelt/property} with per-request options. */
  public ApiResponse immoweltProperty(ImmoweltPropertyParams params, RequestOptions options) {
    return call("immowelt_property", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Immowelt Search. Search Immowelt listings from a search URL and retrieve prices, specs, photos, agency info, and pagination. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/immowelt/search}
   */
  public ApiResponse immoweltSearch(ImmoweltSearchParams params) {
    return immoweltSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/immowelt/search} with per-request options. */
  public ApiResponse immoweltSearch(ImmoweltSearchParams params, RequestOptions options) {
    return call("immowelt_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * ImoveisWeb Property. Read one Imobiliária Aquários listing from its imoveisweb.com.br URL. price_display is rebuilt from the asking amount. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/imoveisweb/property}
   */
  public ApiResponse imoveiswebProperty(ImoveiswebPropertyParams params) {
    return imoveiswebProperty(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/imoveisweb/property} with per-request options. */
  public ApiResponse imoveiswebProperty(ImoveiswebPropertyParams params, RequestOptions options) {
    return call("imoveisweb_property", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * ImoveisWeb Search. Search the Imobiliária Aquários catalog on imoveisweb.com.br. Pass a catalog name such as casas. price_display is rebuilt from the asking amount. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/imoveisweb/search}
   */
  public ApiResponse imoveiswebSearch(ImoveiswebSearchParams params) {
    return imoveiswebSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/imoveisweb/search} with per-request options. */
  public ApiResponse imoveiswebSearch(ImoveiswebSearchParams params, RequestOptions options) {
    return call("imoveisweb_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Indeed Company Info. Indeed Company Info API. Full employer profile by slug or URL. Browser rendering returns headcount, revenue, industry and FAQ or review blocks. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/indeed/company/info}
   */
  public ApiResponse indeedCompanyInfo(IndeedCompanyInfoParams params) {
    return indeedCompanyInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/indeed/company/info} with per-request options. */
  public ApiResponse indeedCompanyInfo(IndeedCompanyInfoParams params, RequestOptions options) {
    return call("indeed_company_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Indeed Company Search. Indeed Company Search API. Search fr.indeed.com employer profiles by keyword or URL. Browser rendering returns ratings, industry and profile links. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/indeed/company/search}
   */
  public ApiResponse indeedCompanySearch(IndeedCompanySearchParams params) {
    return indeedCompanySearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/indeed/company/search} with per-request options. */
  public ApiResponse indeedCompanySearch(IndeedCompanySearchParams params, RequestOptions options) {
    return call("indeed_company_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Indeed Job Info. Indeed Job Info API. Fetch a full job posting by viewjob URL, sponsored click URL or job key. Browser rendering returns description and structured salary. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/indeed/job/info}
   */
  public ApiResponse indeedJobInfo(IndeedJobInfoParams params) {
    return indeedJobInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/indeed/job/info} with per-request options. */
  public ApiResponse indeedJobInfo(IndeedJobInfoParams params, RequestOptions options) {
    return call("indeed_job_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Indeed Job Salary. Indeed Job Salary API. Career salary aggregates by job title slug or URL. Browser rendering returns median, percentiles and top paying cities. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/indeed/job/salary}
   */
  public ApiResponse indeedJobSalary(IndeedJobSalaryParams params) {
    return indeedJobSalary(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/indeed/job/salary} with per-request options. */
  public ApiResponse indeedJobSalary(IndeedJobSalaryParams params, RequestOptions options) {
    return call("indeed_job_salary", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Indeed Job Search. Indeed Job Search API. Search fr.indeed.com job listings by keyword or URL. Browser rendering returns cards with salary snippets and pagination. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/indeed/job/search}
   */
  public ApiResponse indeedJobSearch(IndeedJobSearchParams params) {
    return indeedJobSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/indeed/job/search} with per-request options. */
  public ApiResponse indeedJobSearch(IndeedJobSearchParams params, RequestOptions options) {
    return call("indeed_job_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Inet Product. Read one Inet.se product by article number or URL, including SEK price, specs, and warehouse stock. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/inet/product}
   */
  public ApiResponse inetProduct(InetProductParams params) {
    return inetProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/inet/product} with per-request options. */
  public ApiResponse inetProduct(InetProductParams params, RequestOptions options) {
    return call("inet_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Inet Search. Search Inet.se by keyword or category. Prices are inc-VAT SEK, with the next page URL when more results exist. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/inet/search}
   */
  public ApiResponse inetSearch(InetSearchParams params) {
    return inetSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/inet/search} with per-request options. */
  public ApiResponse inetSearch(InetSearchParams params, RequestOptions options) {
    return call("inet_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Instagram Post Info. Monitor competitors and market trends by scraping Instagram posts for valuable data. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/instagram/post/info}
   */
  public ApiResponse instagramPostInfo(InstagramPostInfoParams params) {
    return instagramPostInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/instagram/post/info} with per-request options. */
  public ApiResponse instagramPostInfo(InstagramPostInfoParams params, RequestOptions options) {
    return call("instagram_post_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Instagram User Info. Gather user insights efficiently from Instagram for targeted marketing strategies. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/instagram/user/info}
   */
  public ApiResponse instagramUserInfo(InstagramUserInfoParams params) {
    return instagramUserInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/instagram/user/info} with per-request options. */
  public ApiResponse instagramUserInfo(InstagramUserInfoParams params, RequestOptions options) {
    return call("instagram_user_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Intermarché Product. Get an Intermarché product sheet for a store (shop_id). Try an EAN or product URL with shop_id=11301. 3 credits per call. Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/intermarche/product}
   */
  public ApiResponse intermarcheProduct(IntermarcheProductParams params) {
    return intermarcheProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/intermarche/product} with per-request options. */
  public ApiResponse intermarcheProduct(IntermarcheProductParams params, RequestOptions options) {
    return call("intermarche_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Intermarché Search. Search Intermarché products for a store (shop_id). Try query=lait and shop_id=11301. 3 credits per call. Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/intermarche/search}
   */
  public ApiResponse intermarcheSearch(IntermarcheSearchParams params) {
    return intermarcheSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/intermarche/search} with per-request options. */
  public ApiResponse intermarcheSearch(IntermarcheSearchParams params, RequestOptions options) {
    return call("intermarche_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Intersport Product. Read one Intersport product from its URL. You get price, brand, and the product details. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/intersport/product}
   */
  public ApiResponse intersportProduct(IntersportProductParams params) {
    return intersportProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/intersport/product} with per-request options. */
  public ApiResponse intersportProduct(IntersportProductParams params, RequestOptions options) {
    return call("intersport_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Intersport Search. Search Intersport by keyword and page the sports grid. Cards include title, price, and product link. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/intersport/search}
   */
  public ApiResponse intersportSearch(IntersportSearchParams params) {
    return intersportSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/intersport/search} with per-request options. */
  public ApiResponse intersportSearch(IntersportSearchParams params, RequestOptions options) {
    return call("intersport_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * JamesEdition Property. Read one JamesEdition listing from its URL. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/jamesedition/property}
   */
  public ApiResponse jameseditionProperty(JameseditionPropertyParams params) {
    return jameseditionProperty(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/jamesedition/property} with per-request options. */
  public ApiResponse jameseditionProperty(JameseditionPropertyParams params, RequestOptions options) {
    return call("jamesedition_property", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * JamesEdition Search. Search JamesEdition luxury homes by keyword or by a results URL. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/jamesedition/search}
   */
  public ApiResponse jameseditionSearch(JameseditionSearchParams params) {
    return jameseditionSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/jamesedition/search} with per-request options. */
  public ApiResponse jameseditionSearch(JameseditionSearchParams params, RequestOptions options) {
    return call("jamesedition_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Jobs. Poll the status, progress (0–100) and result of a job started with is_async=true. Polling is free. Poll until status is terminal: CompletedJob, Error, NotFound or BadRequest.
   *
   * <p>{@code GET /v1/jobs/{id}}
   */
  public ApiResponse jobs(JobsParams params) {
    return jobs(params, RequestOptions.defaults());
  }

  /** {@code GET /v1/jobs/{id}} with per-request options. */
  public ApiResponse jobs(JobsParams params, RequestOptions options) {
    return call("jobs", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Kick User Info. Retrieve complete public channel data from Kick including streamer profile, follower count, live status, and recent categories. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/kick/user/info}
   */
  public ApiResponse kickUserInfo(KickUserInfoParams params) {
    return kickUserInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/kick/user/info} with per-request options. */
  public ApiResponse kickUserInfo(KickUserInfoParams params, RequestOptions options) {
    return call("kick_user_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Kijiji Ad. Read one Kijiji ad from its id or URL. You get the asking price, the title, and the city. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/kijiji/ad}
   */
  public ApiResponse kijijiAd(KijijiAdParams params) {
    return kijijiAd(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/kijiji/ad} with per-request options. */
  public ApiResponse kijijiAd(KijijiAdParams params, RequestOptions options) {
    return call("kijiji_ad", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Kijiji Search. Search Kijiji with a results URL or a city and category. Language can be English or French. Page through classified ads. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/kijiji/search}
   */
  public ApiResponse kijijiSearch(KijijiSearchParams params) {
    return kijijiSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/kijiji/search} with per-request options. */
  public ApiResponse kijijiSearch(KijijiSearchParams params, RequestOptions options) {
    return call("kijiji_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Kroger Product. Read one Kroger product by UPC. The sheet uses the same default store as search. You get the shelf price and the previous price when Kroger shows one. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/kroger/product}
   */
  public ApiResponse krogerProduct(KrogerProductParams params) {
    return krogerProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/kroger/product} with per-request options. */
  public ApiResponse krogerProduct(KrogerProductParams params, RequestOptions options) {
    return call("kroger_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Kroger Search. Search Kroger groceries by keyword and page the results. Prices come from the default pickup store returned in the response. A location id you pass is not applied. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/kroger/search}
   */
  public ApiResponse krogerSearch(KrogerSearchParams params) {
    return krogerSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/kroger/search} with per-request options. */
  public ApiResponse krogerSearch(KrogerSearchParams params, RequestOptions options) {
    return call("kroger_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Lamudi Ad. Get a full Lamudi listing by URL: price, area, amenities, coordinates, images, and agent (browser rendering, 2 credits). Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/lamudi/ad}
   */
  public ApiResponse lamudiAd(LamudiAdParams params) {
    return lamudiAd(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/lamudi/ad} with per-request options. */
  public ApiResponse lamudiAd(LamudiAdParams params, RequestOptions options) {
    return call("lamudi_ad", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Lamudi Search. Search Lamudi Philippines and Indonesia listings. Pass a search URL in query, or use location and filters. Prices, area, rooms, tags, and pagination via browser rendering (2 credits). Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/lamudi/search}
   */
  public ApiResponse lamudiSearch(LamudiSearchParams params) {
    return lamudiSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/lamudi/search} with per-request options. */
  public ApiResponse lamudiSearch(LamudiSearchParams params, RequestOptions options) {
    return call("lamudi_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Leboncoin Ad. Extract full details from a Leboncoin ad including price, location, attributes, seller info, and images. Returns structured JSON by default, or raw HTML on demand. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/leboncoin/ad}
   */
  public ApiResponse leboncoinAd(LeboncoinAdParams params) {
    return leboncoinAd(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/leboncoin/ad} with per-request options. */
  public ApiResponse leboncoinAd(LeboncoinAdParams params, RequestOptions options) {
    return call("leboncoin_ad", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Leboncoin Search. Scrape Leboncoin search results or category pages by URL, returning paginated listings with price, location, and attributes. Returns structured JSON. return_page_source is deprecated and returns 400. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/leboncoin/search}
   */
  public ApiResponse leboncoinSearch(LeboncoinSearchParams params) {
    return leboncoinSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/leboncoin/search} with per-request options. */
  public ApiResponse leboncoinSearch(LeboncoinSearchParams params, RequestOptions options) {
    return call("leboncoin_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Leboncoin Search API. Search Leboncoin listings via structured POST API with advanced filters for category, location, price range, seller type, and sorting. Credit cost: 1 credit per call.
   *
   * <p>{@code POST /v2/leboncoin/search_api}
   */
  public ApiResponse leboncoinSearchApi(LeboncoinSearchApiParams params) {
    return leboncoinSearchApi(params, RequestOptions.defaults());
  }

  /** {@code POST /v2/leboncoin/search_api} with per-request options. */
  public ApiResponse leboncoinSearchApi(LeboncoinSearchApiParams params, RequestOptions options) {
    return call("leboncoin_search_api", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Lenovo Product. Extract a Lenovo product page by URL: id, name, price, currency, availability, images, and description (browser rendering, 2 credits). Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/lenovo/product}
   */
  public ApiResponse lenovoProduct(LenovoProductParams params) {
    return lenovoProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/lenovo/product} with per-request options. */
  public ApiResponse lenovoProduct(LenovoProductParams params, RequestOptions options) {
    return call("lenovo_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Lenovo Search. Search Lenovo products by keyword or listing URL. Returns product id, price, currency, series, and availability via browser rendering (2 credits). Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/lenovo/search}
   */
  public ApiResponse lenovoSearch(LenovoSearchParams params) {
    return lenovoSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/lenovo/search} with per-request options. */
  public ApiResponse lenovoSearch(LenovoSearchParams params, RequestOptions options) {
    return call("lenovo_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Leroy Merlin Product. Live Leroy Merlin product detail for France and Romania from a URL or identifier: offers, currency, images, features and store context. Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/leroymerlin/product}
   */
  public ApiResponse leroymerlinProduct(LeroymerlinProductParams params) {
    return leroymerlinProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/leroymerlin/product} with per-request options. */
  public ApiResponse leroymerlinProduct(LeroymerlinProductParams params, RequestOptions options) {
    return call("leroymerlin_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Leroy Merlin Search. Live Leroy Merlin search across France and Romania: title, URL, price, currency, brand, seller, rating and pagination. Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/leroymerlin/search}
   */
  public ApiResponse leroymerlinSearch(LeroymerlinSearchParams params) {
    return leroymerlinSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/leroymerlin/search} with per-request options. */
  public ApiResponse leroymerlinSearch(LeroymerlinSearchParams params, RequestOptions options) {
    return call("leroymerlin_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Lidl Product. Lidl Product API: fetch full product page data from a Lidl URL or ERP id. Returns pricing, images, description, availability and category via Nuxt SSR parsing. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/lidl/product}
   */
  public ApiResponse lidlProduct(LidlProductParams params) {
    return lidlProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/lidl/product} with per-request options. */
  public ApiResponse lidlProduct(LidlProductParams params, RequestOptions options) {
    return call("lidl_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Lidl Search. Lidl Search API: search product listings by keyword or URL across Lidl online shops in 27 countries. Parses Nuxt SSR payload from HTML; returns prices, ratings, brand and pagination. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/lidl/search}
   */
  public ApiResponse lidlSearch(LidlSearchParams params) {
    return lidlSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/lidl/search} with per-request options. */
  public ApiResponse lidlSearch(LidlSearchParams params, RequestOptions options) {
    return call("lidl_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * LinkedIn Company Info. LinkedIn Company Info API. Fetch firmographics by LinkedIn slug, URL, company ID, or reverse domain lookup (guideflow.com). Industry, headcount, HQ, posts, and more. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/linkedin/company/info}
   */
  public ApiResponse linkedinCompanyInfo(LinkedinCompanyInfoParams params) {
    return linkedinCompanyInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/linkedin/company/info} with per-request options. */
  public ApiResponse linkedinCompanyInfo(LinkedinCompanyInfoParams params, RequestOptions options) {
    return call("linkedin_company_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * LinkedIn Job Count. Obtain real-time job counts on LinkedIn by industry, region, and role for strategic planning. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/linkedin/job/count}
   */
  public ApiResponse linkedinJobCount(LinkedinJobCountParams params) {
    return linkedinJobCount(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/linkedin/job/count} with per-request options. */
  public ApiResponse linkedinJobCount(LinkedinJobCountParams params, RequestOptions options) {
    return call("linkedin_job_count", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * LinkedIn Job Info. Dive into LinkedIn job data and trends for in-depth labor market insights. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/linkedin/job/info}
   */
  public ApiResponse linkedinJobInfo(LinkedinJobInfoParams params) {
    return linkedinJobInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/linkedin/job/info} with per-request options. */
  public ApiResponse linkedinJobInfo(LinkedinJobInfoParams params, RequestOptions options) {
    return call("linkedin_job_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * LinkedIn Job Search. Experience advanced job data extraction from LinkedIn, enhancing your recruitment strategy. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/linkedin/job/search}
   */
  public ApiResponse linkedinJobSearch(LinkedinJobSearchParams params) {
    return linkedinJobSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/linkedin/job/search} with per-request options. */
  public ApiResponse linkedinJobSearch(LinkedinJobSearchParams params, RequestOptions options) {
    return call("linkedin_job_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * LinkedIn Job Suggest. Get personalized job suggestions based on keywords or locations to streamline your job search. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/linkedin/job/suggest}
   */
  public ApiResponse linkedinJobSuggest(LinkedinJobSuggestParams params) {
    return linkedinJobSuggest(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/linkedin/job/suggest} with per-request options. */
  public ApiResponse linkedinJobSuggest(LinkedinJobSuggestParams params, RequestOptions options) {
    return call("linkedin_job_suggest", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * LinkedIn Post Info. Simplify LinkedIn post retrieval with our efficient API for enhanced content analysis. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/linkedin/post/info}
   */
  public ApiResponse linkedinPostInfo(LinkedinPostInfoParams params) {
    return linkedinPostInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/linkedin/post/info} with per-request options. */
  public ApiResponse linkedinPostInfo(LinkedinPostInfoParams params, RequestOptions options) {
    return call("linkedin_post_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * LinkedIn Product Info. Gain competitive insights with detailed LinkedIn product information for strategic advantage. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/linkedin/product/info}
   */
  public ApiResponse linkedinProductInfo(LinkedinProductInfoParams params) {
    return linkedinProductInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/linkedin/product/info} with per-request options. */
  public ApiResponse linkedinProductInfo(LinkedinProductInfoParams params, RequestOptions options) {
    return call("linkedin_product_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * LinkedIn Profile Info. Streamline recruitment and business development by extracting valuable LinkedIn profile insights. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/linkedin/profile/info}
   */
  public ApiResponse linkedinProfileInfo(LinkedinProfileInfoParams params) {
    return linkedinProfileInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/linkedin/profile/info} with per-request options. */
  public ApiResponse linkedinProfileInfo(LinkedinProfileInfoParams params, RequestOptions options) {
    return call("linkedin_profile_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * LinkedIn Ad Library Ad. Get one LinkedIn Ad Library creative by URL or id: copy, CTA, payer, impressions, and targeting (2 credits). Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/linkedinadlibrary/ad}
   */
  public ApiResponse linkedinadlibraryAd(LinkedinadlibraryAdParams params) {
    return linkedinadlibraryAd(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/linkedinadlibrary/ad} with per-request options. */
  public ApiResponse linkedinadlibraryAd(LinkedinadlibraryAdParams params, RequestOptions options) {
    return call("linkedinadlibrary_ad", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * LinkedIn Ad Library Search. Search the LinkedIn Ad Library by advertiser, keyword, country, and date. Returns ad cards and pagination (2 credits). Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/linkedinadlibrary/search}
   */
  public ApiResponse linkedinadlibrarySearch(LinkedinadlibrarySearchParams params) {
    return linkedinadlibrarySearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/linkedinadlibrary/search} with per-request options. */
  public ApiResponse linkedinadlibrarySearch(LinkedinadlibrarySearchParams params, RequestOptions options) {
    return call("linkedinadlibrary_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * LoopNet Property. Read one LoopNet commercial listing from its URL. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/loopnet/property}
   */
  public ApiResponse loopnetProperty(LoopnetPropertyParams params) {
    return loopnetProperty(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/loopnet/property} with per-request options. */
  public ApiResponse loopnetProperty(LoopnetPropertyParams params, RequestOptions options) {
    return call("loopnet_property", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * LoopNet Search. Search LoopNet commercial listings by city or by a results URL. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/loopnet/search}
   */
  public ApiResponse loopnetSearch(LoopnetSearchParams params) {
    return loopnetSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/loopnet/search} with per-request options. */
  public ApiResponse loopnetSearch(LoopnetSearchParams params, RequestOptions options) {
    return call("loopnet_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Maersk Tracking. Track a Maersk container or BOL: origin, destination, current milestone and full transport-plan events via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/maersk/tracking}
   */
  public ApiResponse maerskTracking(MaerskTrackingParams params) {
    return maerskTracking(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/maersk/tracking} with per-request options. */
  public ApiResponse maerskTracking(MaerskTrackingParams params, RequestOptions options) {
    return call("maersk_tracking", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * ManoMano Product. Read a ManoMano product URL. The body is the product page: brand and seller are objects, and the current price is price.primary_price.amount_with_vat. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/manomano/product}
   */
  public ApiResponse manomanoProduct(ManomanoProductParams params) {
    return manomanoProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/manomano/product} with per-request options. */
  public ApiResponse manomanoProduct(ManomanoProductParams params, RequestOptions options) {
    return call("manomano_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * ManoMano Product Offer. List the other sellers for a ManoMano product id. Each offer adds price and price_display on top of the site offer. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/manomano/product/offer}
   */
  public ApiResponse manomanoProductOffer(ManomanoProductOfferParams params) {
    return manomanoProductOffer(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/manomano/product/offer} with per-request options. */
  public ApiResponse manomanoProductOffer(ManomanoProductOfferParams params, RequestOptions options) {
    return call("manomano_product_offer", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * ManoMano Search. Read a ManoMano category URL or keyword. Each card keeps the site fields and adds prices.main, brand_name, and image_fullpath. pagination.next is a boolean. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/manomano/search}
   */
  public ApiResponse manomanoSearch(ManomanoSearchParams params) {
    return manomanoSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/manomano/search} with per-request options. */
  public ApiResponse manomanoSearch(ManomanoSearchParams params, RequestOptions options) {
    return call("manomano_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Mascus Ad. Get full details for a Mascus used equipment listing: specs, price, seller and description. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/mascus/ad}
   */
  public ApiResponse mascusAd(MascusAdParams params) {
    return mascusAd(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/mascus/ad} with per-request options. */
  public ApiResponse mascusAd(MascusAdParams params, RequestOptions options) {
    return call("mascus_ad", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Mascus Search. Search used equipment listings on Mascus from a category URL, with pagination that keeps country and category filters. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/mascus/search}
   */
  public ApiResponse mascusSearch(MascusSearchParams params) {
    return mascusSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/mascus/search} with per-request options. */
  public ApiResponse mascusSearch(MascusSearchParams params, RequestOptions options) {
    return call("mascus_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * MediaMarkt Product. Read one MediaMarkt product by id or product URL, in the region you choose. You get price, currency, EAN, and availability. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/mediamarkt/product}
   */
  public ApiResponse mediamarktProduct(MediamarktProductParams params) {
    return mediamarktProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/mediamarkt/product} with per-request options. */
  public ApiResponse mediamarktProduct(MediamarktProductParams params, RequestOptions options) {
    return call("mediamarkt_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * MediaMarkt Search. Search a MediaMarkt shop by keyword. Set region for Switzerland, Germany, Austria, and the other MediaMarktSaturn countries, then page the results. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/mediamarkt/search}
   */
  public ApiResponse mediamarktSearch(MediamarktSearchParams params) {
    return mediamarktSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/mediamarkt/search} with per-request options. */
  public ApiResponse mediamarktSearch(MediamarktSearchParams params, RequestOptions options) {
    return call("mediamarkt_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Micromania Product. Read one Micromania product from its URL or product id. You get the price, brand, EAN, platform, and stock. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/micromania/product}
   */
  public ApiResponse micromaniaProduct(MicromaniaProductParams params) {
    return micromaniaProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/micromania/product} with per-request options. */
  public ApiResponse micromaniaProduct(MicromaniaProductParams params, RequestOptions options) {
    return call("micromania_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Micromania Search. Search Micromania for a keyword or a Search-Show URL. Each card has the product id, name, brand, EAN, price, and stock. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/micromania/search}
   */
  public ApiResponse micromaniaSearch(MicromaniaSearchParams params) {
    return micromaniaSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/micromania/search} with per-request options. */
  public ApiResponse micromaniaSearch(MicromaniaSearchParams params, RequestOptions options) {
    return call("micromania_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Mobile Ad. Mobile Ad API. Fetch full car listing data from a mobile.de ad URL or numeric id. Returns pricing, specs, images and features via webunlocker. Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/mobile/ad}
   */
  public ApiResponse mobileAd(MobileAdParams params) {
    return mobileAd(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/mobile/ad} with per-request options. */
  public ApiResponse mobileAd(MobileAdParams params, RequestOptions options) {
    return call("mobile_ad", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Mobile Search. Mobile Search API. Same mobile.de inventory for every locale. locale sets the language label and ad links, not which cars are returned. (3 credits) Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/mobile/search}
   */
  public ApiResponse mobileSearch(MobileSearchParams params) {
    return mobileSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/mobile/search} with per-request options. */
  public ApiResponse mobileSearch(MobileSearchParams params, RequestOptions options) {
    return call("mobile_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Morele Product. Read one morele.net product page. Flat JSON with PLN price, EAN, specs, and availability. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/morele/product}
   */
  public ApiResponse moreleProduct(MoreleProductParams params) {
    return moreleProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/morele/product} with per-request options. */
  public ApiResponse moreleProduct(MoreleProductParams params, RequestOptions options) {
    return call("morele_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Morele Search. Search morele.net by keyword or search URL. Returns product cards with PLN prices and pagination. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/morele/search}
   */
  public ApiResponse moreleSearch(MoreleSearchParams params) {
    return moreleSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/morele/search} with per-request options. */
  public ApiResponse moreleSearch(MoreleSearchParams params, RequestOptions options) {
    return call("morele_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Mr.Bricolage Suggest. Live Mr.Bricolage product suggestions for a search keyword or EAN, including title, URL, image, price and currency. Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/mrbricolage/suggest}
   */
  public ApiResponse mrbricolageSuggest(MrbricolageSuggestParams params) {
    return mrbricolageSuggest(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/mrbricolage/suggest} with per-request options. */
  public ApiResponse mrbricolageSuggest(MrbricolageSuggestParams params, RequestOptions options) {
    return call("mrbricolage_suggest", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Newegg Product. Read a Newegg product from an item number, catalog id, or URL. Returns price, specs, images, and seller. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/newegg/product}
   */
  public ApiResponse neweggProduct(NeweggProductParams params) {
    return neweggProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/newegg/product} with per-request options. */
  public ApiResponse neweggProduct(NeweggProductParams params, RequestOptions options) {
    return call("newegg_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Newegg Search. Search Newegg by keyword or search URL. Returns item numbers, dollar prices, stock, and pagination. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/newegg/search}
   */
  public ApiResponse neweggSearch(NeweggSearchParams params) {
    return neweggSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/newegg/search} with per-request options. */
  public ApiResponse neweggSearch(NeweggSearchParams params, RequestOptions options) {
    return call("newegg_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * North Data Company. North Data company profile: register, address, financials and history (1 credit). Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/northdata/company}
   */
  public ApiResponse northdataCompany(NorthdataCompanyParams params) {
    return northdataCompany(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/northdata/company} with per-request options. */
  public ApiResponse northdataCompany(NorthdataCompanyParams params, RequestOptions options) {
    return call("northdata_company", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * North Data Search. Search North Data companies and people by name. Returns suggestion cards and pagination (1 credit). Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/northdata/search}
   */
  public ApiResponse northdataSearch(NorthdataSearchParams params) {
    return northdataSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/northdata/search} with per-request options. */
  public ApiResponse northdataSearch(NorthdataSearchParams params, RequestOptions options) {
    return call("northdata_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * NPI Provider. Fetch one NPPES provider or organization by 10-digit NPI or provider-view URL, including addresses and taxonomies. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/npi/provider}
   */
  public ApiResponse npiProvider(NpiProviderParams params) {
    return npiProvider(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/npi/provider} with per-request options. */
  public ApiResponse npiProvider(NpiProviderParams params, RequestOptions options) {
    return call("npi_provider", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * NPI Search. Search the NPPES NPI registry by person name, organization, or 10-digit NPI. With NPI-2, a text query is the organization name. total_results is null, and next is set only when the page is full. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/npi/search}
   */
  public ApiResponse npiSearch(NpiSearchParams params) {
    return npiSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/npi/search} with per-request options. */
  public ApiResponse npiSearch(NpiSearchParams params, RequestOptions options) {
    return call("npi_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Oceanio Search. Search Ocean.io by domain or company name to find the matching company, similar companies, contacts, and people. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/oceanio/search}
   */
  public ApiResponse oceanioSearch(OceanioSearchParams params) {
    return oceanioSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/oceanio/search} with per-request options. */
  public ApiResponse oceanioSearch(OceanioSearchParams params, RequestOptions options) {
    return call("oceanio_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * OLX Ad. Read one OLX ad from its id or URL. You get the asking price, the city, and the seller name. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/olx/ad}
   */
  public ApiResponse olxAd(OlxAdParams params) {
    return olxAd(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/olx/ad} with per-request options. */
  public ApiResponse olxAd(OlxAdParams params, RequestOptions options) {
    return call("olx_ad", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * OLX Product. Read one OLX ad from its id or URL. You get the asking price, the city, and the seller name. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/olx/product}
   */
  public ApiResponse olxProduct(OlxProductParams params) {
    return olxProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/olx/product} with per-request options. */
  public ApiResponse olxProduct(OlxProductParams params, RequestOptions options) {
    return call("olx_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * OLX Search. Search OLX Brazil classifieds by keyword and get title, price, location, seller type, and condition as structured JSON in real time. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/olx/search}
   */
  public ApiResponse olxSearch(OlxSearchParams params) {
    return olxSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/olx/search} with per-request options. */
  public ApiResponse olxSearch(OlxSearchParams params, RequestOptions options) {
    return call("olx_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Oscaro Product. Oscaro Product API. Fetch full auto parts product data from an Oscaro product URL. Returns pricing, brand, reference, images and availability via browser rendering and JSON-LD parsing. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/oscaro/product}
   */
  public ApiResponse oscaroProduct(OscaroProductParams params) {
    return oscaroProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/oscaro/product} with per-request options. */
  public ApiResponse oscaroProduct(OscaroProductParams params, RequestOptions options) {
    return call("oscaro_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Oscaro Search. Oscaro Search API. Search auto parts by keyword, search URL or category listing on oscaro.com, oscaro.es and oscaro.pt. Returns prices and pagination via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/oscaro/search}
   */
  public ApiResponse oscaroSearch(OscaroSearchParams params) {
    return oscaroSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/oscaro/search} with per-request options. */
  public ApiResponse oscaroSearch(OscaroSearchParams params, RequestOptions options) {
    return call("oscaro_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Ovoko Ad. Extract an Ovoko listing from a SKU URL: price, currency, images, seller, quality, vehicle make, part numbers, and warranty. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/ovoko/ad}
   */
  public ApiResponse ovokoAd(OvokoAdParams params) {
    return ovokoAd(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/ovoko/ad} with per-request options. */
  public ApiResponse ovokoAd(OvokoAdParams params, RequestOptions options) {
    return call("ovoko_ad", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Ovoko Search. Search Ovoko used-parts listings from a search URL and retrieve title, price, OEM codes, seller, vehicle text, and pagination. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/ovoko/search}
   */
  public ApiResponse ovokoSearch(OvokoSearchParams params) {
    return ovokoSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/ovoko/search} with per-request options. */
  public ApiResponse ovokoSearch(OvokoSearchParams params, RequestOptions options) {
    return call("ovoko_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Owler Company Info. Retrieve detailed company intelligence from Owler, including revenue, employee count, CEO info, competitors, funding, and industry sectors. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/owler/company/info}
   */
  public ApiResponse owlerCompanyInfo(OwlerCompanyInfoParams params) {
    return owlerCompanyInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/owler/company/info} with per-request options. */
  public ApiResponse owlerCompanyInfo(OwlerCompanyInfoParams params, RequestOptions options) {
    return call("owler_company_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Owler Search. Search for companies on Owler by name or keyword, returning a list of matching companies with slugs, logos, domains, and profile URLs. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/owler/search}
   */
  public ApiResponse owlerSearch(OwlerSearchParams params) {
    return owlerSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/owler/search} with per-request options. */
  public ApiResponse owlerSearch(OwlerSearchParams params, RequestOptions options) {
    return call("owler_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * PagesJaunes Page Info. Retrieve detailed business information from a PagesJaunes page, including contact details, opening hours, services, payment methods, and SIREN number. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/pagesjaunes/page/info}
   */
  public ApiResponse pagesjaunesPageInfo(PagesjaunesPageInfoParams params) {
    return pagesjaunesPageInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/pagesjaunes/page/info} with per-request options. */
  public ApiResponse pagesjaunesPageInfo(PagesjaunesPageInfoParams params, RequestOptions options) {
    return call("pagesjaunes_page_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * PagesJaunes Search. Search PagesJaunes for French local businesses by category and location, returning paginated results with contact details and ratings. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/pagesjaunes/search}
   */
  public ApiResponse pagesjaunesSearch(PagesjaunesSearchParams params) {
    return pagesjaunesSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/pagesjaunes/search} with per-request options. */
  public ApiResponse pagesjaunesSearch(PagesjaunesSearchParams params, RequestOptions options) {
    return call("pagesjaunes_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Patreon Profile Info. Retrieve a Patreon creator profile with patron counts, membership tiers, campaign summary, and creator metadata. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/patreon/profile/info}
   */
  public ApiResponse patreonProfileInfo(PatreonProfileInfoParams params) {
    return patreonProfileInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/patreon/profile/info} with per-request options. */
  public ApiResponse patreonProfileInfo(PatreonProfileInfoParams params, RequestOptions options) {
    return call("patreon_profile_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Patreon Search. Search Patreon creators by keyword and retrieve campaign cards with patron counts, post counts, and profile URLs. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/patreon/search}
   */
  public ApiResponse patreonSearch(PatreonSearchParams params) {
    return patreonSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/patreon/search} with per-request options. */
  public ApiResponse patreonSearch(PatreonSearchParams params, RequestOptions options) {
    return call("patreon_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * PetSmart Product. Read one PetSmart product URL, including the selected size, dollar price, flavor, and item number. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/petsmart/product}
   */
  public ApiResponse petsmartProduct(PetsmartProductParams params) {
    return petsmartProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/petsmart/product} with per-request options. */
  public ApiResponse petsmartProduct(PetsmartProductParams params, RequestOptions options) {
    return call("petsmart_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * PetSmart Search. Search PetSmart by keyword or search URL. Page through pet food, litter, and supplies with dollar prices. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/petsmart/search}
   */
  public ApiResponse petsmartSearch(PetsmartSearchParams params) {
    return petsmartSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/petsmart/search} with per-request options. */
  public ApiResponse petsmartSearch(PetsmartSearchParams params, RequestOptions options) {
    return call("petsmart_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Picard Product. Picard Product API. Fetch full frozen food product data from a Picard URL or reference. Returns pricing, images, description, Nutri-Score, Planet-Score, breadcrumb and reviews. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/picard/product}
   */
  public ApiResponse picardProduct(PicardProductParams params) {
    return picardProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/picard/product} with per-request options. */
  public ApiResponse picardProduct(PicardProductParams params, RequestOptions options) {
    return call("picard_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Picard Search. Picard Search API. Search frozen food listings on picard.fr by keyword or URL. Parses SFCC Search-UpdateGrid HTML; returns prices, Nutri-Score, ratings and pagination. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/picard/search}
   */
  public ApiResponse picardSearch(PicardSearchParams params) {
    return picardSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/picard/search} with per-request options. */
  public ApiResponse picardSearch(PicardSearchParams params, RequestOptions options) {
    return call("picard_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Pinterest User Info. Access Pinterest user profiles and analytics for targeted marketing and audience insights. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/pinterest/user/info}
   */
  public ApiResponse pinterestUserInfo(PinterestUserInfoParams params) {
    return pinterestUserInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/pinterest/user/info} with per-request options. */
  public ApiResponse pinterestUserInfo(PinterestUserInfoParams params, RequestOptions options) {
    return call("pinterest_user_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * PitchBook Company Info. Get a PitchBook company profile: overview, funding, competitors and more via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/pitchbook/company/info}
   */
  public ApiResponse pitchbookCompanyInfo(PitchbookCompanyInfoParams params) {
    return pitchbookCompanyInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/pitchbook/company/info} with per-request options. */
  public ApiResponse pitchbookCompanyInfo(PitchbookCompanyInfoParams params, RequestOptions options) {
    return call("pitchbook_company_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * PitchBook Search. Search PitchBook for companies, news and profiles by keyword or search URL via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/pitchbook/search}
   */
  public ApiResponse pitchbookSearch(PitchbookSearchParams params) {
    return pitchbookSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/pitchbook/search} with per-request options. */
  public ApiResponse pitchbookSearch(PitchbookSearchParams params, RequestOptions options) {
    return call("pitchbook_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Point.P Product. Point.P Product API: scrape live /p/ product pages from a full URL. Get title, images, EAN, pricing, specifications, and breadcrumbs as JSON via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/pointp/product}
   */
  public ApiResponse pointpProduct(PointpProductParams params) {
    return pointpProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/pointp/product} with per-request options. */
  public ApiResponse pointpProduct(PointpProductParams params, RequestOptions options) {
    return call("pointp_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Point.P Search. Point.P Search API: search by keyword or URL and get building-material listings with prices, brands, ratings, units, and pagination as JSON via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/pointp/search}
   */
  public ApiResponse pointpSearch(PointpSearchParams params) {
    return pointpSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/pointp/search} with per-request options. */
  public ApiResponse pointpSearch(PointpSearchParams params, RequestOptions options) {
    return call("pointp_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * PR Newswire Article. Read one PR Newswire press release from its URL. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/prnewswire/article}
   */
  public ApiResponse prnewswireArticle(PrnewswireArticleParams params) {
    return prnewswireArticle(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/prnewswire/article} with per-request options. */
  public ApiResponse prnewswireArticle(PrnewswireArticleParams params, RequestOptions options) {
    return call("prnewswire_article", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * PR Newswire Search. Search PR Newswire press releases by keyword or by a news-search URL. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/prnewswire/search}
   */
  public ApiResponse prnewswireSearch(PrnewswireSearchParams params) {
    return prnewswireSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/prnewswire/search} with per-request options. */
  public ApiResponse prnewswireSearch(PrnewswireSearchParams params, RequestOptions options) {
    return call("prnewswire_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * ProductHunt Product Info. Retrieve product data from ProductHunt, including tagline, categories, screenshots, reviews rating, and social links. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/producthunt/product/info}
   */
  public ApiResponse producthuntProductInfo(ProducthuntProductInfoParams params) {
    return producthuntProductInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/producthunt/product/info} with per-request options. */
  public ApiResponse producthuntProductInfo(ProducthuntProductInfoParams params, RequestOptions options) {
    return call("producthunt_product_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Proshop Product. Read one proshop.dk product page. Flat JSON with DKK price, EAN, specs, and stock text. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/proshop/product}
   */
  public ApiResponse proshopProduct(ProshopProductParams params) {
    return proshopProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/proshop/product} with per-request options. */
  public ApiResponse proshopProduct(ProshopProductParams params, RequestOptions options) {
    return call("proshop_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Proshop Search. Search proshop.dk by keyword or search URL. Returns product cards with DKK prices and pagination. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/proshop/search}
   */
  public ApiResponse proshopSearch(ProshopSearchParams params) {
    return proshopSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/proshop/search} with per-request options. */
  public ApiResponse proshopSearch(ProshopSearchParams params, RequestOptions options) {
    return call("proshop_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Rappi Product. Fetch one Rappi Mexico product, including the primary store price and the other store offers. The primary price can differ from a search card. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/rappi/product}
   */
  public ApiResponse rappiProduct(RappiProductParams params) {
    return rappiProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/rappi/product} with per-request options. */
  public ApiResponse rappiProduct(RappiProductParams params, RequestOptions options) {
    return call("rappi_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Rappi Search. Search Rappi Mexico on rappi.com.mx. The city resolves to Mexico City. Each row is one store offer, and next is null. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/rappi/search}
   */
  public ApiResponse rappiSearch(RappiSearchParams params) {
    return rappiSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/rappi/search} with per-request options. */
  public ApiResponse rappiSearch(RappiSearchParams params, RequestOptions options) {
    return call("rappi_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Rate My Professors Professor. Read one Rate My Professors profile, including difficulty and would-take-again when the page shows them. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/ratemyprofessors/professor}
   */
  public ApiResponse ratemyprofessorsProfessor(RatemyprofessorsProfessorParams params) {
    return ratemyprofessorsProfessor(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/ratemyprofessors/professor} with per-request options. */
  public ApiResponse ratemyprofessorsProfessor(RatemyprofessorsProfessorParams params, RequestOptions options) {
    return call("ratemyprofessors_professor", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Rate My Professors Search. Search Rate My Professors by name. Cards include school, department, difficulty, and would-take-again when the card shows them. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/ratemyprofessors/search}
   */
  public ApiResponse ratemyprofessorsSearch(RatemyprofessorsSearchParams params) {
    return ratemyprofessorsSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/ratemyprofessors/search} with per-request options. */
  public ApiResponse ratemyprofessorsSearch(RatemyprofessorsSearchParams params, RequestOptions options) {
    return call("ratemyprofessors_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * realestate.com.au Search. Search realestate.com.au buy, rent, or sold results. A place such as Sydney, NSW defaults to homes for sale. Each card has price, address, rooms, and a property URL. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/realestatecomau/search}
   */
  public ApiResponse realestatecomauSearch(RealestatecomauSearchParams params) {
    return realestatecomauSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/realestatecomau/search} with per-request options. */
  public ApiResponse realestatecomauSearch(RealestatecomauSearchParams params, RequestOptions options) {
    return call("realestatecomau_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Realtor Property. Extract full property details from a Realtor.com listing URL, including price, specs, photos, agents, features, and location data. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/realtor/property}
   */
  public ApiResponse realtorProperty(RealtorPropertyParams params) {
    return realtorProperty(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/realtor/property} with per-request options. */
  public ApiResponse realtorProperty(RealtorPropertyParams params, RequestOptions options) {
    return call("realtor_property", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Realtor Search. Search Realtor.com listings from a search URL and retrieve prices, beds, baths, sqft, photos, and property metadata. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/realtor/search}
   */
  public ApiResponse realtorSearch(RealtorSearchParams params) {
    return realtorSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/realtor/search} with per-request options. */
  public ApiResponse realtorSearch(RealtorSearchParams params, RequestOptions options) {
    return call("realtor_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Redfin Property. Read one for-sale Redfin home. Pass the url from search, which already includes the market id. The response is a flat listing, the same fields as one search result. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/redfin/property}
   */
  public ApiResponse redfinProperty(RedfinPropertyParams params) {
    return redfinProperty(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/redfin/property} with per-request options. */
  public ApiResponse redfinProperty(RedfinPropertyParams params, RequestOptions options) {
    return call("redfin_property", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Redfin Search. Search for-sale homes on Redfin from a city, neighborhood, or county link. Each listing url includes the market id to pass to the property endpoint. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/redfin/search}
   */
  public ApiResponse redfinSearch(RedfinSearchParams params) {
    return redfinSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/redfin/search} with per-request options. */
  public ApiResponse redfinSearch(RedfinSearchParams params, RequestOptions options) {
    return call("redfin_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Reichelt Product. Read one reichelt.de product URL, including article number, EAN, and the euro price. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/reichelt/product}
   */
  public ApiResponse reicheltProduct(ReicheltProductParams params) {
    return reicheltProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/reichelt/product} with per-request options. */
  public ApiResponse reicheltProduct(ReicheltProductParams params, RequestOptions options) {
    return call("reichelt_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Reichelt Search. Search reichelt.de by keyword or category. Euro prices are rebuilt from the number, including whole-euro amounts. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/reichelt/search}
   */
  public ApiResponse reicheltSearch(ReicheltSearchParams params) {
    return reicheltSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/reichelt/search} with per-request options. */
  public ApiResponse reicheltSearch(ReicheltSearchParams params, RequestOptions options) {
    return call("reichelt_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Reverb Product. Reverb Product API: fetch full listing data from a Reverb URL or listing id. Returns pricing, specs, images, description and shop info via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/reverb/product}
   */
  public ApiResponse reverbProduct(ReverbProductParams params) {
    return reverbProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/reverb/product} with per-request options. */
  public ApiResponse reverbProduct(ReverbProductParams params, RequestOptions options) {
    return call("reverb_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Reverb Search. Reverb Search API: search music gear listings by keyword, query string or marketplace URL on Reverb. Returns prices, thumbnails and pagination via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/reverb/search}
   */
  public ApiResponse reverbSearch(ReverbSearchParams params) {
    return reverbSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/reverb/search} with per-request options. */
  public ApiResponse reverbSearch(ReverbSearchParams params, RequestOptions options) {
    return call("reverb_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Rexel Product. Get a Rexel France product page: references, EAN, descriptions, images and specs via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/rexel/product}
   */
  public ApiResponse rexelProduct(RexelProductParams params) {
    return rexelProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/rexel/product} with per-request options. */
  public ApiResponse rexelProduct(RexelProductParams params, RequestOptions options) {
    return call("rexel_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Rexel Search. Search Rexel France by keyword, EAN or search URL. Returns product cards and pagination via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/rexel/search}
   */
  public ApiResponse rexelSearch(RexelSearchParams params) {
    return rexelSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/rexel/search} with per-request options. */
  public ApiResponse rexelSearch(RexelSearchParams params, RequestOptions options) {
    return call("rexel_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Rightmove Property. Read one Rightmove property from its URL or numeric id. The response is a flat object with price, tenure, floor area, stations, and the agent. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/rightmove/property}
   */
  public ApiResponse rightmoveProperty(RightmovePropertyParams params) {
    return rightmoveProperty(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/rightmove/property} with per-request options. */
  public ApiResponse rightmoveProperty(RightmovePropertyParams params, RequestOptions options) {
    return call("rightmove_property", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Rightmove Search. Search Rightmove by a UK place name or a search URL. Each card has a numeric price, a price label rebuilt from that number, the address, the agent, and a property URL. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/rightmove/search}
   */
  public ApiResponse rightmoveSearch(RightmoveSearchParams params) {
    return rightmoveSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/rightmove/search} with per-request options. */
  public ApiResponse rightmoveSearch(RightmoveSearchParams params, RequestOptions options) {
    return call("rightmove_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * RS Online Product. Read one RS Online UK product URL, including the RS stock number, manufacturer part, and pound price. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/rsonline/product}
   */
  public ApiResponse rsonlineProduct(RsonlineProductParams params) {
    return rsonlineProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/rsonline/product} with per-request options. */
  public ApiResponse rsonlineProduct(RsonlineProductParams params, RequestOptions options) {
    return call("rsonline_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * RS Online Search. Search RS Online UK by keyword or category. Pound prices keep the amount printed on the page. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/rsonline/search}
   */
  public ApiResponse rsonlineSearch(RsonlineSearchParams params) {
    return rsonlineSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/rsonline/search} with per-request options. */
  public ApiResponse rsonlineSearch(RsonlineSearchParams params, RequestOptions options) {
    return call("rsonline_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * SEEK Job. Read one SEEK Australia job from its URL or numeric id. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/seek/job}
   */
  public ApiResponse seekJob(SeekJobParams params) {
    return seekJob(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/seek/job} with per-request options. */
  public ApiResponse seekJob(SeekJobParams params, RequestOptions options) {
    return call("seek_job", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * SEEK Search. Search SEEK Australia jobs by keyword or by a results URL. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/seek/search}
   */
  public ApiResponse seekSearch(SeekSearchParams params) {
    return seekSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/seek/search} with per-request options. */
  public ApiResponse seekSearch(SeekSearchParams params, RequestOptions options) {
    return call("seek_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * SeLoger Agency. Retrieve detailed profile information for a SeLoger real estate agency including contact details, ratings, reviews, and address. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/seloger/agency}
   */
  public ApiResponse selogerAgency(SelogerAgencyParams params) {
    return selogerAgency(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/seloger/agency} with per-request options. */
  public ApiResponse selogerAgency(SelogerAgencyParams params, RequestOptions options) {
    return call("seloger_agency", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * SeLoger Agency Rent. Retrieve paginated rental property listings published by a specific SeLoger agency using its numeric agency ID. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/seloger/agency/rent}
   */
  public ApiResponse selogerAgencyRent(SelogerAgencyRentParams params) {
    return selogerAgencyRent(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/seloger/agency/rent} with per-request options. */
  public ApiResponse selogerAgencyRent(SelogerAgencyRentParams params, RequestOptions options) {
    return call("seloger_agency_rent", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * SeLoger Agency Sale. Retrieve paginated sale property listings published by a specific SeLoger agency using its numeric agency ID. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/seloger/agency/sale}
   */
  public ApiResponse selogerAgencySale(SelogerAgencySaleParams params) {
    return selogerAgencySale(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/seloger/agency/sale} with per-request options. */
  public ApiResponse selogerAgencySale(SelogerAgencySaleParams params, RequestOptions options) {
    return call("seloger_agency_sale", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * SeLoger Property. Efficiently extract detailed data from a SeLoger real estate listing including agency info, pricing, photos, description, and property criteria. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/seloger/property}
   */
  public ApiResponse selogerProperty(SelogerPropertyParams params) {
    return selogerProperty(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/seloger/property} with per-request options. */
  public ApiResponse selogerProperty(SelogerPropertyParams params, RequestOptions options) {
    return call("seloger_property", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * SeLoger Search. Efficiently extract real estate listings from SeLoger search pages for market insights, property management, and investment analysis. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/seloger/search}
   */
  public ApiResponse selogerSearch(SelogerSearchParams params) {
    return selogerSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/seloger/search} with per-request options. */
  public ApiResponse selogerSearch(SelogerSearchParams params, RequestOptions options) {
    return call("seloger_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Sephora Product. Read one European Sephora product by id or product URL. Set region when you are not on the French shop. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/sephora/product}
   */
  public ApiResponse sephoraProduct(SephoraProductParams params) {
    return sephoraProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/sephora/product} with per-request options. */
  public ApiResponse sephoraProduct(SephoraProductParams params, RequestOptions options) {
    return call("sephora_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Sephora Search. Search a European Sephora shop by keyword. Region defaults to France. Page through makeup and skincare cards. The US site is a different page and is not included. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/sephora/search}
   */
  public ApiResponse sephoraSearch(SephoraSearchParams params) {
    return sephoraSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/sephora/search} with per-request options. */
  public ApiResponse sephoraSearch(SephoraSearchParams params, RequestOptions options) {
    return call("sephora_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Shophouzz Product. Retrieve full product details from Shophouzz, including title, price, variants, images, vendor, and structured metadata. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/shophouzz/product}
   */
  public ApiResponse shophouzzProduct(ShophouzzProductParams params) {
    return shophouzzProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/shophouzz/product} with per-request options. */
  public ApiResponse shophouzzProduct(ShophouzzProductParams params, RequestOptions options) {
    return call("shophouzz_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Shopify Apps. Retrieve structured details about any Shopify App Store listing, including title, logo, screenshots, pricing plans, categories, and description. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/shopify/apps}
   */
  public ApiResponse shopifyApps(ShopifyAppsParams params) {
    return shopifyApps(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/shopify/apps} with per-request options. */
  public ApiResponse shopifyApps(ShopifyAppsParams params, RequestOptions options) {
    return call("shopify_apps", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Shopify Catalog. List products from a Shopify shop. query is the shop domain or a URL on that shop. page and limit choose the slice. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/shopify/catalog}
   */
  public ApiResponse shopifyCatalog(ShopifyCatalogParams params) {
    return shopifyCatalog(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/shopify/catalog} with per-request options. */
  public ApiResponse shopifyCatalog(ShopifyCatalogParams params, RequestOptions options) {
    return call("shopify_catalog", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Shopify Collections. List collections from a Shopify shop. query is the shop domain or a URL on that shop. page and limit choose the slice. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/shopify/collections}
   */
  public ApiResponse shopifyCollections(ShopifyCollectionsParams params) {
    return shopifyCollections(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/shopify/collections} with per-request options. */
  public ApiResponse shopifyCollections(ShopifyCollectionsParams params, RequestOptions options) {
    return call("shopify_collections", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Shopify Product. Extract full product details from any Shopify store product page, including title, variants, pricing, images, inventory, and options. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/shopify/product}
   */
  public ApiResponse shopifyProduct(ShopifyProductParams params) {
    return shopifyProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/shopify/product} with per-request options. */
  public ApiResponse shopifyProduct(ShopifyProductParams params, RequestOptions options) {
    return call("shopify_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * SimilarWeb Domain. Retrieve website traffic and engagement metrics for a domain from the public Similarweb page, including visits, bounce rate, rank, keywords, and traffic sources. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/similarweb/domain}
   */
  public ApiResponse similarwebDomain(SimilarwebDomainParams params) {
    return similarwebDomain(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/similarweb/domain} with per-request options. */
  public ApiResponse similarwebDomain(SimilarwebDomainParams params, RequestOptions options) {
    return call("similarweb_domain", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Similarweb Search. Search the public Similarweb page for websites, companies, and mobile apps related to a keyword. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/similarweb/search}
   */
  public ApiResponse similarwebSearch(SimilarwebSearchParams params) {
    return similarwebSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/similarweb/search} with per-request options. */
  public ApiResponse similarwebSearch(SimilarwebSearchParams params, RequestOptions options) {
    return call("similarweb_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * SnapEDA Product. SnapEDA Product API: fetch full part page data from a SnapEDA URL or MPN/manufacturer slug. Returns CAD models, datasheet, RoHS, distributor pricing and availability via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/snapeda/product}
   */
  public ApiResponse snapedaProduct(SnapedaProductParams params) {
    return snapedaProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/snapeda/product} with per-request options. */
  public ApiResponse snapedaProduct(SnapedaProductParams params, RequestOptions options) {
    return call("snapeda_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * SnapEDA Search. SnapEDA Search API: search electronic parts by keyword or URL on SnapMagic Search (ex-SnapEDA). Returns MPN, manufacturer, pricing, CAD availability and pagination via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/snapeda/search}
   */
  public ApiResponse snapedaSearch(SnapedaSearchParams params) {
    return snapedaSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/snapeda/search} with per-request options. */
  public ApiResponse snapedaSearch(SnapedaSearchParams params, RequestOptions options) {
    return call("snapeda_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Starbucks Menu. Full Starbucks menu for one café, with the default-size price of each item. Prices use browser rendering. Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/starbucks/menu}
   */
  public ApiResponse starbucksMenu(StarbucksMenuParams params) {
    return starbucksMenu(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/starbucks/menu} with per-request options. */
  public ApiResponse starbucksMenu(StarbucksMenuParams params, RequestOptions options) {
    return call("starbucks_menu", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Starbucks Product. One Starbucks item at one café, with a price for every size. Get the café from Search first. Prices use browser rendering. Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/starbucks/product}
   */
  public ApiResponse starbucksProduct(StarbucksProductParams params) {
    return starbucksProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/starbucks/product} with per-request options. */
  public ApiResponse starbucksProduct(StarbucksProductParams params, RequestOptions options) {
    return call("starbucks_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Starbucks Search. Nearest Starbucks cafés from coordinates or a store-locator URL. Returns address, hours, amenities, and the store number used by Menu and Product. Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/starbucks/search}
   */
  public ApiResponse starbucksSearch(StarbucksSearchParams params) {
    return starbucksSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/starbucks/search} with per-request options. */
  public ApiResponse starbucksSearch(StarbucksSearchParams params, RequestOptions options) {
    return call("starbucks_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Stats. Infrastructure health for an engine or endpoint over the last hour. Not scoped to your account or tenant. Enabled per account: HTTP 403 means contact support to activate it.
   *
   * <p>{@code GET /v2/stats}
   */
  public ApiResponse stats(StatsParams params) {
    return stats(params, RequestOptions.defaults());
  }

  /** {@code GET /v2/stats} with per-request options. */
  public ApiResponse stats(StatsParams params, RequestOptions options) {
    return call("stats", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * StockX Product. Retrieve detailed product information from StockX, including market prices, bid/ask data, size variants, and product metadata. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/stockx/product}
   */
  public ApiResponse stockxProduct(StockxProductParams params) {
    return stockxProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/stockx/product} with per-request options. */
  public ApiResponse stockxProduct(StockxProductParams params, RequestOptions options) {
    return call("stockx_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * StockX Search. Search StockX for products by name, brand, SKU, or slug and receive a list of matching items with product IDs, slugs, images, and colorways. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/stockx/search}
   */
  public ApiResponse stockxSearch(StockxSearchParams params) {
    return stockxSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/stockx/search} with per-request options. */
  public ApiResponse stockxSearch(StockxSearchParams params, RequestOptions options) {
    return call("stockx_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * StockX Trends. Retrieve trending products on StockX by category, with options to sort by featured, most active, or release date. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/stockx/trends}
   */
  public ApiResponse stockxTrends(StockxTrendsParams params) {
    return stockxTrends(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/stockx/trends} with per-request options. */
  public ApiResponse stockxTrends(StockxTrendsParams params, RequestOptions options) {
    return call("stockx_trends", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * StubHub Event. Read one StubHub event from its URL or numeric id, including the lowest printed ticket price. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/stubhub/event}
   */
  public ApiResponse stubhubEvent(StubhubEventParams params) {
    return stubhubEvent(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/stubhub/event} with per-request options. */
  public ApiResponse stubhubEvent(StubhubEventParams params, RequestOptions options) {
    return call("stubhub_event", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * StubHub Search. Search StubHub events by keyword or by a StubHub search URL. The response is the first embedded page. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/stubhub/search}
   */
  public ApiResponse stubhubSearch(StubhubSearchParams params) {
    return stubhubSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/stubhub/search} with per-request options. */
  public ApiResponse stubhubSearch(StubhubSearchParams params, RequestOptions options) {
    return call("stubhub_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Target Product. Get a Target.com product page: price, description, images and variations via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/target/product}
   */
  public ApiResponse targetProduct(TargetProductParams params) {
    return targetProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/target/product} with per-request options. */
  public ApiResponse targetProduct(TargetProductParams params, RequestOptions options) {
    return call("target_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Target Search. Search Target.com by keyword or search URL. Returns product cards with price, rating and pagination via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/target/search}
   */
  public ApiResponse targetSearch(TargetSearchParams params) {
    return targetSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/target/search} with per-request options. */
  public ApiResponse targetSearch(TargetSearchParams params, RequestOptions options) {
    return call("target_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * TDLR License. Fetch one TDLR license by detail id or SearchResultDetail URL, including status, subtype, DBA, and notes. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/tdlr/license}
   */
  public ApiResponse tdlrLicense(TdlrLicenseParams params) {
    return tdlrLicense(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/tdlr/license} with per-request options. */
  public ApiResponse tdlrLicense(TdlrLicenseParams params, RequestOptions options) {
    return call("tdlr_license", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * TDLR License Search. Search Texas TDLR licenses by name, city, county, or ZIP. license_type such as AIRREF is required for a useful search. 25 rows per page. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/tdlr/search}
   */
  public ApiResponse tdlrSearch(TdlrSearchParams params) {
    return tdlrSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/tdlr/search} with per-request options. */
  public ApiResponse tdlrSearch(TdlrSearchParams params, RequestOptions options) {
    return call("tdlr_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * TED Notice. TED notice profile: buyer, lots, deadline, value and documents (1 credit). Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/ted/notice}
   */
  public ApiResponse tedNotice(TedNoticeParams params) {
    return tedNotice(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/ted/notice} with per-request options. */
  public ApiResponse tedNotice(TedNoticeParams params, RequestOptions options) {
    return call("ted_notice", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * TED Search. Search TED procurement notices by keyword or filters. Returns notice cards and pagination (1 credit). Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/ted/search}
   */
  public ApiResponse tedSearch(TedSearchParams params) {
    return tedSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/ted/search} with per-request options. */
  public ApiResponse tedSearch(TedSearchParams params, RequestOptions options) {
    return call("ted_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Thalia Product. Read one Thalia product from its URL. You get price, author or brand, and the product details. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/thalia/product}
   */
  public ApiResponse thaliaProduct(ThaliaProductParams params) {
    return thaliaProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/thalia/product} with per-request options. */
  public ApiResponse thaliaProduct(ThaliaProductParams params, RequestOptions options) {
    return call("thalia_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Thalia Search. Search Thalia by keyword and page books, media, and gifts. Cards include title, price, and the product link. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/thalia/search}
   */
  public ApiResponse thaliaSearch(ThaliaSearchParams params) {
    return thaliaSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/thalia/search} with per-request options. */
  public ApiResponse thaliaSearch(ThaliaSearchParams params, RequestOptions options) {
    return call("thalia_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * TheFork Restaurant. Extract a full TheFork restaurant profile from a restaurant page URL, including ratings, tags, photo gallery, opening hours, and AI review summaries via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/thefork/restaurant}
   */
  public ApiResponse theforkRestaurant(TheforkRestaurantParams params) {
    return theforkRestaurant(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/thefork/restaurant} with per-request options. */
  public ApiResponse theforkRestaurant(TheforkRestaurantParams params, RequestOptions options) {
    return call("thefork_restaurant", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * TheFork Search. Search TheFork restaurants from a full search or city URL and retrieve ratings, cuisine, prices, availability, review summaries, and pagination via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/thefork/search}
   */
  public ApiResponse theforkSearch(TheforkSearchParams params) {
    return theforkSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/thefork/search} with per-request options. */
  public ApiResponse theforkSearch(TheforkSearchParams params, RequestOptions options) {
    return call("thefork_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Thomasnet Company. Get a full Thomasnet supplier profile from a company URL, including contact details, product categories, and business information. Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/thomasnet/company}
   */
  public ApiResponse thomasnetCompany(ThomasnetCompanyParams params) {
    return thomasnetCompany(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/thomasnet/company} with per-request options. */
  public ApiResponse thomasnetCompany(ThomasnetCompanyParams params, RequestOptions options) {
    return call("thomasnet_company", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Thomasnet Search. Search Thomasnet suppliers from a search or category URL and get company listings, filters, name suggestions, and pagination. Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/thomasnet/search}
   */
  public ApiResponse thomasnetSearch(ThomasnetSearchParams params) {
    return thomasnetSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/thomasnet/search} with per-request options. */
  public ApiResponse thomasnetSearch(ThomasnetSearchParams params, RequestOptions options) {
    return call("thomasnet_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Ticketmaster Event. Fetch one Ticketmaster event from a slugged event URL. The slug is searched and the matching event id is returned with the venue address. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/ticketmaster/event}
   */
  public ApiResponse ticketmasterEvent(TicketmasterEventParams params) {
    return ticketmasterEvent(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/ticketmaster/event} with per-request options. */
  public ApiResponse ticketmasterEvent(TicketmasterEventParams params, RequestOptions options) {
    return call("ticketmaster_event", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Ticketmaster Search. Search Ticketmaster events by keyword. Returns event cards, including Ticketweb partner rows, and pagination. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/ticketmaster/search}
   */
  public ApiResponse ticketmasterSearch(TicketmasterSearchParams params) {
    return ticketmasterSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/ticketmaster/search} with per-request options. */
  public ApiResponse ticketmasterSearch(TicketmasterSearchParams params, RequestOptions options) {
    return call("ticketmaster_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * TikTok Top Ads Ad. Get one TikTok Creative Center Top Ad by URL or id: creative, landing page, CTR, and engagement (1 credit). Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/tiktoktopads/ad}
   */
  public ApiResponse tiktoktopadsAd(TiktoktopadsAdParams params) {
    return tiktoktopadsAd(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/tiktoktopads/ad} with per-request options. */
  public ApiResponse tiktoktopadsAd(TiktoktopadsAdParams params, RequestOptions options) {
    return call("tiktoktopads_ad", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * TikTok Top Ads Search. Search TikTok Creative Center Top Ads by region, objective, period, and keyword. Returns ad cards and pagination (1 credit). Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/tiktoktopads/search}
   */
  public ApiResponse tiktoktopadsSearch(TiktoktopadsSearchParams params) {
    return tiktoktopadsSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/tiktoktopads/search} with per-request options. */
  public ApiResponse tiktoktopadsSearch(TiktoktopadsSearchParams params, RequestOptions options) {
    return call("tiktoktopads_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Trade-A-Plane Listing. Read one Trade-A-Plane aircraft listing from its URL. You get the asking price, the airframe details, and the seller. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/tradeaplane/listing}
   */
  public ApiResponse tradeaplaneListing(TradeaplaneListingParams params) {
    return tradeaplaneListing(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/tradeaplane/listing} with per-request options. */
  public ApiResponse tradeaplaneListing(TradeaplaneListingParams params, RequestOptions options) {
    return call("tradeaplane_listing", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Trade-A-Plane Search. Search Trade-A-Plane aircraft listings by keyword and page the results. Cards include title, price, and the listing link. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/tradeaplane/search}
   */
  public ApiResponse tradeaplaneSearch(TradeaplaneSearchParams params) {
    return tradeaplaneSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/tradeaplane/search} with per-request options. */
  public ApiResponse tradeaplaneSearch(TradeaplaneSearchParams params, RequestOptions options) {
    return call("tradeaplane_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Trustpilot Company Info. Retrieve Trustpilot company profile data, including trust score, star rating, categories, contact info, and review response behavior. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/trustpilot/company/info}
   */
  public ApiResponse trustpilotCompanyInfo(TrustpilotCompanyInfoParams params) {
    return trustpilotCompanyInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/trustpilot/company/info} with per-request options. */
  public ApiResponse trustpilotCompanyInfo(TrustpilotCompanyInfoParams params, RequestOptions options) {
    return call("trustpilot_company_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Untappd Beer. Read one Untappd beer, including ABV, IBU, and brewery. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/untappd/beer}
   */
  public ApiResponse untappdBeer(UntappdBeerParams params) {
    return untappdBeer(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/untappd/beer} with per-request options. */
  public ApiResponse untappdBeer(UntappdBeerParams params, RequestOptions options) {
    return call("untappd_beer", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Untappd Search. Search Untappd beers by keyword. Cards include brewery, ABV, and IBU. next is null when Untappd does not expose a next page. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/untappd/search}
   */
  public ApiResponse untappdSearch(UntappdSearchParams params) {
    return untappdSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/untappd/search} with per-request options. */
  public ApiResponse untappdSearch(UntappdSearchParams params, RequestOptions options) {
    return call("untappd_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Upwork Freelancer. Upwork Freelancer API: fetch public freelancer profile data from a profile ID, username or URL. Returns bio, hourly rate, skills, ratings and agency info via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/upwork/freelancer}
   */
  public ApiResponse upworkFreelancer(UpworkFreelancerParams params) {
    return upworkFreelancer(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/upwork/freelancer} with per-request options. */
  public ApiResponse upworkFreelancer(UpworkFreelancerParams params, RequestOptions options) {
    return call("upwork_freelancer", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Upwork Jobs. Upwork Jobs API: search freelance job listings by skill slug or URL. Returns budgets, skills, experience level and pagination via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/upwork/jobs}
   */
  public ApiResponse upworkJobs(UpworkJobsParams params) {
    return upworkJobs(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/upwork/jobs} with per-request options. */
  public ApiResponse upworkJobs(UpworkJobsParams params, RequestOptions options) {
    return call("upwork_jobs", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Upwork Services. Upwork Services API: browse Project Catalog listings by category slug or URL. Returns prices, product IDs and pagination via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/upwork/services}
   */
  public ApiResponse upworkServices(UpworkServicesParams params) {
    return upworkServices(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/upwork/services} with per-request options. */
  public ApiResponse upworkServices(UpworkServicesParams params, RequestOptions options) {
    return call("upwork_services", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Upwork Services Info. Upwork Services Info API: fetch Project Catalog product details from a product URL. Returns packages, pricing, seller, media and breadcrumbs via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/upwork/services/info}
   */
  public ApiResponse upworkServicesInfo(UpworkServicesInfoParams params) {
    return upworkServicesInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/upwork/services/info} with per-request options. */
  public ApiResponse upworkServicesInfo(UpworkServicesInfoParams params, RequestOptions options) {
    return call("upwork_services_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Usage. Retrieve real-time usage statistics and credit balance for your Piloterr account.
   *
   * <p>{@code GET /v2/usage}
   */
  public ApiResponse usage() {
    return usage(RequestOptions.defaults());
  }

  /** {@code GET /v2/usage} with per-request options. */
  public ApiResponse usage(RequestOptions options) {
    return call("usage", Map.of(), options);
  }

  /**
   * USAspending Award. Fetch one US federal award by its generated id or by a usaspending.gov award URL. Amounts are a number plus a display string. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/usaspending/award}
   */
  public ApiResponse usaspendingAward(UsaspendingAwardParams params) {
    return usaspendingAward(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/usaspending/award} with per-request options. */
  public ApiResponse usaspendingAward(UsaspendingAwardParams params, RequestOptions options) {
    return call("usaspending_award", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * USAspending Search. Search US federal awards by keyword. award_type selects one group: contract (default), grant, idv, or loan. Amounts are a number plus a display string. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/usaspending/search}
   */
  public ApiResponse usaspendingSearch(UsaspendingSearchParams params) {
    return usaspendingSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/usaspending/search} with per-request options. */
  public ApiResponse usaspendingSearch(UsaspendingSearchParams params, RequestOptions options) {
    return call("usaspending_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Vestiaire Collective Product. Fetch one Vestiaire Collective listing by product URL. The response is a flat object with price, size, condition, and seller. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/vestiairecollective/product}
   */
  public ApiResponse vestiairecollectiveProduct(VestiairecollectiveProductParams params) {
    return vestiairecollectiveProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/vestiairecollective/product} with per-request options. */
  public ApiResponse vestiairecollectiveProduct(VestiairecollectiveProductParams params, RequestOptions options) {
    return call("vestiairecollective_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Vestiaire Collective Search. Search Vestiaire Collective by keyword or catalog URL. Each card has brand, name, price, size, and a product link. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/vestiairecollective/search}
   */
  public ApiResponse vestiairecollectiveSearch(VestiairecollectiveSearchParams params) {
    return vestiairecollectiveSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/vestiairecollective/search} with per-request options. */
  public ApiResponse vestiairecollectiveSearch(VestiairecollectiveSearchParams params, RequestOptions options) {
    return call("vestiairecollective_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Vinted Item. Fetch a Vinted listing by item URL or numeric ID. Parses the public item page (Next.js payload), returns a normalized snake_case object with photos, attributes, shipping, pricing, and optional seller profile. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/vinted/item}
   */
  public ApiResponse vintedItem(VintedItemParams params) {
    return vintedItem(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/vinted/item} with per-request options. */
  public ApiResponse vintedItem(VintedItemParams params, RequestOptions options) {
    return call("vinted_item", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Vinted Search. Search Vinted second-hand listings by keyword or catalog URL across vinted.fr, vinted.com and other domains. Returns results and pagination. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/vinted/search}
   */
  public ApiResponse vintedSearch(VintedSearchParams params) {
    return vintedSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/vinted/search} with per-request options. */
  public ApiResponse vintedSearch(VintedSearchParams params, RequestOptions options) {
    return call("vinted_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Vinted User Info. Retrieve public Vinted seller profile by member URL or user ID. Returns the raw /api/v2/users/{id} object: login, photo, feedback stats, followers, verification, bundle discounts, and listing counts. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/vinted/user/info}
   */
  public ApiResponse vintedUserInfo(VintedUserInfoParams params) {
    return vintedUserInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/vinted/user/info} with per-request options. */
  public ApiResponse vintedUserInfo(VintedUserInfoParams params, RequestOptions options) {
    return call("vinted_user_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Viva Real Property. Read one Viva Real property from its URL. The price follows the operation of that URL, so a sale link stays a sale price. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/vivareal/property}
   */
  public ApiResponse vivarealProperty(VivarealPropertyParams params) {
    return vivarealProperty(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/vivareal/property} with per-request options. */
  public ApiResponse vivarealProperty(VivarealPropertyParams params, RequestOptions options) {
    return call("vivareal_property", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Viva Real Search. Search Viva Real by city and operation, sale or rent. Page with pagina. Development cards and 'from' prices are left out. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/vivareal/search}
   */
  public ApiResponse vivarealSearch(VivarealSearchParams params) {
    return vivarealSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/vivareal/search} with per-request options. */
  public ApiResponse vivarealSearch(VivarealSearchParams params, RequestOptions options) {
    return call("vivareal_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Walmart Product. Extract full Walmart product page (PDP) data from a /ip/ URL: pricing, ratings, images, fulfillment, specs, badges, and availability via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/walmart/product}
   */
  public ApiResponse walmartProduct(WalmartProductParams params) {
    return walmartProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/walmart/product} with per-request options. */
  public ApiResponse walmartProduct(WalmartProductParams params, RequestOptions options) {
    return call("walmart_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Walmart Search. Scrape Walmart.com search result pages from a full URL and retrieve product cards with pricing, ratings, availability, and pagination via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/walmart/search}
   */
  public ApiResponse walmartSearch(WalmartSearchParams params) {
    return walmartSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/walmart/search} with per-request options. */
  public ApiResponse walmartSearch(WalmartSearchParams params, RequestOptions options) {
    return call("walmart_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Walmart Suggest. Get Walmart.com search autocomplete suggestions for a keyword via browser rendering, with optional US ZIP code localization. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/walmart/suggest}
   */
  public ApiResponse walmartSuggest(WalmartSuggestParams params) {
    return walmartSuggest(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/walmart/suggest} with per-request options. */
  public ApiResponse walmartSuggest(WalmartSuggestParams params, RequestOptions options) {
    return call("walmart_suggest", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Website Antibot. Detect which anti-bot protection protects a website before you scrape. Get the vendor, confidence level, and matching clues from a single URL check. Credit cost: 1 credit per call.
   *
   * <p>{@code GET /v2/website/antibot}
   */
  public ApiResponse websiteAntibot(WebsiteAntibotParams params) {
    return websiteAntibot(params, RequestOptions.defaults());
  }

  /** {@code GET /v2/website/antibot} with per-request options. */
  public ApiResponse websiteAntibot(WebsiteAntibotParams params, RequestOptions options) {
    return call("website_antibot", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Website Crawler. Fetch public pages with fast HTTP crawling (no JavaScript). Best first choice for static or server-rendered HTML; use Rendering or WebUnlocker when JS or anti-bot blocks you. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/website/crawler}
   */
  public ApiResponse websiteCrawler(WebsiteCrawlerParams params) {
    return websiteCrawler(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/website/crawler} with per-request options. */
  public ApiResponse websiteCrawler(WebsiteCrawlerParams params, RequestOptions options) {
    return call("website_crawler", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Website Email Phone Extractor. Extract emails and phone numbers from websites for comprehensive contact details, including social media profiles across 12+ platforms. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/website/email_phone_extractor}
   */
  public ApiResponse websiteEmailPhoneExtractor(WebsiteEmailPhoneExtractorParams params) {
    return websiteEmailPhoneExtractor(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/website/email_phone_extractor} with per-request options. */
  public ApiResponse websiteEmailPhoneExtractor(WebsiteEmailPhoneExtractorParams params, RequestOptions options) {
    return call("website_email_phone_extractor", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Website Rendering. Render JavaScript-heavy pages in a headless browser and return post-render HTML. Use when Crawler returns empty DOM; escalate to WebUnlocker if bot protection blocks the session. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/website/rendering}
   */
  public ApiResponse websiteRendering(WebsiteRenderingParams params) {
    return websiteRendering(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/website/rendering} with per-request options. */
  public ApiResponse websiteRendering(WebsiteRenderingParams params, RequestOptions options) {
    return call("website_rendering", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Website Screenshot. Browser-like Screenshot API that captures webpages as PNG/JPEG/WebP or PDF for previews, QA, reporting, monitoring, and archiving no headless setup. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/website/screenshot}
   */
  public ApiResponse websiteScreenshot(WebsiteScreenshotParams params) {
    return websiteScreenshot(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/website/screenshot} with per-request options. */
  public ApiResponse websiteScreenshot(WebsiteScreenshotParams params, RequestOptions options) {
    return call("website_screenshot", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Website Technology. Identify the technologies behind any website CMS, frameworks, analytics, CDN, hosting, and more, for competitive market analysis and technological insight. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/website/technology}
   */
  public ApiResponse websiteTechnology(WebsiteTechnologyParams params) {
    return websiteTechnology(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/website/technology} with per-request options. */
  public ApiResponse websiteTechnology(WebsiteTechnologyParams params, RequestOptions options) {
    return call("website_technology", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Website WebUnlocker. Bypass advanced anti-bot systems (Cloudflare, DataDome, Akamai, PerimeterX) on whitelisted domains. Combines browser rendering and unlock tooling; use after Crawler and Rendering fail. Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/website/webunlocker}
   */
  public ApiResponse websiteWebunlocker(WebsiteWebunlockerParams params) {
    return websiteWebunlocker(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/website/webunlocker} with per-request options. */
  public ApiResponse websiteWebunlocker(WebsiteWebunlockerParams params, RequestOptions options) {
    return call("website_webunlocker", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Weibo Post Info. Retrieve one public Weibo post by post id, including text, images, publish time, engagement counts, and author profile. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/weibo/post/info}
   */
  public ApiResponse weiboPostInfo(WeiboPostInfoParams params) {
    return weiboPostInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/weibo/post/info} with per-request options. */
  public ApiResponse weiboPostInfo(WeiboPostInfoParams params, RequestOptions options) {
    return call("weibo_post_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Weibo Search. Search public Weibo posts by keywords. Each post includes the author, text, publish time, engagement counts, image, and video link. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/weibo/search}
   */
  public ApiResponse weiboSearch(WeiboSearchParams params) {
    return weiboSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/weibo/search} with per-request options. */
  public ApiResponse weiboSearch(WeiboSearchParams params, RequestOptions options) {
    return call("weibo_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Welcome to the Jungle Company. Welcome to the Jungle company profile: headcount, offices, sectors and website (1 credit). Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/welcometothejungle/company}
   */
  public ApiResponse welcometothejungleCompany(WelcometothejungleCompanyParams params) {
    return welcometothejungleCompany(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/welcometothejungle/company} with per-request options. */
  public ApiResponse welcometothejungleCompany(WelcometothejungleCompanyParams params, RequestOptions options) {
    return call("welcometothejungle_company", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Welcome to the Jungle Search. Search Welcome to the Jungle jobs. Returns offer cards and pagination (1 credit). Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/welcometothejungle/search}
   */
  public ApiResponse welcometothejungleSearch(WelcometothejungleSearchParams params) {
    return welcometothejungleSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/welcometothejungle/search} with per-request options. */
  public ApiResponse welcometothejungleSearch(WelcometothejungleSearchParams params, RequestOptions options) {
    return call("welcometothejungle_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Wellfound Company Info. Retrieve startup and company data from Wellfound (formerly AngelList), including description, funding, team size, hiring status, and social links. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/wellfound/company/info}
   */
  public ApiResponse wellfoundCompanyInfo(WellfoundCompanyInfoParams params) {
    return wellfoundCompanyInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/wellfound/company/info} with per-request options. */
  public ApiResponse wellfoundCompanyInfo(WellfoundCompanyInfoParams params, RequestOptions options) {
    return call("wellfound_company_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * X Post Info (twitter). X Post Info API: fetch public tweet data (text, likes, retweets, views, media) from a status URL or tweet ID via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/x/post/info}
   */
  public ApiResponse xPostInfo(XPostInfoParams params) {
    return xPostInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/x/post/info} with per-request options. */
  public ApiResponse xPostInfo(XPostInfoParams params, RequestOptions options) {
    return call("x_post_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * X Profile Info (twitter). X Profile Info API: fetch public X/Twitter profile data (bio, followers, verification, avatars) from a username or URL via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/x/profile/info}
   */
  public ApiResponse xProfileInfo(XProfileInfoParams params) {
    return xProfileInfo(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/x/profile/info} with per-request options. */
  public ApiResponse xProfileInfo(XProfileInfoParams params, RequestOptions options) {
    return call("x_profile_info", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Yahoo Search. Query Yahoo via API and retrieve structured organic results with country and language. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/yahoo/search}
   */
  public ApiResponse yahooSearch(YahooSearchParams params) {
    return yahooSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/yahoo/search} with per-request options. */
  public ApiResponse yahooSearch(YahooSearchParams params, RequestOptions options) {
    return call("yahoo_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Yellowpages Business. Extract a full Yellow Pages business profile from a listing URL, including phone, website, categories, and ratings via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/yellowpages/business}
   */
  public ApiResponse yellowpagesBusiness(YellowpagesBusinessParams params) {
    return yellowpagesBusiness(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/yellowpages/business} with per-request options. */
  public ApiResponse yellowpagesBusiness(YellowpagesBusinessParams params, RequestOptions options) {
    return call("yellowpages_business", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Yellowpages Search. Search Yellow Pages businesses from a full search URL and retrieve names, phones, addresses, ratings, opening hours, and pagination via browser rendering. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/yellowpages/search}
   */
  public ApiResponse yellowpagesSearch(YellowpagesSearchParams params) {
    return yellowpagesSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/yellowpages/search} with per-request options. */
  public ApiResponse yellowpagesSearch(YellowpagesSearchParams params, RequestOptions options) {
    return call("yellowpages_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Yelp Business. Fetch a Yelp business profile from a full business URL with phone, address, website, logo, hours, and claim status. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/yelp/business}
   */
  public ApiResponse yelpBusiness(YelpBusinessParams params) {
    return yelpBusiness(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/yelp/business} with per-request options. */
  public ApiResponse yelpBusiness(YelpBusinessParams params, RequestOptions options) {
    return call("yelp_business", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Yelp Reviews. Fetch paginated Yelp reviews for a business with rating, text, author profile, reactions, and attached photos or videos. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/yelp/reviews}
   */
  public ApiResponse yelpReviews(YelpReviewsParams params) {
    return yelpReviews(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/yelp/reviews} with per-request options. */
  public ApiResponse yelpReviews(YelpReviewsParams params, RequestOptions options) {
    return call("yelp_reviews", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Yelp Search. Search Yelp by keyword and location and retrieve paginated business cards with ratings, categories, photos, and sponsored flags. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/yelp/search}
   */
  public ApiResponse yelpSearch(YelpSearchParams params) {
    return yelpSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/yelp/search} with per-request options. */
  public ApiResponse yelpSearch(YelpSearchParams params, RequestOptions options) {
    return call("yelp_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Zap Imóveis Ad. Get a full Zap Imóveis listing by URL: price, IPTU, features, coordinates, images, and publisher (1 credit). Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/zapimoveis/ad}
   */
  public ApiResponse zapimoveisAd(ZapimoveisAdParams params) {
    return zapimoveisAd(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/zapimoveis/ad} with per-request options. */
  public ApiResponse zapimoveisAd(ZapimoveisAdParams params, RequestOptions options) {
    return call("zapimoveis_ad", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Zap Imóveis Search. Search Zap Imóveis listings by city or search URL. Returns prices, IPTU, condominium fees, rooms, and pagination (1 credit). Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/zapimoveis/search}
   */
  public ApiResponse zapimoveisSearch(ZapimoveisSearchParams params) {
    return zapimoveisSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/zapimoveis/search} with per-request options. */
  public ApiResponse zapimoveisSearch(ZapimoveisSearchParams params, RequestOptions options) {
    return call("zapimoveis_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Zazzle Product. Fetch one Zazzle product by its product URL or by a slug that ends in the numeric product id. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/zazzle/product}
   */
  public ApiResponse zazzleProduct(ZazzleProductParams params) {
    return zazzleProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/zazzle/product} with per-request options. */
  public ApiResponse zazzleProduct(ZazzleProductParams params, RequestOptions options) {
    return call("zazzle_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Zazzle Search. Search Zazzle products by keyword. page maps to the pg parameter. Prices are a number plus a display string. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/zazzle/search}
   */
  public ApiResponse zazzleSearch(ZazzleSearchParams params) {
    return zazzleSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/zazzle/search} with per-request options. */
  public ApiResponse zazzleSearch(ZazzleSearchParams params, RequestOptions options) {
    return call("zazzle_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Zillow Property. Extract full property details from a Zillow homedetails URL, including specs, photos, schools, tax history, and agent attribution. Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/zillow/property}
   */
  public ApiResponse zillowProperty(ZillowPropertyParams params) {
    return zillowProperty(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/zillow/property} with per-request options. */
  public ApiResponse zillowProperty(ZillowPropertyParams params, RequestOptions options) {
    return call("zillow_property", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Zillow Search. Search Zillow listings from a search or map URL and retrieve prices, beds, baths, coordinates, and listing status. Credit cost: 3 credits per call.
   *
   * <p>{@code GET / POST /v2/zillow/search}
   */
  public ApiResponse zillowSearch(ZillowSearchParams params) {
    return zillowSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/zillow/search} with per-request options. */
  public ApiResponse zillowSearch(ZillowSearchParams params, RequestOptions options) {
    return call("zillow_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * ZonaProp Property. Read one ZonaProp listing from its URL. You get the asking price in USD or ARS, the address, and the publisher. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/zonaprop/property}
   */
  public ApiResponse zonapropProperty(ZonapropPropertyParams params) {
    return zonapropProperty(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/zonaprop/property} with per-request options. */
  public ApiResponse zonapropProperty(ZonapropPropertyParams params, RequestOptions options) {
    return call("zonaprop_property", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * ZonaProp Search. Search ZonaProp for apartments or houses in an Argentine city. Page with pagina. A few featured ads can repeat. Development projects priced 'from' are left out. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/zonaprop/search}
   */
  public ApiResponse zonapropSearch(ZonapropSearchParams params) {
    return zonapropSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/zonaprop/search} with per-request options. */
  public ApiResponse zonapropSearch(ZonapropSearchParams params, RequestOptions options) {
    return call("zonaprop_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Zoopla Property. Extract full property details from a Zoopla listing URL, including price, specs, photo gallery, EPC rating, coordinates, and agent data. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/zoopla/property}
   */
  public ApiResponse zooplaProperty(ZooplaPropertyParams params) {
    return zooplaProperty(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/zoopla/property} with per-request options. */
  public ApiResponse zooplaProperty(ZooplaPropertyParams params, RequestOptions options) {
    return call("zoopla_property", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Zoopla Search. Search Zoopla listings from a search URL and retrieve prices, specs, photos, agency info, and pagination. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/zoopla/search}
   */
  public ApiResponse zooplaSearch(ZooplaSearchParams params) {
    return zooplaSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/zoopla/search} with per-request options. */
  public ApiResponse zooplaSearch(ZooplaSearchParams params, RequestOptions options) {
    return call("zoopla_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Zooplus Product. Read one Zooplus product by id or URL, in the region of that shop. Variants keep their own price and EAN. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/zooplus/product}
   */
  public ApiResponse zooplusProduct(ZooplusProductParams params) {
    return zooplusProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/zooplus/product} with per-request options. */
  public ApiResponse zooplusProduct(ZooplusProductParams params, RequestOptions options) {
    return call("zooplus_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Zooplus Search. Search a Zooplus country shop by keyword. Region defaults to France. Page through food, litter, and accessory cards with local prices. Credit cost: 1 credit per call.
   *
   * <p>{@code GET / POST /v2/zooplus/search}
   */
  public ApiResponse zooplusSearch(ZooplusSearchParams params) {
    return zooplusSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/zooplus/search} with per-request options. */
  public ApiResponse zooplusSearch(ZooplusSearchParams params, RequestOptions options) {
    return call("zooplus_search", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Zoro Product. Get a Zoro product page: title, brand, USD price, Zoro number, manufacturer number, images, and specifications. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/zoro/product}
   */
  public ApiResponse zoroProduct(ZoroProductParams params) {
    return zoroProduct(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/zoro/product} with per-request options. */
  public ApiResponse zoroProduct(ZoroProductParams params, RequestOptions options) {
    return call("zoro_product", params == null ? Map.of() : params.toMap(), options);
  }

  /**
   * Zoro Search. Search Zoro by keyword or category URL. Returns product cards with USD price, brand, Zoro number, and pagination. Credit cost: 2 credits per call.
   *
   * <p>{@code GET / POST /v2/zoro/search}
   */
  public ApiResponse zoroSearch(ZoroSearchParams params) {
    return zoroSearch(params, RequestOptions.defaults());
  }

  /** {@code GET / POST /v2/zoro/search} with per-request options. */
  public ApiResponse zoroSearch(ZoroSearchParams params, RequestOptions options) {
    return call("zoro_search", params == null ? Map.of() : params.toMap(), options);
  }

  private ApiResponse call(String name, Map<String, Object> params, RequestOptions options) {
    Endpoints.Endpoint endpoint = Endpoints.ALL.get(name);
    RequestOptions opts = options == null ? RequestOptions.defaults() : options;
    String method = opts.method() == null ? endpoint.methods().get(0) : opts.method();
    if (!endpoint.methods().contains(method)) {
      throw new PiloterrConfigurationException(
          endpoint.path() + " supports " + String.join(", ", endpoint.methods()) + ", not " + method + ".");
    }
    for (String required : endpoint.required()) {
      if (params.get(required) == null) {
        throw new PiloterrSerializationException("The \"" + required + "\" parameter is required.", required);
      }
    }
    return send(method, endpoint.path(), params, opts, endpoint.json());
  }

  private ApiResponse send(
      String method, String path, Map<String, ?> params, RequestOptions options, boolean expectJson) {
    if (!"GET".equals(method) && !"POST".equals(method)) {
      throw new PiloterrConfigurationException("Unsupported HTTP method: " + method + ".");
    }
    if (path == null || !path.startsWith("/")) {
      throw new PiloterrConfigurationException("The request path must start with \"/\".");
    }
    RequestOptions opts = options == null ? RequestOptions.defaults() : options;
    Serialization.Serialized serialized = Serialization.serialize(method, path, params);
    String url = baseUrl + serialized.path() + (serialized.query().isEmpty() ? "" : "?" + serialized.query());

    Map<String, String> requestHeaders = new LinkedHashMap<>();
    requestHeaders.put("Accept", "application/json, */*;q=0.8");
    requestHeaders.put("User-Agent", "piloterr-java/" + VERSION);
    requestHeaders.putAll(headers);
    requestHeaders.putAll(opts.headers());
    requestHeaders.put("x-api-key", apiKey);
    byte[] body = null;
    if (serialized.body() != null) {
      requestHeaders.put("Content-Type", "application/json");
      body = Serialization.toJson(serialized.body());
    }
    Duration requestTimeout = opts.timeout() != null ? opts.timeout() : timeout;

    int retries = opts.maxRetries() != null ? Math.max(opts.maxRetries(), 0) : maxRetries;
    RawResponse raw;
    for (int attempt = 0; ; attempt++) {
      String retryAfter = null;
      try {
        raw = transport.send(new PiloterrTransport.Request(method, URI.create(url), requestHeaders, body, requestTimeout));
        if (raw.status() >= 200 && raw.status() <= 299) {
          break;
        }
        if (attempt >= retries || !isRetryable(raw)) {
          throw apiException(raw);
        }
        List<String> values = raw.headers().get("retry-after");
        retryAfter = values == null || values.isEmpty() ? null : values.get(0);
      } catch (HttpTimeoutException e) {
        throw new PiloterrTransportException("Piloterr request timed out.", e);
      } catch (IOException e) {
        if (attempt >= retries) {
          throw new PiloterrTransportException("Piloterr request failed before an HTTP response was received.", e);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new PiloterrTransportException("Piloterr request was interrupted.", e);
      }
      pause(retryDelay(attempt, retryAfter));
    }
    ApiResponse response = new ApiResponse(raw);
    if (response.isJson()) {
      try {
        response.json();
      } catch (PiloterrResponseDecodingException e) {
        throw new PiloterrResponseDecodingException(
            "Piloterr returned a successful JSON response that could not be decoded.", raw, e);
      }
    } else if (expectJson) {
      throw new PiloterrResponseDecodingException(
          "Piloterr returned a successful non-JSON response for a JSON operation.", raw, null);
    }
    return response;
  }

  /**
   * Server errors and plan rate limits are retried. The API reports rate limits as 401 (or 429) with a "Rate limit
   * exceeded" message; per-key quotas ("...: quota daily") do not reset within seconds.
   */
  private static boolean isRetryable(RawResponse raw) {
    if (RETRYABLE_STATUSES.contains(raw.status())) {
      return true;
    }
    if (raw.status() != 401 && raw.status() != 429) {
      return false;
    }
    String error = apiException(raw).errorMessage();
    String message = error == null ? "" : error.toLowerCase(Locale.ROOT);
    if (message.contains("quota")) {
      return false;
    }
    return message.contains("rate limit") || (raw.status() == 429 && message.isEmpty());
  }

  private Duration retryDelay(int attempt, String retryAfter) {
    if (retryAfter != null) {
      try {
        double seconds = Double.parseDouble(retryAfter.trim());
        if (seconds >= 0) {
          return Duration.ofMillis((long) (Math.min(seconds, 60) * 1000));
        }
      } catch (NumberFormatException e) {
        // An HTTP date: fall back to exponential backoff.
      }
    }
    double millis = retryDelay.toMillis() * Math.pow(RETRY_BACKOFF_FACTOR, attempt);
    return Duration.ofMillis((long) Math.min(millis, MAX_RETRY_DELAY.toMillis()));
  }

  private static void pause(Duration delay) {
    try {
      Thread.sleep(delay.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new PiloterrTransportException("Piloterr request was interrupted while waiting to retry.", e);
    }
  }

  /**
   * Runs {@code task} for every item on {@code concurrency} threads and returns one {@link BatchResult} per
   * item, in input order. A failing item never stops the others; the client is thread-safe.
   *
   * <pre>{@code
   * List<BatchResult<ApiResponse>> results = client.batch(ids, 5, id -> client.someEndpoint(...));
   * }</pre>
   */
  public <I, T> List<BatchResult<T>> batch(Collection<? extends I> items, int concurrency, Function<? super I, ? extends T> task) {
    if (concurrency < 1) {
      throw new PiloterrConfigurationException("concurrency must be a positive integer.");
    }
    List<I> list = new ArrayList<>(items);
    if (list.isEmpty()) {
      return List.of();
    }
    ExecutorService pool = Executors.newFixedThreadPool(Math.min(concurrency, list.size()), runnable -> {
      Thread thread = new Thread(runnable, "piloterr-batch");
      thread.setDaemon(true);
      return thread;
    });
    try {
      List<Future<T>> futures = new ArrayList<>(list.size());
      for (I item : list) {
        futures.add(pool.submit(() -> task.apply(item)));
      }
      List<BatchResult<T>> results = new ArrayList<>(list.size());
      for (int i = 0; i < futures.size(); i++) {
        try {
          results.add(new BatchResult<>(i, futures.get(i).get(), null));
        } catch (ExecutionException e) {
          results.add(new BatchResult<>(i, null, e.getCause()));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new PiloterrTransportException("batch was interrupted.", e);
        }
      }
      return results;
    } finally {
      pool.shutdownNow();
    }
  }

  /** {@link #batch(Collection, int, Function)} with {@link #DEFAULT_CONCURRENCY} threads. */
  public <I, T> List<BatchResult<T>> batch(Collection<? extends I> items, Function<? super I, ? extends T> task) {
    return batch(items, DEFAULT_CONCURRENCY, task);
  }

  private static PiloterrApiException apiException(RawResponse raw) {
    JsonNode body = null;
    String message = null;
    try {
      body = Serialization.MAPPER.readTree(raw.body());
    } catch (IOException e) {
      body = null;
    }
    if (body != null && body.isObject()) {
      for (String key : ERROR_MESSAGE_KEYS) {
        JsonNode value = body.get(key);
        if (value != null && value.isTextual() && !value.asText().isEmpty()) {
          message = value.asText();
          break;
        }
      }
    }
    return new PiloterrApiException(raw, body, message);
  }

  /** Builder for {@link PiloterrClient}. */
  public static final class Builder {
    private String apiKey = System.getenv(API_KEY_ENVIRONMENT_VARIABLE);
    private String baseUrl = DEFAULT_BASE_URL;
    private Duration timeout = DEFAULT_TIMEOUT;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private PiloterrTransport transport;
    private int maxRetries = MAX_RETRIES;
    private Duration retryDelay = DEFAULT_RETRY_DELAY;

    private Builder() {}

    /** Retries after a network error or a 408/429/5xx response; 0 disables retries. Default 3. */
    public Builder maxRetries(int maxRetries) {
      this.maxRetries = maxRetries;
      return this;
    }

    /** Delay before the first retry, growing 1.5x per retry. Default 1 second. */
    public Builder retryDelay(Duration retryDelay) {
      this.retryDelay = retryDelay;
      return this;
    }

    /** API key sent in the {@code x-api-key} header. */
    public Builder apiKey(String apiKey) {
      this.apiKey = apiKey;
      return this;
    }

    /** API origin, for tests or proxies. */
    public Builder baseUrl(String baseUrl) {
      this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
      return this;
    }

    /** Client-side deadline per request; null disables it. */
    public Builder timeout(Duration timeout) {
      this.timeout = timeout;
      return this;
    }

    /** Header sent with every request. */
    public Builder header(String name, String value) {
      headers.put(name, value);
      return this;
    }

    /** Custom transport; defaults to {@link java.net.http.HttpClient} without redirects. */
    public Builder transport(PiloterrTransport transport) {
      this.transport = transport;
      return this;
    }

    /** Validates the configuration and creates the client. */
    public PiloterrClient build() {
      return new PiloterrClient(this);
    }
  }
}
