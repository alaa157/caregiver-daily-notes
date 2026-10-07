package com.caregiver.ai;

import com.caregiver.common.NoteSignals;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvalMetamorphicTest {

  static final LocalDate END = LocalDate.of(2026, 10, 30);

  private final SafetySignalEvaluator evaluator = new SafetySignalEvaluator();

  static NoteSignals signal(boolean fall, String text) {
    return new NoteSignals("eval", END, fall, null, null, false, null, text);
  }

  @Test
  void irrelevantSentenceInsertion_doesNotChangeFlags() {
    var base = List.of(signal(true, "she fell today"));
    var padded = List.of(signal(true, "she fell today. The weather was nice today."));

    assertThat(evaluator.evaluate(padded, END, 7)).isEqualTo(evaluator.evaluate(base, END, 7));
  }

  @Test
  void paraphraseWithSameNormalizedContent_validatesGrounded() {
    var summary = new GroundedSummary(
        List.of(new SummaryObservation("eating", "n1", "ياكل جيدا")), List.of(), false);

    assertThat(EvidenceValidator.validate(summary, Map.of("n1", "يَأْكُلُ جَيِّدًا"))).isSameAs(summary);
  }

  @Test
  void negationFlip_changesFlagsInExpectedDirection() {
    var without = evaluator.evaluate(List.of(signal(false, "no fall, steady day")), END, 7);
    var with = evaluator.evaluate(List.of(signal(true, "fall reported")), END, 7);

    assertThat(without.signals()).extracting(SafetySignal::ruleId).doesNotContain(SafetyRuleId.FALL_DETECTED);
    assertThat(with.signals()).extracting(SafetySignal::ruleId).contains(SafetyRuleId.FALL_DETECTED);
  }
}
