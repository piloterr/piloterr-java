package com.piloterr.examples;

import com.piloterr.ApiResponse;
import com.piloterr.PiloterrClient;
import com.piloterr.params.AmazonProductParams;
import java.time.Duration;

/** Run with PILOTERR_API_KEY set. */
public final class WaitForJobExample {
  private WaitForJobExample() {}

  public static void main(String[] args) throws Exception {
    PiloterrClient client = PiloterrClient.fromEnv();
    ApiResponse submitted = client.amazonProduct(AmazonProductParams.builder().query("B09DFCB66S").isAsync(true).build());
    ApiResponse job = client.waitForJob(
        submitted.json().get("job_id").asText(),
        Duration.ofSeconds(3),
        Duration.ofHours(12),
        progress -> System.out.println(progress.path("status").asText() + " " + progress.path("progress").asInt()));
    System.out.println(job.json().toPrettyString());
  }
}
