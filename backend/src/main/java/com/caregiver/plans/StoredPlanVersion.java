package com.caregiver.plans;

import java.util.Objects;

/**
 * A persisted suggested-plan version. The store assigns the version id;
 * callers never choose it.
 */
public record StoredPlanVersion(String versionId, SuggestedPlan plan) {

  public StoredPlanVersion {
    if (versionId == null || versionId.isBlank()) {
      throw new IllegalArgumentException("versionId must be non-blank");
    }
    Objects.requireNonNull(plan, "plan");
  }
}
