package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/northdata/search}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class NorthdataSearchParams implements PiloterrParams {
  private final Map<String, Object> values;

  private NorthdataSearchParams(Map<String, Object> values) {
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

  /** Builder for {@link NorthdataSearchParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Required. Company or person name, or a North Data suggest URL. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** ISO alpha-2 codes, separated by comma, pipe or space (FR or FR,DE). Empty searches every country. A URL that already has countries wins. */
    public Builder countries(String value) {
      values.put("countries", value);
      return this;
    }

    /** Host of a name: fr (default), com or en, de. Ignored when query is a URL. Default: "fr". */
    public Builder domain(String value) {
      values.put("domain", value);
      return this;
    }

    /** Only 1. Below 1 returns 400. Above 1 returns 404. At most 15 rows. */
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

    /** Validates required parameters and returns the immutable {@link NorthdataSearchParams}. */
    public NorthdataSearchParams build() {
      if (values.get("query") == null) {
        throw new PiloterrSerializationException("The \"query\" parameter is required.", "query");
      }
      return new NorthdataSearchParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
