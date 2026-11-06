package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/olx/search}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class OlxSearchParams implements PiloterrParams {
  private final Map<String, Object> values;

  private OlxSearchParams(Map<String, Object> values) {
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

  /** Builder for {@link OlxSearchParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Required. Search keyword or phrase, as you would type it in the OLX search bar. Supports Portuguese terms for Brazilian inventory. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** OLX category slug to restrict results to a specific vertical. Common values: */
    public Builder category(String value) {
      values.put("category", value);
      return this;
    }

    /** Brazilian state abbreviation to filter results geographically. */
    public Builder state(String value) {
      values.put("state", value);
      return this;
    }

    /** City slug to narrow results to a specific municipality. Use lowercase with hyphens. */
    public Builder city(String value) {
      values.put("city", value);
      return this;
    }

    /** Minimum listing price in BRL. */
    public Builder minPrice(Number value) {
      values.put("min_price", value);
      return this;
    }

    /** Maximum listing price in BRL. */
    public Builder maxPrice(Number value) {
      values.put("max_price", value);
      return this;
    }

    /** Filter by seller type. */
    public Builder sellerType(String value) {
      values.put("seller_type", value);
      return this;
    }

    /** Sort order for results. Default: "relevance". */
    public Builder sort(String value) {
      values.put("sort", value);
      return this;
    }

    /** Page number for paginated results. Default: 1. Default: 1. */
    public Builder page(Number value) {
      values.put("page", value);
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

    /** Validates required parameters and returns the immutable {@link OlxSearchParams}. */
    public OlxSearchParams build() {
      if (values.get("query") == null) {
        throw new PiloterrSerializationException("The \"query\" parameter is required.", "query");
      }
      return new OlxSearchParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
