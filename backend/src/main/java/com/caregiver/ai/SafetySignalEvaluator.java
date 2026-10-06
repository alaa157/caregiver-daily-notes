package com.caregiver.ai;

import com.caregiver.common.NoteSignals;
import com.caregiver.common.Severity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic red-flag decision table over stubbed note signals.
 * Pure Java — no LLM, no Spring. Missing data yields absent signals, never positives.
 */
public class SafetySignalEvaluator {

  /** Pain score at or above this triggers {@code HIGH_PAIN/HIGH}. Scores 0-6 never trigger. */
  public static final int HIGH_PAIN_THRESHOLD = 7;

  /** Verified missed-medication distinct dates at or above this triggers {@code REPEATED_MISSED_MED/HIGH}. */
  public static final int MISSED_MED_COUNT_THRESHOLD = 2;

  /** Poor-appetite distinct dates at or above this triggers {@code POOR_APPETITE_DAYS/WATCH}. */
  public static final int POOR_APPETITE_DAYS_THRESHOLD = 3;

  /** Default evaluation window length in days. */
  public static final int DEFAULT_PERIOD_DAYS = 7;

  /**
   * Evaluates notes in the inclusive window {@code [periodEndInclusive-periodDays+1, periodEndInclusive]}.
   * Null or empty {@code notes}, null window end, or non-positive {@code periodDays}
   * yield an empty evaluation with no banner — never throw on missing data.
   */
  public SafetyEvaluation evaluate(List<NoteSignals> notes, LocalDate periodEndInclusive, int periodDays) {
    if (notes == null || notes.isEmpty() || periodEndInclusive == null || periodDays <= 0) {
      return new SafetyEvaluation(List.of(), false);
    }
    LocalDate windowStart = periodEndInclusive.minusDays((long) periodDays - 1);
    List<NoteSignals> window = notes.stream()
        .filter(n -> n != null && n.date() != null
            && !n.date().isBefore(windowStart)
            && !n.date().isAfter(periodEndInclusive))
        .toList();

    List<SafetySignal> signals = new ArrayList<>();

    if (window.stream().anyMatch(NoteSignals::fallReported)) {
      signals.add(new SafetySignal(
          SafetyRuleId.FALL_DETECTED, Severity.HIGH, "Fall reported in period"));
    }

    window.stream()
        .map(NoteSignals::painScore)
        .filter(p -> p != null && p >= HIGH_PAIN_THRESHOLD)
        .max(Integer::compareTo)
        .ifPresent(max -> signals.add(new SafetySignal(
            SafetyRuleId.HIGH_PAIN, Severity.HIGH, "Max pain " + max + " in period")));

    long missedDates = window.stream()
        .filter(n -> Boolean.TRUE.equals(n.missedMedication()) && !n.medicationUnverified())
        .map(NoteSignals::date)
        .distinct()
        .count();
    if (missedDates >= MISSED_MED_COUNT_THRESHOLD) {
      signals.add(new SafetySignal(
          SafetyRuleId.REPEATED_MISSED_MED, Severity.HIGH,
          "Missed medication on " + missedDates + " days in period"));
    }

    long appetiteDates = window.stream()
        .filter(n -> Boolean.TRUE.equals(n.poorAppetite()))
        .map(NoteSignals::date)
        .distinct()
        .count();
    if (appetiteDates >= POOR_APPETITE_DAYS_THRESHOLD) {
      signals.add(new SafetySignal(
          SafetyRuleId.POOR_APPETITE_DAYS, Severity.WATCH,
          "Poor appetite on " + appetiteDates + " days in period"));
    }

    boolean needsDoctorBanner = signals.stream().anyMatch(s -> s.severity() == Severity.HIGH);
    return new SafetyEvaluation(signals, needsDoctorBanner);
  }
}
