package com.caregiver.plans;

import java.util.List;

/** Versioned catalog content with immutable medications. */
public record PlanDocument(int schemaVersion, List<SuggestedPlanChange> changes, List<MedicationEntry> medications) {
  public PlanDocument {
    if (schemaVersion != 1 || changes == null || medications == null || changes.size() > 20 || medications.size() > 100) throw new ProtectedPlanException();
    changes = List.copyOf(changes); medications = List.copyOf(medications);
    for (SuggestedPlanChange change : changes) {
      if (!change.params().keySet().equals(change.action().allowedParams())
          || change.reason().length() > 500
          || change.params().values().stream().anyMatch(value -> value.isBlank() || value.length() > 500)) throw new ProtectedPlanException();
      if (change.action() == PlanAction.SCHEDULE_CHECK_IN) {
        String days = change.params().get("withinDays");
        if (!days.matches("[0-9]{1,3}") || Integer.parseInt(days) < 1 || Integer.parseInt(days) > 365) throw new ProtectedPlanException();
      }
      if (change.action() == PlanAction.UPDATE_MEAL_REMINDER && !change.params().get("time").matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) throw new ProtectedPlanException();
    }
  }
}
