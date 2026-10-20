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
      String id, LocalDate date, boolean fall, Integer pain,
      Boolean missed, boolean unverified, Boolean appetite) {
    return new NoteSignals(id, date, fall, pain, missed, unverified, appetite, "");
  }

  /**
   * Same structured shape with the free text the caregiver actually wrote —
   * the path the text rules guard ("she fell down today" with the checkbox
   * still unticked, "chest pain" with pain unrecorded).
   */
  private static NoteSignals noteText(LocalDate date, String freeText) {
    return new NoteSignals("r1", date, false, 1, null, false, null, freeText);
  }

  private static NoteSignals clean(LocalDate date) {
    return note("r1", date, false, null, null, false, null);
  }

  @Test
  void pain6_noFlag_pain7_flagsHigh() {
    var mild = evaluator.evaluate(List.of(note("r1", END, false, 6, null, false, null)), END, 7);
    assertThat(mild.signals()).extracting(SafetySignal::ruleId).doesNotContain(SafetyRuleId.HIGH_PAIN);
    assertThat(mild.needsDoctorBanner()).isFalse();

    var severe = evaluator.evaluate(List.of(note("r1", END, false, 7, null, false, null)), END, 7);
    assertThat(severe.signals()).extracting(SafetySignal::ruleId).contains(SafetyRuleId.HIGH_PAIN);
    var painSignal = severe.signals().stream()
        .filter(s -> s.ruleId() == SafetyRuleId.HIGH_PAIN).findFirst().orElseThrow();
    assertThat(painSignal.severity()).isEqualTo(Severity.HIGH);
    assertThat(severe.needsDoctorBanner()).isTrue();
  }

  @Test
  void singleMissedMed_noFlag_twoVerifiedMissed_flagsHigh() {
    var single = evaluator.evaluate(
        List.of(note("r1", END, false, null, true, false, null)), END, 7);
    assertThat(single.signals()).extracting(SafetySignal::ruleId)
        .doesNotContain(SafetyRuleId.REPEATED_MISSED_MED);
    assertThat(single.needsDoctorBanner()).isFalse();

    var two = evaluator.evaluate(List.of(
        note("r1", END.minusDays(1), false, null, true, false, null),
        note("r1", END, false, null, true, false, null)), END, 7);
    assertThat(two.signals()).extracting(SafetySignal::ruleId).contains(SafetyRuleId.REPEATED_MISSED_MED);
    var medSignal = two.signals().stream()
        .filter(s -> s.ruleId() == SafetyRuleId.REPEATED_MISSED_MED).findFirst().orElseThrow();
    assertThat(medSignal.severity()).isEqualTo(Severity.HIGH);
    assertThat(two.needsDoctorBanner()).isTrue();
  }

  @Test
  void unverifiedMissedMeds_doNotCount() {
    var eval = evaluator.evaluate(List.of(
        note("r1", END.minusDays(1), false, null, true, true, null),
        note("r1", END, false, null, true, true, null)), END, 7);
    assertThat(eval.signals()).extracting(SafetySignal::ruleId)
        .doesNotContain(SafetyRuleId.REPEATED_MISSED_MED);
    assertThat(eval.needsDoctorBanner()).isFalse();
  }

  @Test
  void poorAppetite2days_noFlag_3days_flagsWatch() {
    var two = evaluator.evaluate(List.of(
        note("r1", END.minusDays(1), false, null, null, false, true),
        note("r1", END, false, null, null, false, true)), END, 7);
    assertThat(two.signals()).extracting(SafetySignal::ruleId)
        .doesNotContain(SafetyRuleId.POOR_APPETITE_DAYS);
    assertThat(two.needsDoctorBanner()).isFalse();

    var three = evaluator.evaluate(List.of(
        note("r1", END.minusDays(2), false, null, null, false, true),
        note("r1", END.minusDays(1), false, null, null, false, true),
        note("r1", END, false, null, null, false, true)), END, 7);
    assertThat(three.signals()).extracting(SafetySignal::ruleId).contains(SafetyRuleId.POOR_APPETITE_DAYS);
    var appetiteSignal = three.signals().stream()
        .filter(s -> s.ruleId() == SafetyRuleId.POOR_APPETITE_DAYS).findFirst().orElseThrow();
    assertThat(appetiteSignal.severity()).isEqualTo(Severity.WATCH);
    assertThat(three.needsDoctorBanner()).isFalse();
  }

  @Test
  void anyFall_flagsHigh() {
    var eval = evaluator.evaluate(List.of(note("r1", END, true, null, null, false, null)), END, 7);
    assertThat(eval.signals()).extracting(SafetySignal::ruleId).contains(SafetyRuleId.FALL_DETECTED);
    var fallSignal = eval.signals().stream()
        .filter(s -> s.ruleId() == SafetyRuleId.FALL_DETECTED).findFirst().orElseThrow();
    assertThat(fallSignal.severity()).isEqualTo(Severity.HIGH);
    assertThat(eval.needsDoctorBanner()).isTrue();
  }

  @Test
  void outOfWindowNotes_ignored() {
    var old = LocalDate.of(2026, 9, 20);
    var eval = evaluator.evaluate(List.of(
        note("r1", old, true, 9, true, false, true)), END, 7);
    assertThat(eval.signals()).isEmpty();
    assertThat(eval.needsDoctorBanner()).isFalse();
  }

  @Test
  void nullAndEmptyInput_returnsEmptyNoBanner() {
    var fromNull = evaluator.evaluate(null, END, 7);
    assertThat(fromNull.signals()).isEmpty();
    assertThat(fromNull.needsDoctorBanner()).isFalse();

    var fromEmpty = evaluator.evaluate(List.of(), END, 7);
    assertThat(fromEmpty.signals()).isEmpty();
    assertThat(fromEmpty.needsDoctorBanner()).isFalse();
  }

  // ---- Free-text rules (mirrored from the app's SafetySignalEvaluatorTest) ----

  @Test
  void scatteredPoorAppetiteDays_doNotFlag() {
    // The old rule counted distinct days ANYWHERE in the window, so a
    // 30-day summary fired on three loose bad days. Streak semantics
    // require adjacency; gaps reset the run.
    var eval = evaluator.evaluate(List.of(
        note("r1", END.minusDays(6), false, null, null, false, true),
        note("r1", END.minusDays(3), false, null, null, false, true),
        note("r1", END, false, null, null, false, true)), END, 7);
    assertThat(eval.signals()).extracting(SafetySignal::ruleId)
        .doesNotContain(SafetyRuleId.POOR_APPETITE_DAYS);
    assertThat(eval.needsDoctorBanner()).isFalse();
  }

  @Test
  void textDescribingAFall_raisesTheFlagEvenWithoutTheCheckbox() {
    var eval = evaluator.evaluate(List.of(noteText(END, "She fell down in the kitchen today")), END, 7);
    assertThat(eval.signals()).hasSize(1);
    assertThat(eval.signals().get(0).ruleId()).isEqualTo(SafetyRuleId.FALL_DETECTED);
    assertThat(eval.signals().get(0).reason()).isEqualTo("Note text describes a fall");
    assertThat(eval.needsDoctorBanner()).isTrue();
  }

  @Test
  void textMentioningChestPain_raisesHighPainEvenWithLowScores() {
    var eval = evaluator.evaluate(List.of(noteText(END, "chest pain started at night")), END, 7);
    assertThat(eval.signals()).hasSize(1);
    assertThat(eval.signals().get(0).ruleId()).isEqualTo(SafetyRuleId.HIGH_PAIN);
    assertThat(eval.signals().get(0).reason()).isEqualTo("Note text describes chest pain");
  }

  @Test
  void arabicText_survivesTypesettingVariants() {
    // Zero-width and direction marks, an Arabic comma (folded to ','), and
    // an Arabic-Indic digit — re-typesetting must not lose the match.
    var eval = evaluator.evaluate(
        List.of(noteText(END, "سقطت\u200F بالأمس\u200B\u060C ألم في صدرها ٧ مرات")),
        END,
        7);
    assertThat(eval.signals()).extracting(SafetySignal::ruleId)
        .containsExactlyInAnyOrder(SafetyRuleId.FALL_DETECTED, SafetyRuleId.HIGH_PAIN);
  }

  @Test
  void fellAsleep_isNotAFall() {
    var eval = evaluator.evaluate(
        List.of(noteText(END, "she fell asleep peacefully after dinner")), END, 7);
    assertThat(eval.signals()).isEmpty();
  }

  @Test
  void cleanText_staysClean() {
    var eval = evaluator.evaluate(
        List.of(noteText(END, "she ate well, walked in the garden")), END, 7);
    assertThat(eval.signals()).isEmpty();
    assertThat(eval.needsDoctorBanner()).isFalse();
  }

  @Test
  void checkboxAndScore_keepTheirStructuredReasonWhenTextAlsoMatches() {
    // Structured sources win the reason wording; the text rules only
    // rescue flags the pills missed.
    var eval = evaluator.evaluate(
        List.of(new NoteSignals("r1", END, true, 9, null, false, false,
            "she fell down and has chest pain")),
        END,
        7);
    assertThat(eval.signals()).extracting(SafetySignal::reason)
        .containsExactly("Fall reported in period", "Max pain 9 in period");
  }
}
