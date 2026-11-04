package com.caregiver.plans;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanCatalogTest {

  private static SuggestedPlanChange change(PlanAction action, Map<String, String> params) {
    return new SuggestedPlanChange(action, params, "review with caregiver", List.of("n1"));
  }

  private static PlanDocument document(PlanAction action, Map<String, String> params) {
    return new PlanDocument(1, List.of(change(action, params)), List.of());
  }

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
  void schemaVersion_oneIsAcceptedAndOtherVersionsAreRejected() {
    assertThat(new PlanDocument(1, List.of(), List.of()).schemaVersion()).isEqualTo(1);
    assertThatThrownBy(() -> new PlanDocument(0, List.of(), List.of())).isInstanceOf(ProtectedPlanException.class);
    assertThatThrownBy(() -> new PlanDocument(2, List.of(), List.of())).isInstanceOf(ProtectedPlanException.class);
  }

  @Test
  void everyCatalogActionRequiresExactlyItsDeclaredParameters() {
    Map<PlanAction, Map<String, String>> valid = Map.of(
        PlanAction.SCHEDULE_CHECK_IN, Map.of("withinDays", "1"),
        PlanAction.UPDATE_MEAL_REMINDER, Map.of("meal", "breakfast", "time", "09:00"),
        PlanAction.LOG_MOBILITY_GOAL, Map.of("activity", "walking", "frequency", "daily"),
        PlanAction.CAREGIVER_EDUCATION_TOPIC, Map.of("topic", "hydration"),
        PlanAction.ESCALATE_TO_DOCTOR, Map.of("reason", "new concern"));

    assertThat(valid.keySet()).containsExactlyInAnyOrder(PlanAction.values());
    for (PlanAction action : PlanAction.values()) {
      Map<String, String> params = valid.get(action);
      assertThat(document(action, params).changes()).hasSize(1);

      Map<String, String> missing = new HashMap<>(params);
      missing.remove(action.allowedParams().iterator().next());
      assertThatThrownBy(() -> document(action, missing)).isInstanceOf(ProtectedPlanException.class);

      Map<String, String> unexpected = new HashMap<>(params);
      unexpected.put("unexpected", "value");
      assertThatThrownBy(() -> document(action, unexpected)).isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void checkInDaysAcceptOnlyOneThrough365WithoutNumericNormalization() {
    for (String invalid : List.of("0", "366", "1.5", "1.9", "abc", "+1")) {
      assertThatThrownBy(() -> document(PlanAction.SCHEDULE_CHECK_IN, Map.of("withinDays", invalid)))
          .as("withinDays=%s", invalid).isInstanceOf(ProtectedPlanException.class);
    }
    assertThat(document(PlanAction.SCHEDULE_CHECK_IN, Map.of("withinDays", "1"))).isNotNull();
    assertThat(document(PlanAction.SCHEDULE_CHECK_IN, Map.of("withinDays", "365"))).isNotNull();
  }

  @Test
  void mealReminderRequiresStrict24HourTime() {
    for (String validTime : List.of("00:00", "23:59")) {
      assertThat(document(PlanAction.UPDATE_MEAL_REMINDER, Map.of("meal", "breakfast", "time", validTime)))
          .isNotNull();
    }
    for (String invalidTime : List.of("24:00", "9:00", "", "09:", "aa:00", "12:60")) {
      assertThatThrownBy(() -> document(PlanAction.UPDATE_MEAL_REMINDER,
          Map.of("meal", "breakfast", "time", invalidTime)))
          .as("time=%s", invalidTime).isInstanceOf(ProtectedPlanException.class);
    }
  }

  @Test
  void actionTextRejectsBlankValuesAndReason() {
    List<Map.Entry<PlanAction, Map<String, String>>> blankParams = List.of(
        Map.entry(PlanAction.UPDATE_MEAL_REMINDER, Map.of("meal", "", "time", "09:00")),
        Map.entry(PlanAction.LOG_MOBILITY_GOAL, Map.of("activity", "  ", "frequency", "daily")),
        Map.entry(PlanAction.LOG_MOBILITY_GOAL, Map.of("activity", "walking", "frequency", " ")),
        Map.entry(PlanAction.CAREGIVER_EDUCATION_TOPIC, Map.of("topic", "\t")),
        Map.entry(PlanAction.ESCALATE_TO_DOCTOR, Map.of("reason", " ")));
    for (var entry : blankParams) {
      assertThatThrownBy(() -> document(entry.getKey(), entry.getValue()))
          .isInstanceOf(ProtectedPlanException.class);
    }
    assertThatThrownBy(() -> new SuggestedPlanChange(
        PlanAction.SCHEDULE_CHECK_IN, Map.of("withinDays", "1"), "  ", List.of("n1")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void actionTextLongerThan500CharactersIsRejected() {
    String tooLong = "x".repeat(501);
    List<Map.Entry<PlanAction, Map<String, String>>> longParams = List.of(
        Map.entry(PlanAction.UPDATE_MEAL_REMINDER, Map.of("meal", tooLong, "time", "09:00")),
        Map.entry(PlanAction.LOG_MOBILITY_GOAL, Map.of("activity", tooLong, "frequency", "daily")),
        Map.entry(PlanAction.LOG_MOBILITY_GOAL, Map.of("activity", "walking", "frequency", tooLong)),
        Map.entry(PlanAction.CAREGIVER_EDUCATION_TOPIC, Map.of("topic", tooLong)),
        Map.entry(PlanAction.ESCALATE_TO_DOCTOR, Map.of("reason", tooLong)));
    for (var entry : longParams) {
      assertThatThrownBy(() -> document(entry.getKey(), entry.getValue()))
          .isInstanceOf(ProtectedPlanException.class);
    }
    assertThatThrownBy(() -> new PlanDocument(1,
        List.of(new SuggestedPlanChange(PlanAction.SCHEDULE_CHECK_IN, Map.of("withinDays", "1"),
            "x".repeat(501), List.of("n1"))), List.of()))
        .isInstanceOf(ProtectedPlanException.class);
  }

  @Test
  void actionTextAt500CharactersIsAccepted() {
    String boundary = "x".repeat(500);
    assertThat(document(PlanAction.UPDATE_MEAL_REMINDER, Map.of("meal", boundary, "time", "00:00"))).isNotNull();
    assertThat(document(PlanAction.LOG_MOBILITY_GOAL,
        Map.of("activity", boundary, "frequency", boundary))).isNotNull();
    assertThat(document(PlanAction.CAREGIVER_EDUCATION_TOPIC, Map.of("topic", boundary))).isNotNull();
    assertThat(document(PlanAction.ESCALATE_TO_DOCTOR, Map.of("reason", boundary))).isNotNull();
    assertThat(new PlanDocument(1,
        List.of(new SuggestedPlanChange(PlanAction.SCHEDULE_CHECK_IN, Map.of("withinDays", "1"),
            boundary, List.of("n1"))), List.of())).isNotNull();
  }

  @Test
  void preservesValidArabicAndUnicodeActionText() {
    String activity = "المشي اليومي — café 🚶";
    var doc = document(PlanAction.LOG_MOBILITY_GOAL, Map.of("activity", activity, "frequency", "كل يوم"));
    assertThat(doc.changes().get(0).params().get("activity")).isEqualTo(activity);
    assertThat(doc.changes().get(0).params().get("frequency")).isEqualTo("كل يوم");
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
