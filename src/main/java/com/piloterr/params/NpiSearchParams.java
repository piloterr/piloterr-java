package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/npi/search}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class NpiSearchParams implements PiloterrParams {
  private final Map<String, Object> values;

  private NpiSearchParams(Map<String, Object> values) {
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

  /** Builder for {@link NpiSearchParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Optional when another criterion is set. Example: Anthony Fauci. Also a 10-digit NPI such as 1972813608, or an NPPES search URL. With enumeration_type=NPI-2 this text is the organization name, for example Mayo Clinic. A provider-view URL is rejected here. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** Optional. Example: Anthony. Used as the given name when you do not want query to be split. */
    public Builder firstName(String value) {
      values.put("first_name", value);
      return this;
    }

    /** Optional. Example: Fauci. A single-word query such as Smith is used as the last name. */
    public Builder lastName(String value) {
      values.put("last_name", value);
      return this;
    }

    /** Optional. Example: Mayo Clinic. You can also pass that name in query with enumeration_type=NPI-2. */
    public Builder organizationName(String value) {
      values.put("organization_name", value);
      return this;
    }

    /** Optional. Practice city. Example: Washington. */
    public Builder city(String value) {
      values.put("city", value);
      return this;
    }

    /** Optional. Two-letter state code. Example: DC. Any other value is rejected. */
    public Builder state(String value) {
      values.put("state", value);
      return this;
    }

    /** Optional. Postal code. Example: 20016. */
    public Builder postalCode(String value) {
      values.put("postal_code", value);
      return this;
    }

    /** Optional. Taxonomy description. Example: Internal Medicine. */
    public Builder taxonomy(String value) {
      values.put("taxonomy", value);
      return this;
    }

    /** Optional. NPI-1 for a person or NPI-2 for an organization. Example: NPI-2. NPI-2 treats a text query as an organization name. */
    public Builder enumerationType(String value) {
      values.put("enumeration_type", value);
      return this;
    }

    /** Optional. Page number, starting at 1. Example: 2. Defaults to 1. */
    public Builder page(Number value) {
      values.put("page", value);
      return this;
    }

    /** Optional. Page size. Example: 10. Defaults to 10 and cannot exceed 200. next is a URL only when the page contains this many rows. */
    public Builder limit(Number value) {
      values.put("limit", value);
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

    /** Validates required parameters and returns the immutable {@link NpiSearchParams}. */
    public NpiSearchParams build() {
      return new NpiSearchParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
