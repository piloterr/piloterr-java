package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/website/webunlocker}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class WebsiteWebunlockerParams implements PiloterrParams {
  private final Map<String, Object> values;

  private WebsiteWebunlockerParams(Map<String, Object> values) {
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

  /** Builder for {@link WebsiteWebunlockerParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Required. A public website URL to unlock with the WebUnlocker engine. Must include http:// or https://. The target domain must be on Piloterr's approved whitelist. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** Controls whether the WebUnlocker engine should follow HTTP redirects. Default: true. */
    public Builder allowRedirects(Boolean value) {
      values.put("allow_redirects", value);
      return this;
    }

    /** Controls whether the response returns the raw, unprocessed HTML source of the page. Default: false. */
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

    /** Validates required parameters and returns the immutable {@link WebsiteWebunlockerParams}. */
    public WebsiteWebunlockerParams build() {
      if (values.get("query") == null) {
        throw new PiloterrSerializationException("The \"query\" parameter is required.", "query");
      }
      return new WebsiteWebunlockerParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
