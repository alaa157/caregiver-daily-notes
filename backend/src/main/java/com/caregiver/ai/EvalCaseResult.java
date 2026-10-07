package com.caregiver.ai;

import java.util.List;

/**
 * Per-case harness outcome: one boolean per metric plus the failure ids
 * (as {@code caseId:metric}) for the report.
 */
public record EvalCaseResult(
    String caseId, boolean flagsHit, boolean schemaValid, boolean grounded, boolean medsSafe,
    List<String> failures) {

  public EvalCaseResult {
    failures = failures == null ? List.of() : List.copyOf(failures);
  }
}
