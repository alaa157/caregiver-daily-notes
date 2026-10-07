package com.caregiver.plans;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanCatalogTest {

  @Test
  void catalog_containsExactlyFiveStableIds() {
    assertThat(PlanAction.values()).extracting(PlanAction::getId)
        .containsExactlyInAnyOrder(
            "SCHEDULE_CHECK_IN",
            "UPDATE_MEAL_REMINDER",
            "LOG_MOBILITY_GOAL",
            "CAREGIVER_EDUCATION_TOPIC",
            "ESCALATE_TO_DOCTOR");
    assertThat(PlanAction.SCHEDULE_CHECK_IN.allowedParams()).containsExactly("withinDays");
    assertThat(PlanAction.UPDATE_MEAL_REMINDER.allowedParams()).containsExactlyInAnyOrder("meal", "time");
    assertThat(PlanAction.LOG_MOBILITY_GOAL.allowedParams()).containsExactlyInAnyOrder("activity", "frequency");
    assertThat(PlanAction.CAREGIVER_EDUCATION_TOPIC.allowedParams()).containsExactly("topic");
    assertThat(PlanAction.ESCALATE_TO_DOCTOR.allowedParams()).containsExactly("reason");
  }

  @Test
  void change_rejectsUnknownParamKeys() {
    assertThatThrownBy(() -> new SuggestedPlanChange(
        PlanAction.SCHEDULE_CHECK_IN, Map.of("dosage", "5mg"), "check soon", List.of("n1")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void change_rejectsBlankReasonAndEmptyEvidence() {
    assertThatThrownBy(() -> new SuggestedPlanChange(
        PlanAction.SCHEDULE_CHECK_IN, Map.of("withinDays", "3"), "  ", List.of("n1")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SuggestedPlanChange(
        PlanAction.SCHEDULE_CHECK_IN, Map.of("withinDays", "3"), "check soon", List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void medicationEntry_rejectsBlanks() {
    assertThatThrownBy(() -> new MedicationEntry("", "5mg", "daily")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new MedicationEntry("Aspirin", null, "daily")).isInstanceOf(IllegalArgumentException.class);
    var entry = new MedicationEntry("Aspirin", "5mg", "daily");
    assertThat(entry.name()).isEqualTo("Aspirin");
  }
}
