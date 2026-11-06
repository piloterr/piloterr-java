package com.piloterr.params;

import com.piloterr.PiloterrParams;
import com.piloterr.PiloterrSerializationException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters for {@code GET / POST /v2/autodoc/product/related}.
 *
 * <p>Generated from the Piloterr OpenAPI document. Do not edit by hand.
 */
public final class AutodocProductRelatedParams implements PiloterrParams {
  private final Map<String, Object> values;

  private AutodocProductRelatedParams(Map<String, Object> values) {
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

  /** Builder for {@link AutodocProductRelatedParams}. */
  public static final class Builder {
    private final Map<String, Object> values = new LinkedHashMap<>();

    private Builder() {}

    /** Required. Same HTTPS product page URL as Product. */
    public Builder query(String value) {
      values.put("query", value);
      return this;
    }

    /** Required. TecDoc maker id from compatibility.cars[] / compatibility.trucks[], e.g. 93 for RENAULT. */
    public Builder makerId(Number value) {
      values.put("maker_id", value);
      return this;
    }

    /** Required. Required. pkw (passenger cars) or lkw (commercial). Must match the compatibility row. Default: "pkw". */
    public Builder vehicleType(String value) {
      values.put("vehicle_type", value);
      return this;
    }

    /** Required. Required maker label from compatibility, e.g. RENAULT. Used to parse vehicle lines. */
    public Builder makerName(String value) {
      values.put("maker_name", value);
      return this;
    }

    /** Required. Required article id from Product response or URL path, e.g. 7999541. */
    public Builder productId(Number value) {
      values.put("product_id", value);
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

    /** Validates required parameters and returns the immutable {@link AutodocProductRelatedParams}. */
    public AutodocProductRelatedParams build() {
      if (values.get("query") == null) {
        throw new PiloterrSerializationException("The \"query\" parameter is required.", "query");
      }
      if (values.get("maker_id") == null) {
        throw new PiloterrSerializationException("The \"maker_id\" parameter is required.", "maker_id");
      }
      if (values.get("vehicle_type") == null) {
        throw new PiloterrSerializationException("The \"vehicle_type\" parameter is required.", "vehicle_type");
      }
      if (values.get("maker_name") == null) {
        throw new PiloterrSerializationException("The \"maker_name\" parameter is required.", "maker_name");
      }
      if (values.get("product_id") == null) {
        throw new PiloterrSerializationException("The \"product_id\" parameter is required.", "product_id");
      }
      return new AutodocProductRelatedParams(java.util.Collections.unmodifiableMap(new LinkedHashMap<>(values)));
    }
  }
}
