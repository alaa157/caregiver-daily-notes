package com.caregiver.ai;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * The only network seam. Production uses JDK {@code HttpClient}; tests script
 * this interface, so no test ever touches the network.
 */
@FunctionalInterface
public interface HttpExchange {

  String post(String url, Map<String, String> headers, String jsonBody, Duration timeout)
      throws IOException, InterruptedException;
}
