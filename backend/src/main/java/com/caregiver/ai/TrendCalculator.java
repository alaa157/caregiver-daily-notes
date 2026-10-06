package com.caregiver.ai;

import com.caregiver.common.NoteSignals;

import java.util.List;

/**
 * Backend-owned trend computation over stubbed note signals.
 * Pure Java — zero LLM calls. Counting mirrors {@link SafetySignalEvaluator}:
 * verified meds only, pain at/above threshold, distinct dates for meds/appetite,
 * raw event counts for falls and high-pain entries.
 */
public class TrendCalculator {

  /**
   * Builds one trend result with {@code delta = currentCount - previousCount}.
   */
  public TrendResult computeSingle(String metric, int previousCount, int currentCount) {
    return new TrendResult(metric, previousCount, currentCount, currentCount - previousCount);
  }

  /**
   * Compares two periods, returning four metrics in fixed order:
   * {@code FALL_COUNT}, {@code HIGH_PAIN_COUNT}, {@code MISSED_MED_COUNT},
   * {@code POOR_APPETITE_COUNT}. Null periods count as zeros.
   */
  public List<TrendResult> computeTrends(List<NoteSignals> currentPeriod, List<NoteSignals> previousPeriod) {
    return List.of(
        computeSingle("FALL_COUNT", countFalls(previousPeriod), countFalls(currentPeriod)),
        computeSingle("HIGH_PAIN_COUNT", countHighPain(previousPeriod), countHighPain(currentPeriod)),
        computeSingle("MISSED_MED_COUNT", countVerifiedMissed(previousPeriod), countVerifiedMissed(currentPeriod)),
        computeSingle("POOR_APPETITE_COUNT", countPoorAppetite(previousPeriod), countPoorAppetite(currentPeriod)));
  }

  private int countFalls(List<NoteSignals> notes) {
    if (notes == null) {
      return 0;
    }
    return (int) notes.stream().filter(n -> n != null && n.fallReported()).count();
  }

  private int countHighPain(List<NoteSignals> notes) {
    if (notes == null) {
      return 0;
    }
    return (int) notes.stream()
        .filter(n -> n != null && n.painScore() != null
            && n.painScore() >= SafetySignalEvaluator.HIGH_PAIN_THRESHOLD)
        .count();
  }

  private int countVerifiedMissed(List<NoteSignals> notes) {
    if (notes == null) {
      return 0;
    }
    return (int) notes.stream()
        .filter(n -> n != null && Boolean.TRUE.equals(n.missedMedication()) && !n.medicationUnverified())
        .map(NoteSignals::date)
        .distinct()
        .count();
  }

  private int countPoorAppetite(List<NoteSignals> notes) {
    if (notes == null) {
      return 0;
    }
    return (int) notes.stream()
        .filter(n -> n != null && Boolean.TRUE.equals(n.poorAppetite()))
        .map(NoteSignals::date)
        .distinct()
        .count();
  }
}
