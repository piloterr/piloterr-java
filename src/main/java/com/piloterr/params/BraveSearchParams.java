package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/brave/search}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class BraveSearchParams implements PiloterrParams {
  private final Map<String, Object> values;

  private BraveSearchParams(Map<String, Object> values) {
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

  /** Builder for {@link BraveSearchParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Required. The search query to execute on Brave Search. Supports all standard Brave search operators. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** Page number of results to retrieve. Used for pagination. Default: 1. */
    public Builder page(Number value) {
      values.put("page", value);
      return this;
    }

    /** Country code, for example us, fr, gb, de. Default: "us". */
    public Builder gl(String value) {
      values.put("gl", value);
      return this;
    }

    /** Language code, for example en, fr, de. Default: "en". */
    public Builder hl(String value) {
      values.put("hl", value);
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

    /** Validates required parameters and returns the immutable {@link BraveSearchParams}. */
    public BraveSearchParams build() {
      if (values.get("query") == null) {
        throw new PiloterrSerializationException("The \"query\" parameter is required.", "query");
      }
      return new BraveSearchParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
