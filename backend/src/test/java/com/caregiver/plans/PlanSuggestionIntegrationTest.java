package com.caregiver.plans;

import com.caregiver.ai.FakeLlmClient;
import com.caregiver.ai.GroundedSummary;
import com.caregiver.ai.InvalidModelOutputException;
import com.caregiver.ai.LlmResult;
import com.caregiver.ai.LlmStatus;
import com.caregiver.ai.PlanSuggestionRequest;
import com.caregiver.ai.PlanSuggestionService;
import com.caregiver.ai.SafetyEvaluation;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanSuggestionIntegrationTest {

  static PlanSuggestionService service(InMemorySuggestedPlanStore store, LlmResult... script) {
    return new PlanSuggestionService(
        new FakeLlmClient(new ArrayDeque<>(List.of(script))), store);
  }

  static PlanSuggestionRequest request(Map<String, String> notes, StoredPlanVersion lastAccepted) {
    return new PlanSuggestionRequest(
        "r1",
        new GroundedSummary(List.of(), List.of(), false),
        new SafetyEvaluation(List.of(), false),
        notes,
        lastAccepted,
        List.of(new MedicationEntry("Aspirin", "5mg", "daily")));
  }

  static String suggestionJson(String changes, String meds) {
    return "{\"changes\":[" + changes + "],\"proposedMedications\":[" + meds + "]}";
  }

  static final String CHANGE =
      "{\"actionId\":\"SCHEDULE_CHECK_IN\",\"params\":{\"withinDays\":\"3\"},\"reason\":\"check soon\",\"evidenceNoteIds\":[\"n1\"]}";
  static final String MEDS = "{\"name\":\"Aspirin\",\"dose\":\"5mg\",\"schedule\":\"daily\"}";

  @Test
  void suggestedVersion_linksToSummaryEvidence() {
    var store = new InMemorySuggestedPlanStore();
    var svc = service(store, new LlmResult(LlmStatus.OK, suggestionJson(CHANGE, MEDS), null));

    var result = svc.suggest(request(Map.of("n1", "ate well"), null));

    assertThat(result.stored().versionId()).isEqualTo("v1");
    assertThat(result.stored().plan().changes().get(0).evidenceNoteIds()).containsExactly("n1");
    assertThat(store.saved()).hasSize(1);
  }

  @Test
  void secondSuggestion_chainsToFirstVersion() {
    var store = new InMemorySuggestedPlanStore();
    var svc = service(store,
        new LlmResult(LlmStatus.OK, suggestionJson(CHANGE, MEDS), null),
        new LlmResult(LlmStatus.OK, suggestionJson(CHANGE, MEDS), null));

    var first = svc.suggest(request(Map.of("n1", "ate well"), null));
    var second = svc.suggest(request(Map.of("n1", "ate well"), first.stored()));

    assertThat(second.stored().versionId()).isEqualTo("v2");
    assertThat(second.stored().plan().basedOnPlanId()).isEqualTo("v1");
    assertThat(store.saves()).isEqualTo(2);
  }

  @Test
  void rejectedSuggestion_leavesNoVersion() {
    var store = new InMemorySuggestedPlanStore();
    var svc = service(store,
        new LlmResult(LlmStatus.OK, suggestionJson(CHANGE, MEDS), null),
        new LlmResult(LlmStatus.OK,
            suggestionJson(CHANGE, "{\"name\":\"Aspirin\",\"dose\":\"10mg\",\"schedule\":\"daily\"}"), null));

    svc.suggest(request(Map.of("n1", "ate well"), null));
    assertThatThrownBy(() -> svc.suggest(request(Map.of("n1", "ate well"), null)))
        .isInstanceOf(InvalidModelOutputException.class);
    assertThat(store.saves()).isEqualTo(1);
  }
}
