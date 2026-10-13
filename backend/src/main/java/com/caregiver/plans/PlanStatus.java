package com.caregiver.plans;

/** Persisted lifecycle states for immutable care-plan versions. */
public enum PlanStatus {
  SUGGESTED,
  ACCEPTED,
  EDITED_AND_ACCEPTED,
  DISMISSED,
  ARCHIVED;

  public boolean isActiveAccepted() {
    return this == ACCEPTED || this == EDITED_AND_ACCEPTED;
  }
}
