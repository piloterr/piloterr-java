package com.piloterr.examples;

import com.piloterr.ApiResponse;
import com.piloterr.PiloterrClient;
import com.piloterr.params.AmazonProductParams;

/** Run with PILOTERR_API_KEY set. */
public final class JsonExample {
  private JsonExample() {}

  public static void main(String[] args) throws Exception {
    PiloterrClient client = PiloterrClient.fromEnv();
    ApiResponse response = client.amazonProduct(AmazonProductParams.builder().query("B09DFCB66S").build());
    System.out.println(response.json().toPrettyString());
  }
}
