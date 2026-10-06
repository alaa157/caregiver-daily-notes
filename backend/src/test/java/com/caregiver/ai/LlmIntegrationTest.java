package com.caregiver.ai;

import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Replay suite: scripted provider outcomes through {@link FakeLlmClient},
 * with every suspicious body re-validated through {@link StrictJsonParser}.
 * Rejected output is never trusted.
 */
class LlmIntegrationTest {

  enum Severity {
    CALM,
    LOW
  }

  record Summary(String summary, Severity mood) {
  }

  static LlmRequest request() {
    return new LlmRequest("Summarize the notes.", "note data");
  }

  @Test
  void replay_okThenFallback_sequencesStatuses() {
    var fake = new FakeLlmClient(new ArrayDeque<>(List.of(
        new LlmResult(LlmStatus.OK, "{\"summary\":\"ok\"}", null),
        new LlmResult(LlmStatus.FALLBACK, "", "AI_UNAVAILABLE: HTTP 500"))));

    assertThat(fake.complete(request()).status()).isEqualTo(LlmStatus.OK);
    assertThat(fake.complete(request()).status()).isEqualTo(LlmStatus.FALLBACK);
    assertThat(fake.calls()).isEqualTo(2);
  }

  @Test
  void replay_timeoutShape_isFallbackWithDetail() {
    var fake = new FakeLlmClient(new ArrayDeque<>(List.of(
        new LlmResult(LlmStatus.FALLBACK, "", "AI_UNAVAILABLE: HttpTimeoutException: timed out"))));

    var result = fake.complete(request());

    assertThat(result.status()).isEqualTo(LlmStatus.FALLBACK);
    assertThat(result.text()).isEmpty();
    assertThat(result.error()).startsWith("AI_UNAVAILABLE");
  }

  @Test
  void replay_truncatedJson_isRejectedNotTrusted() {
    assertThatThrownBy(() -> StrictJsonParser.parse("{\"summary\":\"", Summary.class, Set.of("summary")))
        .isInstanceOf(InvalidModelOutputException.class);
  }

  @Test
  void replay_invalidEnum_isRejectedNotTrusted() {
    assertThatThrownBy(
        () -> StrictJsonParser.parse("{\"summary\":\"s\",\"mood\":\"EUPHORIC\"}", Summary.class, Set.of("summary", "mood")))
        .isInstanceOf(InvalidModelOutputException.class);
  }

  @Test
  void replay_malformed_isRejectedNotTrusted() {
    assertThatThrownBy(() -> StrictJsonParser.parse("not json at all {{{", Summary.class, Set.of("summary")))
        .isInstanceOf(InvalidModelOutputException.class);
  }

  @Test
  void replay_emptyAndNullBodies_yieldFallback() {
    // Empty/null bodies fail parsing, which the provider maps to retry-then-fallback.
    assertThatThrownBy(() -> StrictJsonParser.parseTree("")).isInstanceOf(InvalidModelOutputException.class);
    assertThatThrownBy(() -> StrictJsonParser.parseTree(null)).isInstanceOf(InvalidModelOutputException.class);
  }

  @Test
  void replay_codeFencedValid_parsesAfterStrip() {
    var summary = StrictJsonParser.parse(
        "```json\n{\"summary\":\"steady day\",\"mood\":\"CALM\"}\n```", Summary.class, Set.of("summary", "mood"));
    assertThat(summary.summary()).isEqualTo("steady day");
    assertThat(summary.mood()).isEqualTo(Severity.CALM);
  }
}
