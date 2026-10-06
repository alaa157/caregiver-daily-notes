package com.caregiver.ai;

import com.caregiver.config.LlmProperties;
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

class HfLlmClientTest {

  static final String VALID_BODY = "[{\"generated_text\": \"{\\\"summary\\\":\\\"ok\\\"}\"}]";
  static final String TRUNCATED_BODY = "[{\"generated_text\": \"{\\\"summary\\\":\"}]";

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
    public String post(String url, Map<String, String> headers, String jsonBody, Duration timeout) throws IOException, InterruptedException {
      calls.add(new Call(url, Map.copyOf(headers), jsonBody));
      return script.poll().run();
    }
  }

  static LlmProperties props(String apiKey) {
    return new LlmProperties(
        "https://inference.example.com", "test-model", Duration.ofSeconds(30), 3, 200, apiKey);
  }

  static LlmRequest request() {
    return new LlmRequest("You are a summarizer.", "note data");
  }

  @Test
  void allAttempts500_yieldsFallbackNeverThrows() {
    var exchange = new FakeExchange();
    exchange.addFailure(new IOException("HTTP 500"));
    exchange.addFailure(new IOException("HTTP 500"));
    exchange.addFailure(new IOException("HTTP 500"));
    var delays = new ArrayList<Long>();
    var client = new HfLlmClient(props(""), exchange, delays::add);

    var result = client.complete(request());

    assertThat(result.status()).isEqualTo(LlmStatus.FALLBACK);
    assertThat(result.text()).isEmpty();
    assertThat(result.error()).startsWith("AI_UNAVAILABLE");
    assertThat(exchange.calls).hasSize(3);
    assertThat(delays).containsExactly(200L, 400L);
  }

  @Test
  void truncatedThenRepairSucceeds_yieldsRepaired() {
    var exchange = new FakeExchange();
    exchange.addBody(TRUNCATED_BODY);
    exchange.addBody(VALID_BODY);
    var client = new HfLlmClient(props(""), exchange, ms -> {
    });

    var result = client.complete(request());

    assertThat(result.status()).isEqualTo(LlmStatus.REPAIRED);
    assertThat(result.text()).contains("\"summary\"");
    assertThat(exchange.calls).hasSize(2);
    assertThat(exchange.calls.get(1).body()).contains("Return ONLY valid JSON");
  }

  @Test
  void truncatedThenRepairFails_yieldsFallback() {
    var exchange = new FakeExchange();
    exchange.addBody(TRUNCATED_BODY);
    exchange.addBody(TRUNCATED_BODY);
    exchange.addFailure(new IOException("HTTP 500"));
    exchange.addFailure(new IOException("HTTP 500"));
    var client = new HfLlmClient(props(""), exchange, ms -> {
    });

    var result = client.complete(request());

    assertThat(result.status()).isEqualTo(LlmStatus.FALLBACK);
    assertThat(result.text()).isEmpty();
  }

  @Test
  void timeoutOnFirstAttempt_retriesAndSucceeds() {
    var exchange = new FakeExchange();
    exchange.addTimeout();
    exchange.addBody(VALID_BODY);
    var client = new HfLlmClient(props(""), exchange, ms -> {
    });

    var result = client.complete(request());

    assertThat(result.status()).isEqualTo(LlmStatus.OK);
    assertThat(exchange.calls).hasSize(2);
  }

  @Test
  void emptyBody_countsAsFailedAttempt() {
    var exchange = new FakeExchange();
    exchange.addBody("");
    exchange.addBody(VALID_BODY);
    var client = new HfLlmClient(props(""), exchange, ms -> {
    });

    var result = client.complete(request());

    assertThat(result.status()).isEqualTo(LlmStatus.OK);
    assertThat(exchange.calls).hasSize(2);
  }

  @Test
  void skipsAuthHeaderWhenKeyBlank() {
    var anonExchange = new FakeExchange();
    anonExchange.addBody(VALID_BODY);
    var anon = new HfLlmClient(props(""), anonExchange, ms -> {
    });
    assertThat(anon.complete(request()).status()).isEqualTo(LlmStatus.OK);
    assertThat(anonExchange.calls.get(0).headers()).doesNotContainKey("Authorization");

    var keyedExchange = new FakeExchange();
    keyedExchange.addBody(VALID_BODY);
    var keyed = new HfLlmClient(props("secret"), keyedExchange, ms -> {
    });
    assertThat(keyed.complete(request()).status()).isEqualTo(LlmStatus.OK);
    assertThat(keyedExchange.calls.get(0).headers()).containsEntry("Authorization", "Bearer secret");
  }
}
