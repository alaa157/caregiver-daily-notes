package com.caregiver.ai;

import com.caregiver.config.CaregiverApplication;
import com.caregiver.plans.*;
import com.caregiver.recipient.CareRecipientRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = CaregiverApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:summaryplanhttp;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate", "auth.jwt.secret=synthetic-summary-plan-test-secret-32-bytes"})
@AutoConfigureMockMvc
class SummaryPlanHttpTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @Autowired CarePlanRepository plans;
  @Autowired CareRecipientRepository recipients;
  @Autowired PlanDocumentCodec documents;
  @MockBean LlmClient llm;
  private String owner, recipientId, noteId, outOfWindowNoteId;
  private List<MedicationEntry> medications = List.of();
  private String summaryMode, planMode;

  @BeforeEach void setup() throws Exception {
    for (String table : List.of("care_plan_versions", "note_addenda", "daily_notes", "audit_events", "care_recipients", "caregivers")) jdbc.update("delete from " + table);
    owner = register();
    recipientId = json(call("/api/recipients", owner, Map.of("name", "Synthetic مستفيد")).andExpect(status().isCreated())).get("id").asText();
    noteId = note("2026-10-26", "Source note عربي.", true);
    summaryMode = "ok"; planMode = "ok"; medications = List.of();
    when(llm.complete(any())).thenAnswer(invocation -> {
      LlmRequest request = invocation.getArgument(0);
      if (request.dataBlock().startsWith("SUMMARY:")) {
        if (planMode.equals("unknown")) return ok(Map.of("changes", List.of(Map.of("actionId", "INVENTED_ACTION", "params", Map.of(), "reason", "Synthetic", "evidenceNoteIds", List.of(noteId))), "proposedMedications", medications));
        List<MedicationEntry> proposed = medications;
        if (planMode.equals("remove")) proposed = List.of();
        if (planMode.equals("add")) proposed = List.of(new MedicationEntry("OTHER_SYNTHETIC", "reported", "reported"));
        if (planMode.equals("dose")) proposed = List.of(new MedicationEntry(medications.getFirst().name(), "changed", medications.getFirst().schedule()));
        if (planMode.equals("schedule")) proposed = List.of(new MedicationEntry(medications.getFirst().name(), medications.getFirst().dose(), "changed"));
        return ok(Map.of("changes", List.of(Map.of("actionId", "SCHEDULE_CHECK_IN", "params", Map.of("withinDays", "7"), "reason", "Review logged observations.", "evidenceNoteIds", List.of(noteId))), "proposedMedications", proposed));
      }
      if (summaryMode.equals("throw")) throw new IllegalStateException("private provider diagnostic");
      if (summaryMode.equals("fallback")) return new LlmResult(LlmStatus.FALLBACK, "", "private provider diagnostic");
      if (summaryMode.equals("malformed")) return new LlmResult(LlmStatus.OK, "not-json", "");
      if (summaryMode.equals("empty")) return ok(Map.of("observations", List.of(), "uncertainties", List.of()));
      if (summaryMode.equals("out-of-window")) return ok(Map.of("observations", List.of(Map.of("text", "Out of range.", "noteId", outOfWindowNoteId, "quote", "OUTSIDE_PERIOD")), "uncertainties", List.of()));
      return ok(Map.of("observations", List.of(Map.of("text", "Reported observation.", "noteId", noteId, "quote", summaryMode.equals("quote") ? "fabricated quote" : "Source note")), "uncertainties", List.of()));
    });
  }

  @ParameterizedTest @ValueSource(ints = {7, 14, 30})
  void ownedInclusivePeriodsExcludeOtherDatesAndPreserveSafety(int days) throws Exception {
    String start = LocalDate.parse("2026-10-26").minusDays(days - 1L).toString();
    String included = note(start, "Boundary inside", false);
    String excluded = note(LocalDate.parse(start).minusDays(1).toString(), "OUTSIDE_PERIOD", false);
    outOfWindowNoteId = excluded;
    note("2026-10-27", "FUTURE_NOTE", false);
    call("/api/summaries", owner, request(days)).andExpect(status().isOk()).andExpect(jsonPath("$.summary.aiUnavailable").value(false))
        .andExpect(jsonPath("$.summary.observations[0].noteId").value(noteId)).andExpect(jsonPath("$.safety.needsDoctorBanner").value(true));
    var capture = org.mockito.ArgumentCaptor.forClass(LlmRequest.class); verify(llm).complete(capture.capture());
    assertThat(capture.getValue().dataBlock()).contains(included, "Source note عربي.").doesNotContain(excluded, "OUTSIDE_PERIOD", "FUTURE_NOTE");
  }

  @Test void outOfWindowNoteCannotBeAcceptedAsEvidenceEvenWhenModelNamesItsId() throws Exception {
    outOfWindowNoteId = note("2026-10-18", "OUTSIDE_PERIOD", false);
    summaryMode = "out-of-window";

    call("/api/summaries", owner, request(7)).andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.aiUnavailable").value(true))
        .andExpect(jsonPath("$.summary.observations").isEmpty());

    var capture = org.mockito.ArgumentCaptor.forClass(LlmRequest.class);
    verify(llm).complete(capture.capture());
    assertThat(capture.getValue().dataBlock()).doesNotContain("OUTSIDE_PERIOD");
  }

  @Test void advertisesTheConcreteSummaryRequestAndResultAsJson() throws Exception {
    mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/summaries'].post.requestBody.content['application/json'].schema['$ref']").value("#/components/schemas/SummaryRequest"))
        .andExpect(jsonPath("$.paths['/api/summaries'].post.responses['200'].content['application/json'].schema['$ref']").value("#/components/schemas/SummaryResult"));
  }

  @ParameterizedTest @ValueSource(strings = {"fallback", "throw", "malformed", "quote"})
  void everyProviderFailureRetainsAuthoritativeSafety(String mode) throws Exception {
    summaryMode = mode;
    var response = call("/api/summaries", owner, request(7)).andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.aiUnavailable").value(true)).andExpect(jsonPath("$.summary.observations").isEmpty())
        .andExpect(jsonPath("$.safety.needsDoctorBanner").value(true));
    assertThat(response.andReturn().getResponse().getContentAsString()).doesNotContain("private provider diagnostic");
  }
  @Test @DisplayName("returns unavailable summary with no model observations after provider failure")
  void returnsUnavailableSummaryWithNoModelObservationsAfterProviderFailure() throws Exception {
    summaryMode = "throw";
    var response = call("/api/summaries", owner, request(7)).andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.aiUnavailable").value(true))
        .andExpect(jsonPath("$.summary.observations").isEmpty())
        .andExpect(jsonPath("$.safety.needsDoctorBanner").value(true));

    JsonNode body = json(response);
    assertThat(body.path("trends").isArray()).isTrue();
    assertThat(body.path("trends").size()).isPositive();
    assertThat(body.path("trends").get(0).path("metric").asText()).isNotBlank();
    assertThat(body.path("summary").path("uncertainties").get(0).path("topic").asText()).isEqualTo("availability");
    assertThat(response.andReturn().getResponse().getContentAsString())
        .doesNotContain("private provider diagnostic", "Reported observation.");
  }

  @Test @DisplayName("does not call provider when recipient ownership check fails")
  void doesNotCallProviderWhenRecipientOwnershipCheckFails() throws Exception {
    String otherCaregiver = register();
    reset(llm);

    var response = call("/api/summaries", otherCaregiver, request(7)).andExpect(status().isNotFound());

    assertThat(response.andReturn().getResponse().getContentAsString())
        .doesNotContain("Synthetic مستفيد", "Source note عربي.");
    verifyNoInteractions(llm);
  }
  @Test @DisplayName("accepts one request scoped credential for summary")
  void acceptsOneRequestScopedCredentialForSummary() throws Exception {
    var response = call("/api/summaries/byok", owner, byokRequest(recipientId, "test-key-do-not-use"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.aiUnavailable").value(false))
        .andExpect(jsonPath("$.summary.observations[0].noteId").value(noteId))
        .andExpect(jsonPath("$.safety").exists())
        .andExpect(jsonPath("$.trends").isArray());

    var captured = org.mockito.ArgumentCaptor.forClass(LlmRequest.class);
    verify(llm, times(1)).complete(captured.capture());
    assertThat(captured.getValue().credentials()).isNotNull();
    assertThat(captured.getValue().credentials().providerId()).isEqualTo("GEMINI");
    assertThat(captured.getValue().credentials().modelId()).isEqualTo("gemini-3.8-flash");
    assertThat(captured.getValue().credentials().apiKey()).isEqualTo("test-key-do-not-use");
    assertThat(response.andReturn().getResponse().getContentAsString()).doesNotContain("test-key-do-not-use");
  }

  @Test @DisplayName("rejects BYOK summary for foreign recipient before provider call")
  void rejectsByokSummaryForForeignRecipientBeforeProviderCall() throws Exception {
    reset(llm);
    var response = call("/api/summaries/byok", owner, byokRequest(UUID.randomUUID().toString(), "synthetic-byok-key"))
        .andExpect(status().isNotFound());
    assertThat(response.andReturn().getResponse().getContentAsString()).doesNotContain("synthetic-byok-key");
    verifyNoInteractions(llm);
  }
  @Test @DisplayName("does not return request credential in summary response")
  void doesNotReturnRequestCredentialInSummaryResponse() throws Exception {
    String secret = "byok-test-secret-12345";
    var response = call("/api/summaries/byok", owner, byokRequest(recipientId, secret))
        .andExpect(status().isOk());
    assertThat(response.andReturn().getResponse().getContentAsString()).doesNotContain(secret, "apiKey", "credentials");
  }

  @Test @DisplayName("keeps evidence validation active for BYOK summary")
  void keepsEvidenceValidationActiveForByokSummary() throws Exception {
    summaryMode = "quote";
    call("/api/summaries/byok", owner, byokRequest(recipientId, "synthetic-invalid-quote-key"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.aiUnavailable").value(true))
        .andExpect(jsonPath("$.summary.observations").isEmpty());

    var captured = org.mockito.ArgumentCaptor.forClass(LlmRequest.class);
    verify(llm, times(1)).complete(captured.capture());
    assertThat(captured.getValue().credentials()).isNotNull();
  }
  @Test @DisplayName("keeps non BYOK summary endpoint behavior unchanged")
  void keepsNonByokSummaryEndpointBehaviorUnchanged() throws Exception {
    var response = call("/api/summaries", owner, request(7))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.summary.aiUnavailable").value(false))
        .andExpect(jsonPath("$.summary.observations[0].noteId").value(noteId))
        .andExpect(jsonPath("$.safety").exists())
        .andExpect(jsonPath("$.trends").isArray());
    assertThat(json(response).fieldNames()).toIterable().containsExactlyInAnyOrder("summary", "safety", "trends");
    var captured = org.mockito.ArgumentCaptor.forClass(LlmRequest.class);
    verify(llm, times(1)).complete(captured.capture());
    assertThat(captured.getValue().credentials()).isNull();
  }
  @Test @DisplayName("rejects invalid BYOK request fields without echoing credentials")
  void rejectsInvalidByokRequestFieldsWithoutEchoingCredentials() throws Exception {
    String secret = "invalid-byok-secret";
    call("/api/summaries/byok", owner, Map.of("summary", request(7, recipientId)))
        .andExpect(status().isBadRequest());
    call("/api/summaries/byok", owner, Map.of("credentials",
        Map.of("providerId", "GEMINI", "model", "gemini-3.8-flash", "apiKey", secret)))
        .andExpect(status().isBadRequest());
    var invalidCredentials = Map.of("providerId", "GEMINI", "model", "gemini-3.8-flash", "apiKey", " ");
    var response = call("/api/summaries/byok", owner,
        Map.of("summary", request(7, recipientId), "credentials", invalidCredentials))
        .andExpect(status().isBadRequest());
    assertThat(response.andReturn().getResponse().getContentAsString()).doesNotContain(secret, "apiKey");
    verifyNoInteractions(llm);
  }

  private Map<String, Object> byokRequest(String targetRecipientId, String apiKey) {
    return Map.of("summary", request(7, targetRecipientId), "credentials",
        Map.of("providerId", "GEMINI", "model", "gemini-3.8-flash", "apiKey", apiKey));
  }

  @Test @DisplayName("generates plan with the same request-scoped credentials as its summary")
  void generatesPlanWithRequestScopedCredentials() throws Exception {
    String secret = "synthetic-byok-plan-key";

    var response = call("/api/plans/generate/byok", owner, byokRequest(recipientId, secret))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.aiUnavailable").value(false))
        .andExpect(jsonPath("$.plan").isNotEmpty());

    var captured = org.mockito.ArgumentCaptor.forClass(LlmRequest.class);
    verify(llm, times(2)).complete(captured.capture());
    List<LlmRequest> calls = captured.getAllValues();
    assertThat(calls.get(0).dataBlock()).doesNotStartWith("SUMMARY:");
    assertThat(calls.get(1).dataBlock()).startsWith("SUMMARY:");
    assertThat(calls).allSatisfy(call -> assertThat(call.credentials().providerId()).isEqualTo("GEMINI"));
    assertThat(calls.get(1).credentials()).isSameAs(calls.get(0).credentials());
    assertThat(response.andReturn().getResponse().getContentAsString()).doesNotContain(secret);
  }

  @Test @DisplayName("does not save a plan when the BYOK summary is unavailable")
  void doesNotSavePlanWhenByokSummaryIsUnavailable() throws Exception {
    summaryMode = "fallback";

    call("/api/plans/generate/byok", owner, byokRequest(recipientId, "synthetic-byok-fallback-key"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.aiUnavailable").value(true))
        .andExpect(jsonPath("$.plan").isEmpty());

    assertThat(history().size()).isZero();
    verify(llm, times(1)).complete(any());
  }

  @Test @DisplayName("does not save a plan when BYOK plan output fails validation")
  void doesNotSavePlanWhenByokPlanOutputFailsValidation() throws Exception {
    planMode = "unknown";

    call("/api/plans/generate/byok", owner, byokRequest(recipientId, "synthetic-byok-invalid-plan-key"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.aiUnavailable").value(true))
        .andExpect(jsonPath("$.plan").isEmpty());

    assertThat(history().size()).isZero();
    verify(llm, times(2)).complete(any());
  }

  @Test @DisplayName("rejects BYOK plan generation for a foreign recipient before provider calls")
  void rejectsByokPlanGenerationForForeignRecipientBeforeProviderCalls() throws Exception {
    String otherCaregiver = register();
    String foreignRecipientId = json(call("/api/recipients", otherCaregiver, Map.of("name", "Other synthetic recipient"))
        .andExpect(status().isCreated())).get("id").asText();
    reset(llm);

    var response = call("/api/plans/generate/byok", owner, byokRequest(foreignRecipientId, "synthetic-byok-foreign-key"))
        .andExpect(status().isNotFound());

    assertThat(response.andReturn().getResponse().getContentAsString()).doesNotContain("synthetic-byok-foreign-key");
    verifyNoInteractions(llm);
  }

  @Test @DisplayName("never returns BYOK plan request keys")
  void neverReturnsByokPlanRequestKeys() throws Exception {
    String secret = "synthetic-plan-response-secret";

    var response = call("/api/plans/generate/byok", owner, byokRequest(recipientId, secret))
        .andExpect(status().isOk());

    assertThat(response.andReturn().getResponse().getContentAsString()).doesNotContain(secret, "apiKey", "credentials");
  }

  @Test @DisplayName("requires authentication for BYOK plan generation")
  void requiresAuthenticationForByokPlanGeneration() throws Exception {
    reset(llm);

    mvc.perform(post("/api/plans/generate/byok")
        .contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(byokRequest(recipientId, "synthetic-unauthenticated-key"))))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(llm);
  }

  @Test @DisplayName("rejects unsupported BYOK plan providers without exposing credentials")
  void rejectsUnsupportedByokPlanProvidersWithoutExposingCredentials() throws Exception {
    String secret = "synthetic-unsupported-provider-key";
    var invalid = Map.of("summary", request(7), "credentials",
        Map.of("providerId", "UNSUPPORTED", "model", "synthetic-model", "apiKey", secret));

    var response = call("/api/plans/generate/byok", owner, invalid)
        .andExpect(status().isUnprocessableEntity());

    assertThat(response.andReturn().getResponse().getContentAsString()).doesNotContain(secret);
    verifyNoInteractions(llm);
  }





  @Test void doesNotGenerateOrPersistAPlanAfterSummaryFailure() throws Exception {
    summaryMode = "throw";

    call("/api/plans/generate", owner, request(7)).andExpect(status().isOk())
        .andExpect(jsonPath("$.aiUnavailable").value(true))
        .andExpect(jsonPath("$.plan").isEmpty())
        .andExpect(jsonPath("$.safety.needsDoctorBanner").value(true));

    assertThat(history().size()).isZero();
    var captured = org.mockito.ArgumentCaptor.forClass(LlmRequest.class);
    verify(llm, times(1)).complete(captured.capture());
    assertThat(captured.getValue().dataBlock()).doesNotStartWith("SUMMARY:");
  }

  @Test void doesNotGenerateAPlanWithoutValidatedObservationEvidence() throws Exception {
    summaryMode = "empty";

    call("/api/plans/generate", owner, request(7)).andExpect(status().isOk())
        .andExpect(jsonPath("$.aiUnavailable").value(true))
        .andExpect(jsonPath("$.plan").isEmpty());

    assertThat(history().size()).isZero();
    verify(llm, times(1)).complete(any());
  }

  @Test @DisplayName("does not call plan provider or persist after summary is unavailable")
  void doesNotCallPlanProviderOrPersistAfterSummaryIsUnavailable() throws Exception {
    summaryMode = "fallback";
    long rowsBefore = plans.count();

    var response = call("/api/plans/generate", owner, request(7)).andExpect(status().isOk())
        .andExpect(jsonPath("$.aiUnavailable").value(true))
        .andExpect(jsonPath("$.plan").isEmpty());

    assertThat(plans.count()).isEqualTo(rowsBefore);
    assertThat(response.andReturn().getResponse().getContentAsString())
        .doesNotContain("private provider diagnostic", "Reported observation.");
    var captured = org.mockito.ArgumentCaptor.forClass(LlmRequest.class);
    verify(llm, times(1)).complete(captured.capture());
    assertThat(captured.getValue().dataBlock()).doesNotStartWith("SUMMARY:");
  }

  @Test @DisplayName("does not call plan provider or persist when summary evidence is invalid")
  void doesNotCallPlanProviderOrPersistWhenSummaryEvidenceIsInvalid() throws Exception {
    summaryMode = "quote";
    long rowsBefore = plans.count();

    call("/api/plans/generate", owner, request(7)).andExpect(status().isOk())
        .andExpect(jsonPath("$.aiUnavailable").value(true))
        .andExpect(jsonPath("$.plan").isEmpty());

    assertThat(plans.count()).isEqualTo(rowsBefore);
    var captured = org.mockito.ArgumentCaptor.forClass(LlmRequest.class);
    verify(llm, times(1)).complete(captured.capture());
    assertThat(captured.getValue().dataBlock()).doesNotStartWith("SUMMARY:");
  }

  @Test @DisplayName("does not call providers or persist when recipient ownership check fails")
  void doesNotCallProvidersOrPersistWhenRecipientOwnershipCheckFails() throws Exception {
    String otherCaregiver = register();
    String foreignRecipientId = json(call("/api/recipients", otherCaregiver,
        Map.of("name", "Other synthetic recipient")).andExpect(status().isCreated())).get("id").asText();
    call("/api/notes", otherCaregiver, Map.of(
        "recipientId", foreignRecipientId, "date", "2026-10-26",
        "freeText", "Foreign private source note.", "fallReported", false)).andExpect(status().isCreated());
    reset(llm);
    long rowsBefore = plans.count();

    var response = call("/api/plans/generate", owner, request(7, foreignRecipientId))
        .andExpect(status().isNotFound());

    assertThat(response.andReturn().getResponse().getContentAsString())
        .doesNotContain("Other synthetic recipient", "Foreign private source note.");
    assertThat(plans.count()).isEqualTo(rowsBefore);
    verifyNoInteractions(llm);
  }

  @Test void authenticationOwnershipValidationAndEmptyPeriodsNeverInvokeTheProvider() throws Exception {
    String other = register(); reset(llm);
    call("/api/summaries", null, request(7)).andExpect(status().isUnauthorized());
    call("/api/summaries", other, request(7)).andExpect(status().isNotFound());
    call("/api/plans/generate", other, request(7)).andExpect(status().isNotFound());
    call("/api/summaries", owner, request(9)).andExpect(status().isUnprocessableEntity());
    call("/api/summaries", owner, Map.of("recipientId", "invalid", "periodEndInclusive", "2026-10-26", "periodDays", 7)).andExpect(status().isUnprocessableEntity());
    call("/api/summaries", owner, Map.of("recipientId", recipientId, "periodEndInclusive", "2026-09-01", "periodDays", 7))
        .andExpect(status().isOk()).andExpect(jsonPath("$.summary.aiUnavailable").value(true));
    verifyNoInteractions(llm);
  }

  @Test void generatedSuggestionAndProtectedEditPreserveOriginalMedicationsAndProvenance() throws Exception {
    seedAccepted();
    JsonNode original = generate();
    assertThat(original.path("sourceType").asText()).isEqualTo("AI_GENERATED");
    assertThat(original.path("sourceSummaryId").asText()).startsWith("summary-sha256:");
    assertThat(original.path("editable").asBoolean()).isTrue();
    JsonNode edited = json(call(path(original, "edit-and-accept"), owner, edit(original, "10")).andExpect(status().isOk()));
    assertThat(edited.path("status").asText()).isEqualTo("EDITED_AND_ACCEPTED");
    assertThat(edited.path("sourceType").asText()).isEqualTo("CAREGIVER_EDITED");
    assertThat(edited.path("sourcePlanId").asText()).isEqualTo(original.path("id").asText());
    assertThat(edited.path("version").asInt()).isEqualTo(original.path("version").asInt() + 1);
    assertThat(edited.path("structuredContent").path("medications")).isEqualTo(original.path("structuredContent").path("medications"));
    assertThat(edited.path("structuredContent").path("changes").get(0).path("params").path("withinDays").asText()).isEqualTo("10");
    JsonNode history = history();
    JsonNode preserved = java.util.stream.StreamSupport.stream(history.spliterator(), false).filter(item -> item.path("id").equals(original.path("id"))).findFirst().orElseThrow();
    assertThat(preserved.path("content")).isEqualTo(original.path("content"));
    assertThat(preserved.path("status").asText()).isEqualTo("ARCHIVED");
    assertThat(preserved.path("sourceType").asText()).isEqualTo("AI_GENERATED");
    call(path(original, "edit-and-accept"), owner, edit(original, "11")).andExpect(status().isConflict());
  }

  @ParameterizedTest @ValueSource(strings = {"content", "medications", "sourceType", "sourcePlanId", "evidenceNoteIds"})
  void protectedFieldsCannotBeInjectedAtHttpEditBoundary(String field) throws Exception {
    seedAccepted(); JsonNode original = generate();
    var payload = new java.util.HashMap<String, Object>(edit(original, "10")); payload.put(field, "untrusted replacement");
    call(path(original, "edit-and-accept"), owner, payload).andExpect(status().isUnprocessableEntity());
    assertThat(history().size()).isEqualTo(2);
    assertThat(history().get(0).path("status").asText()).isEqualTo("SUGGESTED");
  }

  @ParameterizedTest @ValueSource(strings = {"remove", "add", "dose", "schedule", "unknown"})
  void invalidAiMedicationOrCatalogChangesNeverPersist(String mode) throws Exception {
    seedAccepted(); planMode = mode;
    call("/api/plans/generate", owner, request(7)).andExpect(status().isOk())
        .andExpect(jsonPath("$.aiUnavailable").value(true)).andExpect(jsonPath("$.plan").isEmpty())
        .andExpect(jsonPath("$.safety.needsDoctorBanner").value(true));
    assertThat(history().size()).isEqualTo(1);
  }

  @ParameterizedTest @ValueSource(strings = {"0", "-1", "366", "text", "20mg"})
  void invalidActionParameterValuesCannotCreateAnEditedVersion(String value) throws Exception {
    JsonNode original = generate();
    call(path(original, "edit-and-accept"), owner, edit(original, value)).andExpect(status().isUnprocessableEntity());
    assertThat(history().size()).isEqualTo(1);
    assertThat(history().get(0).path("status").asText()).isEqualTo("SUGGESTED");
  }

  @Test void staleBaselineAndCrossCaregiverEditsAreRejectedWithoutMutation() throws Exception {
    JsonNode first = generate(), second = generate(); String other = register();
    call(path(first, "edit-and-accept"), other, edit(first, "10")).andExpect(status().isNotFound());
    call(path(first, "accept"), owner, null).andExpect(status().isOk());
    call(path(second, "accept"), owner, null).andExpect(status().isConflict());
    call(path(second, "edit-and-accept"), owner, edit(second, "10")).andExpect(status().isConflict());
    assertThat(history().size()).isEqualTo(2);
  }

  @Test void inactiveRecipientsRemainReadableButCannotGenerate() throws Exception {
    mvc.perform(patch("/api/recipients/" + recipientId + "/deactivate").header("Authorization", "Bearer " + owner)).andExpect(status().isOk());
    reset(llm); call("/api/plans/generate", owner, request(7)).andExpect(status().isConflict()); verifyNoInteractions(llm);
  }

  private void seedAccepted() {
    medications = List.of(new MedicationEntry("SYNTHETIC_REPORTED_ITEM", "reported-value", "reported-schedule"));
    var document = new PlanDocument(1, List.of(new SuggestedPlanChange(PlanAction.SCHEDULE_CHECK_IN, Map.of("withinDays", "7"), "Review logged observations.", List.of(noteId))), medications);
    plans.saveAndFlush(new CarePlan(recipients.findById(UUID.fromString(recipientId)).orElseThrow(), 1, PlanStatus.ACCEPTED,
        documents.write(document), "synthetic-summary", null, noteId, PlanSource.AI_GENERATED));
  }
  private JsonNode generate() throws Exception { return json(call("/api/plans/generate", owner, request(7)).andExpect(status().isOk()).andExpect(jsonPath("$.aiUnavailable").value(false))).get("plan"); }
  private String path(JsonNode plan, String action) { return "/api/recipients/" + recipientId + "/plans/" + plan.path("id").asText() + "/" + action; }
  private Map<String, Object> edit(JsonNode plan, String value) { return Map.of("expectedVersion", plan.path("version").asInt(), "changes", List.of(Map.of("index", 0, "params", Map.of("withinDays", value)))); }
  private Map<String, Object> request(int days) { return request(days, recipientId); }
  private Map<String, Object> request(int days, String targetRecipientId) {
    return Map.of("recipientId", targetRecipientId, "periodEndInclusive", "2026-10-26", "periodDays", days);
  }
  private JsonNode history() throws Exception { return json(mvc.perform(get("/api/recipients/" + recipientId + "/plans/history").header("Authorization", "Bearer " + owner)).andExpect(status().isOk())); }
  private String note(String date, String text, boolean fall) throws Exception { return json(call("/api/notes", owner, Map.of("recipientId", recipientId, "date", date, "freeText", text, "fallReported", fall))).get("id").asText(); }
  private String register() throws Exception { return json(call("/api/auth/register", null, Map.of("email", "summary-" + UUID.randomUUID() + "@example.com", "password", "Synthetic-password-123" )).andExpect(status().isCreated())).get("token").asText(); }
  private ResultActions call(String path, String token, Object body) throws Exception {
    var request = post(path); if (token != null) request.header("Authorization", "Bearer " + token);
    if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
    return mvc.perform(request);
  }
  private JsonNode json(ResultActions response) throws Exception { return mapper.readTree(response.andReturn().getResponse().getContentAsString()); }
  private LlmResult ok(Object value) throws Exception { return new LlmResult(LlmStatus.OK, mapper.writeValueAsString(value), ""); }
}
