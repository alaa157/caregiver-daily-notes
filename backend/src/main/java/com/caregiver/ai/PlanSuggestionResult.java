package com.caregiver.ai;

import com.caregiver.plans.StoredPlanVersion;

/**
 * Outcome of one suggestion call: the persisted version, or an explicit
 * unavailable result with no stored plan when the provider failed.
 * {@code removedUnsafeChanges} counts changes dropped by the text safety
 * filter while the remaining changes were still persisted.
 */
public record PlanSuggestionResult(
    StoredPlanVersion stored, boolean aiUnavailable, int removedUnsafeChanges) {

  public PlanSuggestionResult(StoredPlanVersion stored, boolean aiUnavailable) {
    this(stored, aiUnavailable, 0);
  }

  public PlanSuggestionResult {
    if (stored == null && !aiUnavailable) {
      throw new IllegalArgumentException("a stored plan is required unless aiUnavailable");
    }
    if (removedUnsafeChanges < 0) {
      throw new IllegalArgumentException("removedUnsafeChanges must not be negative");
    }
  }
}
