package com.caregiver.ai;

import java.util.List;

/**
 * Aggregate harness metrics. Every rate is in {@code [0,1]}; empty
 * denominators score vacuous {@code 1.0} so excluded cases never drag a
 * metric (the dataset coverage test guards against an empty dataset).
 */
public record EvalMetrics(
    double redFlagRecall,
    double schemaValidRate,
    double groundednessRate,
    double medicationSafetyRate,
    int totalCases,
    List<String> failures) {

  public EvalMetrics {
    failures = failures == null ? List.of() : List.copyOf(failures);
  }
}
