package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/linkedinadlibrary/search}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class LinkedinadlibrarySearchParams implements PiloterrParams {
  private final Map<String, Object> values;

  private LinkedinadlibrarySearchParams(Map<String, Object> values) {
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

  /** Builder for {@link LinkedinadlibrarySearchParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Full LinkedIn Ad Library search URL, or a keyword when account_owner is empty. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** Advertiser / company name filter (accountOwner). */
    public Builder accountOwner(String value) {
      values.put("account_owner", value);
      return this;
    }

    /** Keyword filter on ad copy. */
    public Builder keyword(String value) {
      values.put("keyword", value);
      return this;
    }

    /** Comma-separated ISO country codes, or ALL. */
    public Builder countries(String value) {
      values.put("countries", value);
      return this;
    }

    /** Date window: last-30-days, current-month, current-year, last-year, custom-date-range. */
    public Builder dateOption(String value) {
      values.put("date_option", value);
      return this;
    }

    /** Start date YYYY-MM-DD when date_option=custom-date-range. */
    public Builder startDate(String value) {
      values.put("start_date", value);
      return this;
    }

    /** End date YYYY-MM-DD when date_option=custom-date-range. */
    public Builder endDate(String value) {
      values.put("end_date", value);
      return this;
    }

    /** Opaque cursor from pagination.cursor / pagination.next. */
    public Builder cursor(String value) {
      values.put("cursor", value);
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

    /** Validates required parameters and returns the immutable {@link LinkedinadlibrarySearchParams}. */
    public LinkedinadlibrarySearchParams build() {
      return new LinkedinadlibrarySearchParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
