package com.caregiver.ai;

import com.caregiver.common.Severity;

import java.util.Map;

/**
 * Minimal LLM-side suggestion stub for the overlay to merge against.
 * {@code suggestsDoctorBanner} is nullable — {@code null} means "no opinion".
 * A {@code null} severity map defaults to empty.
 */
public record AiSuggestion(Map<SafetyRuleId, Severity> suggestedSeverities, Boolean suggestsDoctorBanner) {

  public AiSuggestion {
    suggestedSeverities = suggestedSeverities == null ? Map.of() : Map.copyOf(suggestedSeverities);
  }
}
