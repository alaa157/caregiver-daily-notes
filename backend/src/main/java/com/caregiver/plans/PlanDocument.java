package com.caregiver.plans;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Versioned catalog content with immutable medications. */
public record PlanDocument(int schemaVersion, List<SuggestedPlanChange> changes, List<MedicationEntry> medications) {
  /** Params carrying the scheduled value; the remaining params identify the target. */
  private static final Map<PlanAction, String> VALUE_PARAMS = Map.of(
      PlanAction.UPDATE_MEAL_REMINDER, "time",
      PlanAction.LOG_MOBILITY_GOAL, "frequency");

  public PlanDocument {
    if (schemaVersion != 1 || changes == null || medications == null || changes.isEmpty()
        || changes.size() > 20 || medications.size() > 100) throw new ProtectedPlanException();
    changes = List.copyOf(changes); medications = List.copyOf(medications);
    Set<String> exactChanges = new HashSet<>();
    Map<String, String> targets = new HashMap<>();
    for (SuggestedPlanChange change : changes) {
      if (!change.params().keySet().equals(change.action().allowedParams())
          || change.reason().length() > 500
          || change.params().values().stream().anyMatch(value -> value.isBlank() || value.length() > 500)) throw new ProtectedPlanException();
      if (change.action() == PlanAction.SCHEDULE_CHECK_IN) {
        String days = change.params().get("withinDays");
        if (!days.matches("[0-9]{1,3}") || Integer.parseInt(days) < 1 || Integer.parseInt(days) > 365) throw new ProtectedPlanException();
      }
      if (change.action() == PlanAction.UPDATE_MEAL_REMINDER && !change.params().get("time").matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) throw new ProtectedPlanException();
      String valueParam = VALUE_PARAMS.get(change.action());
      String targetKey = signature(change.action(), change.params(), valueParam);
      String value = valueParam == null ? "" : change.params().get(valueParam);
      String previousValue = targets.putIfAbsent(targetKey, value);
      if (previousValue != null && !previousValue.equals(value)) throw new ProtectedPlanException();
      if (!exactChanges.add(signature(change.action(), change.params(), null))) throw new ProtectedPlanException();
    }
  }

  private static String signature(PlanAction action, Map<String, String> params, String exclude) {
    StringBuilder key = new StringBuilder(action.name());
    params.entrySet().stream()
        .filter(entry -> !entry.getKey().equals(exclude))
        .sorted(Map.Entry.comparingByKey())
        .forEach(entry -> key.append(';').append(entry.getKey()).append('=').append(entry.getValue()));
    return key.toString();
  }
}
