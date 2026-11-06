package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/crunchbase/funding_rounds}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class CrunchbaseFundingRoundsParams implements PiloterrParams {
  private final Map<String, Object> values;

  private CrunchbaseFundingRoundsParams(Map<String, Object> values) {
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

  /** Builder for {@link CrunchbaseFundingRoundsParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Filter funding rounds announced within the last N days. Default: 1. */
    public Builder daysSinceAnnouncement(Number value) {
      values.put("days_since_announcement", value);
      return this;
    }

    /** Filter by funding round type. Accepted values: */
    public Builder investmentType(String value) {
      values.put("investment_type", value);
      return this;
    }

    /** Filter rounds by a specific investor using their Crunchbase UUID. */
    public Builder investorIdentifiers(String value) {
      values.put("investor_identifiers", value);
      return this;
    }

    /** Filter rounds by a specific funded organization using their Crunchbase UUID. */
    public Builder fundedOrganizationIdentifier(String value) {
      values.put("funded_organization_identifier", value);
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

    /** Validates required parameters and returns the immutable {@link CrunchbaseFundingRoundsParams}. */
    public CrunchbaseFundingRoundsParams build() {
      return new CrunchbaseFundingRoundsParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
