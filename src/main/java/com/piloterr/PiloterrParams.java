package com.piloterr;

import java.util.Map;

/** Parameters of one endpoint, in wire order. Implemented by every class in {@code com.piloterr.params}. */
public interface PiloterrParams {
  /** Wire name to value; null values are omitted from requests. */
  Map<String, Object> toMap();
}
