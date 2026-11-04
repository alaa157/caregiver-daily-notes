package com.caregiver.ai;

import com.caregiver.common.NoteSignals;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Backend-owned trend computation over structured note signals; no LLM calls. */
public class TrendCalculator {
  public TrendResult computeSingle(
      String metric, int previousCount, int currentCount, int previousSampleSize, int currentSampleSize) {
    return new TrendResult(
        metric, previousCount, currentCount, currentCount - previousCount, previousSampleSize, currentSampleSize);
  }

  public List<TrendResult> computeTrends(List<NoteSignals> currentPeriod, List<NoteSignals> previousPeriod) {
    return List.of(
        compare("FALL_COUNT", countFalls(previousPeriod), countFalls(currentPeriod)),
        compare("HIGH_PAIN_COUNT", countHighPain(previousPeriod), countHighPain(currentPeriod)),
        compare("MISSED_MED_COUNT", countVerifiedMissed(previousPeriod), countVerifiedMissed(currentPeriod)),
        compare("POOR_APPETITE_COUNT", countPoorAppetite(previousPeriod), countPoorAppetite(currentPeriod)));
  }

  private TrendResult compare(String metric, MetricCounts previous, MetricCounts current) {
    return computeSingle(metric, previous.events(), current.events(), previous.sampleSize(), current.sampleSize());
  }

  private MetricCounts countFalls(List<NoteSignals> notes) {
    return count(
        notes,
        note -> note.fallReported() != null,
        note -> Boolean.TRUE.equals(note.fallReported()));
  }

  private MetricCounts countHighPain(List<NoteSignals> notes) {
    return count(
        notes,
        note -> note.painScore() != null,
        note -> note.painScore() >= SafetySignalEvaluator.HIGH_PAIN_THRESHOLD);
  }

  private MetricCounts countVerifiedMissed(List<NoteSignals> notes) {
    return countDistinctDates(
        notes,
        note -> note.missedMedication() != null && !note.medicationUnverified(),
        note -> Boolean.TRUE.equals(note.missedMedication()));
  }

  private MetricCounts countPoorAppetite(List<NoteSignals> notes) {
    return countDistinctDates(
        notes,
        note -> note.poorAppetite() != null,
        note -> Boolean.TRUE.equals(note.poorAppetite()));
  }

  private MetricCounts count(
      List<NoteSignals> notes, Predicate<NoteSignals> recorded, Predicate<NoteSignals> event) {
    if (notes == null) return new MetricCounts(0, 0);
    int events = 0;
    int sampleSize = 0;
    for (NoteSignals note : notes) {
      if (note != null && recorded.test(note)) {
        sampleSize++;
        if (event.test(note)) events++;
      }
    }
    return new MetricCounts(events, sampleSize);
  }

  private MetricCounts countDistinctDates(
      List<NoteSignals> notes, Predicate<NoteSignals> recorded, Predicate<NoteSignals> event) {
    if (notes == null) return new MetricCounts(0, 0);
    Set<LocalDate> sampledDates = new HashSet<>();
    Set<LocalDate> eventDates = new HashSet<>();
    for (NoteSignals note : notes) {
      if (note != null && recorded.test(note)) {
        sampledDates.add(note.date());
        if (event.test(note)) eventDates.add(note.date());
      }
    }
    return new MetricCounts(eventDates.size(), sampledDates.size());
  }

  private record MetricCounts(int events, int sampleSize) {}
}
