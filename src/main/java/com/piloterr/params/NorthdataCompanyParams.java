package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/northdata/company}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class NorthdataCompanyParams implements PiloterrParams {
  private final Map<String, Object> values;

  private NorthdataCompanyParams(Map<String, Object> values) {
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

  /** Builder for {@link NorthdataCompanyParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Required. Company name, North Data id, or a profile URL. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** ISO alpha-2, used only when query is a name. Same form as search: FR or FR,DE. */
    public Builder countries(String value) {
      values.put("countries", value);
      return this;
    }

    /** fr (default), com or en, de. Ignored when query is a URL. Default: "fr". */
    public Builder domain(String value) {
      values.put("domain", value);
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

    /** Validates required parameters and returns the immutable {@link NorthdataCompanyParams}. */
    public NorthdataCompanyParams build() {
      if (values.get("query") == null) {
        throw new PiloterrSerializationException("The \"query\" parameter is required.", "query");
      }
      return new NorthdataCompanyParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
