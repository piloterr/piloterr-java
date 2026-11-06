package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/kijiji/search}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class KijijiSearchParams implements PiloterrParams {
  private final Map<String, Object> values;

  private KijijiSearchParams(Map<String, Object> values) {
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

  /** Builder for {@link KijijiSearchParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Required. Keyword or full Kijiji results URL. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** Results page, starting at 1. Page 2 of the City of Toronto bikes search returns a different set of listing ids. When page stays at 1, a page-N segment already present in the URL is kept. */
    public Builder page(Number value) {
      values.put("page", value);
      return this;
    }

    /** Locale sent to Kijiji as the siteLocale cookie. en sets en-CA. fr sets fr-CA. French cards print prices like 10,00 $. Default: "en". */
    public Builder language(String value) {
      values.put("language", value);
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

    /** Validates required parameters and returns the immutable {@link KijijiSearchParams}. */
    public KijijiSearchParams build() {
      if (values.get("query") == null) {
        throw new PiloterrSerializationException("The \"query\" parameter is required.", "query");
      }
      return new KijijiSearchParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
