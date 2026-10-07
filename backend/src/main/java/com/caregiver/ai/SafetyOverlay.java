package com.caregiver.ai;

import com.caregiver.common.Severity;

import java.util.ArrayList;
import java.util.List;

/**
 * Escalation-only merge of deterministic flags with LLM suggestions.
 * The model may escalate a severity ({@code WATCH→HIGH}) and may raise the
 * doctor banner — it can never de-escalate, clear the banner, or add flags
 * the deterministic evaluation did not fire. Severity order is
 * {@code INFO < WATCH < HIGH} (enum ordinal).
 */
public class SafetyOverlay {

  /**
   * Merges LLM suggestions into the deterministic evaluation without ever
   * downgrading it. Null {@code deterministic} throws; a null suggestion or
   * one with no severities and no banner opinion returns {@code deterministic}
   * unchanged.
   */
  public SafetyEvaluation merge(SafetyEvaluation deterministic, AiSuggestion suggestion) {
    if (deterministic == null) {
      throw new IllegalArgumentException("deterministic");
    }
    if (suggestion == null
        || (suggestion.suggestedSeverities().isEmpty() && suggestion.suggestsDoctorBanner() == null)) {
      return deterministic;
    }

    List<SafetySignal> merged = new ArrayList<>();
    for (SafetySignal signal : deterministic.signals()) {
      Severity suggested = suggestion.suggestedSeverities().get(signal.ruleId());
      if (suggested != null && suggested.ordinal() > signal.severity().ordinal()) {
        merged.add(new SafetySignal(signal.ruleId(), suggested, signal.reason()));
      } else {
        merged.add(signal);
      }
    }

    boolean banner = deterministic.needsDoctorBanner()
        || merged.stream().anyMatch(s -> s.severity() == Severity.HIGH)
        || Boolean.TRUE.equals(suggestion.suggestsDoctorBanner());
    return new SafetyEvaluation(merged, banner);
  }
}
