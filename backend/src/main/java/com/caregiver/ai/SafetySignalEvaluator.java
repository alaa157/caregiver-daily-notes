package com.caregiver.ai;

import com.caregiver.common.NoteSignals;
import com.caregiver.common.Severity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Deterministic red-flag decision table over note signals.
 * Pure Java — no LLM, no Spring. Missing data yields absent signals, never positives.
 *
 * The structured flags answer the checkboxes; the free text answers what
 * the caregiver actually wrote. A checkbox can sit unticked while the note
 * says "she fell down today", and a real fall must still raise the banner.
 */
public class SafetySignalEvaluator {

  /** Pain score at or above this triggers {@code HIGH_PAIN}. Scores 0-6 never trigger. */
  public static final int HIGH_PAIN_THRESHOLD = 7;

  /** Verified missed-medication distinct dates at or above this triggers {@code REPEATED_MISSED_MED}. */
  public static final int MISSED_MED_COUNT_THRESHOLD = 2;

  /**
   * Poor-appetite CONSECUTIVE-day run at or above this triggers
   * {@code POOR_APPETITE_DAYS}. The README contract word is "streaks":
   * three bad days scattered across a 30-day window is recurrence, not a
   * streak, and must not fire the banner.
   */
  public static final int POOR_APPETITE_DAYS_THRESHOLD = 3;

  /** Default evaluation window length in days. */
  public static final int DEFAULT_PERIOD_DAYS = 7;

  // Text rules below run on ArabicNormalizer-folded text, so the Arabic
  // side is written post-normalization ("ألم" becomes "الم"), and Arabic
  // word boundaries are unreliable — these are substring stems with
  // co-occurrence or English word-lookahead keeping false positives rare.
  // A stray positive costs one look; a missed real fall breaks trust.
  static final Pattern FALL_TEXT = Pattern.compile("fell(?! asleep)|سقط|سقوط");
  static final Pattern CHEST_PAIN_TEXT = Pattern.compile("chest pain|chest hurts");
  static final Pattern ARABIC_PAIN = Pattern.compile("الم");
  static final Pattern ARABIC_CHEST = Pattern.compile("صدر");

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

    // One normalizer fold per note; every text rule matches against it.
    List<String> foldedTexts = window.stream()
        .map(n -> n == null ? "" : ArabicNormalizer.normalize(n.freeText()))
        .toList();
    boolean textDescribesFall = foldedTexts.stream().anyMatch(t -> FALL_TEXT.matcher(t).find());
    boolean textDescribesChestPain = foldedTexts.stream().anyMatch(
        t -> CHEST_PAIN_TEXT.matcher(t).find()
            || (ARABIC_PAIN.matcher(t).find() && ARABIC_CHEST.matcher(t).find()));

    List<SafetySignal> signals = new ArrayList<>();

    if (window.stream().anyMatch(NoteSignals::fallReported)) {
      signals.add(new SafetySignal(
          SafetyRuleId.FALL_DETECTED, Severity.HIGH, "Fall reported in period"));
    } else if (textDescribesFall) {
      signals.add(new SafetySignal(
          SafetyRuleId.FALL_DETECTED, Severity.HIGH, "Note text describes a fall"));
    }

    Integer maxPain = window.stream()
        .map(NoteSignals::painScore)
        .filter(p -> p != null && p >= HIGH_PAIN_THRESHOLD)
        .max(Integer::compareTo)
        .orElse(null);
    if (maxPain != null) {
      signals.add(new SafetySignal(
          SafetyRuleId.HIGH_PAIN, Severity.HIGH, "Max pain " + maxPain + " in period"));
    } else if (textDescribesChestPain) {
      signals.add(new SafetySignal(
          SafetyRuleId.HIGH_PAIN, Severity.HIGH, "Note text describes chest pain"));
    }

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

    TreeSet<LocalDate> appetiteDates = window.stream()
        .filter(n -> n != null && Boolean.TRUE.equals(n.poorAppetite()))
        .map(NoteSignals::date)
        .collect(Collectors.toCollection(TreeSet::new));
    int appetiteStreak = longestConsecutiveRun(appetiteDates);
    if (appetiteStreak >= POOR_APPETITE_DAYS_THRESHOLD) {
      signals.add(new SafetySignal(
          SafetyRuleId.POOR_APPETITE_DAYS, Severity.WATCH,
          "Poor appetite for " + appetiteStreak + " consecutive days in period"));
    }

    boolean needsDoctorBanner = signals.stream().anyMatch(s -> s.severity() == Severity.HIGH);
    return new SafetyEvaluation(signals, needsDoctorBanner);
  }

  /**
   * The longest run of adjacent calendar dates in a sorted, duplicated-free
   * set — the README's "streak". Scattered days reset the run.
   */
  static int longestConsecutiveRun(TreeSet<LocalDate> dates) {
    int longest = 0;
    int run = 0;
    LocalDate prev = null;
    for (LocalDate date : dates) {
      run = (prev != null && prev.plusDays(1).equals(date)) ? run + 1 : 1;
      longest = Math.max(longest, run);
      prev = date;
    }
    return longest;
  }
}
