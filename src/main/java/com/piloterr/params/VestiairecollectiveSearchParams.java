package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/vestiairecollective/search}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class VestiairecollectiveSearchParams implements PiloterrParams {
  private final Map<String, Object> values;

  private VestiairecollectiveSearchParams(Map<String, Object> values) {
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

  /** Builder for {@link VestiairecollectiveSearchParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Required. A keyword or a vestiairecollective.com search or category URL. Empty, another host, or a product URL returns 400. A keyword searches www.vestiairecollective.com. A full URL keeps its host. Each card has price as a number and price_display as the label on the card. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** Page number starting at 1. Omit it for page 1, which has no /p-N/ segment. Page 2 requests /p-2/ and sets pagination.page to 2. pagination.next is the next catalog URL, or null on the last page. A value below 1 returns 400. */
    public Builder page(Number value) {
      values.put("page", value);
      return this;
    }

    /** Default false. true replaces results and pagination with result, the rendered HTML. */
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

    /** Validates required parameters and returns the immutable {@link VestiairecollectiveSearchParams}. */
    public VestiairecollectiveSearchParams build() {
      if (values.get("query") == null) {
        throw new PiloterrSerializationException("The \"query\" parameter is required.", "query");
      }
      return new VestiairecollectiveSearchParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
