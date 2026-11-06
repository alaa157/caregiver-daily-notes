package com.caregiver.ai;

import java.time.LocalDate;

/** Validated caregiver review metadata for a supported safety period. */
public record SafetyAcknowledgementRequest(
    SafetyRuleId ruleId,
    LocalDate periodEndInclusive,
    int periodDays) {

  public SafetyAcknowledgementRequest {
    if (ruleId == null) {
      throw new IllegalArgumentException("ruleId must be non-null");
    }
    if (periodEndInclusive == null) {
      throw new IllegalArgumentException("periodEndInclusive must be non-null");
    }
    if (!SummaryRequest.ALLOWED_PERIODS.contains(periodDays)) {
      throw new IllegalArgumentException("periodDays must be one of " + SummaryRequest.ALLOWED_PERIODS);
    }
  }
}
