package com.caregiver.ai;

import com.caregiver.common.Severity;

import java.util.Objects;

/**
 * One deterministic safety flag: which rule fired, at what severity, and why.
 */
public record SafetySignal(SafetyRuleId ruleId, Severity severity, String reason) {

  public SafetySignal {
    Objects.requireNonNull(ruleId, "ruleId");
    Objects.requireNonNull(severity, "severity");
  }
}
