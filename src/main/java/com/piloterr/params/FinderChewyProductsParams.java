package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/finder/chewy-products}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class FinderChewyProductsParams implements PiloterrParams {
  private final Map<String, Object> values;

  private FinderChewyProductsParams(Map<String, Object> values) {
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

  /** Builder for {@link FinderChewyProductsParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Free-text search across product titles and IDs. Optional if at least one filter is provided. */
    public Builder search(String value) {
      values.put("search", value);
      return this;
    }

    /** Legacy alias of search. Kept for backward compatibility. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** Filter by one or several internal product IDs. Provide a comma-separated list to match multiple IDs. */
    public Builder id(String value) {
      values.put("id", value);
      return this;
    }

    /** Filter by website domain. Comma-separated values are accepted. */
    public Builder domain(String value) {
      values.put("domain", value);
      return this;
    }

    /** Raw filter expression for advanced filtering. Combined with the shorthand filters using AND. */
    public Builder filter(String value) {
      values.put("filter", value);
      return this;
    }

    /** Maximum number of results to return. Default is 10, maximum is 100. Default: 10. */
    public Builder limit(Number value) {
      values.put("limit", value);
      return this;
    }

    /** Number of results to skip for pagination. Default is 0. Default: 0. */
    public Builder offset(Number value) {
      values.put("offset", value);
      return this;
    }

    /** Sort the results. Provide one or several attributes with :asc or :desc, comma-separated. */
    public Builder sort(String value) {
      values.put("sort", value);
      return this;
    }

    /** Comma-separated list of fields to return. Defaults to all fields. */
    public Builder fields(String value) {
      values.put("fields", value);
      return this;
    }

    /** When true, the response is wrapped in an object containing the total estimated count plus the hits: { count, hits }. When false or omitted, returns the raw hits array. Default: false. */
    public Builder count(Boolean value) {
      values.put("count", value);
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

    /** Validates required parameters and returns the immutable {@link FinderChewyProductsParams}. */
    public FinderChewyProductsParams build() {
      return new FinderChewyProductsParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
