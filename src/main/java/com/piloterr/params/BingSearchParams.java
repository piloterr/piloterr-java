package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/bing/search}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class BingSearchParams implements PiloterrParams {
  private final Map<String, Object> values;

  private BingSearchParams(Map<String, Object> values) {
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

  /** Builder for {@link BingSearchParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Required. The search query to execute on Bing. Supports all Bing advanced search operators. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** Page number of results to retrieve. Used for pagination. */
    public Builder page(Number value) {
      values.put("page", value);
      return this;
    }

    /** Number of results per page. Minimum: 10, Maximum: 50. */
    public Builder num(Number value) {
      values.put("num", value);
      return this;
    }

    /** City or region from which the search should originate. If multiple locations match, the most popular one is selected. */
    public Builder location(String value) {
      values.put("location", value);
      return this;
    }

    /** GPS latitude coordinate for the search origin. */
    public Builder lat(Number value) {
      values.put("lat", value);
      return this;
    }

    /** GPS longitude coordinate for the search origin. */
    public Builder lon(Number value) {
      values.put("lon", value);
      return this;
    }

    /** Market code combining language and country, in the form &lt;language&gt;-&lt;country&gt;. Defines where the results come from. */
    public Builder mkt(String value) {
      values.put("mkt", value);
      return this;
    }

    /** Two-letter ISO 3166-1 country code defining the country to search from. */
    public Builder cc(String value) {
      values.put("cc", value);
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

    /** Validates required parameters and returns the immutable {@link BingSearchParams}. */
    public BingSearchParams build() {
      if (values.get("query") == null) {
        throw new PiloterrSerializationException("The \"query\" parameter is required.", "query");
      }
      return new BingSearchParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
