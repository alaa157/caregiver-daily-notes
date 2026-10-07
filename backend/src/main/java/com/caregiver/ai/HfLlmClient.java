package com.caregiver.ai;

import com.caregiver.config.LlmProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Hugging Face inference provider behind {@link LlmClient}. Retries failed
 * attempts with exponential backoff, spends one JSON-repair retry on malformed
 * output, then falls back to an explicit {@code AI_UNAVAILABLE} result.
 * {@code complete} never throws (except on a null request).
 */
public class HfLlmClient implements LlmClient {

  /** Request timeout mirror; the {@code llm.timeout} property wins at runtime. */
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

  private static final ObjectMapper JSON = new ObjectMapper();

  private final LlmProperties props;
  private final HttpExchange exchange;
  private final Sleeper sleeper;

  public HfLlmClient(LlmProperties props, HttpExchange exchange, Sleeper sleeper) {
    this.props = Objects.requireNonNull(props, "props");
    this.exchange = Objects.requireNonNull(exchange, "exchange");
    this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
  }

  public HfLlmClient(LlmProperties props) {
    this(props, defaultExchange(), Thread::sleep);
  }

  private static HttpExchange defaultExchange() {
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

  @Override
  public LlmResult complete(LlmRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("request");
    }
    int attempts = props.maxAttempts() > 0 ? props.maxAttempts() : MAX_ATTEMPTS;
    Duration timeout = props.timeout() != null ? props.timeout() : REQUEST_TIMEOUT;
    long backoffBase = props.backoffBaseMs() >= 0 ? props.backoffBaseMs() : BACKOFF_BASE_MS;
    String url = baseUrl() + "/models/" + props.model();
    Map<String, String> headers = headers();
    String inputs = request.systemPrompt() + "\n" + DATA_BEGIN + "\n" + request.dataBlock() + "\n" + DATA_END;

    String lastError = "no attempts made";
    int repairs = 0;
    String body;
    String repairBody;
    try {
      body = requestBody(inputs);
      repairBody = requestBody(inputs + REPAIR_SUFFIX);
    } catch (IOException e) {
      return fallback("request build failed");
    }
    for (int attempt = 1; attempt <= attempts; attempt++) {
      String raw;
      try {
        raw = exchange.post(url, headers, body, timeout);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return fallback("interrupted");
      } catch (IOException | RuntimeException e) {
        lastError = describe(e);
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
          repaired = tryRepair(url, headers, repairBody, timeout);
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

  private String tryRepair(String url, Map<String, String> headers, String inputs, Duration timeout)
      throws InterruptedException {
    String raw;
    try {
      raw = exchange.post(url, headers, requestBody(inputs), timeout);
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

  private String baseUrl() {
    String base = props.baseUrl() != null ? props.baseUrl() : "";
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    return base;
  }

  private Map<String, String> headers() {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Content-Type", "application/json");
    if (props.apiKey() != null && !props.apiKey().isBlank()) {
      headers.put("Authorization", "Bearer " + props.apiKey());
    }
    return headers;
  }

  private String requestBody(String inputs) throws IOException {
    return JSON.writeValueAsString(Map.of(
        "inputs", inputs,
        "parameters", Map.of("max_new_tokens", 512, "temperature", 0.2, "return_full_text", false)));
  }

  private String extractText(String raw) {
    if (raw == null) {
      return null;
    }
    try {
      JsonNode tree = JSON.readTree(raw);
      if (tree.isArray() && !tree.isEmpty() && tree.get(0).has("generated_text")) {
        return tree.get(0).get("generated_text").asText();
      }
      return null;
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

  private static String describe(Exception e) {
    String message = e.getMessage();
    return e.getClass().getSimpleName() + (message != null ? ": " + message : "");
  }
}
