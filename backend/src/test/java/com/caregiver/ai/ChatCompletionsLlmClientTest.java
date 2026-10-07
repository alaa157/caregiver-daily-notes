package com.caregiver.ai;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;

class ChatCompletionsLlmClientTest {

  static final String VALID_BODY =
      "{\"choices\":[{\"message\":{\"content\":\"{\\\"mood\\\":\\\"good\\\"}\"},\"finish_reason\":\"stop\"}]}";
  static final String LENGTH_BODY =
      "{\"choices\":[{\"message\":{\"content\":\"{\\\"mood\\\":\\\"good\\\"}\"},\"finish_reason\":\"length\"}]}";

  interface ExchangeOutcome {
    String run() throws IOException, InterruptedException;
  }

  static class FakeExchange implements HttpExchange {
    record Call(String url, Map<String, String> headers, String body) {
    }

    final Queue<ExchangeOutcome> script = new ArrayDeque<>();
    final List<Call> calls = new ArrayList<>();

    void addBody(String body) {
      script.add(() -> body);
    }

    void addFailure(IOException e) {
      script.add(() -> {
        throw e;
      });
    }

    void addTimeout() {
      script.add(() -> {
        throw new HttpTimeoutException("timed out");
      });
    }

    @Override
    public String post(String url, Map<String, String> headers, String jsonBody, Duration timeout)
        throws IOException, InterruptedException {
      calls.add(new Call(url, Map.copyOf(headers), jsonBody));
      return script.poll().run();
    }
  }

  static ChatCompletionsLlmClient client(String apiKey, Long reasoningCap, FakeExchange exchange,
      Sleeper sleeper) {
    return new ChatCompletionsLlmClient(
        "https://inference.example.com", "test-model", apiKey, 10000, reasoningCap,
        Duration.ofSeconds(30), 3, 200L, exchange, sleeper);
  }

  static LlmRequest request() {
    return new LlmRequest("You are a summarizer.", "note data");
  }

  @Test
  void validChatCompletion_yieldsOk() {
    var exchange = new FakeExchange();
    exchange.addBody(VALID_BODY);
    var client = client("secret", null, exchange, ms -> {
    });

    var result = client.complete(request());

    assertThat(result.status()).isEqualTo(LlmStatus.OK);
    assertThat(result.text()).contains("\"mood\"");
    assertThat(exchange.calls).hasSize(1);
    assertThat(exchange.calls.get(0).url())
        .isEqualTo("https://inference.example.com/chat/completions");
    assertThat(exchange.calls.get(0).headers())
        .containsEntry("Authorization", "Bearer secret");
    assertThat(exchange.calls.get(0).body()).contains("\"model\"");
    assertThat(exchange.calls.get(0).body()).contains("\"messages\"");
    assertThat(exchange.calls.get(0).body()).contains("\"max_tokens\":10000");
  }

  @Test
  void lengthFinishReason_countsAsFailure() {
    var exchange = new FakeExchange();
    exchange.addBody(LENGTH_BODY);
    exchange.addBody(LENGTH_BODY);
    exchange.addBody(LENGTH_BODY);
    var client = client("", null, exchange, ms -> {
    });

    var result = client.complete(request());

    assertThat(result.status()).isEqualTo(LlmStatus.FALLBACK);
    assertThat(result.text()).isEmpty();
  }

  @Test
  void reasoningCapIncludedWhenSet() {
    var cappedExchange = new FakeExchange();
    cappedExchange.addBody(VALID_BODY);
    var capped = client("", 500L, cappedExchange, ms -> {
    });
    capped.complete(request());
    assertThat(cappedExchange.calls.get(0).body()).contains("\"reasoning\"");

    var plainExchange = new FakeExchange();
    plainExchange.addBody(VALID_BODY);
    var plain = client("", null, plainExchange, ms -> {
    });
    plain.complete(request());
    assertThat(plainExchange.calls.get(0).body()).doesNotContain("\"reasoning\"");
  }

  @Test
  void http500EveryAttempt_yieldsFallbackNeverThrows() {
    var exchange = new FakeExchange();
    exchange.addTimeout();
    exchange.addFailure(new IOException("HTTP 500"));
    exchange.addFailure(new IOException("HTTP 500"));
    var delays = new ArrayList<Long>();
    var client = client("secret", null, exchange, delays::add);

    var result = client.complete(request());

    assertThat(result.status()).isEqualTo(LlmStatus.FALLBACK);
    assertThat(result.text()).isEmpty();
    assertThat(result.error()).startsWith("AI_UNAVAILABLE");
    assertThat(result.error()).doesNotContain("Bearer");
    assertThat(exchange.calls).hasSize(3);
    assertThat(delays).containsExactly(200L, 400L);
  }

  @Test
  void blankKey_sendsNoAuthHeader() {
    var exchange = new FakeExchange();
    exchange.addBody(VALID_BODY);
    var client = client("", null, exchange, ms -> {
    });

    assertThat(client.complete(request()).status()).isEqualTo(LlmStatus.OK);
    assertThat(exchange.calls.get(0).headers()).doesNotContainKey("Authorization");
  }
}
