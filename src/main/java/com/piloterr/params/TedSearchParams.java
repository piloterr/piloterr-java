package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/ted/search}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class TedSearchParams implements PiloterrParams {
  private final Map<String, Object> values;

  private TedSearchParams(Map<String, Object> values) {
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

  /** Builder for {@link TedSearchParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Keyword, publication number, or a TED search URL. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** Notice code such as cn-standard or can-standard. Combined with AND. */
    public Builder noticeType(String value) {
      values.put("notice_type", value);
      return this;
    }

    /** Buyer country, ISO alpha-2 or alpha-3. FR becomes FRA. A country name returns 400. */
    public Builder country(String value) {
      values.put("country", value);
      return this;
    }

    /** Place of performance: country code or NUTS code such as FRK24. */
    public Builder place(String value) {
      values.put("place", value);
      return this;
    }

    /** CPV code or prefix. Digits and an optional star, for example 72000000 or 30*. */
    public Builder cpv(String value) {
      values.put("cpv", value);
      return this;
    }

    /** Buyer name, matched as a phrase. */
    public Builder buyer(String value) {
      values.put("buyer", value);
      return this;
    }

    /** Page from 1. next is always null: pass page + 1. A page past the last one returns 404. */
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

    /** Validates required parameters and returns the immutable {@link TedSearchParams}. */
    public TedSearchParams build() {
      return new TedSearchParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
