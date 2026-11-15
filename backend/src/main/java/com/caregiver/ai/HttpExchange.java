package com.caregiver.ai;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

  static HttpExchange defaultExchange() {
    HttpClient client = HttpClient.newHttpClient();
    return (url, headers, body, timeout) -> {
      HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
          .timeout(timeout)
          .POST(HttpRequest.BodyPublishers.ofString(body));
      headers.forEach(builder::header);
      HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new ProviderHttpException(response.statusCode());
      }
      return response.body();
    };
  }
}
