package com.caregiver.ai;

import java.util.List;

/**
 * One summarize call: the validated model summary plus the authoritative
 * code-computed safety evaluation, trends and deterministic coverage. Model
 * text never alters the safety or coverage result.
 */
public record SummaryResult(GroundedSummary summary, SafetyEvaluation safety, List<TrendResult> trends,
                            int daysWithNotes, int periodDays, int truncatedNoteCount) {

  public SummaryResult {
    if (summary == null) {
      throw new IllegalArgumentException("summary must be non-null");
    }
    if (safety == null) {
      safety = new SafetyEvaluation(List.of(), false);
    }
    trends = trends == null ? List.of() : List.copyOf(trends);
  }

  /** Convenience for callers that have no deterministic coverage to report. */
  public SummaryResult(GroundedSummary summary, SafetyEvaluation safety, List<TrendResult> trends) {
    this(summary, safety, trends, 0, 0, 0);
  }

  public SummaryResult(GroundedSummary summary, SafetyEvaluation safety, List<TrendResult> trends,
      int daysWithNotes, int periodDays) {
    this(summary, safety, trends, daysWithNotes, periodDays, 0);
  }
}
