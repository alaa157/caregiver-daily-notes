package com.caregiver.ai;

import com.caregiver.common.NoteSignals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrendCalculatorTest {

  private final TrendCalculator calculator = new TrendCalculator();

  private static NoteSignals note(
      LocalDate date, Boolean fall, Integer pain,
      Boolean missed, boolean unverified, Boolean appetite) {
    return new NoteSignals("r1", date, fall, pain, missed, unverified, appetite, "");
  }

  private static TrendResult trend(List<TrendResult> trends, String metric) {
    return trends.stream().filter(result -> result.metric().equals(metric)).findFirst().orElseThrow();
  }

  @Test
  void delta_isCurrentMinusPrevious() {
    assertThat(calculator.computeSingle("FALL_COUNT", 1, 3, 4, 7))
        .isEqualTo(new TrendResult("FALL_COUNT", 1, 3, 2, 4, 7));
  }

  @Test
  void computeTrends_comparesPeriodsWithVerifiedMedsOnly() {
    var previous = List.of(
        note(LocalDate.of(2026, 9, 25), false, null, true, false, null));
    var current = List.of(
        note(LocalDate.of(2026, 10, 5), false, null, true, false, null),
        note(LocalDate.of(2026, 10, 6), false, null, true, false, null),
        note(LocalDate.of(2026, 10, 7), false, null, true, true, null));

    var trends = calculator.computeTrends(current, previous);

    assertThat(trends).extracting(TrendResult::metric)
        .containsExactly("FALL_COUNT", "HIGH_PAIN_COUNT", "MISSED_MED_COUNT", "POOR_APPETITE_COUNT");
    assertThat(trends).contains(new TrendResult("MISSED_MED_COUNT", 1, 2, 1, 1, 2));
  }

  @Test
  void emptyPeriods_yieldZeroDeltas() {
    for (var trends : List.of(
        calculator.computeTrends(null, null),
        calculator.computeTrends(List.of(), List.of()))) {
      assertThat(trends).hasSize(4);
      assertThat(trends).allMatch(t -> t.previousCount() == 0 && t.currentCount() == 0
          && t.delta() == 0 && t.previousSampleSize() == 0 && t.currentSampleSize() == 0);
    }
  }

  @Test
  @DisplayName("counts explicit false fall values as recorded samples")
  void countsExplicitFalseFallValuesAsRecordedSamples() {
    var trends = calculator.computeTrends(
        Arrays.asList(
            note(LocalDate.of(2026, 10, 1), false, null, null, false, null),
            note(LocalDate.of(2026, 10, 2), null, null, null, false, null)),
        List.of());

    assertThat(trend(trends, "FALL_COUNT").currentCount()).isZero();
    assertThat(trend(trends, "FALL_COUNT").currentSampleSize()).isEqualTo(1);
  }

  @Test
  @DisplayName("counts explicit pain zero as a recorded sample")
  void countsExplicitPainZeroAsARecordedSample() {
    var trends = calculator.computeTrends(
        List.of(
            note(LocalDate.of(2026, 10, 1), null, 0, null, false, null),
            note(LocalDate.of(2026, 10, 2), null, null, null, false, null)),
        List.of());

    assertThat(trend(trends, "HIGH_PAIN_COUNT").currentCount()).isZero();
    assertThat(trend(trends, "HIGH_PAIN_COUNT").currentSampleSize()).isEqualTo(1);
  }

  @Test
  @DisplayName("excludes unknown and unverified medication values from sample size")
  void excludesUnknownAndUnverifiedMedicationValuesFromSampleSize() {
    var trends = calculator.computeTrends(
        List.of(
            note(LocalDate.of(2026, 10, 1), null, null, true, false, null),
            note(LocalDate.of(2026, 10, 2), null, null, false, false, null),
            note(LocalDate.of(2026, 10, 3), null, null, true, true, null),
            note(LocalDate.of(2026, 10, 4), null, null, false, true, null),
            note(LocalDate.of(2026, 10, 5), null, null, null, false, null)),
        List.of());

    assertThat(trend(trends, "MISSED_MED_COUNT").currentCount()).isEqualTo(1);
    assertThat(trend(trends, "MISSED_MED_COUNT").currentSampleSize()).isEqualTo(2);
  }

  @Test
  @DisplayName("distinguishes no recorded observations from recorded zero events")
  void distinguishesNoRecordedObservationsFromRecordedZeroEvents() {
    var noData = calculator.computeTrends(
        Arrays.asList(null, note(LocalDate.of(2026, 10, 1), null, null, null, false, null)), List.of());
    assertThat(noData).allMatch(t -> t.currentCount() == 0 && t.currentSampleSize() == 0);

    var recordedZeros = calculator.computeTrends(
        List.of(note(LocalDate.of(2026, 10, 1), false, 0, false, false, false)), List.of());
    assertThat(recordedZeros).allMatch(t -> t.currentCount() == 0 && t.currentSampleSize() == 1);
  }

  @Test
  @DisplayName("keeps event delta equal to current minus previous")
  void keepsEventDeltaEqualToCurrentMinusPrevious() {
    var result = calculator.computeSingle("FALL_COUNT", 1, 3, 4, 7);

    assertThat(result.delta()).isEqualTo(2);
    assertThat(result.previousSampleSize()).isEqualTo(4);
    assertThat(result.currentSampleSize()).isEqualTo(7);
  }

  @Test
  @DisplayName("rejects a delta that does not match event counts")
  void rejectsDeltaThatDoesNotMatchEventCounts() {
    assertThatThrownBy(() -> new TrendResult("FALL_COUNT", 2, 5, 0, 4, 6))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("delta must equal currentCount - previousCount");
  }

  @Test
  @DisplayName("rejects negative previous sample size")
  void rejectsNegativePreviousSampleSize() {
    assertThatThrownBy(() -> new TrendResult("FALL_COUNT", 2, 5, 3, -1, 6))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("rejects negative current sample size")
  void rejectsNegativeCurrentSampleSize() {
    assertThatThrownBy(() -> new TrendResult("FALL_COUNT", 2, 5, 3, 4, -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("preserves negative delta when current event count decreases")
  void preservesNegativeDeltaWhenCurrentEventCountDecreases() {
    var result = calculator.computeSingle("FALL_COUNT", 3, 1, 7, 5);

    assertThat(result.delta()).isEqualTo(-2);
    assertThat(result.previousSampleSize()).isEqualTo(7);
    assertThat(result.currentSampleSize()).isEqualTo(5);
  }

  @Test
  void countsMissedMedicationSampleSizesByDistinctDate() {
    var trends = calculator.computeTrends(
        List.of(
            note(LocalDate.of(2026, 10, 1), null, null, false, false, null),
            note(LocalDate.of(2026, 10, 1), null, null, true, false, null),
            note(LocalDate.of(2026, 10, 2), null, null, false, false, null)),
        List.of());

    assertThat(trend(trends, "MISSED_MED_COUNT").currentCount()).isEqualTo(1);
    assertThat(trend(trends, "MISSED_MED_COUNT").currentSampleSize()).isEqualTo(2);
  }

  @Test
  void countsPoorAppetiteSampleSizesByDistinctDate() {
    var trends = calculator.computeTrends(
        List.of(
            note(LocalDate.of(2026, 10, 1), null, null, null, false, false),
            note(LocalDate.of(2026, 10, 1), null, null, null, false, true),
            note(LocalDate.of(2026, 10, 2), null, null, null, false, false)),
        List.of());

    assertThat(trend(trends, "POOR_APPETITE_COUNT").currentCount()).isEqualTo(1);
    assertThat(trend(trends, "POOR_APPETITE_COUNT").currentSampleSize()).isEqualTo(2);
  }

  @Test
  void countsExplicitFalseAppetiteValuesAsRecordedSamples() {
    var trends = calculator.computeTrends(
        List.of(note(LocalDate.of(2026, 10, 1), null, null, null, false, false)), List.of());

    assertThat(trend(trends, "POOR_APPETITE_COUNT").currentCount()).isZero();
    assertThat(trend(trends, "POOR_APPETITE_COUNT").currentSampleSize()).isEqualTo(1);
  }

  @Test
  void usesTheExistingHighPainThreshold() {
    var trends = calculator.computeTrends(
        List.of(
            note(LocalDate.of(2026, 10, 1), null, SafetySignalEvaluator.HIGH_PAIN_THRESHOLD - 1, null, false, null),
            note(LocalDate.of(2026, 10, 2), null, SafetySignalEvaluator.HIGH_PAIN_THRESHOLD, null, false, null),
            note(LocalDate.of(2026, 10, 3), null, SafetySignalEvaluator.HIGH_PAIN_THRESHOLD + 1, null, false, null)),
        List.of());

    assertThat(trend(trends, "HIGH_PAIN_COUNT").currentCount()).isEqualTo(2);
    assertThat(trend(trends, "HIGH_PAIN_COUNT").currentSampleSize()).isEqualTo(3);
  }
}
