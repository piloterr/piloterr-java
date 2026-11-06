package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/leroymerlin/search}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class LeroymerlinSearchParams implements PiloterrParams {
  private final Map<String, Object> values;

  private LeroymerlinSearchParams(Map<String, Object> values) {
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

  /** Builder for {@link LeroymerlinSearchParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Required. Search keyword, SKU/EAN, or full Leroy Merlin search URL. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** Leroy Merlin country when query is a keyword or identifier (default: fr). Ignored when query is a full Leroy Merlin URL (country is detected from the domain). */
    public Builder region(String value) {
      values.put("region", value);
      return this;
    }

    /** 1-based page number for pagination. Defaults to 1 when omitted. Use the pagination.total_pages returned by a previous call to iterate. */
    public Builder page(Number value) {
      values.put("page", value);
      return this;
    }

    /** Optional store id for local prices and availability. Must exist for the active region (FR stores with region=fr, RO stores with region=ro). Returns 400 when unknown. */
    public Builder storeId(String value) {
      values.put("store_id", value);
      return this;
    }

    /** When set to true, returns the raw HTML of the page instead of the parsed JSON payload. Useful for debugging or custom parsing. The response shape is {"return": "&lt;html&gt;..."}. Defaults to false. Default: false. */
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

    /** Validates required parameters and returns the immutable {@link LeroymerlinSearchParams}. */
    public LeroymerlinSearchParams build() {
      if (values.get("query") == null) {
        throw new PiloterrSerializationException("The \"query\" parameter is required.", "query");
      }
      return new LeroymerlinSearchParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
