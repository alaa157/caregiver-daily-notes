package com.caregiver.notes;

import com.caregiver.ai.AiSuggestion;
import com.caregiver.ai.SafetyEvaluation;
import com.caregiver.ai.SafetyOverlay;
import com.caregiver.ai.SafetyRuleId;
import com.caregiver.ai.SafetySignal;
import com.caregiver.ai.SafetySignalEvaluator;
import com.caregiver.ai.TrendCalculator;
import com.caregiver.ai.TrendResult;
import com.caregiver.common.NoteSignals;
import com.caregiver.common.Severity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end proof that flags + trends + overlay hold together
 * across two synthetic 7-day windows.
 */
class SafetyIntegrationTest {

  private static final LocalDate END = LocalDate.of(2026, 10, 7);

  private final SafetySignalEvaluator evaluator = new SafetySignalEvaluator();
  private final TrendCalculator trends = new TrendCalculator();
  private final SafetyOverlay overlay = new SafetyOverlay();

  @Test
  void syntheticFallAndPainWeek_flagsAndTrendsUp() {
    List<NoteSignals> fourteen = SyntheticNotes.fallAndPainWeek("r1", END);
    List<NoteSignals> previous = fourteen.subList(0, 7);
    List<NoteSignals> current = fourteen.subList(7, 14);

    SafetyEvaluation eval = evaluator.evaluate(current, END, 7);
    assertThat(eval.signals()).extracting(SafetySignal::ruleId)
        .contains(SafetyRuleId.FALL_DETECTED, SafetyRuleId.HIGH_PAIN);
    assertThat(eval.needsDoctorBanner()).isTrue();

    List<TrendResult> deltas = trends.computeTrends(current, previous);
    assertThat(deltas.stream()
        .filter(t -> t.metric().equals("FALL_COUNT")).findFirst().orElseThrow().delta()).isPositive();
    assertThat(deltas.stream()
        .filter(t -> t.metric().equals("HIGH_PAIN_COUNT")).findFirst().orElseThrow().delta()).isPositive();

    SafetyEvaluation merged = overlay.merge(eval, new AiSuggestion(
        Map.of(SafetyRuleId.FALL_DETECTED, Severity.WATCH, SafetyRuleId.HIGH_PAIN, Severity.INFO), false));
    assertThat(merged.signals()).extracting(SafetySignal::severity)
        .containsOnly(Severity.HIGH);
    assertThat(merged.needsDoctorBanner()).isTrue();
  }

  @Test
  void syntheticMedsWeek_requiresTwoVerified() {
    List<NoteSignals> verified = SyntheticNotes.medsWeek("r1", END, true);
    SafetyEvaluation flagged = evaluator.evaluate(verified.subList(7, 14), END, 7);
    assertThat(flagged.signals()).extracting(SafetySignal::ruleId)
        .contains(SafetyRuleId.REPEATED_MISSED_MED);
    assertThat(flagged.needsDoctorBanner()).isTrue();

    List<NoteSignals> mixed = SyntheticNotes.medsWeek("r1", END, false);
    SafetyEvaluation clean = evaluator.evaluate(mixed.subList(7, 14), END, 7);
    assertThat(clean.signals()).extracting(SafetySignal::ruleId)
        .doesNotContain(SafetyRuleId.REPEATED_MISSED_MED);
    assertThat(clean.needsDoctorBanner()).isFalse();
  }

  @Test
  void syntheticAppetiteWeek_watchUnlessEscalated() {
    List<NoteSignals> fourteen = SyntheticNotes.appetiteWeek("r1", END);
    SafetyEvaluation eval = evaluator.evaluate(fourteen.subList(7, 14), END, 7);
    assertThat(eval.signals()).extracting(SafetySignal::ruleId)
        .contains(SafetyRuleId.POOR_APPETITE_DAYS);
    var signal = eval.signals().stream()
        .filter(s -> s.ruleId() == SafetyRuleId.POOR_APPETITE_DAYS).findFirst().orElseThrow();
    assertThat(signal.severity()).isEqualTo(Severity.WATCH);
    assertThat(eval.needsDoctorBanner()).isFalse();

    SafetyEvaluation escalated = overlay.merge(eval, new AiSuggestion(
        Map.of(SafetyRuleId.POOR_APPETITE_DAYS, Severity.HIGH), null));
    assertThat(escalated.needsDoctorBanner()).isTrue();
  }
}
