package com.caregiver.ai;

import com.caregiver.plans.StoredPlanVersion;

/**
 * Outcome of one suggestion call: the persisted version, or an explicit
 * unavailable result with no stored plan when the provider failed.
 */
public record PlanSuggestionResult(StoredPlanVersion stored, boolean aiUnavailable) {

  public PlanSuggestionResult {
    if (stored == null && !aiUnavailable) {
      throw new IllegalArgumentException("a stored plan is required unless aiUnavailable");
    }
  }
}
