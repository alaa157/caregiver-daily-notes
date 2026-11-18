package com.caregiver.ai;

import com.caregiver.common.NoteSignals;
import com.caregiver.common.ObservationState;
import com.caregiver.common.Severity;
import com.caregiver.common.VerificationState;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;

/**
 * Deterministic red-flag decision table over note signals.
 * Missing data yields absent signals, never positives.
 */
// capstone baseline, not clinically validated
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

    if (window.stream().anyMatch(SafetySignalEvaluator::medicationNeedsReview)) {
      signals.add(new SafetySignal(
          SafetyRuleId.MEDICATION_STATUS_UNCLEAR, Severity.WATCH,
          "Medication status unclear or unverified in period"));
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

    window.stream()
        .map(NoteSignals::freeText)
        .map(SafetySignalEvaluator::freeTextConcerns)
        .flatMap(List::stream)
        .distinct()
        .forEach(topic -> signals.add(new SafetySignal(
            SafetyRuleId.FREE_TEXT_CONCERN, Severity.WATCH,
            "Possible " + topic + " mentioned in note text — please review")));

    if (window.stream().map(NoteSignals::mood).filter(Objects::nonNull)
        .anyMatch(mood -> mood.toLowerCase(Locale.ROOT).contains("confus"))) {
      signals.add(new SafetySignal(
          SafetyRuleId.CONFUSED_MOOD, Severity.WATCH,
          "Confused mood recorded in period — please review"));
    }

    boolean needsDoctorBanner = signals.stream().anyMatch(s -> s.severity() == Severity.HIGH);
    return new SafetyEvaluation(signals, needsDoctorBanner);
  }

  private static final Map<String, List<String>> FREE_TEXT_KEYWORDS = Map.of(
      "fall", List.of("fell", "fall", "fallen", "سقط", "سقوط", "وقعت"),
      "chest pain", List.of("chest pain", "chest pains", "pain in his chest", "pain in her chest",
          "pain in their chest", "crushing chest", "ألم في الصدر", "وجع في الصدر"),
      "breathing difficulty", List.of("can't breathe", "cannot breathe", "couldn't breathe",
          "difficulty breathing", "shortness of breath", "gasping", "choking",
          "صعوبة في التنفس", "لا أستطيع التنفس", "لا يستطيع التنفس", "لا تستطيع التنفس",
          "ضيق التنفس", "يختنق", "تختنق"),
      "unresponsiveness", List.of("unresponsive", "unconscious", "not responding", "passed out",
          "collapsed", "fainted", "لا يستجيب", "لا تستجيب", "فاقد الوعي", "فقدان الوعي",
          "إغماء", "اغماء", "انهار", "انهارت"),
      "bleeding", List.of("bleeding", "bled", "blood loss", "hemorrhage", "نزيف", "ينزف", "تنزف", "فقدان دم", "فقد دم"),
      "confusion", List.of("confused", "confusion", "disoriented", "doesn't know where",
          "does not know where", "مرتبك", "تشوش", "لا يعرف أين", "لا تعرف أين", "هذيان"));

  private static List<String> freeTextConcerns(String freeText) {
    if (freeText == null || freeText.isBlank()) return List.of();
    String text = freeText.toLowerCase(Locale.ROOT);
    return FREE_TEXT_KEYWORDS.entrySet().stream()
        .filter(entry -> entry.getValue().stream().anyMatch(text::contains))
        .map(Map.Entry::getKey)
        .toList();
  }

  private static boolean medicationNeedsReview(NoteSignals note) {
    return note.medicationUnverified()
        || "UNKNOWN".equals(note.medicationStatus())
        || note.observations().medicationStatus().state() == ObservationState.UNKNOWN
        || note.observations().medicationVerification() == VerificationState.UNVERIFIED;
  }
}
