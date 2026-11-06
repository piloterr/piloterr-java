package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/starbucks/search}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class StarbucksSearchParams implements PiloterrParams {
  private final Map<String, Object> values;

  private StarbucksSearchParams(Map<String, Object> values) {
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

  /** Builder for {@link StarbucksSearchParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Store-locator URL, or a lat,lng pair. Required when lat and lng are empty. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** Latitude. Send it with lng. If both are set, they replace any coordinates found in query. */
    public Builder lat(Number value) {
      values.put("lat", value);
      return this;
    }

    /** Longitude. Send it with lat. */
    public Builder lng(Number value) {
      values.put("lng", value);
      return this;
    }

    /** Optional place label sent with the coordinates, for example New York, NY. Coordinates are still required. */
    public Builder place(String value) {
      values.put("place", value);
      return this;
    }

    /** Optional amenity filter. Separate codes with commas. ON keeps cafés that offer mobile order. */
    public Builder features(String value) {
      values.put("features", value);
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

    /** Validates required parameters and returns the immutable {@link StarbucksSearchParams}. */
    public StarbucksSearchParams build() {
      return new StarbucksSearchParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
