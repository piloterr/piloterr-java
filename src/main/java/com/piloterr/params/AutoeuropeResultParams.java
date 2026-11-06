package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/autoeurope/result}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class AutoeuropeResultParams implements PiloterrParams {
  private final Map<String, Object> values;

  private AutoeuropeResultParams(Map<String, Object> values) {
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

  /** Builder for {@link AutoeuropeResultParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Required. Vehicle id from search results[].id, or a package id of that vehicle. */
    public Builder offerId(String value) {
      values.put("offer_id", value);
      return this;
    }

    /** Required. Same pickup place as the search. Example: CDG */
    public Builder pickup(String value) {
      values.put("pickup", value);
      return this;
    }

    /** Required. Same pickup date as the search, YYYY-MM-DD. Example: 2026-11-06 */
    public Builder pickupDate(String value) {
      values.put("pickup_date", value);
      return this;
    }

    /** Required. Same return date as the search, YYYY-MM-DD, after pickup_date. Example: 2026-11-09 */
    public Builder dropoffDate(String value) {
      values.put("dropoff_date", value);
      return this;
    }

    /** Country site used for the search. Default fr. Example: fr. There is no page parameter. */
    public Builder region(String value) {
      values.put("region", value);
      return this;
    }

    /** Return place when it differs from pickup. */
    public Builder dropoff(String value) {
      values.put("dropoff", value);
      return this;
    }

    /** Pickup time HH:MM. Default 10:00. Default: "10:00". */
    public Builder pickupTime(String value) {
      values.put("pickup_time", value);
      return this;
    }

    /** Return time HH:MM. Default 10:00. Default: "10:00". */
    public Builder dropoffTime(String value) {
      values.put("dropoff_time", value);
      return this;
    }

    /** Driver age from 18 to 99. Default 40. */
    public Builder driverAge(Number value) {
      values.put("driver_age", value);
      return this;
    }

    /** When true, return the raw quote HTML instead of JSON. Default: false. */
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

    /** Validates required parameters and returns the immutable {@link AutoeuropeResultParams}. */
    public AutoeuropeResultParams build() {
      if (values.get("offer_id") == null) {
        throw new PiloterrSerializationException("The \"offer_id\" parameter is required.", "offer_id");
      }
      if (values.get("pickup") == null) {
        throw new PiloterrSerializationException("The \"pickup\" parameter is required.", "pickup");
      }
      if (values.get("pickup_date") == null) {
        throw new PiloterrSerializationException("The \"pickup_date\" parameter is required.", "pickup_date");
      }
      if (values.get("dropoff_date") == null) {
        throw new PiloterrSerializationException("The \"dropoff_date\" parameter is required.", "dropoff_date");
      }
      return new AutoeuropeResultParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
