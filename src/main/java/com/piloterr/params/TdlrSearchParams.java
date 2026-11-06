package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/tdlr/search}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class TdlrSearchParams implements PiloterrParams {
  private final Map<String, Object> values;

  private TdlrSearchParams(Map<String, Object> values) {
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

  /** Builder for {@link TdlrSearchParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Optional when city, county, or postal_code is set. Person or business name, example SMITH. A detail URL is rejected. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** Optional on the request, required for a useful search. License type code, example AIRREF for air conditioning contractors. ALL or blank searches every type. */
    public Builder licenseType(String value) {
      values.put("license_type", value);
      return this;
    }

    /** Optional. Business city. Example: Austin. One of query, city, county, or postal_code is required. */
    public Builder city(String value) {
      values.put("city", value);
      return this;
    }

    /** Optional. Business county. Example: TRAVIS. */
    public Builder county(String value) {
      values.put("county", value);
      return this;
    }

    /** Optional. Business ZIP. Example: 78701. */
    public Builder postalCode(String value) {
      values.put("postal_code", value);
      return this;
    }

    /** Optional. Page number starting at 1. Example: 2. Defaults to 1. Each page has 25 rows. */
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

    /** Validates required parameters and returns the immutable {@link TdlrSearchParams}. */
    public TdlrSearchParams build() {
      return new TdlrSearchParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
