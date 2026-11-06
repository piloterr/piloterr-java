package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/tiktoktopads/search}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class TiktoktopadsSearchParams implements PiloterrParams {
  private final Map<String, Object> values;

  private TiktoktopadsSearchParams(Map<String, Object> values) {
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

  /** Builder for {@link TiktoktopadsSearchParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Full TikTok Top Ads search URL, or a keyword. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** Brand or product keyword. */
    public Builder keyword(String value) {
      values.put("keyword", value);
      return this;
    }

    /** ISO country code (country_code). */
    public Builder region(String value) {
      values.put("region", value);
      return this;
    }

    /** Campaign objective id (URL param object). */
    public Builder objective(String value) {
      values.put("objective", value);
      return this;
    }

    /** Lookback window: 7, 30, or 180. Default: "30". */
    public Builder period(String value) {
      values.put("period", value);
      return this;
    }

    /** Sort: for_you, reach/impression, ctr/like. */
    public Builder orderBy(String value) {
      values.put("order_by", value);
      return this;
    }

    /** Industry key filter. */
    public Builder industry(String value) {
      values.put("industry", value);
      return this;
    }

    /** Page number. */
    public Builder page(Number value) {
      values.put("page", value);
      return this;
    }

    /** Page size (max 20). */
    public Builder perPage(Number value) {
      values.put("per_page", value);
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

    /** Validates required parameters and returns the immutable {@link TiktoktopadsSearchParams}. */
    public TiktoktopadsSearchParams build() {
      return new TiktoktopadsSearchParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
