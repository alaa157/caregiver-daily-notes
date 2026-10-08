package com.caregiver.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * OpenAI-compatible chat-completions provider behind {@link LlmClient}.
 * One instance serves one endpoint: construct one for the primary provider
 * and one for the backup with their own base URL, model, key, and budgets.
 * Retries failed attempts with exponential backoff, spends one JSON-repair
 * retry on malformed output, then falls back to an explicit
 * {@code AI_UNAVAILABLE} result. A {@code finish_reason} other than
 * {@code "stop"} (notably {@code "length"}) counts as a failed attempt, never
 * as content. {@code complete} never throws (except on a null request).
 */
public class ChatCompletionsLlmClient implements LlmClient {

  /** Request timeout mirror; configuration wins at runtime. */
  public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

  /** Default total attempts when configuration supplies a non-positive value. */
  public static final int MAX_ATTEMPTS = 3;

  /** Default backoff base in ms when configuration supplies a negative value. */
  public static final long BACKOFF_BASE_MS = 200;

  /** JSON-repair retries per call, spent only on malformed output. */
  public static final int REPAIR_ATTEMPTS = 1;

  /** Error marker on every fallback result; fallback text is always empty. */
  public static final String FALLBACK_MARKER = "AI_UNAVAILABLE";

  /** Delimiters wrapping caregiver notes in the request input. */
  public static final String DATA_BEGIN = "<<<NOTES_DATA>>>";
  public static final String DATA_END = "<<<END_NOTES_DATA>>>";

  /** Instruction appended to the single repair attempt. */
  public static final String REPAIR_SUFFIX = "\nReturn ONLY valid JSON, no fences, no commentary.";

  /** Fixed sampling temperature for deterministic structured output. */
  public static final double TEMPERATURE = 0.2;

  private static final ObjectMapper JSON = new ObjectMapper();

  private final String baseUrl;
  private final String model;
  private final String apiKey;
  private final int maxTokens;
  private final Long reasoningMaxTokens;
  private final Duration timeout;
  private final int maxAttempts;
  private final long backoffBaseMs;
  private final HttpExchange exchange;
  private final Sleeper sleeper;

  /** Non-secret endpoint, exposed for wiring verification. */
  public String baseUrl() {
    return baseUrl;
  }

  /** Non-secret model id, exposed for wiring verification. */
  public String model() {
    return model;
  }

  public ChatCompletionsLlmClient(
      String baseUrl,
      String model,
      String apiKey,
      int maxTokens,
      Long reasoningMaxTokens,
      Duration timeout,
      int maxAttempts,
      long backoffBaseMs,
      HttpExchange exchange,
      Sleeper sleeper) {
    this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
    this.model = Objects.requireNonNull(model, "model");
    this.apiKey = apiKey;
    this.maxTokens = maxTokens;
    this.reasoningMaxTokens = reasoningMaxTokens;
    this.timeout = timeout;
    this.exchange = Objects.requireNonNull(exchange, "exchange");
    this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    this.maxAttempts = maxAttempts;
    this.backoffBaseMs = backoffBaseMs;
  }

  @Override
  public LlmResult complete(LlmRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("request");
    }
    int attempts = maxAttempts > 0 ? maxAttempts : MAX_ATTEMPTS;
    Duration effectiveTimeout = timeout != null ? timeout : REQUEST_TIMEOUT;
    long backoffBase = backoffBaseMs >= 0 ? backoffBaseMs : BACKOFF_BASE_MS;
    String url = trimmedBaseUrl() + "/chat/completions";
    Map<String, String> headers = headers();

    String lastError = "no attempts made";
    int repairs = 0;
    String body;
    String repairBody;
    try {
      body = requestBody(request.systemPrompt(), delimitedData(request.dataBlock()));
      repairBody = requestBody(
          request.systemPrompt(), delimitedData(request.dataBlock()) + REPAIR_SUFFIX);
    } catch (IOException e) {
      return fallback("request build failed");
    }
    for (int attempt = 1; attempt <= attempts; attempt++) {
      String raw;
      try {
        raw = exchange.post(url, headers, body, effectiveTimeout);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return fallback("interrupted");
      } catch (IOException | RuntimeException e) {
        // Transport exception messages may contain request details; never
        // expose them through the result returned to callers.
        lastError = "transport failure";
        if (!backoff(attempt, attempts, backoffBase)) {
          return fallback("interrupted during backoff");
        }
        continue;
      }
      String text = extractText(raw);
      if (text == null || text.isBlank()) {
        lastError = "empty model output";
        if (!backoff(attempt, attempts, backoffBase)) {
          return fallback("interrupted during backoff");
        }
        continue;
      }
      String stripped = ModelSanitizer.strip(text);
      if (isValidJson(stripped)) {
        return new LlmResult(LlmStatus.OK, stripped, null);
      }
      if (repairs < REPAIR_ATTEMPTS) {
        repairs++;
        String repaired;
        try {
          repaired = tryRepair(url, headers, repairBody, effectiveTimeout);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return fallback("interrupted");
        }
        if (repaired != null) {
          return new LlmResult(LlmStatus.REPAIRED, repaired, null);
        }
        lastError = "repair attempt failed";
      } else {
        lastError = "invalid model output";
      }
      if (!backoff(attempt, attempts, backoffBase)) {
        return fallback("interrupted during backoff");
      }
    }
    return fallback(lastError);
  }

  private String tryRepair(String url, Map<String, String> headers, String repairBody, Duration timeout)
      throws InterruptedException {
    String raw;
    try {
      raw = exchange.post(url, headers, repairBody, timeout);
    } catch (IOException | RuntimeException e) {
      return null;
    }
    String text = extractText(raw);
    if (text == null || text.isBlank()) {
      return null;
    }
    String stripped = ModelSanitizer.strip(text);
    return isValidJson(stripped) ? stripped : null;
  }

  private boolean backoff(int attempt, int attempts, long baseMs) {
    if (attempt >= attempts) {
      return true;
    }
    try {
      sleeper.sleep(baseMs * (1L << (attempt - 1)));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
    return true;
  }

  private String trimmedBaseUrl() {
    String base = baseUrl;
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    return base;
  }

  /** Prevent note content from being mistaken for the provider's data boundaries. */
  private static String escapeDataMarkers(String dataBlock) {
    return dataBlock
        .replace(DATA_BEGIN, "\\u003c\\u003c\\u003cNOTES_DATA\\u003e\\u003e\\u003e")
        .replace(DATA_END, "\\u003c\\u003c\\u003cEND_NOTES_DATA\\u003e\\u003e\\u003e");
  }

  private Map<String, String> headers() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Content-Type", "application/json");
    if (apiKey != null && !apiKey.isBlank()) {
      headers.put("Authorization", "Bearer " + apiKey);
    }
    return headers;
  }

  private String delimitedData(String dataBlock) {
    return DATA_BEGIN + "\n" + escapeDataMarkers(dataBlock) + "\n" + DATA_END;
  }

  private String requestBody(String systemPrompt, String userContent) throws IOException {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("model", model);
    List<Map<String, String>> messages = new ArrayList<>();
    messages.add(Map.of("role", "system", "content", systemPrompt));
    messages.add(Map.of("role", "user", "content", userContent));
    root.put("messages", messages);
    root.put("max_tokens", maxTokens);
    root.put("temperature", TEMPERATURE);
    if (reasoningMaxTokens != null) {
      root.put("reasoning", Map.of("max_tokens", reasoningMaxTokens));
    }
    return JSON.writeValueAsString(root);
  }

  private String extractText(String raw) {
    if (raw == null) {
      return null;
    }
    try {
      JsonNode tree = JSON.readTree(raw);
      JsonNode choices = tree.get("choices");
      if (choices == null || !choices.isArray() || choices.isEmpty()) {
        return null;
      }
      JsonNode first = choices.get(0);
      JsonNode finishReason = first.get("finish_reason");
      if (finishReason == null || !"stop".equals(finishReason.asText())) {
        return null;
      }
      JsonNode message = first.get("message");
      if (message == null) {
        return null;
      }
      JsonNode content = message.get("content");
      if (content == null || !content.isTextual()) {
        return null;
      }
      return content.asText();
    } catch (IOException e) {
      return null;
    }
  }

  private boolean isValidJson(String stripped) {
    try {
      StrictJsonParser.parseTree(stripped);
      return true;
    } catch (InvalidModelOutputException e) {
      return false;
    }
  }

  private LlmResult fallback(String reason) {
    return new LlmResult(LlmStatus.FALLBACK, "", FALLBACK_MARKER + ": " + reason);
  }

  /** Real HTTP exchange for production composition roots. */
  public static HttpExchange defaultExchange() {
    HttpClient client = HttpClient.newHttpClient();
    return (url, headers, body, timeout) -> {
      HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
          .timeout(timeout)
          .POST(HttpRequest.BodyPublishers.ofString(body));
      headers.forEach(builder::header);
      HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IOException("HTTP " + response.statusCode());
      }
      return response.body();
    };
  }
}
