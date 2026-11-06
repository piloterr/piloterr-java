package com.piloterr.examples;

import com.piloterr.ApiResponse;
import com.piloterr.PiloterrApiException;
import com.piloterr.PiloterrClient;
import java.util.Map;

/** Run with PILOTERR_API_KEY set. */
public final class RequestExample {
  private RequestExample() {}

  public static void main(String[] args) throws Exception {
    PiloterrClient client = PiloterrClient.fromEnv();
    try {
      ApiResponse response = client.request("GET", "/v1/usage", Map.of());
      System.out.println(response.text());
    } catch (PiloterrApiException e) {
      System.err.println("HTTP " + e.status() + ": " + e.getMessage());
    }
  }
}
