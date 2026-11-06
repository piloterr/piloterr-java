package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/website/rendering}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class WebsiteRenderingParams implements PiloterrParams {
  private final Map<String, Object> values;

  private WebsiteRenderingParams(Map<String, Object> values) {
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

  /** Builder for {@link WebsiteRenderingParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Required. A public website URL to render in a headless browser. Must include http:// or https://. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** Number of seconds to wait after page load before returning the rendered HTML. Useful for pages with delayed rendering or animations. Default: 0. */
    public Builder waitInSeconds(Number value) {
      values.put("wait_in_seconds", value);
      return this;
    }

    /** A CSS or XPath selector to wait for before returning the rendered HTML. The headless browser will hold the response until the specified element appears in the DOM. */
    public Builder waitFor(String value) {
      values.put("wait_for", value);
      return this;
    }

    /** Controls whether the headless browser should block ad requests during rendering. Default: false. */
    public Builder blockAds(Boolean value) {
      values.put("block_ads", value);
      return this;
    }

    /** Maximum number of seconds to wait for the page to fully load before returning whatever has been rendered so far. Default: 30. */
    public Builder timeout(Number value) {
      values.put("timeout", value);
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

    /** Validates required parameters and returns the immutable {@link WebsiteRenderingParams}. */
    public WebsiteRenderingParams build() {
      if (values.get("query") == null) {
        throw new PiloterrSerializationException("The \"query\" parameter is required.", "query");
      }
      return new WebsiteRenderingParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
