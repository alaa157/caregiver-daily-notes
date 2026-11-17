package com.caregiver.plans;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanDocumentTest {

  private static SuggestedPlanChange change(PlanAction action, Map<String, String> params) {
    return new SuggestedPlanChange(action, params, "review with caregiver", List.of("n1"));
  }

  private static PlanDocument document(SuggestedPlanChange... changes) {
    return new PlanDocument(1, List.of(changes), List.of());
  }

  @Test
  void emptyPlanIsRejected() {
    assertThatThrownBy(() -> new PlanDocument(1, List.of(), List.of()))
        .isInstanceOf(ProtectedPlanException.class);
  }

  @Test
  void singleChangePlanIsAccepted() {
    PlanDocument document = document(
        change(PlanAction.SCHEDULE_CHECK_IN, Map.of("withinDays", "7")));
    assertThat(document.changes()).hasSize(1);
  }

  @Test
  void identicalActionAndTargetChangesAreRejected() {
    SuggestedPlanChange duplicate = change(PlanAction.UPDATE_MEAL_REMINDER,
        Map.of("meal", "breakfast", "time", "08:00"));
    assertThatThrownBy(() -> document(duplicate, duplicate))
        .isInstanceOf(ProtectedPlanException.class);
  }

  @Test
  void conflictingMealReminderTimesAreRejected() {
    assertThatThrownBy(() -> document(
        change(PlanAction.UPDATE_MEAL_REMINDER, Map.of("meal", "breakfast", "time", "08:00")),
        change(PlanAction.UPDATE_MEAL_REMINDER, Map.of("meal", "breakfast", "time", "09:00"))))
        .isInstanceOf(ProtectedPlanException.class);
  }

  @Test
  void conflictingMobilityFrequenciesAreRejected() {
    assertThatThrownBy(() -> document(
        change(PlanAction.LOG_MOBILITY_GOAL, Map.of("activity", "walking", "frequency", "daily")),
        change(PlanAction.LOG_MOBILITY_GOAL, Map.of("activity", "walking", "frequency", "weekly"))))
        .isInstanceOf(ProtectedPlanException.class);
  }

  @Test
  void distinctTargetsAndActionsAreAccepted() {
    PlanDocument document = document(
        change(PlanAction.UPDATE_MEAL_REMINDER, Map.of("meal", "breakfast", "time", "08:00")),
        change(PlanAction.UPDATE_MEAL_REMINDER, Map.of("meal", "lunch", "time", "08:00")),
        change(PlanAction.LOG_MOBILITY_GOAL, Map.of("activity", "walking", "frequency", "daily")),
        change(PlanAction.CAREGIVER_EDUCATION_TOPIC, Map.of("topic", "hydration")),
        change(PlanAction.CAREGIVER_EDUCATION_TOPIC, Map.of("topic", "sleep")));
    assertThat(document.changes()).hasSize(5);
  }
}
