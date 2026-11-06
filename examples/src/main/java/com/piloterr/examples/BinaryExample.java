package com.piloterr.examples;

import com.piloterr.ApiResponse;
import com.piloterr.PiloterrClient;
import com.piloterr.params.WebsiteScreenshotParams;
import java.nio.file.Files;
import java.nio.file.Path;

/** Run with PILOTERR_API_KEY set. */
public final class BinaryExample {
  private BinaryExample() {}

  public static void main(String[] args) throws Exception {
    PiloterrClient client = PiloterrClient.fromEnv();
    ApiResponse response = client.websiteScreenshot(WebsiteScreenshotParams.builder().url("https://example.com").build());
    if (response.isJson()) {
      System.out.println(response.json().toPrettyString());
    } else {
      Files.write(Path.of("screenshot.png"), response.bytes());
      System.out.println("Saved screenshot.png (" + response.contentType() + ")");
    }
  }
}
