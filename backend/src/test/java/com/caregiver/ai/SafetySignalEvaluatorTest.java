package com.caregiver.ai;

import com.caregiver.common.NoteSignals;
import com.caregiver.common.Severity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SafetySignalEvaluatorTest {

  private final SafetySignalEvaluator evaluator = new SafetySignalEvaluator();
  private static final LocalDate END = LocalDate.of(2026, 10, 7);

  private static NoteSignals note(
      String id, LocalDate date, Boolean fall, Integer pain,
      Boolean missed, boolean unverified, Boolean appetite) {
    return new NoteSignals(id, date, fall, pain, missed, unverified, appetite, "");
  }

  private static NoteSignals clean(LocalDate date) {
    return note("r1", date, false, null, null, false, null);
  }

  private static boolean hasRule(SafetyEvaluation evaluation, SafetyRuleId ruleId) {
    return evaluation.signals().stream().anyMatch(signal -> signal.ruleId() == ruleId);
  }

  @Test
  void highPainBelowAtAndAboveThresholdPreservesSeverityAndBanner() {
    for (int pain : List.of(6, 7, 8)) {
      var evaluation = evaluator.evaluate(
          List.of(note("r1", END, false, pain, null, false, null)), END, 7);
      boolean expected = pain >= 7;

      assertThat(hasRule(evaluation, SafetyRuleId.HIGH_PAIN)).isEqualTo(expected);
      assertThat(evaluation.needsDoctorBanner()).isEqualTo(expected);
      if (expected) {
        var signal = evaluation.signals().stream()
            .filter(value -> value.ruleId() == SafetyRuleId.HIGH_PAIN).findFirst().orElseThrow();
        assertThat(signal.severity()).isEqualTo(Severity.HIGH);
      }
    }
  }

  @Test
  void repeatedMissedMedicationUsesOneTwoAndThreeDistinctVerifiedDates() {
    for (int dates : List.of(1, 2, 3)) {
      var notes = java.util.stream.IntStream.range(0, dates)
          .mapToObj(day -> note("r1", END.minusDays(day), false, null, true, false, null))
          .toList();
      var evaluation = evaluator.evaluate(notes, END, 7);
      boolean expected = dates >= 2;

      assertThat(hasRule(evaluation, SafetyRuleId.REPEATED_MISSED_MED)).isEqualTo(expected);
      assertThat(evaluation.needsDoctorBanner()).isEqualTo(expected);
      if (expected) {
        var signal = evaluation.signals().stream()
            .filter(value -> value.ruleId() == SafetyRuleId.REPEATED_MISSED_MED).findFirst().orElseThrow();
        assertThat(signal.severity()).isEqualTo(Severity.HIGH);
      }
    }
  }

  @Test
  void repeatedMissedMedicationCountsDistinctDatesNotReports() {
    var sameDate = evaluator.evaluate(List.of(
        note("n1", END, false, null, true, false, null),
        note("n2", END, false, null, true, false, null)), END, 7);
    assertThat(hasRule(sameDate, SafetyRuleId.REPEATED_MISSED_MED)).isFalse();
    assertThat(sameDate.needsDoctorBanner()).isFalse();

    var differentDates = evaluator.evaluate(List.of(
        note("n1", END, false, null, true, false, null),
        note("n2", END.minusDays(1), false, null, true, false, null)), END, 7);
    assertThat(hasRule(differentDates, SafetyRuleId.REPEATED_MISSED_MED)).isTrue();
    assertThat(differentDates.needsDoctorBanner()).isTrue();
  }

  @Test
  void unverifiedAndMissingMedicationReportsDoNotCount() {
    var allUnverified = evaluator.evaluate(List.of(
        note("n1", END, false, null, true, true, null),
        note("n2", END.minusDays(1), false, null, true, true, null),
        note("n3", END.minusDays(2), false, null, null, false, null)), END, 7);
    assertThat(hasRule(allUnverified, SafetyRuleId.REPEATED_MISSED_MED)).isFalse();
    assertThat(allUnverified.needsDoctorBanner()).isFalse();

    var oneVerifiedWithUnverified = evaluator.evaluate(List.of(
        note("n1", END, false, null, true, false, null),
        note("n2", END.minusDays(1), false, null, true, true, null),
        note("n3", END.minusDays(2), false, null, null, false, null)), END, 7);
    assertThat(hasRule(oneVerifiedWithUnverified, SafetyRuleId.REPEATED_MISSED_MED)).isFalse();

    var twoVerifiedWithUnverified = evaluator.evaluate(List.of(
        note("n1", END, false, null, true, false, null),
        note("n2", END.minusDays(1), false, null, true, true, null),
        note("n3", END.minusDays(2), false, null, true, false, null)), END, 7);
    assertThat(hasRule(twoVerifiedWithUnverified, SafetyRuleId.REPEATED_MISSED_MED)).isTrue();
  }

  @Test
  void poorAppetiteBelowAtAndAboveThresholdUsesDistinctDatesAndWatchSeverity() {
    for (int dates : List.of(2, 3, 4)) {
      var notes = java.util.stream.IntStream.range(0, dates)
          .mapToObj(day -> note("r1", END.minusDays(day), false, null, null, false, true))
          .toList();
      var evaluation = evaluator.evaluate(notes, END, 7);
      boolean expected = dates >= 3;

      assertThat(hasRule(evaluation, SafetyRuleId.POOR_APPETITE_DAYS)).isEqualTo(expected);
      if (expected) {
        var signal = evaluation.signals().stream()
            .filter(value -> value.ruleId() == SafetyRuleId.POOR_APPETITE_DAYS).findFirst().orElseThrow();
        assertThat(signal.severity()).isEqualTo(Severity.WATCH);
      }
      assertThat(evaluation.needsDoctorBanner()).isFalse();
    }
  }

  @Test
  void duplicatePoorAppetiteReportsOnOneDateCountOnce() {
    var evaluation = evaluator.evaluate(List.of(
        note("n1", END, false, null, null, false, true),
        note("n2", END, false, null, null, false, true),
        note("n3", END.minusDays(1), false, null, null, false, true)), END, 7);

    assertThat(hasRule(evaluation, SafetyRuleId.POOR_APPETITE_DAYS)).isFalse();
    assertThat(evaluation.needsDoctorBanner()).isFalse();
  }

  @Test
  void onlyExplicitTrueFallProducesHighSignal() {
    var reported = evaluator.evaluate(
        List.of(note("r1", END, true, null, null, false, null)), END, 7);
    assertThat(hasRule(reported, SafetyRuleId.FALL_DETECTED)).isTrue();
    var signal = reported.signals().stream()
        .filter(value -> value.ruleId() == SafetyRuleId.FALL_DETECTED).findFirst().orElseThrow();
    assertThat(signal.severity()).isEqualTo(Severity.HIGH);
    assertThat(reported.needsDoctorBanner()).isTrue();

    var explicitFalse = evaluator.evaluate(
        List.of(note("r1", END, false, null, null, false, null)), END, 7);
    assertThat(hasRule(explicitFalse, SafetyRuleId.FALL_DETECTED)).isFalse();
    assertThat(explicitFalse.needsDoctorBanner()).isFalse();

    var missing = evaluator.evaluate(
        List.of(note("r1", END, null, null, null, false, null)), END, 7);
    assertThat(hasRule(missing, SafetyRuleId.FALL_DETECTED)).isFalse();
    assertThat(missing.needsDoctorBanner()).isFalse();
  }

  @Test
  void sevenDayWindowIncludesBothBoundariesAndExcludesAdjacentDates() {
    var startInclusive = evaluator.evaluate(
        List.of(note("r1", LocalDate.of(2026, 10, 1), false, 7, null, false, null)), END, 7);
    assertThat(hasRule(startInclusive, SafetyRuleId.HIGH_PAIN)).isTrue();

    var endInclusive = evaluator.evaluate(
        List.of(note("r1", LocalDate.of(2026, 10, 7), false, 7, null, false, null)), END, 7);
    assertThat(hasRule(endInclusive, SafetyRuleId.HIGH_PAIN)).isTrue();

    var beforeStart = evaluator.evaluate(
        List.of(note("r1", LocalDate.of(2026, 9, 30), false, 8, null, false, null)), END, 7);
    assertThat(beforeStart.signals()).isEmpty();
    assertThat(beforeStart.needsDoctorBanner()).isFalse();

    var afterEnd = evaluator.evaluate(
        List.of(note("r1", LocalDate.of(2026, 10, 8), false, 8, null, false, null)), END, 7);
    assertThat(afterEnd.signals()).isEmpty();
    assertThat(afterEnd.needsDoctorBanner()).isFalse();
  }

  @Test
  void outOfWindowNotesAreIgnored() {
    var old = LocalDate.of(2026, 9, 20);
    var evaluation = evaluator.evaluate(
        List.of(note("r1", old, true, 9, true, false, true)), END, 7);
    assertThat(evaluation.signals()).isEmpty();
    assertThat(evaluation.needsDoctorBanner()).isFalse();
  }

  @Test
  void nullAndEmptyInputReturnEmptyWithoutBanner() {
    var fromNull = evaluator.evaluate(null, END, 7);
    assertThat(fromNull.signals()).isEmpty();
    assertThat(fromNull.needsDoctorBanner()).isFalse();

    var fromEmpty = evaluator.evaluate(List.of(), END, 7);
    assertThat(fromEmpty.signals()).isEmpty();
    assertThat(fromEmpty.needsDoctorBanner()).isFalse();
  }
}
