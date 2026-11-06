package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/lamudi/search}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class LamudiSearchParams implements PiloterrParams {
  private final Map<String, Object> values;

  private LamudiSearchParams(Map<String, Object> values) {
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

  /** Builder for {@link LamudiSearchParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Full Lamudi search URL. Used first when set. Location and path filters are ignored. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** City or district. Used when query is empty. Builds a Philippines URL (lamudi.com.ph). For Indonesia, pass a search URL in query. */
    public Builder location(String value) {
      values.put("location", value);
      return this;
    }

    /** Transaction type. Used when query is empty. Default: "sale". */
    public Builder operation(String value) {
      values.put("operation", value);
      return this;
    }

    /** Property type. Used when query is empty. Maps to Lamudi slugs (condominium → /condo/, land → /lot/). */
    public Builder propertyType(String value) {
      values.put("property_type", value);
      return this;
    }

    /** Minimum asking price in PHP or IDR. */
    public Builder minPrice(Number value) {
      values.put("min_price", value);
      return this;
    }

    /** Maximum asking price in PHP or IDR. */
    public Builder maxPrice(Number value) {
      values.put("max_price", value);
      return this;
    }

    /** Minimum bedroom count. */
    public Builder minBedrooms(Number value) {
      values.put("min_bedrooms", value);
      return this;
    }

    /** Maximum bedroom count. */
    public Builder maxBedrooms(Number value) {
      values.put("max_bedrooms", value);
      return this;
    }

    /** Minimum bathroom count. */
    public Builder minBathrooms(Number value) {
      values.put("min_bathrooms", value);
      return this;
    }

    /** Minimum area in m². */
    public Builder minArea(Number value) {
      values.put("min_area", value);
      return this;
    }

    /** Maximum area in m². */
    public Builder maxArea(Number value) {
      values.put("max_area", value);
      return this;
    }

    /** Sort order. Default: "relevance". */
    public Builder sort(String value) {
      values.put("sort", value);
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

    /** Validates required parameters and returns the immutable {@link LamudiSearchParams}. */
    public LamudiSearchParams build() {
      return new LamudiSearchParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
