package com.caregiver.ai;

import com.caregiver.common.NoteSignals;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrendCalculatorTest {

  private final TrendCalculator calculator = new TrendCalculator();

  private static NoteSignals note(
      LocalDate date, boolean fall, Integer pain,
      Boolean missed, boolean unverified, Boolean appetite) {
    return new NoteSignals("r1", date, fall, pain, missed, unverified, appetite, "");
  }

  @Test
  void delta_isCurrentMinusPrevious() {
    assertThat(calculator.computeSingle("FALL_COUNT", 1, 3))
        .isEqualTo(new TrendResult("FALL_COUNT", 1, 3, 2));
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
    assertThat(trends).contains(new TrendResult("MISSED_MED_COUNT", 1, 2, 1));
  }

  @Test
  void emptyPeriods_yieldZeroDeltas() {
    var fromNulls = calculator.computeTrends(null, null);
    assertThat(fromNulls).hasSize(4);
    assertThat(fromNulls).allMatch(t -> t.previousCount() == 0 && t.currentCount() == 0 && t.delta() == 0);

    var fromEmpties = calculator.computeTrends(List.of(), List.of());
    assertThat(fromEmpties).hasSize(4);
    assertThat(fromEmpties).allMatch(t -> t.delta() == 0);
  }
}
