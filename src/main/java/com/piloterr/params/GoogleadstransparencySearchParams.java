package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/googleadstransparency/search}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class GoogleadstransparencySearchParams implements PiloterrParams {
  private final Map<String, Object> values;

  private GoogleadstransparencySearchParams(Map<String, Object> values) {
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

  /** Builder for {@link GoogleadstransparencySearchParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Required. Domain name, advertiser name, advertiser id, or a full Ads Transparency URL. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** Country. ISO alpha-2 (FR), anywhere, or a Transparency region code (2250 for France, 250 is also accepted). */
    public Builder region(String value) {
      values.put("region", value);
      return this;
    }

    /** Domain filter. Used when query is not already a domain. */
    public Builder domain(String value) {
      values.put("domain", value);
      return this;
    }

    /** Advertiser id, AR followed by digits. */
    public Builder advertiserId(String value) {
      values.put("advertiser_id", value);
      return this;
    }

    /** Creative format. */
    public Builder format(String value) {
      values.put("format", value);
      return this;
    }

    /** Comma-separated surfaces. */
    public Builder platform(String value) {
      values.put("platform", value);
      return this;
    }

    /** Relative window. */
    public Builder presetDate(String value) {
      values.put("preset_date", value);
      return this;
    }

    /** Inclusive start date, YYYY-MM-DD or YYYYMMDD. */
    public Builder startDate(String value) {
      values.put("start_date", value);
      return this;
    }

    /** Inclusive end date, YYYY-MM-DD or YYYYMMDD. A single day uses the same value as start_date. */
    public Builder endDate(String value) {
      values.put("end_date", value);
      return this;
    }

    /** Ad topic. Default: commercial. Default: "commercial". */
    public Builder topic(String value) {
      values.put("topic", value);
      return this;
    }

    /** Page number, from 1 to 20. Default: 1. Default: 1. */
    public Builder page(Number value) {
      values.put("page", value);
      return this;
    }

    /** Page size, from 1 to 100. Default: 40. Default: 40. */
    public Builder perPage(Number value) {
      values.put("per_page", value);
      return this;
    }

    /** Opaque cursor copied from pagination.next. */
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

    /** Validates required parameters and returns the immutable {@link GoogleadstransparencySearchParams}. */
    public GoogleadstransparencySearchParams build() {
      if (values.get("query") == null) {
        throw new PiloterrSerializationException("The \"query\" parameter is required.", "query");
      }
      return new GoogleadstransparencySearchParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
