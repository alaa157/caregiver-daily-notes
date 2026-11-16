package com.caregiver.config;

import com.caregiver.ai.ChatCompletionsLlmClient;
import com.caregiver.ai.HttpExchange;
import com.caregiver.ai.LlmClient;
import com.caregiver.ai.Sleeper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Production LLM wiring: one chat-completions client with no configured
 * provider. Every request must carry request-scoped BYOK credentials; calls
 * without them fail closed. No test touches the network (beans only
 * construct the clients).
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmClientConfig {

  @Bean
  public HttpExchange httpExchange() {
    var http = HttpClient.newHttpClient();
    return (url, headers, jsonBody, timeout) -> {
      var builder = HttpRequest.newBuilder(URI.create(url))
          .timeout(timeout)
          .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
      headers.forEach(builder::header);
      HttpResponse<String> response =
          http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IOException("LLM HTTP " + response.statusCode());
      }
      return response.body();
    };
  }

  @Bean
  public Sleeper sleeper() {
    return Thread::sleep;
  }

  @Bean
  public LlmClient llmClient(LlmProperties props, HttpExchange exchange, Sleeper sleeper) {
    return new ChatCompletionsLlmClient(
        "",
        "",
        "",
        props.maxTokens(),
        null,
        props.timeout(),
        props.maxAttempts(),
        props.backoffBaseMs(),
        exchange,
        sleeper);
  }
}
