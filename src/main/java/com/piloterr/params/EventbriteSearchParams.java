package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/eventbrite/search}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class EventbriteSearchParams implements PiloterrParams {
  private final Map<String, Object> values;

  private EventbriteSearchParams(Map<String, Object> values) {
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

  /** Builder for {@link EventbriteSearchParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Keyword, or paste a full Eventbrite city / category / search URL. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** Eventbrite TLD when query is a keyword (fr, com, es, com.mx, co.uk, …). Ignored if query is a full URL. Default: "fr". */
    public Builder domain(String value) {
      values.put("domain", value);
      return this;
    }

    /** Location slug such as france--paris (keyword searches only). */
    public Builder location(String value) {
      values.put("location", value);
      return this;
    }

    /** Page number. */
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

    /** Validates required parameters and returns the immutable {@link EventbriteSearchParams}. */
    public EventbriteSearchParams build() {
      return new EventbriteSearchParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
