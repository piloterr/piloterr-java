package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/glassdoor/job/search}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class GlassdoorJobSearchParams implements PiloterrParams {
  private final Map<String, Object> values;

  private GlassdoorJobSearchParams(Map<String, Object> values) {
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

  /** Builder for {@link GlassdoorJobSearchParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Required. Keyword or full Glassdoor job search URL. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** Glassdoor TLD when query is not a full URL. Default: "fr". */
    public Builder domain(String value) {
      values.put("domain", value);
      return this;
    }

    /** Page number. */
    public Builder page(Number value) {
      values.put("page", value);
      return this;
    }

    /** Glassdoor sortBy value (e.g. date_desc). */
    public Builder sortBy(String value) {
      values.put("sort_by", value);
      return this;
    }

    /** When true, set remoteWorkType=1. */
    public Builder remote(Boolean value) {
      values.put("remote", value);
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

    /** Validates required parameters and returns the immutable {@link GlassdoorJobSearchParams}. */
    public GlassdoorJobSearchParams build() {
      if (values.get("query") == null) {
        throw new PiloterrSerializationException("The \"query\" parameter is required.", "query");
      }
      return new GlassdoorJobSearchParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
