package com.caregiver.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
  void transportExceptionDetails_areNotReturnedInFallback() {
    var exchange = new FakeExchange();
    exchange.addFailure(new IOException("request contained private note and secret key"));
    exchange.addFailure(new IOException("request contained private note and secret key"));
    exchange.addFailure(new IOException("request contained private note and secret key"));
    var client = client("secret", null, exchange, ms -> {
    });

    var result = client.complete(request());

    assertThat(result.status()).isEqualTo(LlmStatus.FALLBACK);
    assertThat(result.error()).isEqualTo("AI_UNAVAILABLE: transport failure");
    assertThat(result.error()).doesNotContain("private note", "secret key");
  }

  @Test
  void malformedJson_yieldsFallbackAfterRetries() {
    var exchange = new FakeExchange();
    exchange.addBody("not-json");
    exchange.addBody("not-json");
    exchange.addBody("not-json");
    var client = client("", null, exchange, ms -> {
    });

    var result = client.complete(request());

    assertThat(result.status()).isEqualTo(LlmStatus.FALLBACK);
    assertThat(exchange.calls).hasSize(3);
  }

  @Test
  void rateLimitAndServiceUnavailable_areRetried() {
    var exchange = new FakeExchange();
    exchange.addFailure(new IOException("HTTP 429"));
    exchange.addFailure(new IOException("HTTP 503"));
    exchange.addBody(VALID_BODY);
    var client = client("", null, exchange, ms -> {
    });

    var result = client.complete(request());

    assertThat(result.status()).isEqualTo(LlmStatus.OK);
    assertThat(exchange.calls).hasSize(3);
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

  @Test
  void usesRequestScopedKeyForOneProviderCall() {
    var exchange = new FakeExchange();
    exchange.addBody(VALID_BODY);
    var client = client("configured-synthetic-key", null, exchange, ms -> { });
    var credentials = new ProviderCredentials(
        "OPENAI", "https://api.openai.com/v1/", "byok-model", "byok-synthetic-key");

    var result = client.complete(new LlmRequest("system", "notes", credentials));

    assertThat(result.status()).isEqualTo(LlmStatus.OK);
    assertThat(exchange.calls).hasSize(1);
    var call = exchange.calls.get(0);
    assertThat(call.url()).isEqualTo("https://api.openai.com/v1/chat/completions");
    assertThat(call.headers()).containsEntry("Authorization", "Bearer byok-synthetic-key")
        .doesNotContainValue("Bearer configured-synthetic-key");
    assertThat(call.body()).contains("\"model\":\"byok-model\"")
        .doesNotContain("test-model");
  }

  @Test
  void doesNotMutateConfiguredCredentialsAfterRequestScopedCall() {
    var exchange = new FakeExchange();
    exchange.addBody(VALID_BODY);
    exchange.addBody(VALID_BODY);
    var client = client("configured-synthetic-key", null, exchange, ms -> { });
    var credentials = new ProviderCredentials(
        "XAI", "https://api.x.ai/v1", "xai-model", "byok-synthetic-key");

    client.complete(new LlmRequest("system", "notes", credentials));
    client.complete(request());

    assertThat(exchange.calls).hasSize(2);
    assertThat(exchange.calls.get(0).url()).isEqualTo("https://api.x.ai/v1/chat/completions");
    assertThat(exchange.calls.get(0).headers()).containsEntry("Authorization", "Bearer byok-synthetic-key");
    assertThat(exchange.calls.get(0).body()).contains("\"model\":\"xai-model\"");
    assertThat(exchange.calls.get(1).url()).isEqualTo("https://inference.example.com/chat/completions");
    assertThat(exchange.calls.get(1).headers()).containsEntry("Authorization", "Bearer configured-synthetic-key")
        .doesNotContainValue("Bearer byok-synthetic-key");
    assertThat(exchange.calls.get(1).body()).contains("\"model\":\"test-model\"")
        .doesNotContain("xai-model");
    assertThat(client.baseUrl()).isEqualTo("https://inference.example.com");
    assertThat(client.model()).isEqualTo("test-model");
  }

  @Test
  void redactsProviderCredentialsFromStringOutput() throws Exception {
    var credentials = new ProviderCredentials(
        "OPEN_ROUTER", "https://openrouter.ai/api/v1", "openai/model", "synthetic-secret-123");

    assertThat(credentials.toString()).contains("[REDACTED]").doesNotContain("synthetic-secret-123");
    var request = new LlmRequest("system", "notes", credentials);
    assertThat(request.toString()).contains("[REDACTED]").doesNotContain("synthetic-secret-123");
    assertThat(new ObjectMapper().writeValueAsString(request))
        .doesNotContain("synthetic-secret-123", "\"credentials\"");
  }

  @Test
  void doesNotReturnRequestKeyInProviderFailureResult() {
    var exchange = new FakeExchange();
    exchange.addFailure(new IOException("provider echoed synthetic-sensitive-key"));
    exchange.addFailure(new IOException("provider echoed synthetic-sensitive-key"));
    exchange.addFailure(new IOException("provider echoed synthetic-sensitive-key"));
    var client = client("configured-synthetic-key", null, exchange, ms -> { });
    var credentials = new ProviderCredentials(
        "OPENAI", "https://api.openai.com/v1", "byok-model", "synthetic-sensitive-key");

    var result = client.complete(new LlmRequest("system", "notes", credentials));

    assertThat(result.status()).isEqualTo(LlmStatus.FALLBACK);
    assertThat(result.error()).isEqualTo("AI_UNAVAILABLE: transport failure")
        .doesNotContain("synthetic-sensitive-key", "provider echoed", "configured-synthetic-key");
    assertThat(exchange.calls).hasSize(3);
    assertThat(exchange.calls).allSatisfy(call ->
        assertThat(call.headers()).containsEntry("Authorization", "Bearer synthetic-sensitive-key"));
  }

  @Test
  void rejectsAnUnlistedProviderHost() {
    var exchange = new FakeExchange();
    var client = client("configured-key", null, exchange, ms -> { });

    assertThatThrownBy(() -> client.complete(new LlmRequest("system", "notes",
        new ProviderCredentials("OPENAI", "https://attacker.example/v1", "model", "key"))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("baseUrl is not approved for providerId");
    assertThat(exchange.calls).isEmpty();
  }

  @Test
  void rejectsUnsupportedAnthropicProviderId() {
    assertThatThrownBy(() -> new ProviderCredentials(
        "ANTHROPIC", "https://api.anthropic.com", "model", "synthetic-key"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("providerId is unsupported");
  }

  @Test
  void rejectsProviderIdPairedWithAnotherProvidersHost() {
    assertThatThrownBy(() -> new ProviderCredentials(
        "OPENAI", "https://generativelanguage.googleapis.com", "model", "synthetic-key"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("baseUrl is not approved for providerId");
  }

  @Test
  void rejectsUnsafeOrMalformedProviderUrls() {
    List<String> invalidUrls = List.of(
        "http://api.openai.com/v1",
        "https://sub.api.openai.com/v1",
        "https://api.openai.com.evil.test/v1",
        "https://user:pass@api.openai.com/v1",
        "https://api.openai.com:8443/v1",
        "https://api.openai.com/v1?query=value",
        "https://api.openai.com/v1#fragment",
        "https://api.openai.com:bad/v1");

    for (String invalidUrl : invalidUrls) {
      assertThatThrownBy(() -> new ProviderCredentials(
          "OPENAI", invalidUrl, "model", "synthetic-key"))
          .as("reject URL %s", invalidUrl)
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void usesExistingGeminiOpenAiCompatibleEndpoint() {
    var exchange = new FakeExchange();
    exchange.addBody(VALID_BODY);
    var credentials = new ProviderCredentials(
        "GEMINI", "https://generativelanguage.googleapis.com", "gemini-model", "gemini-key");

    client("configured-key", null, exchange, ms -> { })
        .complete(new LlmRequest("system", "notes", credentials));

    assertThat(exchange.calls).singleElement().satisfies(call -> {
      assertThat(call.url()).isEqualTo(
          "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions");
      assertThat(call.headers()).containsEntry("Authorization", "Bearer gemini-key");
      assertThat(call.body()).contains("\"model\":\"gemini-model\"");
    });
  }

  @Test
  void omitsAuthorizationHeaderForBlankRequestScopedKey() {
    var exchange = new FakeExchange();
    exchange.addBody(VALID_BODY);
    var credentials = new ProviderCredentials(
        "OPENAI", "https://api.openai.com/v1", "model", " ");

    client("configured-key", null, exchange, ms -> { })
        .complete(new LlmRequest("system", "notes", credentials));

    assertThat(exchange.calls).singleElement().satisfies(call -> {
      assertThat(call.headers()).doesNotContainKey("Authorization");
      assertThat(call.url()).isEqualTo("https://api.openai.com/v1/chat/completions");
    });
  }

  @Test
  void keepsRequestScopedCredentialsAcrossRetryAndRepairAttempts() {
    var exchange = new FakeExchange();
    exchange.addBody("{\"choices\":[{\"message\":{\"content\":\"not-json\"},\"finish_reason\":\"stop\"}]}");
    exchange.addBody("{\"choices\":[{\"message\":{\"content\":\"still-not-json\"},\"finish_reason\":\"stop\"}]}");
    exchange.addBody(VALID_BODY);
    var credentials = new ProviderCredentials(
        "OPEN_ROUTER", "https://openrouter.ai/api/v1", "route/model", "retry-synthetic-key");

    var result = client("configured-key", null, exchange, ms -> { })
        .complete(new LlmRequest("system", "notes", credentials));

    assertThat(result.status()).isEqualTo(LlmStatus.OK);
    assertThat(exchange.calls).hasSize(3).allSatisfy(call -> {
      assertThat(call.url()).isEqualTo("https://openrouter.ai/api/v1/chat/completions");
      assertThat(call.headers()).containsEntry("Authorization", "Bearer retry-synthetic-key")
          .doesNotContainValue("Bearer configured-key");
      assertThat(call.body()).contains("\"model\":\"route/model\"");
    });
  }

  @Test
  void isolatesConcurrentRequestCredentials() throws Exception {
    var calls = new ConcurrentLinkedQueue<FakeExchange.Call>();
    var bothRequestsEntered = new CountDownLatch(2);
    HttpExchange exchange = (url, headers, body, timeout) -> {
      calls.add(new FakeExchange.Call(url, Map.copyOf(headers), body));
      bothRequestsEntered.countDown();
      if (!bothRequestsEntered.await(5, TimeUnit.SECONDS)) {
        throw new IOException("concurrent test synchronization timed out");
      }
      return VALID_BODY;
    };
    var client = new ChatCompletionsLlmClient(
        "https://inference.example.com", "test-model", "configured-key", 10000, null,
        Duration.ofSeconds(30), 3, 0L, exchange, ms -> { });
    var firstCredentials = new ProviderCredentials(
        "OPENAI", "https://api.openai.com/v1", "openai-model", "openai-synthetic-key");
    var secondCredentials = new ProviderCredentials(
        "XAI", "https://api.x.ai/v1", "xai-model", "xai-synthetic-key");
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      Future<LlmResult> first = executor.submit(() ->
          client.complete(new LlmRequest("system", "notes A", firstCredentials)));
      Future<LlmResult> second = executor.submit(() ->
          client.complete(new LlmRequest("system", "notes B", secondCredentials)));
      assertThat(first.get(10, TimeUnit.SECONDS).status()).isEqualTo(LlmStatus.OK);
      assertThat(second.get(10, TimeUnit.SECONDS).status()).isEqualTo(LlmStatus.OK);
    } finally {
      executor.shutdownNow();
    }

    assertThat(calls).hasSize(2);
    var openAiCall = calls.stream().filter(call -> call.url().startsWith("https://api.openai.com/")).findFirst().orElseThrow();
    var xaiCall = calls.stream().filter(call -> call.url().startsWith("https://api.x.ai/")).findFirst().orElseThrow();
    assertThat(openAiCall.url()).isEqualTo("https://api.openai.com/v1/chat/completions");
    assertThat(openAiCall.headers()).containsEntry("Authorization", "Bearer openai-synthetic-key")
        .doesNotContainValue("Bearer xai-synthetic-key");
    assertThat(openAiCall.body()).contains("\"model\":\"openai-model\"")
        .doesNotContain("xai-model");
    assertThat(xaiCall.url()).isEqualTo("https://api.x.ai/v1/chat/completions");
    assertThat(xaiCall.headers()).containsEntry("Authorization", "Bearer xai-synthetic-key")
        .doesNotContainValue("Bearer openai-synthetic-key");
    assertThat(xaiCall.body()).contains("\"model\":\"xai-model\"")
        .doesNotContain("openai-model");
    assertThat(client.baseUrl()).isEqualTo("https://inference.example.com");
    assertThat(client.model()).isEqualTo("test-model");
  }

  @Test
  void oldLlmRequestConstructorRemainsCompatible() {
    LlmRequest request = new LlmRequest("system", "notes");

    assertThat(request.credentials()).isNull();
    assertThat(request.systemPrompt()).isEqualTo("system");
    assertThat(request.dataBlock()).isEqualTo("notes");
  }

  @Test
  void exchangeInterruptionPropagatesAndDoesNotRetry() {
    var exchange = new FakeExchange();
    exchange.script.add(() -> { throw new InterruptedException("cancelled"); });
    var client = client("", null, exchange, ms -> { throw new AssertionError("must not back off"); });

    try {
      assertThatThrownBy(() -> client.complete(request())).isInstanceOf(CancellationException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      assertThat(exchange.calls).hasSize(1);
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void backoffInterruptionPropagatesAndDoesNotRetry() {
    var exchange = new FakeExchange();
    exchange.addFailure(new IOException("synthetic transport error"));
    var client = client("", null, exchange, ms -> { throw new InterruptedException("cancelled"); });

    try {
      assertThatThrownBy(() -> client.complete(request())).isInstanceOf(CancellationException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
      assertThat(exchange.calls).hasSize(1);
    } finally {
      Thread.interrupted();
    }
  }
}
