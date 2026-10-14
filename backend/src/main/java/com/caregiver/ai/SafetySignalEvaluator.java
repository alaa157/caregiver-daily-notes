package com.caregiver.ai;

import com.caregiver.common.NoteSignals;
import com.caregiver.common.Severity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic red-flag decision table over note signals.
 * Missing data yields absent signals, never positives.
 */
public class SafetySignalEvaluator {
  public static final int HIGH_PAIN_THRESHOLD = 7;
  public static final int MISSED_MED_COUNT_THRESHOLD = 2;
  public static final int POOR_APPETITE_DAYS_THRESHOLD = 3;
  public static final int DEFAULT_PERIOD_DAYS = 7;

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
    if (window.stream().anyMatch(n -> Boolean.TRUE.equals(n.fallReported()))) {
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
