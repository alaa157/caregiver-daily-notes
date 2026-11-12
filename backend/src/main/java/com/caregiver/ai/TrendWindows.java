package com.caregiver.ai;

import com.caregiver.common.NoteSignals;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Computes adjacent, equal-length requested and previous summary trend windows. */
final class TrendWindows {
  private TrendWindows() {}

  static List<TrendResult> compute(TrendCalculator calculator, List<NoteSignals> signals, LocalDate end, int days) {
    Objects.requireNonNull(calculator, "calculator");
    Objects.requireNonNull(end, "end");
    LocalDate currentStart = end.minusDays(days - 1L);
    LocalDate previousStart = currentStart.minusDays(days);
    LocalDate previousEnd = currentStart.minusDays(1);
    List<NoteSignals> safeSignals = signals == null ? List.of() : signals;
    return calculator.computeTrends(
        inRange(safeSignals, currentStart, end),
        inRange(safeSignals, previousStart, previousEnd));
  }

  private static List<NoteSignals> inRange(List<NoteSignals> signals, LocalDate start, LocalDate end) {
    List<NoteSignals> ranged = new ArrayList<>();
    for (NoteSignals signal : signals) {
      if (signal != null && signal.date() != null
          && !signal.date().isBefore(start)
          && !signal.date().isAfter(end)) {
        ranged.add(signal);
      }
    }
    return ranged;
  }
}
