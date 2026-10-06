package com.caregiver.ai;

import java.util.List;

/**
 * One summarize call: the validated model summary plus the authoritative
 * code-computed safety evaluation and backend trends. Model text never
 * alters the safety result.
 */
public record SummaryResult(GroundedSummary summary, SafetyEvaluation safety, List<TrendResult> trends) {

  public SummaryResult {
    if (summary == null) {
      throw new IllegalArgumentException("summary must be non-null");
    }
    if (safety == null) {
      safety = new SafetyEvaluation(List.of(), false);
    }
    trends = trends == null ? List.of() : List.copyOf(trends);
  }
}
