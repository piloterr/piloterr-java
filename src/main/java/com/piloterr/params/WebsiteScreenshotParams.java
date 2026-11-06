package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/website/screenshot}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class WebsiteScreenshotParams implements PiloterrParams {
  private final Map<String, Object> values;

  private WebsiteScreenshotParams(Map<String, Object> values) {
    this.values = values;
  }

  /** Returns a new builder. */
  public static Builder builder() {
    return new Builder();
  }

  @Override
  public Map<String, Object> toMap() {
    return values;
  }

  /** Builder for {@link WebsiteScreenshotParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Required. The URL of the webpage to capture. Examples:  https://www.amazon.com/s?k=pokemon  */
    public Builder url(String value) {
      values.put("url", value);
      return this;
    }

    /** The output format of the screenshot. Allowed values: jpeg, jpg, pdf, png, webp. Default: "png". */
    public Builder format(String value) {
      values.put("format", value);
      return this;
    }

    /** The device type to emulate for the screenshot. Allowed values: galaxy_fold4, galaxy_s23, galaxy_s23_ultra, galaxy_s8, huawei_mate_50_pro, huawei_p60_pro, ipad, iphone_12, iphone_13_mini, iphone_13_pro_max, iphone_14_pro, iphone_14_pro_max, iphone_x, pixel_5, pixel_6a, pixel_7_pro, redmi_note_11, redmi_note_12_pro. */
    public Builder device(String value) {
      values.put("device", value);
      return this;
    }

    /** Capture the entire page instead of just the visible viewport. Default: false. */
    public Builder fullPage(Boolean value) {
      values.put("full_page", value);
      return this;
    }

    /** Scroll the page to fully load lazy-loaded elements before capturing a full-page screenshot. Default: false. */
    public Builder fullPageScroll(Boolean value) {
      values.put("full_page_scroll", value);
      return this;
    }

    /** Time in milliseconds for scrolling before capturing the full page. Default: 400. */
    public Builder fullPageScrollDuration(Number value) {
      values.put("full_page_scroll_duration", value);
      return this;
    }

    /** The width of the browser viewport in pixels. Default: 1280. */
    public Builder viewportWidth(Number value) {
      values.put("viewport_width", value);
      return this;
    }

    /** The height of the browser viewport in pixels. Default: 1024. */
    public Builder viewportHeight(Number value) {
      values.put("viewport_height", value);
      return this;
    }

    /** The scale factor for high-resolution screenshots. Default: 1. */
    public Builder scaleFactor(Number value) {
      values.put("scale_factor", value);
      return this;
    }

    /** Generate images with custom quality settings. Default: "80". */
    public Builder imageQuality(String value) {
      values.put("image_quality", value);
      return this;
    }

    /** Capture a specific element on the page instead of the full viewport. */
    public Builder selector(String value) {
      values.put("selector", value);
      return this;
    }

    /** Cache the response. Default: false. */
    public Builder cache(Boolean value) {
      values.put("cache", value);
      return this;
    }

    /** Cache the response for a custom TTL (in seconds). Default: 2592000. */
    public Builder cacheTtl(Number value) {
      values.put("cache_ttl", value);
      return this;
    }

    /** A comma-separated list of elements to hide before capturing (e.g., ads, popups). */
    public Builder removeSelectors(String value) {
      values.put("remove_selectors", value);
      return this;
    }

    /** Automatically remove cookie banners before capturing. Default: false. */
    public Builder removeCookieBanners(Boolean value) {
      values.put("remove_cookie_banners", value);
      return this;
    }

    /** Automatically remove ads before capturing. Default: false. */
    public Builder removeAds(Boolean value) {
      values.put("remove_ads", value);
      return this;
    }

    /** Comma-separated list of resource types to block (e.g., “image,stylesheet,font”). Allowed values: document, eventsource, fetch, font, image, manifest, media, other, script, stylesheet, texttrack, websocket, xhr. */
    public Builder blockResources(String value) {
      values.put("block_resources", value);
      return this;
    }

    /** Comma-separated list of URL patterns to block (e.g., “analytics,tracking,advertisement”). */
    public Builder blockUrls(String value) {
      values.put("block_urls", value);
      return this;
    }

    /** Delay in seconds before capturing the screenshot. Default: 0. */
    public Builder delay(Number value) {
      values.put("delay", value);
      return this;
    }

    /** Define when to capture. Allowed values: domcontentloaded, load, networkidle0, networkidle2. */
    public Builder waitUntil(List<String> value) {
      values.put("wait_until", value);
      return this;
    }

    /** Wait for a specific element to appear before taking the screenshot. */
    public Builder waitForSelector(String value) {
      values.put("wait_for_selector", value);
      return this;
    }

    /** Specify a proxy server to route your request through. */
    public Builder proxy(String value) {
      values.put("proxy", value);
      return this;
    }

    /** AWS S3 secret access key. */
    public Builder s3SecretKey(String value) {
      values.put("s3_secret_key", value);
      return this;
    }

    /** AWS S3 access key ID. */
    public Builder s3AccessKeyId(String value) {
      values.put("s3_access_key_id", value);
      return this;
    }

    /** The S3 bucket name where the screenshot will be uploaded. */
    public Builder s3Bucket(String value) {
      values.put("s3_bucket", value);
      return this;
    }

    /** The filename for the S3 object (auto-generated if not provided). */
    public Builder s3ObjectKey(String value) {
      values.put("s3_object_key", value);
      return this;
    }

    /** AWS S3 region for storage. */
    public Builder s3Region(String value) {
      values.put("s3_region", value);
      return this;
    }

    /** Leave this field empty for AWS S3. Only specify a value if required. S3-compatible storage services are supported, for example, use https://&lt;accountId&gt;.r2.cloudflarestorage.com for Cloudflare R2. */
    public Builder storageEndpoint(String value) {
      values.put("storage_endpoint", value);
      return this;
    }

    /** Use S3 storage for screenshots. The S3 URL will be returned in the response. Default: false. */
    public Builder s3Url(Boolean value) {
      values.put("s3_url", value);
      return this;
    }

    /** Comma-separated list of fields to keep in the response, with dot notation for nested fields (e.g. name,location.city). Does not change the credit cost. */
    public Builder fields(String value) {
      values.put("fields", value);
      return this;
    }

    /** Process the request asynchronously: the response is a job_id to poll on /v1/jobs/{id} (see waitForJob). Default: false. */
    public Builder isAsync(Boolean value) {
      values.put("is_async", value);
      return this;
    }

    /** Return HTTP 200 even when an error occurs; the error is then in the response body. Default: false. */
    public Builder softFail(Boolean value) {
      values.put("soft_fail", value);
      return this;
    }

    /** Validates required parameters and returns the immutable {@link WebsiteScreenshotParams}. */
    public WebsiteScreenshotParams build() {
      if (values.get("url") == null) {
        throw new PiloterrSerializationException("The \"url\" parameter is required.", "url");
      }
      return new WebsiteScreenshotParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
