package com.caregiver.config;

import com.caregiver.ai.ChatCompletionsLlmClient;
import com.caregiver.ai.FailoverLlmClient;
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
 * Production LLM wiring: primary Gemini and backup OpenRouter chat clients
 * behind the failover decorator. Keys stay env-only; no test touches the
 * network (beans only construct the clients).
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
    var primary = new ChatCompletionsLlmClient(
        props.baseUrl(),
        props.model(),
        props.apiKey(),
        props.maxTokens(),
        props.reasoningMaxTokens(),
        props.timeout(),
        props.maxAttempts(),
        props.backoffBaseMs(),
        exchange,
        sleeper);
    // Fail closed with no backup credentials: never send a backup request
    // without a key. FailoverLlmClient skips a null backup.
    if (props.backupApiKey() == null || props.backupApiKey().isBlank()) {
      return new FailoverLlmClient(primary, null);
    }
    var backup = new ChatCompletionsLlmClient(
        props.backupBaseUrl(),
        props.backupModel(),
        props.backupApiKey(),
        props.backupMaxTokens(),
        props.backupReasoningMaxTokens(),
        props.timeout(),
        props.maxAttempts(),
        props.backoffBaseMs(),
        exchange,
        sleeper);
    return new FailoverLlmClient(primary, backup);
  }
}
