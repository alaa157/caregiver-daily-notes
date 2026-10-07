package com.caregiver.ai;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FailoverLlmClientTest {

  static LlmRequest request() {
    return new LlmRequest("You are a summarizer.", "note data");
  }

  static FakeLlmClient scripted(LlmResult... results) {
    Queue<LlmResult> script = new ArrayDeque<>(List.of(results));
    return new FakeLlmClient(script);
  }

  @Test
  void primaryOk_backupNeverCalled() {
    var primary = scripted(new LlmResult(LlmStatus.OK, "{\"a\":1}", null));
    var backup = scripted(new LlmResult(LlmStatus.OK, "{\"b\":2}", null));
    var events = new ArrayList<String>();
    var client = new FailoverLlmClient(primary, backup, events::add);

    var result = client.complete(request());

    assertThat(result).isEqualTo(new LlmResult(LlmStatus.OK, "{\"a\":1}", null));
    assertThat(backup.calls()).isEqualTo(0);
    assertThat(events).containsExactly("primary-ok");
  }

  @Test
  void primaryFallbackThenBackupOk_returnsBackupOk() {
    var primary = scripted(new LlmResult(LlmStatus.FALLBACK, "", "AI_UNAVAILABLE: down"));
    var backup = scripted(new LlmResult(LlmStatus.OK, "{\"a\":1}", null));
    var events = new ArrayList<String>();
    var client = new FailoverLlmClient(primary, backup, events::add);

    var result = client.complete(request());

    assertThat(result).isEqualTo(new LlmResult(LlmStatus.OK, "{\"a\":1}", null));
    assertThat(primary.calls()).isEqualTo(1);
    assertThat(backup.calls()).isEqualTo(1);
    assertThat(events).containsExactly("backup-ok");
  }

  @Test
  void bothFallback_yieldsFallbackEmptyText() {
    var primary = scripted(new LlmResult(LlmStatus.FALLBACK, "", "AI_UNAVAILABLE: down"));
    var backup = scripted(new LlmResult(LlmStatus.FALLBACK, "", "AI_UNAVAILABLE: down too"));
    var events = new ArrayList<String>();
    var client = new FailoverLlmClient(primary, backup, events::add);

    var result = client.complete(request());

    assertThat(result.status()).isEqualTo(LlmStatus.FALLBACK);
    assertThat(result.text()).isEmpty();
    assertThat(result.error()).startsWith("AI_UNAVAILABLE");
    assertThat(events).containsExactly("fallback");
  }

  @Test
  void backupResult_passedThroughVerbatim() {
    var primary = scripted(new LlmResult(LlmStatus.FALLBACK, "", "AI_UNAVAILABLE: down"));
    var expected = new LlmResult(LlmStatus.REPAIRED, "{\"a\":1}", null);
    var backup = scripted(expected);
    var events = new ArrayList<String>();
    var client = new FailoverLlmClient(primary, backup, events::add);

    assertThat(client.complete(request())).isEqualTo(expected);
    assertThat(events).containsExactly("backup-ok");
  }

  @Test
  void nullBackup_skipsBackup() {
    var primary = scripted(new LlmResult(LlmStatus.FALLBACK, "", "AI_UNAVAILABLE: down"));
    var events = new ArrayList<String>();
    var client = new FailoverLlmClient(primary, null, events::add);

    var result = client.complete(request());

    assertThat(result.status()).isEqualTo(LlmStatus.FALLBACK);
    assertThat(result.text()).isEmpty();
    assertThat(events).containsExactly("fallback");
  }

  @Test
  void nullRequest_throwsIllegalArgument() {
    var client = new FailoverLlmClient(scripted(), scripted());
    assertThatThrownBy(() -> client.complete(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void throwingPrimary_stillTriesBackup() {
    LlmClient exploding = request -> {
      throw new IllegalStateException("boom");
    };
    var backup = scripted(new LlmResult(LlmStatus.OK, "{\"a\":1}", null));
    var events = new ArrayList<String>();
    var client = new FailoverLlmClient(exploding, backup, events::add);

    var result = client.complete(request());

    assertThat(result).isEqualTo(new LlmResult(LlmStatus.OK, "{\"a\":1}", null));
    assertThat(events).containsExactly("backup-ok");
  }

  @Test
  void throwingBackup_becomesFallbackNeverThrows() {
    LlmClient exploding = request -> {
      throw new IllegalStateException("boom");
    };
    var primary = scripted(new LlmResult(LlmStatus.FALLBACK, "", "AI_UNAVAILABLE: down"));
    var events = new ArrayList<String>();
    var client = new FailoverLlmClient(primary, exploding, events::add);

    var result = client.complete(request());

    assertThat(result.status()).isEqualTo(LlmStatus.FALLBACK);
    assertThat(result.text()).isEmpty();
    assertThat(events).containsExactly("fallback");
  }

  @Test
  void throwingEventConsumer_doesNotBreakProviderResult() {
    var primary = scripted(new LlmResult(LlmStatus.OK, "{\"a\":1}", null));
    var client = new FailoverLlmClient(primary, scripted(), token -> {
      throw new IllegalStateException("observer unavailable");
    });

    var result = client.complete(request());

    assertThat(result).isEqualTo(new LlmResult(LlmStatus.OK, "{\"a\":1}", null));
  }
}
