package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/vivareal/search}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class VivarealSearchParams implements PiloterrParams {
  private final Map<String, Object> values;

  private VivarealSearchParams(Map<String, Object> values) {
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

  /** Builder for {@link VivarealSearchParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Required. City, City plus UF, or a Viva Real search URL. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** Sale or rent when query is a city. Default: "sale". */
    public Builder operation(String value) {
      values.put("operation", value);
      return this;
    }

    /** Optional type when query is a city. */
    public Builder propertyType(String value) {
      values.put("property_type", value);
      return this;
    }

    /** Page from 1. Placeholder 2. Viva Real receives this as pagina=2. Page 1 and page 2 return different listings. A few featured ids can repeat. Most ids on page 2 are new. */
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

    /** Validates required parameters and returns the immutable {@link VivarealSearchParams}. */
    public VivarealSearchParams build() {
      if (values.get("query") == null) {
        throw new PiloterrSerializationException("The \"query\" parameter is required.", "query");
      }
      return new VivarealSearchParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
