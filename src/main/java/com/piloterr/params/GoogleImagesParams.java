package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/google/images}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class GoogleImagesParams implements PiloterrParams {
  private final Map<String, Object> values;

  private GoogleImagesParams(Map<String, Object> values) {
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

  /** Builder for {@link GoogleImagesParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Required. The image search query to execute on Google Images. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** Google domain to use for the search. Defaults to google.com. Default: "google.com". */
    public Builder googleDomain(String value) {
      values.put("google_domain", value);
      return this;
    }

    /** Country to use for the Google search. Two-letter country code (e.g. us for the United States, uk for United Kingdom, or fr for France). Default: "us". */
    public Builder gl(String value) {
      values.put("gl", value);
      return this;
    }

    /** Language to use for the Google search. Two or three-letter language code (e.g. en, es, fr), optionally followed by a region code (e.g. en-gb, es-419). Default: "en". */
    public Builder hl(String value) {
      values.put("hl", value);
      return this;
    }

    /** Limit results to one or more countries. Use country{XX} with | as delimiter (e.g. countryFR|countryDE). */
    public Builder cr(String value) {
      values.put("cr", value);
      return this;
    }

    /** Limit results to one or more languages. Use lang_{xx} with | as delimiter (e.g. lang_fr|lang_de). */
    public Builder lr(String value) {
      values.put("lr", value);
      return this;
    }

    /** Page number for pagination. Images are returned in batches of 100 per page. Default: 1. */
    public Builder page(Number value) {
      values.put("page", value);
      return this;
    }

    /** Adult content filter. active enables Google SafeSearch; off disables it. Omit to use Google default. */
    public Builder safe(String value) {
      values.put("safe", value);
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

    /** Validates required parameters and returns the immutable {@link GoogleImagesParams}. */
    public GoogleImagesParams build() {
      if (values.get("query") == null) {
        throw new PiloterrSerializationException("The \"query\" parameter is required.", "query");
      }
      return new GoogleImagesParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
