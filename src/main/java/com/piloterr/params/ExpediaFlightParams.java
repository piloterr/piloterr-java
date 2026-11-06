package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/expedia/flight}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class ExpediaFlightParams implements PiloterrParams {
  private final Map<String, Object> values;

  private ExpediaFlightParams(Map<String, Object> values) {
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

  /** Builder for {@link ExpediaFlightParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Required. A route or a full Expedia Flights-Search URL. Empty, a route without two airport codes, or a non-Expedia URL returns 400. A full URL is opened as given. price stays the label on the offer. pagination.next is null. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** Departure as YYYY-MM-DD for a route. Omit it and the route uses 2026-11-15. A value that is not YYYY-MM-DD returns 400. Ignored when query is a full URL. */
    public Builder departureDate(String value) {
      values.put("departure_date", value);
      return this;
    }

    /** Return date as YYYY-MM-DD. Omit it for a one-way trip. A value that is not YYYY-MM-DD returns 400. Ignored when query is a full URL. */
    public Builder returnDate(String value) {
      values.put("return_date", value);
      return this;
    }

    /** Adult passengers for a route. Omit it and the request uses 1. Ignored when query is a full URL. */
    public Builder adults(Number value) {
      values.put("adults", value);
      return this;
    }

    /** Label copied onto every result as cabin_class. Omit it and each result uses economy. It does not add a cabin filter to the Expedia URL. */
    public Builder cabinClass(String value) {
      values.put("cabin_class", value);
      return this;
    }

    /** Host for a route. Omit it for www.expedia.com. Allowed: us, ca, uk, au, fr, de, es, it, nl, mx. Any other value returns 400. Ignored when query is a full URL. */
    public Builder region(String value) {
      values.put("region", value);
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

    /** Validates required parameters and returns the immutable {@link ExpediaFlightParams}. */
    public ExpediaFlightParams build() {
      if (values.get("query") == null) {
        throw new PiloterrSerializationException("The \"query\" parameter is required.", "query");
      }
      return new ExpediaFlightParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
