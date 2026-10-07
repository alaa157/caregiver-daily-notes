package com.caregiver.plans;

import java.util.Set;

/**
 * Closed catalog of suggestible care-plan actions. The model may only select
 * these actions with their exact parameter names — anything else is rejected.
 * The catalog carries no clinical content: no diagnoses, no prescriptions.
 */
public enum PlanAction {
  SCHEDULE_CHECK_IN(Set.of("withinDays")),
  UPDATE_MEAL_REMINDER(Set.of("meal", "time")),
  LOG_MOBILITY_GOAL(Set.of("activity", "frequency")),
  CAREGIVER_EDUCATION_TOPIC(Set.of("topic")),
  ESCALATE_TO_DOCTOR(Set.of("reason"));

  private final Set<String> allowedParams;

  PlanAction(Set<String> allowedParams) {
    this.allowedParams = allowedParams;
  }

  /**
   * Stable wire id — the enum name. Never rename without a migration.
   */
  public String getId() {
    return name();
  }

  public Set<String> allowedParams() {
    return allowedParams;
  }
}
