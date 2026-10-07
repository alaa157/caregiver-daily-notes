package com.caregiver.ai;

import com.caregiver.plans.InMemorySuggestedPlanStore;
import com.caregiver.plans.MedicationEntry;
import com.caregiver.plans.PlanAction;
import com.caregiver.plans.StoredPlanVersion;
import com.caregiver.plans.SuggestedPlan;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanSuggestionServiceTest {

  static GroundedSummary emptySummary() {
    return new GroundedSummary(List.of(), List.of(), false);
  }

  static SafetyEvaluation emptySafety() {
    return new SafetyEvaluation(List.of(), false);
  }

  static MedicationEntry med(String name, String dose, String schedule) {
    return new MedicationEntry(name, dose, schedule);
  }

  static PlanSuggestionRequest request(
      Map<String, String> notes, StoredPlanVersion lastAccepted, List<MedicationEntry> meds) {
    return new PlanSuggestionRequest(
        "r1", emptySummary(), emptySafety(), notes, lastAccepted, meds);
  }

  static PlanSuggestionService service(InMemorySuggestedPlanStore store, LlmResult... script) {
    return new PlanSuggestionService(
        new FakeLlmClient(new ArrayDeque<>(List.of(script))), store);
  }

  static String changeJson(String actionId, String params, String reason, String evidence) {
    return "{\"actionId\":\"" + actionId + "\",\"params\":{" + params + "},\"reason\":\"" + reason
        + "\",\"evidenceNoteIds\":[" + evidence + "]}";
  }

  static String suggestionJson(String changes, String meds) {
    return "{\"changes\":[" + changes + "],\"proposedMedications\":[" + meds + "]}";
  }

  static String medJson(String name, String dose, String schedule) {
    return "{\"name\":\"" + name + "\",\"dose\":\"" + dose + "\",\"schedule\":\"" + schedule + "\"}";
  }

  @Test
  void validSuggestion_persistsVersionedPlan() {
    var store = new InMemorySuggestedPlanStore();
    var meds = List.of(med("Aspirin", "5mg", "daily"));
    var svc = service(store, new LlmResult(LlmStatus.OK,
        suggestionJson(
            changeJson("SCHEDULE_CHECK_IN", "\"withinDays\":\"3\"", "check soon", "\"n1\""),
            medJson("Aspirin", "5mg", "daily")),
        null));

    var result = svc.suggest(request(Map.of("n1", "ate well"), null, meds));

    assertThat(result.aiUnavailable()).isFalse();
    assertThat(result.stored().versionId()).isEqualTo("v1");
    assertThat(store.saves()).isEqualTo(1);
    assertThat(result.stored().plan().changes()).hasSize(1);
    assertThat(result.stored().plan().changes().get(0).action()).isEqualTo(PlanAction.SCHEDULE_CHECK_IN);
  }

  @Test
  void inventedAction_rejectedBeforeStore() {
    var store = new InMemorySuggestedPlanStore();
    var svc = service(store, new LlmResult(LlmStatus.OK,
        suggestionJson(
            changeJson("PRESCRIBE_ANTIBIOTICS", "\"drug\":\"x\"", "treat it", "\"n1\""), ""), null));

    assertThatThrownBy(() -> svc.suggest(request(Map.of("n1", "ate well"), null, List.of())))
        .isInstanceOf(InvalidModelOutputException.class);
    assertThat(store.saves()).isEqualTo(0);
  }

  @Test
  void unsupportedTopLevelField_rejectedBeforeStore() {
    var store = new InMemorySuggestedPlanStore();
    var svc = service(store, new LlmResult(LlmStatus.OK,
        "{\"changes\":[],\"proposedMedications\":[],\"diagnosis\":\"flu\"}", null));

    assertThatThrownBy(() -> svc.suggest(request(Map.of("n1", "ate well"), null, List.of())))
        .isInstanceOf(InvalidModelOutputException.class);
    assertThat(store.saves()).isEqualTo(0);
  }

  @Test
  void medicationDoseChange_rejectedBeforeStore() {
    var store = new InMemorySuggestedPlanStore();
    var svc = service(store, new LlmResult(LlmStatus.OK,
        suggestionJson(
            changeJson("SCHEDULE_CHECK_IN", "\"withinDays\":\"3\"", "check soon", "\"n1\""),
            medJson("Aspirin", "10mg", "daily")),
        null));

    assertThatThrownBy(() -> svc.suggest(
        request(Map.of("n1", "ate well"), null, List.of(med("Aspirin", "5mg", "daily")))))
        .isInstanceOf(InvalidModelOutputException.class);
    assertThat(store.saves()).isEqualTo(0);
  }

  @Test
  void fallbackResult_storesNothing() {
    var store = new InMemorySuggestedPlanStore();
    var svc = service(store, new LlmResult(LlmStatus.FALLBACK, "", "AI_UNAVAILABLE: HTTP 500"));

    var result = svc.suggest(request(Map.of("n1", "ate well"), null, List.of()));

    assertThat(result.aiUnavailable()).isTrue();
    assertThat(result.stored()).isNull();
    assertThat(store.saves()).isEqualTo(0);
  }

  @Test
  void lastAcceptedPlan_includedAsContext() {
    var seen = new ArrayDeque<LlmRequest>();
    LlmClient recording = req -> {
      seen.add(req);
      return new LlmResult(LlmStatus.OK,
          suggestionJson(
              changeJson("SCHEDULE_CHECK_IN", "\"withinDays\":\"3\"", "check soon", "\"n1\""), ""), null);
    };
    var store = new InMemorySuggestedPlanStore();
    var svc = new PlanSuggestionService(recording, store);
    var lastAccepted = new StoredPlanVersion("v1", new SuggestedPlan(List.of(), List.of(), List.of(), null));

    var result = svc.suggest(request(Map.of("n1", "ate well"), lastAccepted, List.of()));

    assertThat(seen).hasSize(1);
    assertThat(seen.poll().dataBlock()).contains("v1");
    assertThat(result.stored().plan().basedOnPlanId()).isEqualTo("v1");
    assertThat(store.saves()).isEqualTo(1);
  }

  @Test
  void emptyEvidence_rejectedBeforeStore() {
    var store = new InMemorySuggestedPlanStore();
    var svc = service(store, new LlmResult(LlmStatus.OK,
        "{\"changes\":[{\"actionId\":\"SCHEDULE_CHECK_IN\",\"params\":{\"withinDays\":\"3\"},\"reason\":\"check soon\",\"evidenceNoteIds\":[]}],\"proposedMedications\":[]}",
        null));

    assertThatThrownBy(() -> svc.suggest(request(Map.of("n1", "ate well"), null, List.of())))
        .isInstanceOf(InvalidModelOutputException.class);
    assertThat(store.saves()).isEqualTo(0);
  }
}
