package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/argenprop/search}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class ArgenpropSearchParams implements PiloterrParams {
  private final Map<String, Object> values;

  private ArgenpropSearchParams(Map<String, Object> values) {
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

  /** Builder for {@link ArgenpropSearchParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Required. The place to search, or a full Argenprop results page. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** Which page of the list to open. The first page is 1. The placeholder is 2. Default: 1. */
    public Builder page(Number value) {
      values.put("page", value);
      return this;
    }

    /** Sale or rent, when query is a place name. The placeholder is venta, which means for sale. alquiler is a long-term rental. alquiler-temporal is a short stay. sale, buy, and rent are accepted as aliases. Default: "venta". */
    public Builder operation(String value) {
      values.put("operation", value);
      return this;
    }

    /** The kind of home, when query is a place name. The placeholder is departamentos, which means apartments. Also accepted: casas (houses), ph, terrenos, locales, oficinas, and quintas. casa and house map to casas. Default: "departamentos". */
    public Builder propertyType(String value) {
      values.put("property_type", value);
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

    /** Validates required parameters and returns the immutable {@link ArgenpropSearchParams}. */
    public ArgenpropSearchParams build() {
      if (values.get("query") == null) {
        throw new PiloterrSerializationException("The \"query\" parameter is required.", "query");
      }
      return new ArgenpropSearchParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
