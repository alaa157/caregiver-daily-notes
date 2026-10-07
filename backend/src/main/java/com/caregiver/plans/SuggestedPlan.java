package com.caregiver.plans;

import java.util.List;

/**
 * A proposed plan version: catalog changes plus the current and proposed
 * medication sections for hard-lock comparison. {@code basedOnPlanId} links
 * to the predecessor version, or null for a first plan.
 */
public record SuggestedPlan(
    List<SuggestedPlanChange> changes,
    List<MedicationEntry> currentMedications,
    List<MedicationEntry> proposedMedications,
    String basedOnPlanId) {

  public SuggestedPlan {
    changes = changes == null ? List.of() : List.copyOf(changes);
    currentMedications = currentMedications == null ? List.of() : List.copyOf(currentMedications);
    proposedMedications = proposedMedications == null ? List.of() : List.copyOf(proposedMedications);
  }
}
