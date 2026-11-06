package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/website/email_phone_extractor}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class WebsiteEmailPhoneExtractorParams implements PiloterrParams {
  private final Map<String, Object> values;

  private WebsiteEmailPhoneExtractorParams(Map<String, Object> values) {
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

  /** Builder for {@link WebsiteEmailPhoneExtractorParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Required. A website URL to crawl for contact information. Must include the http:// or https:// protocol. The crawler will follow internal links ("a" anchors) to find contact details across the site. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** An ISO 3166-1 alpha-2 country code used to improve the accuracy of phone number validation and parsing. */
    public Builder countryCode(String value) {
      values.put("country_code", value);
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

    /** Validates required parameters and returns the immutable {@link WebsiteEmailPhoneExtractorParams}. */
    public WebsiteEmailPhoneExtractorParams build() {
      if (values.get("query") == null) {
        throw new PiloterrSerializationException("The \"query\" parameter is required.", "query");
      }
      return new WebsiteEmailPhoneExtractorParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
