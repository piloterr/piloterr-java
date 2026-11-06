package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/zapimoveis/search}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class ZapimoveisSearchParams implements PiloterrParams {
  private final Map<String, Object> values;

  private ZapimoveisSearchParams(Map<String, Object> values) {
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

  /** Builder for {@link ZapimoveisSearchParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Required. City, City, UF, neighborhood, or full Zap Imóveis search URL. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** Transaction type. Used when query is a city. Ignored as a path builder when query is a full URL. Default: "sale". */
    public Builder operation(String value) {
      values.put("operation", value);
      return this;
    }

    /** Property type. Used when query is a city. */
    public Builder propertyType(String value) {
      values.put("property_type", value);
      return this;
    }

    /** Page number (1-based). Default: 1. Values above 1 set pagina on the URL. Default: 1. */
    public Builder page(Number value) {
      values.put("page", value);
      return this;
    }

    /** Minimum asking price in BRL. */
    public Builder minPrice(Number value) {
      values.put("min_price", value);
      return this;
    }

    /** Maximum asking price in BRL. */
    public Builder maxPrice(Number value) {
      values.put("max_price", value);
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

    /** Minimum bedroom count. */
    public Builder minBedrooms(Number value) {
      values.put("min_bedrooms", value);
      return this;
    }

    /** Minimum bathroom count. */
    public Builder minBathrooms(Number value) {
      values.put("min_bathrooms", value);
      return this;
    }

    /** Minimum parking spaces. */
    public Builder minParking(Number value) {
      values.put("min_parking", value);
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

    /** Validates required parameters and returns the immutable {@link ZapimoveisSearchParams}. */
    public ZapimoveisSearchParams build() {
      if (values.get("query") == null) {
        throw new PiloterrSerializationException("The \"query\" parameter is required.", "query");
      }
      return new ZapimoveisSearchParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
