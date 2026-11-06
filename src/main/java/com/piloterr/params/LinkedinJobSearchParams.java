package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/linkedin/job/search}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class LinkedinJobSearchParams implements PiloterrParams {
  private final Map<String, Object> values;

  private LinkedinJobSearchParams(Map<String, Object> values) {
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

  /** Builder for {@link LinkedinJobSearchParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Job title or keyword to search for. */
    public Builder keyword(String value) {
      values.put("keyword", value);
      return this;
    }

    /** Filter by seniority level. */
    public Builder experienceLevel(String value) {
      values.put("experience_level", value);
      return this;
    }

    /** Filter by employment type. */
    public Builder jobType(String value) {
      values.put("job_type", value);
      return this;
    }

    /** Filter by posting date recency. */
    public Builder when(String value) {
      values.put("when", value);
      return this;
    }

    /** Filter by work arrangement. */
    public Builder flexibility(String value) {
      values.put("flexibility", value);
      return this;
    }

    /** Maximum radius in miles from the target location (requires geo_id). */
    public Builder distance(Number value) {
      values.put("distance", value);
      return this;
    }

    /** LinkedIn geo ID for the target location. Default is 92000000 (worldwide). Default: "92000000". */
    public Builder geoId(String value) {
      values.put("geo_id", value);
      return this;
    }

    /** LinkedIn numeric company ID to filter results to jobs posted by a specific company. */
    public Builder companyId(String value) {
      values.put("company_id", value);
      return this;
    }

    /** Page number for paginating through results. Default is 1. Each page returns up to 25 job listings. Default: 1. */
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

    /** Validates required parameters and returns the immutable {@link LinkedinJobSearchParams}. */
    public LinkedinJobSearchParams build() {
      return new LinkedinJobSearchParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
