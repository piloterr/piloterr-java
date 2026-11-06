package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/action/search}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class ActionSearchParams implements PiloterrParams {
  private final Map<String, Object> values;

  private ActionSearchParams(Map<String, Object> values) {
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

  /** Builder for {@link ActionSearchParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Required. Search keyword or full Action.com search URL. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** Locale (fr-fr, de-de, pt-pt, …) or country code (fr, de, pt). Default: fr-fr. */
    public Builder region(String value) {
      values.put("region", value);
      return this;
    }

    /** 1-based page number (default: 1). */
    public Builder page(Number value) {
      values.put("page", value);
      return this;
    }

    /** When true, return the raw HTML page source as {"result": "..."} instead of structured JSON. Defaults to false. */
    public Builder returnPageSource(Boolean value) {
      values.put("return_page_source", value);
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

    /** Validates required parameters and returns the immutable {@link ActionSearchParams}. */
    public ActionSearchParams build() {
      if (values.get("query") == null) {
        throw new PiloterrSerializationException("The \"query\" parameter is required.", "query");
      }
      return new ActionSearchParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
