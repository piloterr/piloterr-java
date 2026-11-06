package com.piloterr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.piloterr.params.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class PiloterrClientTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  static final class Recording implements PiloterrTransport {
    final List<Request> requests = new ArrayList<>();
    Function<Request, RawResponse> responder = request -> json(200, "{\"ok\":true}");

    @Override
    public RawResponse send(Request request) throws IOException {
      requests.add(request);
      return responder.apply(request);
    }
  }

  static RawResponse json(int status, String body) {
    return new RawResponse(
        status, Map.of("content-type", List.of("application/json; charset=utf-8")), body.getBytes(StandardCharsets.UTF_8));
  }

  private final Recording transport = new Recording();
  private final PiloterrClient client =
      PiloterrClient.builder().apiKey("test-key").transport(transport).retryDelay(java.time.Duration.ofMillis(1)).build();

  @Test
  void requiresApiKey() {
    assertThrows(PiloterrConfigurationException.class, () -> PiloterrClient.builder().apiKey(null).build());
    assertThrows(PiloterrConfigurationException.class, () -> PiloterrClient.builder().apiKey("   ").build());
    assertThrows(
        PiloterrConfigurationException.class,
        () -> PiloterrClient.builder().apiKey("k").baseUrl("ftp://example.test").build());
  }

  @Test
  void featuredOperation() throws IOException {
    transport.responder = request -> json(200, "{\"hello\":\"world\"}");
    ApiResponse response = client.amazonProduct(AmazonProductParams.builder().query("B09DFCB66S").build());
    assertEquals("world", response.json().get("hello").asText());
    PiloterrTransport.Request request = transport.requests.get(0);
    assertEquals("GET", request.method());
    assertEquals("https://api.piloterr.com/v2/amazon/product", request.uri().toString().split("\\?")[0]);
    assertEquals("test-key", request.headers().get("x-api-key"));
    assertTrue(request.headers().get("User-Agent").startsWith("piloterr-java/"));
  }

  @Test
  void querySerialization() {
    PiloterrClient local = PiloterrClient.builder().apiKey("k").baseUrl("https://example.test/").transport(transport).build();
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("text", "a b&c");
    params.put("flag", false);
    params.put("count", 0);
    params.put("ratio", 1.5);
    params.put("whole", 2.0);
    params.put("list", List.of("x", "y"));
    params.put("nothing", null);
    local.request("GET", "/v1/anything", params);
    assertEquals(
        "https://example.test/v1/anything?text=a%20b%26c&flag=false&count=0&ratio=1.5&whole=2&list=x&list=y",
        transport.requests.get(0).uri().toString());
  }

  @Test
  void postSendsJsonBody() throws IOException {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("query", "x");
    params.put("page", 2);
    params.put("gone", null);
    client.request("POST", "/v1/anything", params);
    PiloterrTransport.Request request = transport.requests.get(0);
    assertEquals("POST", request.method());
    assertEquals("application/json", request.headers().get("Content-Type"));
    assertEquals(MAPPER.readTree("{\"query\":\"x\",\"page\":2}"), MAPPER.readTree(request.body()));
    assertEquals(null, request.uri().getRawQuery());
  }

  @Test
  void postKeepsGlobalParametersInQuery() throws IOException {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("query", "x");
    params.put("is_async", true);
    params.put("fields", "a");
    client.request("POST", "/v1/anything", params);
    PiloterrTransport.Request request = transport.requests.get(0);
    assertEquals(MAPPER.readTree("{\"query\":\"x\"}"), MAPPER.readTree(request.body()));
    assertEquals("fields=a&is_async=true", request.uri().getRawQuery());
  }

  @Test
  void pathParameters() {
    client.request("GET", "/v1/items/{id}", Map.of("id", "a/b c"));
    assertEquals("https://api.piloterr.com/v1/items/a%2Fb%20c", transport.requests.get(0).uri().toString());
    assertThrows(PiloterrSerializationException.class, () -> client.request("GET", "/v1/items/{id}", Map.of()));
  }

  @Test
  void requiredParameters() {
    assertThrows(PiloterrSerializationException.class, () -> AmazonProductParams.builder().build());
    assertTrue(transport.requests.isEmpty());
  }

  @Test
  void unsupportedMethod() {
    assertThrows(
        PiloterrConfigurationException.class,
        () -> client.jobs(JobsParams.builder().id("test").build(), RequestOptions.method("POST")));
  }

  @Test
  void dualOperationCanUsePost() throws IOException {
    client.aboutyouProduct(AboutyouProductParams.builder().query("test").build(), RequestOptions.method("POST"));
    PiloterrTransport.Request request = transport.requests.get(0);
    assertEquals("POST", request.method());
    assertEquals(null, request.uri().getRawQuery());
    assertTrue(MAPPER.readTree(request.body()).isObject());
  }

  @Test
  void everyEndpoint() throws Exception {
    Map<String, Runnable> calls = new LinkedHashMap<>();
    calls.put("aboutyou_product", () -> client.aboutyouProduct(AboutyouProductParams.builder().query("test").build()));
    calls.put("aboutyou_search", () -> client.aboutyouSearch(AboutyouSearchParams.builder().query("suede bloom").build()));
    calls.put("action_product", () -> client.actionProduct(ActionProductParams.builder().query("3216159").build()));
    calls.put("action_search", () -> client.actionSearch(ActionSearchParams.builder().query("meuble").build()));
    calls.put("aldi_product", () -> client.aldiProduct(AldiProductParams.builder().query("fiches-produits/filets-danchois-marines-6806.html").build()));
    calls.put("aldi_search", () -> client.aldiSearch(AldiSearchParams.builder().query("produits/viande-poisson/poisson-crustace.html").build()));
    calls.put("alibaba_product", () -> client.alibabaProduct(AlibabaProductParams.builder().query("https://www.alibaba.com/product-detail/Custom-Neon-Light-Acrylic-Neon-Sign_1601175379813.html").build()));
    calls.put("alibaba_search", () -> client.alibabaSearch(AlibabaSearchParams.builder().query("https://www.alibaba.com/trade/search?SearchText=neon").build()));
    calls.put("aliexpress_product", () -> client.aliexpressProduct(AliexpressProductParams.builder().query("https://www.aliexpress.com/item/1005009517624640.html").build()));
    calls.put("aliexpress_product_reviews", () -> client.aliexpressProductReviews(AliexpressProductReviewsParams.builder().query("1005001388303149").build()));
    calls.put("aliexpress_search", () -> client.aliexpressSearch(AliexpressSearchParams.builder().query("meta").build()));
    calls.put("aliexpress_store_product", () -> client.aliexpressStoreProduct(AliexpressStoreProductParams.builder().query("https://www.aliexpress.com/store/1101705614").build()));
    calls.put("allegro_product", () -> client.allegroProduct(AllegroProductParams.builder().query("https://allegro.pl/produkt/slug-uuid?offerId=123").build()));
    calls.put("allegro_search", () -> client.allegroSearch(AllegroSearchParams.builder().query("mufowa").build()));
    calls.put("amazon_product", () -> client.amazonProduct(AmazonProductParams.builder().query("B09DFCB66S").build()));
    calls.put("amazon_product_offer", () -> client.amazonProductOffer(AmazonProductOfferParams.builder().query("B093C9B1HK").build()));
    calls.put("amazon_search", () -> client.amazonSearch(AmazonSearchParams.builder().query("playstation 5").build()));
    calls.put("apartments_property", () -> client.apartmentsProperty(ApartmentsPropertyParams.builder().query("https://www.apartments.com/141-e-33rd-st-new-york-ny-unit-si-fl16-id43/p6b4mes/").build()));
    calls.put("apartments_search", () -> client.apartmentsSearch(ApartmentsSearchParams.builder().query("https://www.apartments.com/new-york-ny/3").build()));
    calls.put("argenprop_property", () -> client.argenpropProperty(ArgenpropPropertyParams.builder().query("https://www.argenprop.com/departamento-en-venta-en-palermo-chico-5-ambientes--20724993").build()));
    calls.put("argenprop_search", () -> client.argenpropSearch(ArgenpropSearchParams.builder().query("palermo").build()));
    calls.put("argos_product", () -> client.argosProduct(ArgosProductParams.builder().query("test").build()));
    calls.put("argos_search", () -> client.argosSearch(ArgosSearchParams.builder().query("laptop").build()));
    calls.put("arxiv_paper", () -> client.arxivPaper(ArxivPaperParams.builder().query("2201.00978 or https://arxiv.org/abs/cond-mat/0011267v1.").build()));
    calls.put("arxiv_search", () -> client.arxivSearch(ArxivSearchParams.builder().query("electron. A paper URL such as https://arxiv.org/abs/2201.00978 returns 400.").build()));
    calls.put("auchan_product", () -> client.auchanProduct(AuchanProductParams.builder().query("https://www.auchan.fr/charles-alice-specialite-pommes-mangues-sans-sucres-ajoutes/pr-C1177497").build()));
    calls.put("auchan_search", () -> client.auchanSearch(AuchanSearchParams.builder().query("pomme").build()));
    calls.put("autodoc_product", () -> client.autodocProduct(AutodocProductParams.builder().query("https://www.auto-doc.fr/ridex/7999541").build()));
    calls.put("autodoc_product_related", () -> client.autodocProductRelated(AutodocProductRelatedParams.builder().query("https://www.auto-doc.fr/ridex/7999541").makerId(93).vehicleType("pkw").makerName("RENAULT").productId(7999541).build()));
    calls.put("autodoc_search", () -> client.autodocSearch(AutodocSearchParams.builder().query("https://www.auto-doc.fr/search?keyword=frein").build()));
    calls.put("autoeurope_result", () -> client.autoeuropeResult(AutoeuropeResultParams.builder().offerId("3CD30DE2D9BE4CE8FC28E20D2702FCC7").pickup("CDG").pickupDate("2026-11-06").dropoffDate("2026-11-09").build()));
    calls.put("autoeurope_search", () -> client.autoeuropeSearch(AutoeuropeSearchParams.builder().pickup("CDG").pickupDate("2026-11-06").dropoffDate("2026-11-09").build()));
    calls.put("autoscout24_ad", () -> client.autoscout24Ad(Autoscout24AdParams.builder().query("https://www.autoscout24.fr/offres/porsche-macan-s-diesel-pano-1-hand-diesel-or-cat_ma57mo20311-547632b2-e4fc-47f3-8143-1a2d7b276366").build()));
    calls.put("autoscout24_search", () -> client.autoscout24Search(Autoscout24SearchParams.builder().query("https://www.autoscout24.fr/lst/volkswagen/golf?atype=C").build()));
    calls.put("autotrader_ad", () -> client.autotraderAd(AutotraderAdParams.builder().query("https://www.autotrader.co.uk/car-details/202606053030491").build()));
    calls.put("autotrader_search", () -> client.autotraderSearch(AutotraderSearchParams.builder().query("https://www.autotrader.co.uk/car-search?channel=cars&postcode=W1F%208EB&price_search_type=total&sort=relevance&page=1").build()));
    calls.put("bestbuy_product", () -> client.bestbuyProduct(BestbuyProductParams.builder().query("https://www.bestbuy.com/product/gigabyte-aero-x16-copilot-pc-165hz-wqxga-rtx-5070-ryzen-ai-7-350-1tb-ssd-16gb-ddr5-windows-11-lunar-white/J3GWPQC2RQ").build()));
    calls.put("bestbuy_product_reviews", () -> client.bestbuyProductReviews(BestbuyProductReviewsParams.builder().query("test").build()));
    calls.put("bestbuy_search", () -> client.bestbuySearch(BestbuySearchParams.builder().query("machine").build()));
    calls.put("bhphotovideo_product", () -> client.bhphotovideoProduct(BhphotovideoProductParams.builder().query("1948568-REG").build()));
    calls.put("bhphotovideo_search", () -> client.bhphotovideoSearch(BhphotovideoSearchParams.builder().query("sandisk extreme pro").build()));
    calls.put("bing_search", () -> client.bingSearch(BingSearchParams.builder().query("openai").build()));
    calls.put("bitiba_product", () -> client.bitibaProduct(BitibaProductParams.builder().query("75036. A search link returns 400.").build()));
    calls.put("bitiba_search", () -> client.bitibaSearch(BitibaSearchParams.builder().query("test").build()));
    calls.put("bol_product", () -> client.bolProduct(BolProductParams.builder().query("9300000240171676").build()));
    calls.put("bol_search", () -> client.bolSearch(BolSearchParams.builder().query("lego").build()));
    calls.put("boulanger_product", () -> client.boulangerProduct(BoulangerProductParams.builder().query("https://www.boulanger.com/ref/1193303").build()));
    calls.put("boulanger_search", () -> client.boulangerSearch(BoulangerSearchParams.builder().query("aspirateur").build()));
    calls.put("brave_search", () -> client.braveSearch(BraveSearchParams.builder().query("anthropic").build()));
    calls.put("bricodepot_product", () -> client.bricodepotProduct(BricodepotProductParams.builder().query("https://www.bricodepot.fr/p/3373270014082/compresseur-coaxial-50l-vertical-10-bars-accessoires-1").build()));
    calls.put("bricodepot_search", () -> client.bricodepotSearch(BricodepotSearchParams.builder().query("https://www.bricodepot.fr/produits/outillage/outillage-electroportatif-a-main-et-accessoire/perceuse-visseuse-et-outillage-de-percage/perceuse-visseuse-et-perceuse-a-percussion-sans-fil").build()));
    calls.put("bricorama_product", () -> client.bricoramaProduct(BricoramaProductParams.builder().query("https://www.bricorama.fr/p/perceuse-visseuse-sans-fil-18v/3999991691267").build()));
    calls.put("bricorama_search", () -> client.bricoramaSearch(BricoramaSearchParams.builder().query("perceuse").build()));
    calls.put("carrefour_product", () -> client.carrefourProduct(CarrefourProductParams.builder().query("https://www.carrefour.fr/p/oeufs-de-caille-le-gaulois-3266980075934").build()));
    calls.put("carrefour_search", () -> client.carrefourSearch(CarrefourSearchParams.builder().query("pomme").build()));
    calls.put("carrefour_suggest", () -> client.carrefourSuggest(CarrefourSuggestParams.builder().query("coca").build()));
    calls.put("castorama_product", () -> client.castoramaProduct(CastoramaProductParams.builder().query("https://www.castorama.fr/mastic-bain-et-cuisine-rubson-blanc-280ml/5410091628840_CAFR.prd").build()));
    calls.put("castorama_search", () -> client.castoramaSearch(CastoramaSearchParams.builder().query("joint-silicone").build()));
    calls.put("cdiscount_product", () -> client.cdiscountProduct(CdiscountProductParams.builder().query("https://www.cdiscount.com/informatique/ordinateurs-pc-portables/acemagic-pc-portables-gaming/f-1070992-aaaph06865.html").build()));
    calls.put("cdiscount_product_offer", () -> client.cdiscountProductOffer(CdiscountProductOfferParams.builder().query("https://www.cdiscount.com/mp-352880-auc3283981039048.html").build()));
    calls.put("cdiscount_search", () -> client.cdiscountSearch(CdiscountSearchParams.builder().query("https://www.cdiscount.com/search/10/laptop.html").build()));
    calls.put("centris_property", () -> client.centrisProperty(CentrisPropertyParams.builder().query("https://www.centris.ca/en/condos~for-sale~montreal-le-sud-ouest/17796540").build()));
    calls.put("centris_search", () -> client.centrisSearch(CentrisSearchParams.builder().query("montreal").build()));
    calls.put("chewy_product", () -> client.chewyProduct(ChewyProductParams.builder().query("test").build()));
    calls.put("chewy_search", () -> client.chewySearch(ChewySearchParams.builder().query("test").build()));
    calls.put("clearedjobs_job", () -> client.clearedjobsJob(ClearedjobsJobParams.builder().query("analyst-washington-district-of-columbia-1902823.").build()));
    calls.put("clearedjobs_search", () -> client.clearedjobsSearch(ClearedjobsSearchParams.builder().query("analyst. A job URL returns 400.").build()));
    calls.put("company", () -> client.company(CompanyParams.builder().query("gucci.com").build()));
    calls.put("coolblue_product", () -> client.coolblueProduct(CoolblueProductParams.builder().query("https://www.coolblue.be/nl/product/905648/sony-wh-1000xm5-zwart.html").build()));
    calls.put("coolblue_search", () -> client.coolblueSearch(CoolblueSearchParams.builder().query("sony wh-1000xm5").build()));
    calls.put("copart_lot", () -> client.copartLot(CopartLotParams.builder().query("55295666").build()));
    calls.put("copart_search", () -> client.copartSearch(CopartSearchParams.builder().query("chevrolet").build()));
    calls.put("costco_product", () -> client.costcoProduct(CostcoProductParams.builder().query("4000266385").build()));
    calls.put("costco_search", () -> client.costcoSearch(CostcoSearchParams.builder().query("home").build()));
    calls.put("crunchbase_company_info", () -> client.crunchbaseCompanyInfo(CrunchbaseCompanyInfoParams.builder().build()));
    calls.put("crunchbase_event", () -> client.crunchbaseEvent(CrunchbaseEventParams.builder().query("london-tech-week-2025").build()));
    calls.put("crunchbase_funding_round", () -> client.crunchbaseFundingRound(CrunchbaseFundingRoundParams.builder().query("openai-secondary-market--3bab6e98").build()));
    calls.put("crunchbase_funding_rounds", () -> client.crunchbaseFundingRounds(CrunchbaseFundingRoundsParams.builder().build()));
    calls.put("crunchbase_people_info", () -> client.crunchbasePeopleInfo(CrunchbasePeopleInfoParams.builder().query("alex-martin").build()));
    calls.put("crunchbase_search", () -> client.crunchbaseSearch(CrunchbaseSearchParams.builder().query("openai").build()));
    calls.put("cultura_product", () -> client.culturaProduct(CulturaProductParams.builder().query("https://www.cultura.com/p-harry-potter-et-le-prisonnier-d-azkaban-illustre-par-minalima-9782075193993.html").build()));
    calls.put("cultura_search", () -> client.culturaSearch(CulturaSearchParams.builder().query("harry potter").build()));
    calls.put("darty_product", () -> client.dartyProduct(DartyProductParams.builder().query("https://www.darty.com/nav/achat/petit_electromenager/aspirateur-balai_main/aspirateur_balai/dyson_piston_animal.html").build()));
    calls.put("darty_search", () -> client.dartySearch(DartySearchParams.builder().query("aspirateur").build()));
    calls.put("datasets_linkedin_company", () -> client.datasetsLinkedinCompany(DatasetsLinkedinCompanyParams.builder().build()));
    calls.put("decathlon_product", () -> client.decathlonProduct(DecathlonProductParams.builder().query("https://www.decathlon.fr/p/gourde-alu-1l-avec-bouchon-instantane-composee-a-100percent-dalu-recycle-bleu/330041/c98m8612536").build()));
    calls.put("decathlon_search", () -> client.decathlonSearch(DecathlonSearchParams.builder().query("gourde").build()));
    calls.put("digikey_product", () -> client.digikeyProduct(DigikeyProductParams.builder().query("https://www.digikey.fr/fr/products/detail/phoenix-contact/2981965/3604293").build()));
    calls.put("digikey_search", () -> client.digikeySearch(DigikeySearchParams.builder().query("phoenix contact").build()));
    calls.put("doaj_article", () -> client.doajArticle(DoajArticleParams.builder().query("000004687a3a411cb466625281f2ceb0.").build()));
    calls.put("doaj_journal", () -> client.doajJournal(DoajJournalParams.builder().query("2747-173X.").build()));
    calls.put("doaj_search", () -> client.doajSearch(DoajSearchParams.builder().query("climate. An article or journal URL returns 400.").build()));
    calls.put("domain_dnsbl", () -> client.domainDnsbl(DomainDnsblParams.builder().query("185.122.10.10").build()));
    calls.put("domain_malicious", () -> client.domainMalicious(DomainMaliciousParams.builder().query("197.33.189.70").build()));
    calls.put("domain_whois", () -> client.domainWhois(DomainWhoisParams.builder().query("piloterr.com").build()));
    calls.put("domaincomau_property", () -> client.domaincomauProperty(DomaincomauPropertyParams.builder().query("https://www.domain.com.au/7d-27-31-ocean-street-n-bondi-nsw-2026-2021178032").build()));
    calls.put("domaincomau_search", () -> client.domaincomauSearch(DomaincomauSearchParams.builder().query("Bondi NSW 2026").build()));
    calls.put("duckduckgo_search", () -> client.duckduckgoSearch(DuckduckgoSearchParams.builder().query("test").build()));
    calls.put("ebay_product", () -> client.ebayProduct(EbayProductParams.builder().query("257758922911").build()));
    calls.put("ebay_search", () -> client.ebaySearch(EbaySearchParams.builder().query("laptop").build()));
    calls.put("elcorteingles_product", () -> client.elcorteinglesProduct(ElcorteinglesProductParams.builder().query("A200910175").build()));
    calls.put("elcorteingles_search", () -> client.elcorteinglesSearch(ElcorteinglesSearchParams.builder().query("marino").build()));
    calls.put("eleclerc_product", () -> client.eleclercProduct(EleclercProductParams.builder().query("3564700201442").build()));
    calls.put("eleclerc_search", () -> client.eleclercSearch(EleclercSearchParams.builder().query("pomme").build()));
    calls.put("eleclerc_suggest", () -> client.eleclercSuggest(EleclercSuggestParams.builder().query("pomme").build()));
    calls.put("email_analyzes", () -> client.emailAnalyzes(EmailAnalyzesParams.builder().query("airbus.com").build()));
    calls.put("email_finder", () -> client.emailFinder(EmailFinderParams.builder().query("Jane Doe").build()));
    calls.put("email_verify", () -> client.emailVerify(EmailVerifyParams.builder().query("jane.doe@example.com").build()));
    calls.put("europages_company", () -> client.europagesCompany(EuropagesCompanyParams.builder().query("https://www.europages.fr/fr/company/synyco-sas-22106635").build()));
    calls.put("europages_search", () -> client.europagesSearch(EuropagesSearchParams.builder().query("emballage").build()));
    calls.put("eventbrite_event", () -> client.eventbriteEvent(EventbriteEventParams.builder().query("2002625586037").build()));
    calls.put("eventbrite_search", () -> client.eventbriteSearch(EventbriteSearchParams.builder().build()));
    calls.put("expedia_ad", () -> client.expediaAd(ExpediaAdParams.builder().query("https://www.expedia.com/New-York-Hotels-Hilton-New-York-Fashion-District.h3117215.Hotel-Information").build()));
    calls.put("expedia_flight", () -> client.expediaFlight(ExpediaFlightParams.builder().query("JFK-LAX").build()));
    calls.put("expedia_search", () -> client.expediaSearch(ExpediaSearchParams.builder().query("https://euro.expedia.net/Hotel-Search?currency=EUR&siteid=4400&destination=Toulouse").build()));
    calls.put("fashionphile_product", () -> client.fashionphileProduct(FashionphileProductParams.builder().query("celine-supple-shiny-lambskin-little-new-luggage-black-1978804").build()));
    calls.put("fashionphile_search", () -> client.fashionphileSearch(FashionphileSearchParams.builder().query("celine").build()));
    calls.put("finder_chewy_products", () -> client.finderChewyProducts(FinderChewyProductsParams.builder().build()));
    calls.put("finder_eleclerc_stores", () -> client.finderEleclercStores(FinderEleclercStoresParams.builder().query("paris").build()));
    calls.put("finder_google_countries", () -> client.finderGoogleCountries(FinderGoogleCountriesParams.builder().query("fr").build()));
    calls.put("finder_google_languages", () -> client.finderGoogleLanguages(FinderGoogleLanguagesParams.builder().query("french").build()));
    calls.put("finder_google_locations", () -> client.finderGoogleLocations(FinderGoogleLocationsParams.builder().query("Paris").build()));
    calls.put("finder_leroymerlin_products", () -> client.finderLeroymerlinProducts(FinderLeroymerlinProductsParams.builder().build()));
    calls.put("finder_leroymerlin_stores", () -> client.finderLeroymerlinStores(FinderLeroymerlinStoresParams.builder().query("balma").build()));
    calls.put("finder_linkedin_industries", () -> client.finderLinkedinIndustries(FinderLinkedinIndustriesParams.builder().query("software development").build()));
    calls.put("finder_mrbricolage_stores", () -> client.finderMrbricolageStores(FinderMrbricolageStoresParams.builder().query("matignon").build()));
    calls.put("fiverr_profile_info", () -> client.fiverrProfileInfo(FiverrProfileInfoParams.builder().query("https://www.fiverr.com/username").build()));
    calls.put("fiverr_search", () -> client.fiverrSearch(FiverrSearchParams.builder().query("https://www.fiverr.com/search/gigs?query=website+development").build()));
    calls.put("fnac_product", () -> client.fnacProduct(FnacProductParams.builder().query("23428278").build()));
    calls.put("fnac_search", () -> client.fnacSearch(FnacSearchParams.builder().query("iphone").build()));
    calls.put("fotocasa_ad", () -> client.fotocasaAd(FotocasaAdParams.builder().query("https://www.fotocasa.es/es/comprar/vivienda/madrid-capital/aire-acondicionado-calefaccion-terraza-ascensor-amueblado/190589697/d").build()));
    calls.put("fotocasa_search", () -> client.fotocasaSearch(FotocasaSearchParams.builder().query("madrid").build()));
    calls.put("franchisedirect_franchise", () -> client.franchisedirectFranchise(FranchisedirectFranchiseParams.builder().query("https://www.franchisedirect.com/coffee-franchises/coffeenow").build()));
    calls.put("franchisedirect_search", () -> client.franchisedirectSearch(FranchisedirectSearchParams.builder().query("coffee").build()));
    calls.put("fressnapf_product", () -> client.fressnapfProduct(FressnapfProductParams.builder().query("https://www.fressnapf.de/p/royal-canin-maxi-adult-15-kg-1003112004/").build()));
    calls.put("fressnapf_search", () -> client.fressnapfSearch(FressnapfSearchParams.builder().query("halsband").build()));
    calls.put("funda_property", () -> client.fundaProperty(FundaPropertyParams.builder().query("https://www.funda.nl/detail/koop/amsterdam/appartement-hillegomstraat-57-1/44654106/").build()));
    calls.put("funda_search", () -> client.fundaSearch(FundaSearchParams.builder().query("amsterdam").build()));
    calls.put("g2_product_info", () -> client.g2ProductInfo(G2ProductInfoParams.builder().query("postman").build()));
    calls.put("github_user_info", () -> client.githubUserInfo(GithubUserInfoParams.builder().query("https://github.com/example-user").build()));
    calls.put("glassdoor_company_info", () -> client.glassdoorCompanyInfo(GlassdoorCompanyInfoParams.builder().query("1692882").build()));
    calls.put("glassdoor_company_search", () -> client.glassdoorCompanySearch(GlassdoorCompanySearchParams.builder().query("airbus").build()));
    calls.put("glassdoor_job_info", () -> client.glassdoorJobInfo(GlassdoorJobInfoParams.builder().query("1010283388161").build()));
    calls.put("glassdoor_job_search", () -> client.glassdoorJobSearch(GlassdoorJobSearchParams.builder().query("security").build()));
    calls.put("goodreads_book", () -> client.goodreadsBook(GoodreadsBookParams.builder().query("44767458").build()));
    calls.put("goodreads_search", () -> client.goodreadsSearch(GoodreadsSearchParams.builder().query("dune").build()));
    calls.put("google_images", () -> client.googleImages(GoogleImagesParams.builder().query("mountain landscape").build()));
    calls.put("google_jobs", () -> client.googleJobs(GoogleJobsParams.builder().query("software engineer").build()));
    calls.put("google_maps", () -> client.googleMaps(GoogleMapsParams.builder().query("coffee shop paris").build()));
    calls.put("google_news", () -> client.googleNews(GoogleNewsParams.builder().query("artificial intelligence news").build()));
    calls.put("google_search", () -> client.googleSearch(GoogleSearchParams.builder().query("best electric cars 2024").build()));
    calls.put("google_search_ai", () -> client.googleSearchAi(GoogleSearchAiParams.builder().query("what is mozilla firefox").build()));
    calls.put("google_search_autocomplete", () -> client.googleSearchAutocomplete(GoogleSearchAutocompleteParams.builder().query("coffee").build()));
    calls.put("google_shopping", () -> client.googleShopping(GoogleShoppingParams.builder().query("playstation 5").build()));
    calls.put("google_videos", () -> client.googleVideos(GoogleVideosParams.builder().query("paris travel guide").build()));
    calls.put("googleadstransparency_ad", () -> client.googleadstransparencyAd(GoogleadstransparencyAdParams.builder().query("https://adstransparency.google.com/advertiser/AR10904289436120383489/creative/CR10927354991047868417?region=FR").build()));
    calls.put("googleadstransparency_search", () -> client.googleadstransparencySearch(GoogleadstransparencySearchParams.builder().query("webapi.group").build()));
    calls.put("grainger_product", () -> client.graingerProduct(GraingerProductParams.builder().query("3FRU1").build()));
    calls.put("grainger_search", () -> client.graingerSearch(GraingerSearchParams.builder().query("carolina").build()));
    calls.put("greenhouse_job", () -> client.greenhouseJob(GreenhouseJobParams.builder().query("discord/8806482002 or https://job-boards.greenhouse.io/discord/jobs/8806482002.").build()));
    calls.put("greenhouse_search", () -> client.greenhouseSearch(GreenhouseSearchParams.builder().query("discord. A job URL returns 400.").build()));
    calls.put("gsaelibrary_contract", () -> client.gsaelibraryContract(GsaelibraryContractParams.builder().query("https://www.gsaelibrary.gsa.gov/ElibMain/contractorInfo.do?contractNumber=GS-07F-0239T&contractorName=US+RADAR+INC&executeQuery=YES").build()));
    calls.put("gsaelibrary_search", () -> client.gsaelibrarySearch(GsaelibrarySearchParams.builder().query("radar").build()));
    calls.put("homestra_ad", () -> client.homestraAd(HomestraAdParams.builder().query("https://homestra.com/property/charming-4-bedroom-stone-house-with-historic-towers-in-idyllic-french-village/").build()));
    calls.put("homestra_search", () -> client.homestraSearch(HomestraSearchParams.builder().query("https://homestra.com/list/houses-for-sale/?maximum-price=900000&minimum-price=75000&page=1").build()));
    calls.put("huggingface_model", () -> client.huggingfaceModel(HuggingfaceModelParams.builder().query("google-bert/bert-base-uncased").build()));
    calls.put("huggingface_search", () -> client.huggingfaceSearch(HuggingfaceSearchParams.builder().query("bert").build()));
    calls.put("idealista_property", () -> client.idealistaProperty(IdealistaPropertyParams.builder().query("https://www.idealista.com/fr/inmueble/111463799/").build()));
    calls.put("idealista_provinces", () -> client.idealistaProvinces(IdealistaProvincesParams.builder().build()));
    calls.put("idealista_search", () -> client.idealistaSearch(IdealistaSearchParams.builder().query("https://www.idealista.com/venta-viviendas/barcelona/ciutat-vella/la-barceloneta/").build()));
    calls.put("idealo_product", () -> client.idealoProduct(IdealoProductParams.builder().query("https://www.idealo.es/precios/205227870/asics-novablast-5-1011b974.html").build()));
    calls.put("idealo_search", () -> client.idealoSearch(IdealoSearchParams.builder().query("maison").build()));
    calls.put("ikea_product", () -> client.ikeaProduct(IkeaProductParams.builder().query("https://www.ikea.com/fr/fr/p/gladhoejden-bureau-assis-debout-blanc-30493939/").build()));
    calls.put("ikea_search", () -> client.ikeaSearch(IkeaSearchParams.builder().query("https://www.ikea.com/fr/fr/search/?q=meuble").build()));
    calls.put("immobilienscout24_property", () -> client.immobilienscout24Property(Immobilienscout24PropertyParams.builder().query("168052908").build()));
    calls.put("immobilienscout24_search", () -> client.immobilienscout24Search(Immobilienscout24SearchParams.builder().query("berlin/berlin").build()));
    calls.put("immoscout24ch_search", () -> client.immoscout24chSearch(Immoscout24chSearchParams.builder().query("zurich").build()));
    calls.put("immowelt_property", () -> client.immoweltProperty(ImmoweltPropertyParams.builder().query("https://www.immowelt.de/expose/65de0309-bf41-48ce-86fd-6a3441fd0487").build()));
    calls.put("immowelt_search", () -> client.immoweltSearch(ImmoweltSearchParams.builder().query("https://www.immowelt.de/suche/mieten/wohnung/deutschland/ad02de1").build()));
    calls.put("imoveisweb_property", () -> client.imoveiswebProperty(ImoveiswebPropertyParams.builder().query("https://www.imoveisweb.com.br/imoveis/casas/3151/").build()));
    calls.put("imoveisweb_search", () -> client.imoveiswebSearch(ImoveiswebSearchParams.builder().query("casas").build()));
    calls.put("indeed_company_info", () -> client.indeedCompanyInfo(IndeedCompanyInfoParams.builder().query("Proman-1").build()));
    calls.put("indeed_company_search", () -> client.indeedCompanySearch(IndeedCompanySearchParams.builder().query("airbus").build()));
    calls.put("indeed_job_info", () -> client.indeedJobInfo(IndeedJobInfoParams.builder().query("585181841cb618e0").build()));
    calls.put("indeed_job_salary", () -> client.indeedJobSalary(IndeedJobSalaryParams.builder().query("ingénieur-etudes-et-développement").build()));
    calls.put("indeed_job_search", () -> client.indeedJobSearch(IndeedJobSearchParams.builder().query("developpeur python").build()));
    calls.put("inet_product", () -> client.inetProduct(InetProductParams.builder().query("4306287. A search link returns 400.").build()));
    calls.put("inet_search", () -> client.inetSearch(InetSearchParams.builder().query("test").build()));
    calls.put("instagram_post_info", () -> client.instagramPostInfo(InstagramPostInfoParams.builder().query("https://www.instagram.com/p/CshTQQlMGMl").build()));
    calls.put("instagram_user_info", () -> client.instagramUserInfo(InstagramUserInfoParams.builder().query("https://www.instagram.com/example-user/").build()));
    calls.put("intermarche_product", () -> client.intermarcheProduct(IntermarcheProductParams.builder().query("https://www.intermarche.com/produit/flan-patissier-cuit-&-dore-au-four/3250391522828").shopId("11301").build()));
    calls.put("intermarche_search", () -> client.intermarcheSearch(IntermarcheSearchParams.builder().query("lait").shopId("11301").build()));
    calls.put("intersport_product", () -> client.intersportProduct(IntersportProductParams.builder().query("https://www.intersport.fr/blancetvert-chaussures_de_running_homme_pegasus_plus2-nike-p-IM2541~VZE/").build()));
    calls.put("intersport_search", () -> client.intersportSearch(IntersportSearchParams.builder().query("nike").build()));
    calls.put("jamesedition_property", () -> client.jameseditionProperty(JameseditionPropertyParams.builder().query("test").build()));
    calls.put("jamesedition_search", () -> client.jameseditionSearch(JameseditionSearchParams.builder().query("villa").build()));
    calls.put("jobs", () -> client.jobs(JobsParams.builder().id("test").build()));
    calls.put("kick_user_info", () -> client.kickUserInfo(KickUserInfoParams.builder().query("https://kick.com/example-user").build()));
    calls.put("kijiji_ad", () -> client.kijijiAd(KijijiAdParams.builder().query("https://www.kijiji.ca/v-cell-phone-accessories/city-of-toronto/nateroso-iphone-16-case-cute-bear-honey-design/1745477275").build()));
    calls.put("kijiji_search", () -> client.kijijiSearch(KijijiSearchParams.builder().query("https://www.kijiji.ca/b-bikes/city-of-toronto/c644l1700273").build()));
    calls.put("kroger_product", () -> client.krogerProduct(KrogerProductParams.builder().query("0001111040101").build()));
    calls.put("kroger_search", () -> client.krogerSearch(KrogerSearchParams.builder().query("milk").build()));
    calls.put("lamudi_ad", () -> client.lamudiAd(LamudiAdParams.builder().query("https://www.lamudi.com.ph/property/41032-73-626c638217d1-1d2a-1985488-907f-7802").build()));
    calls.put("lamudi_search", () -> client.lamudiSearch(LamudiSearchParams.builder().build()));
    calls.put("leboncoin_ad", () -> client.leboncoinAd(LeboncoinAdParams.builder().query("3012186547").build()));
    calls.put("leboncoin_search", () -> client.leboncoinSearch(LeboncoinSearchParams.builder().query("https://www.leboncoin.fr/c/offres_d_emploi?locations=Paris_75015__48.84105_2.29926_2747").build()));
    calls.put("leboncoin_search_api", () -> client.leboncoinSearchApi(LeboncoinSearchApiParams.builder().build()));
    calls.put("lenovo_product", () -> client.lenovoProduct(LenovoProductParams.builder().query("https://www.lenovo.com/fr/fr/p/workstations/thinkstationp/thinkstation-p5-gen-2-intel-tower-workstation/len102s0024").build()));
    calls.put("lenovo_search", () -> client.lenovoSearch(LenovoSearchParams.builder().query("thinkpad").build()));
    calls.put("leroymerlin_product", () -> client.leroymerlinProduct(LeroymerlinProductParams.builder().query("https://www.leroymerlin.fr/produits/bobine-soudure-63378273.html").build()));
    calls.put("leroymerlin_search", () -> client.leroymerlinSearch(LeroymerlinSearchParams.builder().query("perceuse").build()));
    calls.put("lidl_product", () -> client.lidlProduct(LidlProductParams.builder().query("100397447").build()));
    calls.put("lidl_search", () -> client.lidlSearch(LidlSearchParams.builder().query("perceuse").build()));
    calls.put("linkedin_company_info", () -> client.linkedinCompanyInfo(LinkedinCompanyInfoParams.builder().build()));
    calls.put("linkedin_job_count", () -> client.linkedinJobCount(LinkedinJobCountParams.builder().build()));
    calls.put("linkedin_job_info", () -> client.linkedinJobInfo(LinkedinJobInfoParams.builder().query("https://www.linkedin.com/jobs/view/4390885746").build()));
    calls.put("linkedin_job_search", () -> client.linkedinJobSearch(LinkedinJobSearchParams.builder().build()));
    calls.put("linkedin_job_suggest", () -> client.linkedinJobSuggest(LinkedinJobSuggestParams.builder().query("Air").build()));
    calls.put("linkedin_post_info", () -> client.linkedinPostInfo(LinkedinPostInfoParams.builder().query("https://www.linkedin.com/posts/tanyalindsay_we-have-been-building-a-web3-gaming-platform-activity-7027826235833520128-m0GW").build()));
    calls.put("linkedin_product_info", () -> client.linkedinProductInfo(LinkedinProductInfoParams.builder().query("https://www.linkedin.com/products/15five").build()));
    calls.put("linkedin_profile_info", () -> client.linkedinProfileInfo(LinkedinProfileInfoParams.builder().query("https://www.linkedin.com/in/example-user").build()));
    calls.put("linkedinadlibrary_ad", () -> client.linkedinadlibraryAd(LinkedinadlibraryAdParams.builder().query("https://www.linkedin.com/ad-library/detail/1585536544").build()));
    calls.put("linkedinadlibrary_search", () -> client.linkedinadlibrarySearch(LinkedinadlibrarySearchParams.builder().build()));
    calls.put("loopnet_property", () -> client.loopnetProperty(LoopnetPropertyParams.builder().query("test").build()));
    calls.put("loopnet_search", () -> client.loopnetSearch(LoopnetSearchParams.builder().query("New York, NY").build()));
    calls.put("maersk_tracking", () -> client.maerskTracking(MaerskTrackingParams.builder().query("MMAU1155207").build()));
    calls.put("manomano_product", () -> client.manomanoProduct(ManomanoProductParams.builder().query("https://www.manomano.fr/p/perceuse-visseuse-lxt-makita-18-v-3-ah-84523637?product_id=158588345").build()));
    calls.put("manomano_product_offer", () -> client.manomanoProductOffer(ManomanoProductOfferParams.builder().query("158588345").build()));
    calls.put("manomano_search", () -> client.manomanoSearch(ManomanoSearchParams.builder().query("https://www.manomano.fr/perceuse-1146").build()));
    calls.put("mascus_ad", () -> client.mascusAd(MascusAdParams.builder().query("https://www.mascus.fr/travaux-publics/broyeur-dechets/doppstadt-ak-435-profi/hocmgmfi.html").build()));
    calls.put("mascus_search", () -> client.mascusSearch(MascusSearchParams.builder().query("https://www.mascus.be/bouw/graafmachines/be,country.html").build()));
    calls.put("mediamarkt_product", () -> client.mediamarktProduct(MediamarktProductParams.builder().query("https://www.mediamarkt.ch/fr/product/_apple-iphone-15-2203371.html").build()));
    calls.put("mediamarkt_search", () -> client.mediamarktSearch(MediamarktSearchParams.builder().query("iphone").build()));
    calls.put("micromania_product", () -> client.micromaniaProduct(MicromaniaProductParams.builder().query("162114").build()));
    calls.put("micromania_search", () -> client.micromaniaSearch(MicromaniaSearchParams.builder().query("zelda").build()));
    calls.put("mobile_ad", () -> client.mobileAd(MobileAdParams.builder().query("455111824").build()));
    calls.put("mobile_search", () -> client.mobileSearch(MobileSearchParams.builder().query("20100").build()));
    calls.put("morele_product", () -> client.moreleProduct(MoreleProductParams.builder().query("https://www.morele.net/laptop-lenovo-thinkbook-15-iil-20sm000fpb-6721152/").build()));
    calls.put("morele_search", () -> client.moreleSearch(MoreleSearchParams.builder().query("laptop").build()));
    calls.put("mrbricolage_suggest", () -> client.mrbricolageSuggest(MrbricolageSuggestParams.builder().query("marteau").build()));
    calls.put("newegg_product", () -> client.neweggProduct(NeweggProductParams.builder().query("N82E16823126188").build()));
    calls.put("newegg_search", () -> client.neweggSearch(NeweggSearchParams.builder().query("logitech keyboard").build()));
    calls.put("northdata_company", () -> client.northdataCompany(NorthdataCompanyParams.builder().query("Maison Boinaud").build()));
    calls.put("northdata_search", () -> client.northdataSearch(NorthdataSearchParams.builder().query("BMW").build()));
    calls.put("npi_provider", () -> client.npiProvider(NpiProviderParams.builder().query("test").build()));
    calls.put("npi_search", () -> client.npiSearch(NpiSearchParams.builder().build()));
    calls.put("oceanio_search", () -> client.oceanioSearch(OceanioSearchParams.builder().query("google.com").build()));
    calls.put("olx_ad", () -> client.olxAd(OlxAdParams.builder().query("https://www.olx.pl/d/oferta/apple-iphone-17-pro-256-gb-nowy-szczecin-zaplombowany-karta-sim-ul-baluki-4-szczecin-CID99-ID18Acsd.html").build()));
    calls.put("olx_product", () -> client.olxProduct(OlxProductParams.builder().query("https://www.olx.com.br/eletronicos-e-celulares/celulares-e-smartphones/iphone-14-128gb-como-novo-IDJ4mBb.html").build()));
    calls.put("olx_search", () -> client.olxSearch(OlxSearchParams.builder().query("iphone 14").build()));
    calls.put("oscaro_product", () -> client.oscaroProduct(OscaroProductParams.builder().query("https://www.oscaro.com/huile-moteur-motul-102208-15213891-1862-p").build()));
    calls.put("oscaro_search", () -> client.oscaroSearch(OscaroSearchParams.builder().query("moteur ai").build()));
    calls.put("ovoko_ad", () -> client.ovokoAd(OvokoAdParams.builder().query("https://ovoko.fr/les-pieces/lgn634714-1780a036-mitsubishi-asx-filtre-d-habitacle").build()));
    calls.put("ovoko_search", () -> client.ovokoSearch(OvokoSearchParams.builder().query("https://ovoko.pl/szukaj?q=Porsche+Macan+uszczelka+klapy+bagaznika&exact=1").build()));
    calls.put("owler_company_info", () -> client.owlerCompanyInfo(OwlerCompanyInfoParams.builder().query("https://owler.com/company/tesla").build()));
    calls.put("owler_search", () -> client.owlerSearch(OwlerSearchParams.builder().query("tesla").build()));
    calls.put("pagesjaunes_page_info", () -> client.pagesjaunesPageInfo(PagesjaunesPageInfoParams.builder().query("https://www.pagesjaunes.fr/pros/56824689").build()));
    calls.put("pagesjaunes_search", () -> client.pagesjaunesSearch(PagesjaunesSearchParams.builder().query("https://www.pagesjaunes.fr/annuaire/chercherlespros?quoiqui=Dentiste&ou=Toulouse+(31000)&univers=pagesjaunes&idOu=").build()));
    calls.put("patreon_profile_info", () -> client.patreonProfileInfo(PatreonProfileInfoParams.builder().query("https://www.patreon.com/example-user").build()));
    calls.put("patreon_search", () -> client.patreonSearch(PatreonSearchParams.builder().query("creator").build()));
    calls.put("petsmart_product", () -> client.petsmartProduct(PetsmartProductParams.builder().query("https://www.petsmart.com/dog/food/dry-food/purina-pro-plan-sensitive-skin-and-stomach-dry-dog-food-adult-salmon-and-rice-formula-digestive-health-36648.html").build()));
    calls.put("petsmart_search", () -> client.petsmartSearch(PetsmartSearchParams.builder().query("dog food").build()));
    calls.put("picard_product", () -> client.picardProduct(PicardProductParams.builder().query("050314").build()));
    calls.put("picard_search", () -> client.picardSearch(PicardSearchParams.builder().query("patate douce").build()));
    calls.put("pinterest_user_info", () -> client.pinterestUserInfo(PinterestUserInfoParams.builder().query("https://www.pinterest.com/username").build()));
    calls.put("pitchbook_company_info", () -> client.pitchbookCompanyInfo(PitchbookCompanyInfoParams.builder().query("11062-99").build()));
    calls.put("pitchbook_search", () -> client.pitchbookSearch(PitchbookSearchParams.builder().query("airbus").build()));
    calls.put("pointp_product", () -> client.pointpProduct(PointpProductParams.builder().query("https://www.pointp.fr/p/platre-isolation-ite/laine-de-verre-a-souffler-thermo-loft-knauf-insulation-sac-16-A3334230").build()));
    calls.put("pointp_search", () -> client.pointpSearch(PointpSearchParams.builder().query("colle").build()));
    calls.put("prnewswire_article", () -> client.prnewswireArticle(PrnewswireArticleParams.builder().query("test").build()));
    calls.put("prnewswire_search", () -> client.prnewswireSearch(PrnewswireSearchParams.builder().query("apple").build()));
    calls.put("producthunt_product_info", () -> client.producthuntProductInfo(ProducthuntProductInfoParams.builder().query("https://www.producthunt.com/products/miro").build()));
    calls.put("proshop_product", () -> client.proshopProduct(ProshopProductParams.builder().query("3071602").build()));
    calls.put("proshop_search", () -> client.proshopSearch(ProshopSearchParams.builder().query("ssd").build()));
    calls.put("rappi_product", () -> client.rappiProduct(RappiProductParams.builder().query("test").build()));
    calls.put("rappi_search", () -> client.rappiSearch(RappiSearchParams.builder().query("leche").build()));
    calls.put("ratemyprofessors_professor", () -> client.ratemyprofessorsProfessor(RatemyprofessorsProfessorParams.builder().query("1064107").build()));
    calls.put("ratemyprofessors_search", () -> client.ratemyprofessorsSearch(RatemyprofessorsSearchParams.builder().query("smith").build()));
    calls.put("realestatecomau_search", () -> client.realestatecomauSearch(RealestatecomauSearchParams.builder().query("Sydney, NSW").build()));
    calls.put("realtor_property", () -> client.realtorProperty(RealtorPropertyParams.builder().query("https://www.realtor.com/realestateandhomes-detail/1492-E-57th-St_Brooklyn_NY_11234_M46117-61190").build()));
    calls.put("realtor_search", () -> client.realtorSearch(RealtorSearchParams.builder().query("https://www.realtor.com/realestateandhomes-search/New-York_NY").build()));
    calls.put("redfin_property", () -> client.redfinProperty(RedfinPropertyParams.builder().query("https://www.redfin.com/WA/Seattle/1120-8th-Ave-98101/unit-1603/home/14602?region_id=16163&region_type=6").build()));
    calls.put("redfin_search", () -> client.redfinSearch(RedfinSearchParams.builder().query("https://www.redfin.com/city/16163/WA/Seattle").build()));
    calls.put("reichelt_product", () -> client.reicheltProduct(ReicheltProductParams.builder().query("test").build()));
    calls.put("reichelt_search", () -> client.reicheltSearch(ReicheltSearchParams.builder().query("test").build()));
    calls.put("reverb_product", () -> client.reverbProduct(ReverbProductParams.builder().query("96933467").build()));
    calls.put("reverb_search", () -> client.reverbSearch(ReverbSearchParams.builder().query("guitar").build()));
    calls.put("rexel_product", () -> client.rexelProduct(RexelProductParams.builder().query("https://www.rexel.fr/frx/HAGWXF122/p/71742449").build()));
    calls.put("rexel_search", () -> client.rexelSearch(RexelSearchParams.builder().query("prise").build()));
    calls.put("rightmove_property", () -> client.rightmoveProperty(RightmovePropertyParams.builder().query("https://www.rightmove.co.uk/properties/172438235").build()));
    calls.put("rightmove_search", () -> client.rightmoveSearch(RightmoveSearchParams.builder().query("Bristol").build()));
    calls.put("rsonline_product", () -> client.rsonlineProduct(RsonlineProductParams.builder().query("https://uk.rs-online.com/web/p/splice-connectors/0303327").build()));
    calls.put("rsonline_search", () -> client.rsonlineSearch(RsonlineSearchParams.builder().query("connector").build()));
    calls.put("seek_job", () -> client.seekJob(SeekJobParams.builder().query("test").build()));
    calls.put("seek_search", () -> client.seekSearch(SeekSearchParams.builder().query("nurse").build()));
    calls.put("seloger_agency", () -> client.selogerAgency(SelogerAgencyParams.builder().query("https://www.seloger.com/professionnels/agences-immobilieres/castanet-tolosan-31320/agence-21719").build()));
    calls.put("seloger_agency_rent", () -> client.selogerAgencyRent(SelogerAgencyRentParams.builder().query("18317").build()));
    calls.put("seloger_agency_sale", () -> client.selogerAgencySale(SelogerAgencySaleParams.builder().query("18317").build()));
    calls.put("seloger_property", () -> client.selogerProperty(SelogerPropertyParams.builder().query("https://www.seloger.com/annonces/achat/appartement/bordeaux-33/capucins-saint-michel-nansouty-saint-genes/234462129.htm").build()));
    calls.put("seloger_search", () -> client.selogerSearch(SelogerSearchParams.builder().query("https://www.seloger.com/annonces/achat/appartement/bordeaux-33/").build()));
    calls.put("sephora_product", () -> client.sephoraProduct(SephoraProductParams.builder().query("https://www.sephora.fr/p/midnight-espresso---eau-de-parfum-au-cafe-831955.html").build()));
    calls.put("sephora_search", () -> client.sephoraSearch(SephoraSearchParams.builder().query("mascara").build()));
    calls.put("shophouzz_product", () -> client.shophouzzProduct(ShophouzzProductParams.builder().query("https://shophouzz.com/products/burke-counter-stool-cognac-vegan-leather-white-oak-veneer-metal-dowel-prvw-vr-186041078").build()));
    calls.put("shopify_apps", () -> client.shopifyApps(ShopifyAppsParams.builder().query("https://apps.shopify.com/judgeme").build()));
    calls.put("shopify_catalog", () -> client.shopifyCatalog(ShopifyCatalogParams.builder().query("https://www.decathlon.com").build()));
    calls.put("shopify_collections", () -> client.shopifyCollections(ShopifyCollectionsParams.builder().query("https://www.decathlon.com").build()));
    calls.put("shopify_product", () -> client.shopifyProduct(ShopifyProductParams.builder().query("https://www.decathlon.com/products/simond-backpacking-sleeping-bag-mt500-41-f-synthetic-346446").build()));
    calls.put("similarweb_domain", () -> client.similarwebDomain(SimilarwebDomainParams.builder().query("google.com").build()));
    calls.put("similarweb_search", () -> client.similarwebSearch(SimilarwebSearchParams.builder().query("google.com").build()));
    calls.put("snapeda_product", () -> client.snapedaProduct(SnapedaProductParams.builder().query("CYPD3175-24LQXQ/Cypress Semiconductor").build()));
    calls.put("snapeda_search", () -> client.snapedaSearch(SnapedaSearchParams.builder().query("USB Type C").build()));
    calls.put("starbucks_menu", () -> client.starbucksMenu(StarbucksMenuParams.builder().build()));
    calls.put("starbucks_product", () -> client.starbucksProduct(StarbucksProductParams.builder().query("418/hot").build()));
    calls.put("starbucks_search", () -> client.starbucksSearch(StarbucksSearchParams.builder().build()));
    calls.put("stats", () -> client.stats(StatsParams.builder().build()));
    calls.put("stockx_product", () -> client.stockxProduct(StockxProductParams.builder().query("nike-air-max-1-travis-scott-wheat").build()));
    calls.put("stockx_search", () -> client.stockxSearch(StockxSearchParams.builder().query("Air Jordan Retro").build()));
    calls.put("stockx_trends", () -> client.stockxTrends(StockxTrendsParams.builder().query("sneakers").build()));
    calls.put("stubhub_event", () -> client.stubhubEvent(StubhubEventParams.builder().query("test").build()));
    calls.put("stubhub_search", () -> client.stubhubSearch(StubhubSearchParams.builder().query("nba").build()));
    calls.put("target_product", () -> client.targetProduct(TargetProductParams.builder().query("17457624").build()));
    calls.put("target_search", () -> client.targetSearch(TargetSearchParams.builder().query("shampoo").build()));
    calls.put("tdlr_license", () -> client.tdlrLicense(TdlrLicenseParams.builder().query("test").build()));
    calls.put("tdlr_search", () -> client.tdlrSearch(TdlrSearchParams.builder().build()));
    calls.put("ted_notice", () -> client.tedNotice(TedNoticeParams.builder().query("30-2026").build()));
    calls.put("ted_search", () -> client.tedSearch(TedSearchParams.builder().build()));
    calls.put("thalia_product", () -> client.thaliaProduct(ThaliaProductParams.builder().query("A1074644377").build()));
    calls.put("thalia_search", () -> client.thaliaSearch(ThaliaSearchParams.builder().query("Harry Potter").build()));
    calls.put("thefork_restaurant", () -> client.theforkRestaurant(TheforkRestaurantParams.builder().query("https://www.thefork.fr/restaurant/salamboo-amilkar-r10892#date=2026-06-13&rankedBy=SEARCH_ENGINE").build()));
    calls.put("thefork_search", () -> client.theforkSearch(TheforkSearchParams.builder().query("https://www.thefork.fr/search?cityId=529135&date=2026-06-13&hour=660&partySize=8&timezone=Europe%2FParis").build()));
    calls.put("thomasnet_company", () -> client.thomasnetCompany(ThomasnetCompanyParams.builder().query("https://www.thomasnet.com/company/zepole-supply-co-20088400/profile").build()));
    calls.put("thomasnet_search", () -> client.thomasnetSearch(ThomasnetSearchParams.builder().query("https://www.thomasnet.com/suppliers/search?searchterm=Table+Forks").build()));
    calls.put("ticketmaster_event", () -> client.ticketmasterEvent(TicketmasterEventParams.builder().query("test").build()));
    calls.put("ticketmaster_search", () -> client.ticketmasterSearch(TicketmasterSearchParams.builder().query("jazz").build()));
    calls.put("tiktoktopads_ad", () -> client.tiktoktopadsAd(TiktoktopadsAdParams.builder().query("https://ads.tiktok.com/business/creativecenter/topads/7686267188656750612/pc/en?period=30").build()));
    calls.put("tiktoktopads_search", () -> client.tiktoktopadsSearch(TiktoktopadsSearchParams.builder().build()));
    calls.put("tradeaplane_listing", () -> client.tradeaplaneListing(TradeaplaneListingParams.builder().query("https://www.trade-a-plane.com/search?category_level1=Piston+Engine&make=ROLLS+ROYCE&model=PACKARD+V-1650&listing_id=2419556&s-type=engine").build()));
    calls.put("tradeaplane_search", () -> client.tradeaplaneSearch(TradeaplaneSearchParams.builder().query("rol").build()));
    calls.put("trustpilot_company_info", () -> client.trustpilotCompanyInfo(TrustpilotCompanyInfoParams.builder().query("https://www.trustpilot.com/review/nordvpn.com").build()));
    calls.put("untappd_beer", () -> client.untappdBeer(UntappdBeerParams.builder().query("4509").build()));
    calls.put("untappd_search", () -> client.untappdSearch(UntappdSearchParams.builder().query("ipa").build()));
    calls.put("upwork_freelancer", () -> client.upworkFreelancer(UpworkFreelancerParams.builder().query("~0123456789abcdef01").build()));
    calls.put("upwork_jobs", () -> client.upworkJobs(UpworkJobsParams.builder().query("api-development").build()));
    calls.put("upwork_services", () -> client.upworkServices(UpworkServicesParams.builder().query("mobile-apps").build()));
    calls.put("upwork_services_info", () -> client.upworkServicesInfo(UpworkServicesInfoParams.builder().query("https://www.upwork.com/services/product/development-it-mobile-app-developer-android-ios-flutter-application-hybrid-apps-1403673770703212544").build()));
    calls.put("usage", () -> client.usage());
    calls.put("usaspending_award", () -> client.usaspendingAward(UsaspendingAwardParams.builder().query("CONT_AWD_GSFC0198106DNAS526555_8000_-NONE-_-NONE-.").build()));
    calls.put("usaspending_search", () -> client.usaspendingSearch(UsaspendingSearchParams.builder().query("software. An award URL returns 400.").build()));
    calls.put("vestiairecollective_product", () -> client.vestiairecollectiveProduct(VestiairecollectiveProductParams.builder().query("https://www.vestiairecollective.com/vetements-femme/vestes/chanel/vesteblouson-chanel-la-petite-veste-noire-en-tweed-rose-73024488.shtml").build()));
    calls.put("vestiairecollective_search", () -> client.vestiairecollectiveSearch(VestiairecollectiveSearchParams.builder().query("chanel").build()));
    calls.put("vinted_item", () -> client.vintedItem(VintedItemParams.builder().query("https://www.vinted.com/items/9437210920-purple-prom-dress").build()));
    calls.put("vinted_search", () -> client.vintedSearch(VintedSearchParams.builder().query("nike").build()));
    calls.put("vinted_user_info", () -> client.vintedUserInfo(VintedUserInfoParams.builder().query("https://www.vinted.fr/member/123456789-username").build()));
    calls.put("vivareal_property", () -> client.vivarealProperty(VivarealPropertyParams.builder().query("https://www.vivareal.com.br/imovel/apartamento-3-quartos-brooklin-zona-sul-sao-paulo-com-garagem-186m2-venda-RS3710000-id-2776423622/").build()));
    calls.put("vivareal_search", () -> client.vivarealSearch(VivarealSearchParams.builder().query("São Paulo").build()));
    calls.put("walmart_product", () -> client.walmartProduct(WalmartProductParams.builder().query("https://www.walmart.com/ip/Fresh-Rockit-Crisp-Sweet-Miniature-Apples-3lb-Tub/309762096").build()));
    calls.put("walmart_search", () -> client.walmartSearch(WalmartSearchParams.builder().query("https://www.walmart.com/search?q=apples").build()));
    calls.put("walmart_suggest", () -> client.walmartSuggest(WalmartSuggestParams.builder().query("apples").build()));
    calls.put("website_antibot", () -> client.websiteAntibot(WebsiteAntibotParams.builder().query("https://www.example.com").build()));
    calls.put("website_crawler", () -> client.websiteCrawler(WebsiteCrawlerParams.builder().query("https://example.com").build()));
    calls.put("website_email_phone_extractor", () -> client.websiteEmailPhoneExtractor(WebsiteEmailPhoneExtractorParams.builder().query("https://www.hexa.cc").build()));
    calls.put("website_rendering", () -> client.websiteRendering(WebsiteRenderingParams.builder().query("https://example.com").build()));
    calls.put("website_screenshot", () -> client.websiteScreenshot(WebsiteScreenshotParams.builder().url("https://www.amazon.com/s?k=pokemon").build()));
    calls.put("website_technology", () -> client.websiteTechnology(WebsiteTechnologyParams.builder().query("https://example.com").build()));
    calls.put("website_webunlocker", () -> client.websiteWebunlocker(WebsiteWebunlockerParams.builder().query("https://www.norauto.fr/t/pneu/w-185-h-55-r-14/ete-s/80-l-h-q.html").build()));
    calls.put("weibo_post_info", () -> client.weiboPostInfo(WeiboPostInfoParams.builder().query("test").build()));
    calls.put("weibo_search", () -> client.weiboSearch(WeiboSearchParams.builder().query("test").build()));
    calls.put("welcometothejungle_company", () -> client.welcometothejungleCompany(WelcometothejungleCompanyParams.builder().query("alan").build()));
    calls.put("welcometothejungle_search", () -> client.welcometothejungleSearch(WelcometothejungleSearchParams.builder().query("python").build()));
    calls.put("wellfound_company_info", () -> client.wellfoundCompanyInfo(WellfoundCompanyInfoParams.builder().query("https://wellfound.com/company/dataiku").build()));
    calls.put("x_post_info", () -> client.xPostInfo(XPostInfoParams.builder().query("https://x.com/example-user/status/0000000000000000001").build()));
    calls.put("x_profile_info", () -> client.xProfileInfo(XProfileInfoParams.builder().query("elonmusk").build()));
    calls.put("yahoo_search", () -> client.yahooSearch(YahooSearchParams.builder().query("test").build()));
    calls.put("yellowpages_business", () -> client.yellowpagesBusiness(YellowpagesBusinessParams.builder().query("https://www.yellowpages.com/los-angeles-ca/mip/anawalt-lumber-13945594?lid=1002108313121").build()));
    calls.put("yellowpages_search", () -> client.yellowpagesSearch(YellowpagesSearchParams.builder().query("https://www.yellowpages.com/los-angeles-ca/plumbers").build()));
    calls.put("yelp_business", () -> client.yelpBusiness(YelpBusinessParams.builder().query("https://www.yelp.com/biz/novela-san-francisco-2").build()));
    calls.put("yelp_reviews", () -> client.yelpReviews(YelpReviewsParams.builder().query("cX5dOMkwZ5rqWbeIJpw_Ig").build()));
    calls.put("yelp_search", () -> client.yelpSearch(YelpSearchParams.builder().query("https://www.yelp.com/search?find_desc=Cocktail+Bars&find_loc=San+Francisco%2C+CA").build()));
    calls.put("zapimoveis_ad", () -> client.zapimoveisAd(ZapimoveisAdParams.builder().query("https://www.zapimoveis.com.br/imovel/venda-apartamento-3-quartos-mobiliado-jardim-paulista-zona-sul-sao-paulo-sp-170m2-id-2912096343/").build()));
    calls.put("zapimoveis_search", () -> client.zapimoveisSearch(ZapimoveisSearchParams.builder().query("São Paulo").build()));
    calls.put("zazzle_product", () -> client.zazzleProduct(ZazzleProductParams.builder().query("https://www.zazzle.com/branded_logo_two_tone_coffee_mug-256528848440338952.").build()));
    calls.put("zazzle_search", () -> client.zazzleSearch(ZazzleSearchParams.builder().query("coffee mug. A product URL returns 400.").build()));
    calls.put("zillow_property", () -> client.zillowProperty(ZillowPropertyParams.builder().query("https://www.zillow.com/homedetails/20-Kyle-Ct-Staten-Island-NY-10312/32361983_zpid/").build()));
    calls.put("zillow_search", () -> client.zillowSearch(ZillowSearchParams.builder().query("https://www.zillow.com/staten-island-ny/").build()));
    calls.put("zonaprop_property", () -> client.zonapropProperty(ZonapropPropertyParams.builder().query("https://www.zonaprop.com.ar/propiedades/clasificado/veclapin-venta-departamento-2-ambientes-villa-crespo-59096735.html").build()));
    calls.put("zonaprop_search", () -> client.zonapropSearch(ZonapropSearchParams.builder().query("capital federal").build()));
    calls.put("zoopla_property", () -> client.zooplaProperty(ZooplaPropertyParams.builder().query("https://www.zoopla.co.uk/to-rent/details/73119609/").build()));
    calls.put("zoopla_search", () -> client.zooplaSearch(ZooplaSearchParams.builder().query("https://www.zoopla.co.uk/for-sale/property/manchester/").build()));
    calls.put("zooplus_product", () -> client.zooplusProduct(ZooplusProductParams.builder().query("705321").build()));
    calls.put("zooplus_search", () -> client.zooplusSearch(ZooplusSearchParams.builder().query("croquettes").build()));
    calls.put("zoro_product", () -> client.zoroProduct(ZoroProductParams.builder().query("https://www.zoro.com/3m-safety-glasses-securefit-200-series-scotchgard-anti-fog-frameless-blue-temples-clear-lens-sf201sgaf-blu/i/G1937045/").build()));
    calls.put("zoro_search", () -> client.zoroSearch(ZoroSearchParams.builder().query("safety glasses").build()));
    for (Map.Entry<String, Endpoints.Endpoint> entry : Endpoints.ALL.entrySet()) {
      Runnable call = calls.get(entry.getKey());
      assertNotNull(call, entry.getKey());
      call.run();
      PiloterrTransport.Request request = transport.requests.get(transport.requests.size() - 1);
      assertEquals(
          "https://api.piloterr.com" + entry.getValue().path().replace("{id}", "test"),
          request.uri().toString().split("\\?")[0],
          entry.getKey());
      assertEquals(entry.getValue().methods().get(0), request.method(), entry.getKey());
    }
    assertEquals(Endpoints.ALL.size(), calls.size());
  }

  @Test
  void binaryResponse() {
    transport.responder = request -> new RawResponse(200, Map.of("content-type", List.of("image/png")), new byte[] {(byte) 0x89, 'P', 'N', 'G'});
    ApiResponse response = client.request("GET", "/v1/anything", Map.of());
    assertFalse(response.isJson());
    assertEquals(4, response.bytes().length);
    assertEquals("image/png", response.contentType());
  }

  @Test
  void nonJsonSuccessForJsonOperation() {
    transport.responder = request -> new RawResponse(200, Map.of("content-type", List.of("text/html")), "<html>".getBytes(StandardCharsets.UTF_8));
    assertThrows(PiloterrResponseDecodingException.class, () -> client.amazonProduct(AmazonProductParams.builder().query("B09DFCB66S").build()));
  }

  @Test
  void apiError() {
    transport.responder = request -> json(401, "{\"error\":\"Invalid API Key\"}");
    PiloterrApiException error = assertThrows(PiloterrApiException.class, () -> client.request("GET", "/v1/usage", Map.of()));
    assertEquals(401, error.status());
    assertEquals("Invalid API Key", error.getMessage());
    assertEquals("Invalid API Key", error.responseBody().get("error").asText());
  }

  @Test
  void nonJsonErrorBody() {
    transport.responder = request -> new RawResponse(502, Map.of(), "Bad gateway".getBytes(StandardCharsets.UTF_8));
    PiloterrApiException error = assertThrows(PiloterrApiException.class, () -> client.request("GET", "/v1/usage", Map.of()));
    assertEquals(502, error.status());
    assertEquals("Bad gateway", new String(error.rawResponse().body(), StandardCharsets.UTF_8));
  }

  @Test
  void transportError() {
    PiloterrClient failing = PiloterrClient.builder().apiKey("k").maxRetries(0).transport(request -> {
      throw new IOException("network down");
    }).build();
    assertThrows(PiloterrTransportException.class, () -> failing.request("GET", "/v1/usage", Map.of()));
  }

  @Test
  void decodesIntoTypes() {
    transport.responder = request -> json(200, "{\"value\":42}");
    record Payload(int value) {}
    assertEquals(42, client.request("GET", "/v1/usage", Map.of()).as(Payload.class).value());
  }

  @Test
  void retriesThenSucceeds() {
    int[] calls = {0};
    PiloterrClient flaky = PiloterrClient.builder().apiKey("k").retryDelay(java.time.Duration.ofMillis(1)).transport(request -> {
      calls[0]++;
      if (calls[0] == 1) {
        throw new IOException("reset");
      }
      if (calls[0] == 2) {
        return new RawResponse(429, Map.of("retry-after", List.of("0"), "content-type", List.of("application/json")), "{}".getBytes(StandardCharsets.UTF_8));
      }
      return json(200, "{}");
    }).build();
    assertTrue(flaky.request("GET", "/v1/usage", Map.of()).isJson());
    assertEquals(3, calls[0]);
  }

  @Test
  void retryLimitsAndClientErrors() {
    PiloterrClient limited = PiloterrClient.builder().apiKey("k").transport(transport).maxRetries(2).retryDelay(java.time.Duration.ofMillis(1)).build();
    transport.responder = request -> json(503, "{}");
    assertThrows(PiloterrApiException.class, () -> limited.request("GET", "/v1/usage", Map.of()));
    assertEquals(3, transport.requests.size());
    transport.responder = request -> json(402, "{}");
    assertThrows(PiloterrApiException.class, () -> limited.request("GET", "/v1/usage", Map.of()));
    assertEquals(4, transport.requests.size());
    transport.responder = request -> json(500, "{}");
    assertThrows(PiloterrApiException.class, () -> limited.request("GET", "/v1/usage", Map.of(), RequestOptions.builder().maxRetries(0).build()));
    assertEquals(5, transport.requests.size());
  }

  @Test
  void rateLimitsAreRetriedButNotQuotas() {
    Map<String, Integer> cases = new LinkedHashMap<>();
    cases.put("Rate limit exceeded: You've exceeded the rate limit on your subscription", 2);
    cases.put("Rate limit exceeded for the API key: quota daily", 1);
    cases.put("Invalid API Key", 1);
    for (Map.Entry<String, Integer> c : cases.entrySet()) {
      int[] calls = {0};
      PiloterrClient limited = PiloterrClient.builder().apiKey("k").retryDelay(java.time.Duration.ofMillis(1)).transport(request -> {
        calls[0]++;
        return calls[0] == 1 ? json(401, "{\"error\":\"" + c.getKey() + "\"}") : json(200, "{}");
      }).build();
      try {
        limited.request("GET", "/v1/usage", Map.of());
      } catch (PiloterrApiException e) {
        // expected for the non-retried cases
      }
      assertEquals(c.getValue(), calls[0], c.getKey());
    }
  }

  @Test
  void acceptsGlobalParameters() {
    client.amazonProduct(AmazonProductParams.builder().query("B09DFCB66S").fields("a,b.c").isAsync(true).softFail(false).build());
    String query = transport.requests.get(0).uri().getRawQuery();
    assertTrue(query.contains("fields=a%2Cb.c"), query);
    assertTrue(query.contains("is_async=true"), query);
    assertTrue(query.contains("soft_fail=false"), query);
  }

  @Test
  void batchKeepsOrderAndIsolatesFailures() {
    java.util.concurrent.atomic.AtomicInteger running = new java.util.concurrent.atomic.AtomicInteger();
    java.util.concurrent.atomic.AtomicInteger peak = new java.util.concurrent.atomic.AtomicInteger();
    List<BatchResult<Integer>> results = client.batch(List.of(1, 2, 3, 4, 5, 6), 2, n -> {
      peak.accumulateAndGet(running.incrementAndGet(), Math::max);
      try {
        Thread.sleep(5);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
      running.decrementAndGet();
      if (n == 4) {
        throw new IllegalStateException("boom");
      }
      return n * 10;
    });
    assertEquals(2, peak.get());
    assertEquals(6, results.size());
    assertFalse(results.get(3).ok());
    assertEquals(60, results.get(5).value());
    assertEquals(3, results.get(3).index());
  }

  @Test
  void waitForJob() {
    List<String> statuses = new ArrayList<>(List.of("Queued", "Running", "CompletedJob"));
    transport.responder = request -> json(200, "{\"status\":\"" + statuses.remove(0) + "\",\"progress\":50}");
    List<JsonNode> seen = new ArrayList<>();
    ApiResponse job = client.waitForJob("abc", java.time.Duration.ofMillis(1), java.time.Duration.ofMinutes(1), seen::add);
    assertEquals("CompletedJob", job.json().get("status").asText());
    assertEquals(3, transport.requests.size());
    assertEquals("/v1/jobs/abc", transport.requests.get(0).uri().getPath());
    assertEquals(3, seen.size());
  }

  @Test
  void waitForJobTimesOut() {
    transport.responder = request -> json(200, "{\"status\":\"Running\"}");
    assertThrows(
        PiloterrJobTimeoutException.class,
        () -> client.waitForJob("abc", java.time.Duration.ofMillis(10), java.time.Duration.ofMillis(1), null));
  }
}
