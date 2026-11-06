package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/chewy/search}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class ChewySearchParams implements PiloterrParams {
  private final Map<String, Object> values;

  private ChewySearchParams(Map<String, Object> values) {
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

  /** Builder for {@link ChewySearchParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Required. What to search for. A keyword such as salmon kibble, or a Chewy search or category link such as https://www.chewy.com/s?query=dog+food. A product link (a URL that contains /dp/) returns 400. Another website returns 400. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** Which page to fetch, starting at 1. Leave this empty for page 1. If query is a listing link that already has page=2, this value replaces that page. In simple mode, pagination.page is this page and pagination.next is the next page URL or null. */
    public Builder page(Number value) {
      values.put("page", value);
      return this;
    }

    /** Which JSON to return. Default expert. Default: "expert". */
    public Builder mode(String value) {
      values.put("mode", value);
      return this;
    }

    /** Set to true to get the rendered HTML in result instead of JSON. Default false. The string false stays false and still returns JSON. */
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

    /** Validates required parameters and returns the immutable {@link ChewySearchParams}. */
    public ChewySearchParams build() {
      if (values.get("query") == null) {
        throw new PiloterrSerializationException("The \"query\" parameter is required.", "query");
      }
      return new ChewySearchParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
