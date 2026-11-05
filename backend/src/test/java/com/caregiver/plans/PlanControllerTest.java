package com.caregiver.plans;

import com.caregiver.audit.AuditEvent;
import com.caregiver.audit.AuditEventRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import com.caregiver.auth.CaregiverRepository;
import com.caregiver.config.CaregiverApplication;
import com.caregiver.auth.JwtService;
import com.caregiver.recipient.CareRecipient;
import com.caregiver.recipient.CareRecipientRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = CaregiverApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:plantest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "auth.jwt.secret=test-secret-that-is-definitely-32-bytes-long",
      "auth.jwt.expiry=24h"
    })
@AutoConfigureMockMvc
class PlanControllerTest {
  private static final String PLANS = "/api/recipients/";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private JwtService jwtService;
  @Autowired private CarePlanRepository plans;
  @Autowired private PlanDocumentCodec documents;
  @Autowired private CareRecipientRepository recipients;
  @Autowired private CaregiverRepository caregivers;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private AuditEventRepository auditEvents;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from care_plan_versions");
    jdbcTemplate.update("delete from audit_events");
    recipients.deleteAll();
    caregivers.deleteAll();
  }

  @Test
  void acceptingNewVersionArchivesPreviousAcceptedPlanAndKeepsHistory() throws Exception {
    TestCaregiver owner = register("plan-owner@example.com");
    String recipientId = createRecipient(owner.token());
    JsonNode first = suggest(owner.token(), recipientId, "First plan", "summary-1");
    mvc.perform(post(path(recipientId, first.get("id").asText(), "accept"))
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACCEPTED"));

    JsonNode second = suggest(owner.token(), recipientId, "Second plan", "summary-2");
    mvc.perform(post(path(recipientId, second.get("id").asText(), "accept"))
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACCEPTED"));

    mvc.perform(get(PLANS + recipientId + "/plans/active")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(second.get("id").asText()));

    mvc.perform(get(PLANS + recipientId + "/plans/history")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].version").value(2))
        .andExpect(jsonPath("$[1].status").value("ARCHIVED"))
        .andExpect(jsonPath("$[1].content").value("First plan"));

    assertThat(plans.findAllByRecipient_IdOrderByVersionNumberDesc(UUID.fromString(recipientId)))
        .filteredOn(p -> p.getStatus().isActiveAccepted()).hasSize(1);
  }

  @Test
  void returnsLegacyPlanTextByteForByte() throws Exception {
    TestCaregiver owner = register("plan-legacy-exact@example.com");
    String recipientId = createRecipient(owner.token());
    String legacyContent = " \tخطة متابعة care-ID-١٢٣ / Case-A 🩺؟ — “مرحبا!”\n"
        + "English line: preserve <b>literal</b> & <script>alert('x')</script>\n"
        + "  second line with mixed ID: ملف-ABC-42 \n\t";

    MvcResult createdResult = mvc.perform(post(PLANS + recipientId + "/plans/suggestions")
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(Map.of("content", legacyContent, "evidenceNoteIds", List.of("note-1")))))
        .andExpect(status().isCreated())
        .andReturn();
    JsonNode created = mapper.readTree(createdResult.getResponse().getContentAsByteArray());
    assertThat(created.get("content").asText()).isEqualTo(legacyContent);
    assertThat(created.get("sourceType").asText()).isEqualTo("CAREGIVER_SUBMITTED");
    assertThat(created.get("structuredContent").isNull()).isTrue();
    assertThat(created.get("editable").asBoolean()).isFalse();

    MvcResult historyResult = mvc.perform(get(PLANS + recipientId + "/plans/history")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode history = mapper.readTree(historyResult.getResponse().getContentAsByteArray());
    assertThat(history).hasSize(1);
    assertThat(history.get(0).get("content").asText()).isEqualTo(legacyContent);
    assertThat(history.get(0).get("sourceType").asText()).isEqualTo("CAREGIVER_SUBMITTED");
    assertThat(history.get(0).get("structuredContent").isNull()).isTrue();
    assertThat(history.get(0).get("editable").asBoolean()).isFalse();
  }

  @Test
  void keepsLegacyPlanVisibleAfterStructuredPlanIsAdded() throws Exception {
    TestCaregiver owner = register("plan-legacy-history@example.com");
    String recipientId = createRecipient(owner.token());
    String legacyContent = "  Legacy version one — keep this text.\n"
        + "Mixed text: مراجعة case-17 <b>not markup</b>\n";
    JsonNode legacy = suggestOpaque(owner.token(), recipientId, legacyContent);
    assertThat(legacy.get("version").asInt()).isEqualTo(1);

    CareRecipient recipient = recipients.findById(UUID.fromString(recipientId)).orElseThrow();
    PlanDocument structuredDocument = new PlanDocument(1,
        List.of(new SuggestedPlanChange(PlanAction.SCHEDULE_CHECK_IN, Map.of("withinDays", "5"),
            "Review the recorded notes.", List.of("note-1"))),
        List.of());
    CarePlan structured = plans.saveAndFlush(new CarePlan(recipient, 2, PlanStatus.SUGGESTED,
        mapper.writeValueAsString(structuredDocument), null, null, null, PlanSource.AI_GENERATED));

    MvcResult historyResult = mvc.perform(get(PLANS + recipientId + "/plans/history")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode history = mapper.readTree(historyResult.getResponse().getContentAsByteArray());
    assertThat(history).hasSize(2);
    assertThat(history.get(0).get("version").asInt()).isEqualTo(2);
    assertThat(history.get(0).get("id").asText()).isEqualTo(structured.getId().toString());
    assertThat(history.get(0).get("sourceType").asText()).isEqualTo("AI_GENERATED");
    assertThat(history.get(0).get("structuredContent").isNull()).isFalse();
    assertThat(history.get(1).get("version").asInt()).isEqualTo(1);
    assertThat(history.get(1).get("id").asText()).isEqualTo(legacy.get("id").asText());
    assertThat(history.get(1).get("sourceType").asText()).isEqualTo("CAREGIVER_SUBMITTED");
    assertThat(history.get(1).get("content").asText()).isEqualTo(legacyContent);
    assertThat(history.get(1).get("structuredContent").isNull()).isTrue();
    assertThat(history.get(1).get("editable").asBoolean()).isFalse();
  }

  @Test
  void decodesStructuredContentOnlyForSupportedSourceTypes() throws Exception {
    TestCaregiver owner = register("plan-codec-sources@example.com");
    CareRecipient recipient = recipients.findById(UUID.fromString(createRecipient(owner.token()))).orElseThrow();
    PlanDocument document = new PlanDocument(1,
        List.of(new SuggestedPlanChange(PlanAction.SCHEDULE_CHECK_IN, Map.of("withinDays", "5"),
            "Review the recorded notes.", List.of("note-1"))),
        List.of());
    String content = mapper.writeValueAsString(document);

    for (PlanSource source : List.of(
        PlanSource.AI_GENERATED, PlanSource.CAREGIVER_EDITED, PlanSource.CAREGIVER_SUBMITTED)) {
      CarePlan plan = new CarePlan(recipient, 1, PlanStatus.SUGGESTED, content, null, null, null, source);
      assertThat(documents.read(plan)).isEqualTo(document);
    }
    for (PlanSource source : List.of(PlanSource.UNSPECIFIED)) {
      CarePlan plan = new CarePlan(recipient, 1, PlanStatus.SUGGESTED, content, null, null, null, source);
      assertThat(documents.read(plan)).isNull();
    }
  }

  @Test
  void createsCaregiverStructuredDraftWithSuggestedStatus() throws Exception {
    TestCaregiver owner = register("plan-structured-draft@example.com");
    String recipientId = createRecipient(owner.token());
    suggest(owner.token(), recipientId, "Earlier draft", null);
    String request = """
        {
          "content": "",
          "sourceSummaryId": "summary-structured",
          "evidenceNoteIds": ["outer-note"],
          "structuredContent": {
            "schemaVersion": 1,
            "changes": [{
              "action": "SCHEDULE_CHECK_IN",
              "params": {"withinDays": "3"},
              "reason": "Review recorded notes",
              "evidenceNoteIds": ["note-1"]
            }],
            "medications": []
          }
        }
        """;

    MvcResult result = mvc.perform(post(PLANS + recipientId + "/plans/suggestions")
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.status").value("SUGGESTED"))
        .andExpect(jsonPath("$.sourceType").value("CAREGIVER_SUBMITTED"))
        .andExpect(jsonPath("$.structuredContent.schemaVersion").value(1))
        .andExpect(jsonPath("$.structuredContent.changes[0].action").value("SCHEDULE_CHECK_IN"))
        .andExpect(jsonPath("$.structuredContent.changes[0].params.withinDays").value("3"))
        .andReturn();

    JsonNode response = mapper.readTree(result.getResponse().getContentAsByteArray());
    CarePlan stored = plans.findById(UUID.fromString(response.get("id").asText())).orElseThrow();
    assertThat(stored.getVersionNumber()).isEqualTo(2);
    assertThat(stored.getStatus()).isEqualTo(PlanStatus.SUGGESTED);
    assertThat(stored.getSourceType()).isEqualTo(PlanSource.CAREGIVER_SUBMITTED);
    assertThat(stored.getSourceSummaryId()).isEqualTo("summary-structured");
    assertThat(stored.getEvidenceNoteIds()).isEqualTo("outer-note");
    assertThat(stored.getSourcePlanId()).isNull();
    assertThat(plans.findAllByRecipient_IdOrderByVersionNumberDesc(UUID.fromString(recipientId)))
        .filteredOn(plan -> plan.getStatus().isActiveAccepted())
        .isEmpty();
    assertThat(documents.read(stored)).isEqualTo(new PlanDocument(1,
        List.of(new SuggestedPlanChange(PlanAction.SCHEDULE_CHECK_IN, Map.of("withinDays", "3"),
            "Review recorded notes", List.of("note-1"))), List.of()));
  }

  @Test
  void structuredDraftCapturesAcceptedBaselineWithoutChangingIt() throws Exception {
    TestCaregiver owner = register("plan-structured-baseline@example.com");
    String recipientId = createRecipient(owner.token());
    PlanDocument baselineDocument = planDocument(List.of(new MedicationEntry("Medication A", "10 mg", "daily")));
    CarePlan baseline = saveAcceptedBaseline(recipientId, 1, baselineDocument);
    String originalContent = baseline.getContent();

    JsonNode draft = submitStructured(owner.token(), recipientId, baselineDocument);
    CarePlan persistedDraft = plans.findById(UUID.fromString(draft.get("id").asText())).orElseThrow();

    assertThat(draft.get("status").asText()).isEqualTo("SUGGESTED");
    assertThat(draft.get("sourcePlanId").asText()).isEqualTo(baseline.getId().toString());
    assertThat(persistedDraft.getSourcePlanId()).isEqualTo(baseline.getId());
    assertThat(persistedDraft.getVersionNumber()).isEqualTo(2);
    assertThat(baseline.getStatus()).isEqualTo(PlanStatus.ACCEPTED);
    assertThat(baseline.getContent()).isEqualTo(originalContent);
    assertThat(plans.findById(baseline.getId()).orElseThrow().getStatus()).isEqualTo(PlanStatus.ACCEPTED);
  }

  @Test
  void acceptsStructuredDraftAgainstTheAcceptedBaseline() throws Exception {
    TestCaregiver owner = register("plan-structured-accept@example.com");
    String recipientId = createRecipient(owner.token());
    CarePlan baseline = saveAcceptedBaseline(recipientId, 1, planDocument(List.of()));
    String originalContent = baseline.getContent();
    JsonNode draft = submitStructured(owner.token(), recipientId, planDocument(List.of()));

    mvc.perform(post(path(recipientId, draft.get("id").asText(), "accept"))
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACCEPTED"))
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.sourcePlanId").value(baseline.getId().toString()));

    CarePlan archivedBaseline = plans.findById(baseline.getId()).orElseThrow();
    CarePlan acceptedDraft = plans.findById(UUID.fromString(draft.get("id").asText())).orElseThrow();
    assertThat(archivedBaseline.getStatus()).isEqualTo(PlanStatus.ARCHIVED);
    assertThat(archivedBaseline.getContent()).isEqualTo(originalContent);
    assertThat(acceptedDraft.getStatus()).isEqualTo(PlanStatus.ACCEPTED);
    assertThat(acceptedDraft.getSourcePlanId()).isEqualTo(baseline.getId());
    assertThat(plans.findAllByRecipient_IdOrderByVersionNumberDesc(UUID.fromString(recipientId)))
        .filteredOn(plan -> plan.getStatus().isActiveAccepted())
        .hasSize(1);
  }

  @Test
  void staleStructuredDraftCannotReplaceANewerAcceptedBaseline() throws Exception {
    TestCaregiver owner = register("plan-structured-stale@example.com");
    String recipientId = createRecipient(owner.token());
    CarePlan baseline = saveAcceptedBaseline(recipientId, 1, planDocument(List.of()));
    JsonNode staleDraft = submitStructured(owner.token(), recipientId, planDocument(List.of()));
    JsonNode newerDraft = submitStructured(owner.token(), recipientId, planDocument(List.of()));
    assertThat(staleDraft.get("sourcePlanId").asText()).isEqualTo(baseline.getId().toString());
    assertThat(newerDraft.get("sourcePlanId").asText()).isEqualTo(baseline.getId().toString());
    String staleContent = plans.findById(UUID.fromString(staleDraft.get("id").asText()))
        .orElseThrow().getContent();

    mvc.perform(post(path(recipientId, newerDraft.get("id").asText(), "accept"))
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk());
    String newerContent = plans.findById(UUID.fromString(newerDraft.get("id").asText()))
        .orElseThrow().getContent();

    mvc.perform(post(path(recipientId, staleDraft.get("id").asText(), "accept"))
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isConflict());

    JsonNode active = mapper.readTree(mvc.perform(get(PLANS + recipientId + "/plans/active")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsByteArray());
    assertThat(active.get("id").asText()).isEqualTo(newerDraft.get("id").asText());
    assertThat(plans.findById(baseline.getId()).orElseThrow().getStatus()).isEqualTo(PlanStatus.ARCHIVED);
    assertThat(plans.findById(UUID.fromString(newerDraft.get("id").asText())).orElseThrow().getContent())
        .isEqualTo(newerContent);
    assertThat(plans.findById(UUID.fromString(staleDraft.get("id").asText())).orElseThrow().getStatus())
        .isEqualTo(PlanStatus.SUGGESTED);
    assertThat(plans.findById(UUID.fromString(staleDraft.get("id").asText())).orElseThrow().getContent())
        .isEqualTo(staleContent);
    assertThat(plans.findAllByRecipient_IdOrderByVersionNumberDesc(UUID.fromString(recipientId)))
        .hasSize(3)
        .filteredOn(plan -> plan.getStatus().isActiveAccepted())
        .hasSize(1);
  }

  @Test
  void structuredDraftMedicationHardLockRejectsChangesBeforePersistence() throws Exception {
    TestCaregiver owner = register("plan-medication-hard-lock@example.com");
    MedicationEntry first = new MedicationEntry("Medication A", "10 mg", "daily");
    MedicationEntry second = new MedicationEntry("Medication B", "5 mg", "twice daily");
    List<MedicationDraftCase> cases = List.of(
        new MedicationDraftCase("changed-name", List.of(first), List.of(new MedicationEntry("Medication X", "10 mg", "daily")), true),
        new MedicationDraftCase("changed-dose", List.of(first), List.of(new MedicationEntry("Medication A", "20 mg", "daily")), true),
        new MedicationDraftCase("changed-schedule", List.of(first), List.of(new MedicationEntry("Medication A", "10 mg", "weekly")), true),
        new MedicationDraftCase("added-medication", List.of(first), List.of(first, second), true),
        new MedicationDraftCase("removed-medication", List.of(first), List.of(), true),
        new MedicationDraftCase("added-duplicate", List.of(first), List.of(first, first), true),
        new MedicationDraftCase("removed-duplicate", List.of(first, first), List.of(first), true),
        new MedicationDraftCase("empty-baseline-addition", List.of(), List.of(first), false),
        new MedicationDraftCase("nonempty-baseline-removal", List.of(first), List.of(), true),
        new MedicationDraftCase("unchanged", List.of(first), List.of(first), true),
        new MedicationDraftCase("same-entries-reordered", List.of(first, second), List.of(second, first), true));

    for (MedicationDraftCase scenario : cases) {
      String recipientId = createRecipient(owner.token());
      CarePlan baseline = scenario.hasBaseline()
          ? saveAcceptedBaseline(recipientId, 1, planDocument(scenario.baseline()))
          : null;
      long suggestionAuditsBefore = suggestionAuditCount(owner.id());
      MvcResult result = submitStructuredRequest(owner.token(), recipientId, planDocument(scenario.proposed()));

      if (scenario.name().equals("unchanged") || scenario.name().equals("same-entries-reordered")) {
        assertThat(result.getResponse().getStatus()).as(scenario.name()).isEqualTo(201);
        JsonNode response = mapper.readTree(result.getResponse().getContentAsByteArray());
        CarePlan saved = plans.findById(UUID.fromString(response.get("id").asText())).orElseThrow();
        assertThat(documents.read(saved).medications()).containsExactlyElementsOf(scenario.proposed());
        assertThat(saved.getSourcePlanId()).isEqualTo(baseline.getId());
      } else {
        assertThat(result.getResponse().getStatus()).as(scenario.name()).isEqualTo(422);
        assertThat(plans.findAllByRecipient_IdOrderByVersionNumberDesc(UUID.fromString(recipientId)))
            .hasSize(baseline == null ? 0 : 1);
        assertThat(suggestionAuditCount(owner.id())).isEqualTo(suggestionAuditsBefore);
        if (baseline != null) {
          CarePlan unchangedBaseline = plans.findById(baseline.getId()).orElseThrow();
          assertThat(unchangedBaseline.getStatus()).isEqualTo(PlanStatus.ACCEPTED);
          assertThat(unchangedBaseline.getContent()).isEqualTo(baseline.getContent());
        }
      }
    }
  }

  @Test
  void rejectsStructuredDraftWhenAcceptedBaselineCannotBeDecoded() throws Exception {
    TestCaregiver owner = register("plan-undecodable-baseline@example.com");
    String recipientId = createRecipient(owner.token());
    CareRecipient recipient = recipients.findById(UUID.fromString(recipientId)).orElseThrow();
    String malformedContent = "{\"schemaVersion\":1,\"changes\":[}";
    CarePlan baseline = plans.saveAndFlush(new CarePlan(recipient, 1, PlanStatus.ACCEPTED,
        malformedContent, null, null, null, PlanSource.CAREGIVER_SUBMITTED));
    long suggestionAuditsBefore = suggestionAuditCount(owner.id());

    MvcResult result = submitStructuredRequest(owner.token(), recipientId, planDocument(List.of()));

    assertThat(result.getResponse().getStatus()).isEqualTo(422);
    assertThat(result.getResponse().getContentAsString()).doesNotContain("JsonParseException", "schemaVersion");
    var history = plans.findAllByRecipient_IdOrderByVersionNumberDesc(UUID.fromString(recipientId));
    assertThat(history).hasSize(1);
    assertThat(history.get(0).getId()).isEqualTo(baseline.getId());
    assertThat(baseline.getStatus()).isEqualTo(PlanStatus.ACCEPTED);
    assertThat(baseline.getContent()).isEqualTo(malformedContent);
    assertThat(suggestionAuditCount(owner.id())).isEqualTo(suggestionAuditsBefore);
  }

  @Test
  void rejectsUnsupportedStructuredDraftAction() throws Exception {
    TestCaregiver owner = register("plan-unsupported-action@example.com");
    String recipientId = createRecipient(owner.token());
    String request = """
        {"content":"","structuredContent":{"schemaVersion":1,"changes":[
          {"action":"PRESCRIBE_MEDICATION","params":{"drug":"medicine"},
           "reason":"Review medication","evidenceNoteIds":["note-1"]}
        ],"medications":[]}}
        """;

    mvc.perform(post(PLANS + recipientId + "/plans/suggestions")
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
        .andExpect(status().isUnprocessableEntity());

    assertThat(plans.findAllByRecipient_IdOrderByVersionNumberDesc(UUID.fromString(recipientId))).isEmpty();
  }

  @Test
  void rejectsStructuredDraftWithInvalidActionParameters() throws Exception {
    TestCaregiver owner = register("plan-invalid-structured-param@example.com");
    String recipientId = createRecipient(owner.token());
    String request = """
        {"content":"","structuredContent":{"schemaVersion":1,"changes":[
          {"action":"SCHEDULE_CHECK_IN","params":{"withinDays":"0"},
           "reason":"Review recorded notes","evidenceNoteIds":["note-1"]}
        ],"medications":[]}}
        """;

    mvc.perform(post(PLANS + recipientId + "/plans/suggestions")
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
        .andExpect(status().isUnprocessableEntity());

    assertThat(plans.findAllByRecipient_IdOrderByVersionNumberDesc(UUID.fromString(recipientId))).isEmpty();
  }

  @Test
  void rejectsBlankOrMissingContentWithoutStructuredContent() throws Exception {
    TestCaregiver owner = register("plan-missing-content@example.com");
    String recipientId = createRecipient(owner.token());
    List<String> requests = List.of(
        "{\"sourceSummaryId\":\"summary-missing\"}",
        "{\"content\":\"   \"}");

    for (String request : requests) {
      mvc.perform(post(PLANS + recipientId + "/plans/suggestions")
              .header("Authorization", bearer(owner.token()))
              .contentType(MediaType.APPLICATION_JSON)
              .content(request))
          .andExpect(status().isBadRequest());
      assertThat(plans.findAllByRecipient_IdOrderByVersionNumberDesc(UUID.fromString(recipientId))).isEmpty();
    }
  }

  @Test
  void rejectsNonBlankContentWhenStructuredContentIsAlsoProvided() throws Exception {
    TestCaregiver owner = register("plan-conflicting-content@example.com");
    String recipientId = createRecipient(owner.token());
    String request = """
        {"content":"legacy text","structuredContent":{"schemaVersion":1,"changes":[
          {"action":"SCHEDULE_CHECK_IN","params":{"withinDays":"3"},
           "reason":"Review recorded notes","evidenceNoteIds":["note-1"]}
        ],"medications":[]}}
        """;

    mvc.perform(post(PLANS + recipientId + "/plans/suggestions")
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
        .andExpect(status().isUnprocessableEntity());

    assertThat(plans.findAllByRecipient_IdOrderByVersionNumberDesc(UUID.fromString(recipientId))).isEmpty();
  }

  @Test
  void rejectsStructuredDraftExceedingSerializedSizeLimit() throws Exception {
    TestCaregiver owner = register("plan-oversized-structured@example.com");
    String recipientId = createRecipient(owner.token());
    String longName = "x".repeat(20_001);
    String request = mapper.writeValueAsString(Map.of(
        "content", "",
        "structuredContent", Map.of(
            "schemaVersion", 1,
            "changes", List.of(),
            "medications", List.of(Map.of("name", longName, "dose", "5mg", "schedule", "daily")))));

    mvc.perform(post(PLANS + recipientId + "/plans/suggestions")
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(request))
        .andExpect(status().isUnprocessableEntity());

    assertThat(plans.findAllByRecipient_IdOrderByVersionNumberDesc(UUID.fromString(recipientId))).isEmpty();
  }

  @Test
  void preservesExistingFreeTextSuggestionRequest() throws Exception {
    TestCaregiver owner = register("plan-free-text-compatible@example.com");
    String recipientId = createRecipient(owner.token());
    String originalContent = "Caregiver note: follow up after breakfast.\nKeep this wording.";
    MvcResult result = mvc.perform(post(PLANS + recipientId + "/plans/suggestions")
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(Map.of(
                "content", originalContent,
                "sourceSummaryId", "summary-text",
                "evidenceNoteIds", List.of("note-1")))))
        .andExpect(status().isCreated())
        .andReturn();
    JsonNode created = mapper.readTree(result.getResponse().getContentAsByteArray());

    assertThat(created.get("content").asText()).isEqualTo(originalContent);
    assertThat(created.get("status").asText()).isEqualTo("SUGGESTED");
    assertThat(created.get("sourceType").asText()).isEqualTo("CAREGIVER_SUBMITTED");
    assertThat(created.get("structuredContent").isNull()).isTrue();
    CarePlan stored = plans.findById(UUID.fromString(created.get("id").asText())).orElseThrow();
    assertThat(stored.getContent()).isEqualTo(originalContent);
    assertThat(documents.read(stored)).isNull();
  }

  @Test
  void keepsMalformedCaregiverContentOpaqueInHistory() throws Exception {
    TestCaregiver owner = register("plan-malformed-caregiver-content@example.com");
    String recipientId = createRecipient(owner.token());
    List<String> originalContents = List.of(
        "{\"schemaVersion\":1,\"changes\":[}",
        """
        {"schemaVersion":2,"changes":[{"action":"SCHEDULE_CHECK_IN","params":{"withinDays":"3"},"reason":"Review notes","evidenceNoteIds":["note-1"]}],"medications":[]}
        """,
        """
        {"schemaVersion":1,"changes":[{"action":"PRESCRIBE_MEDICATION","params":{"drug":"medicine"},"reason":"Review notes","evidenceNoteIds":["note-1"]}],"medications":[]}
        """);
    List<String> ids = List.of(
        suggestOpaque(owner.token(), recipientId, originalContents.get(0)).get("id").asText(),
        suggestOpaque(owner.token(), recipientId, originalContents.get(1)).get("id").asText(),
        suggestOpaque(owner.token(), recipientId, originalContents.get(2)).get("id").asText());

    MvcResult result = mvc.perform(get(PLANS + recipientId + "/plans/history")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andReturn();

    JsonNode history = mapper.readTree(result.getResponse().getContentAsByteArray());
    assertThat(history).hasSize(3);
    for (int i = 0; i < originalContents.size(); i++) {
      int historyIndex = originalContents.size() - i - 1;
      assertThat(history.get(historyIndex).get("id").asText()).isEqualTo(ids.get(i));
      assertThat(history.get(historyIndex).get("content").asText()).isEqualTo(originalContents.get(i));
      assertThat(history.get(historyIndex).get("structuredContent").isNull()).isTrue();
      assertThat(plans.findById(UUID.fromString(ids.get(i))).orElseThrow().getContent())
          .isEqualTo(originalContents.get(i));
    }
  }

  @Test
  void opaqueLegacyContentCannotBypassProtectedEditing() throws Exception {
    TestCaregiver owner = register("plan-edit@example.com");
    String recipientId = createRecipient(owner.token());
    JsonNode original = suggest(owner.token(), recipientId, "AI suggestion", "summary-edit");

    mvc.perform(post(path(recipientId, original.get("id").asText(), "edit-and-accept"))
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\"Caregiver-edited plan\",\"evidenceNoteIds\":[\"note-1\"]}"))
        .andExpect(status().isUnprocessableEntity());

    mvc.perform(get(PLANS + recipientId + "/plans/history")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].content").value("AI suggestion"))
        .andExpect(jsonPath("$[0].status").value("SUGGESTED"));
  }

  @Test
  void malformedCaregiverContentCannotBeEditedAsStructured() throws Exception {
    TestCaregiver owner = register("plan-malformed-edit@example.com");
    String recipientId = createRecipient(owner.token());
    JsonNode original = suggestOpaque(owner.token(), recipientId, "{\"schemaVersion\":1,\"changes\":[}");
    String planId = original.get("id").asText();

    mvc.perform(post(path(recipientId, planId, "edit-and-accept"))
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"expectedVersion":1,"changes":[{"index":0,"params":{"withinDays":"5"}}]}
                """))
        .andExpect(status().isUnprocessableEntity());

    mvc.perform(get(PLANS + recipientId + "/plans/history")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(planId))
        .andExpect(jsonPath("$[0].content").value("{\"schemaVersion\":1,\"changes\":[}"))
        .andExpect(jsonPath("$[0].status").value("SUGGESTED"))
        .andExpect(jsonPath("$[0].structuredContent").doesNotExist());
  }

  @Test
  void editAndAcceptCreatesANewVersionWithoutChangingSourceContent() throws Exception {
    TestCaregiver owner = register("plan-edit-version@example.com");
    String recipientId = createRecipient(owner.token());
    JsonNode original = saveStructuredSuggestion(recipientId);
    String originalContent = original.get("content").asText();
    String originalId = original.get("id").asText();

    mvc.perform(post(path(recipientId, originalId, "edit-and-accept"))
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"expectedVersion":1,"changes":[{"index":0,"params":{"activity":"walk indoors","frequency":"daily"}}]}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("EDITED_AND_ACCEPTED"))
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.sourcePlanId").value(originalId))
        .andExpect(jsonPath("$.structuredContent.changes[0].params.activity").value("walk indoors"));

    JsonNode history = mapper.readTree(mvc.perform(get(PLANS + recipientId + "/plans/history")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString());
    assertThat(history.size()).isEqualTo(2);
    assertThat(history.get(0).get("version").asInt()).isEqualTo(2);
    assertThat(history.get(0).get("sourcePlanId").asText()).isEqualTo(originalId);
    assertThat(history.get(0).get("sourceSummaryId").asText()).isEqualTo("summary-edit-version");
    assertThat(history.get(0).get("evidenceNoteIds").asText()).isEqualTo("note-1");
    assertThat(history.get(1).get("version").asInt()).isEqualTo(1);
    assertThat(history.get(1).get("status").asText()).isEqualTo("ARCHIVED");
    assertThat(history.get(1).get("content").asText()).isEqualTo(originalContent);
    assertThat(history.get(1).get("sourcePlanId").isNull()).isTrue();
    assertThat(history.get(1).get("sourceSummaryId").asText()).isEqualTo("summary-edit-version");
    assertThat(history.get(1).get("evidenceNoteIds").asText()).isEqualTo("note-1");
  }

  @Test
  void historicalVersionRejectsAnotherEdit() throws Exception {
    TestCaregiver owner = register("plan-historical-edit@example.com");
    String recipientId = createRecipient(owner.token());
    JsonNode first = suggest(owner.token(), recipientId, "First suggestion", null);
    mvc.perform(post(path(recipientId, first.get("id").asText(), "accept"))
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk());

    JsonNode second = suggest(owner.token(), recipientId, "Replacement suggestion", null);
    mvc.perform(post(path(recipientId, second.get("id").asText(), "accept"))
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk());

    mvc.perform(post(path(recipientId, first.get("id").asText(), "edit-and-accept"))
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"expectedVersion\":1,\"changes\":[]}"))
        .andExpect(status().isConflict());
    mvc.perform(post(path(recipientId, first.get("id").asText(), "accept"))
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isConflict());

    mvc.perform(get(PLANS + recipientId + "/plans/history")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[1].id").value(first.get("id").asText()))
        .andExpect(jsonPath("$[1].content").value("First suggestion"))
        .andExpect(jsonPath("$[1].status").value("ARCHIVED"));
  }

  @Test
  void dismissedVersionRemainsVisibleAndCannotBeAccepted() throws Exception {
    TestCaregiver owner = register("plan-dismissed-version@example.com");
    String recipientId = createRecipient(owner.token());
    JsonNode dismissed = suggest(owner.token(), recipientId, "Keep this dismissed plan", null);
    String planId = dismissed.get("id").asText();

    mvc.perform(post(path(recipientId, planId, "dismiss"))
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISMISSED"));
    mvc.perform(post(path(recipientId, planId, "accept"))
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isConflict());
    mvc.perform(post(path(recipientId, planId, "edit-and-accept"))
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"expectedVersion\":1,\"changes\":[]}"))
        .andExpect(status().isConflict());

    mvc.perform(get(PLANS + recipientId + "/plans/history")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].id").value(planId))
        .andExpect(jsonPath("$[0].content").value("Keep this dismissed plan"))
        .andExpect(jsonPath("$[0].status").value("DISMISSED"));
  }

  @Test
  void crossCaregiverCannotReadOrChangeAnotherCaregiversPlans() throws Exception {
    TestCaregiver owner = register("plan-owner2@example.com");
    TestCaregiver other = register("plan-other@example.com");
    String recipientId = createRecipient(owner.token());
    JsonNode suggestion = suggest(owner.token(), recipientId, "Private plan", null);

    mvc.perform(get(PLANS + recipientId + "/plans/history")
            .header("Authorization", bearer(other.token())))
        .andExpect(status().isNotFound());
    mvc.perform(post(path(recipientId, suggestion.get("id").asText(), "accept"))
            .header("Authorization", bearer(other.token())))
        .andExpect(status().isNotFound());
  }

  @Test
  void illegalRepeatedTransitionIsRejectedAndWriteIsAudited() throws Exception {
    TestCaregiver owner = register("plan-state@example.com");
    String recipientId = createRecipient(owner.token());
    JsonNode suggestion = suggest(owner.token(), recipientId, "State test", null);
    String id = suggestion.get("id").asText();

    mvc.perform(post(path(recipientId, id, "accept")).header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk());
    mvc.perform(post(path(recipientId, id, "dismiss")).header("Authorization", bearer(owner.token())))
        .andExpect(status().isConflict());

    mvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"plan-state@example.com\",\"password\":\"password123\"}"))
        .andExpect(status().isOk());

    assertThat(auditEventsFor(owner.id()))
        .extracting(AuditEvent::getEventType)
        .contains("CAREGIVER_REGISTERED", "CAREGIVER_LOGIN_SUCCEEDED", "PLAN_ACCEPTED")
        .doesNotContain("PLAN_DISMISSED");

    assertThat(auditEventsFor(owner.id())).anySatisfy(event -> {
      assertThat(event.getActorCaregiverId()).isEqualTo(owner.id());
      assertThat(event.getEventType()).isEqualTo("PLAN_ACCEPTED");
      assertThat(event.getTargetId()).isEqualTo(id);
    });
  }

  @Test
  void versionsIncreaseMonotonicallyAndDismissedVersionsRemainInHistory() throws Exception {
    TestCaregiver owner = register("plan-versioning@example.com");
    String recipientId = createRecipient(owner.token());

    JsonNode first = suggest(owner.token(), recipientId, "Version one", null);
    mvc.perform(post(path(recipientId, first.get("id").asText(), "dismiss"))
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DISMISSED"));

    JsonNode second = suggest(owner.token(), recipientId, "Version two", null);
    assertThat(first.get("version").asInt()).isEqualTo(1);
    assertThat(second.get("version").asInt()).isEqualTo(2);

    mvc.perform(get(PLANS + recipientId + "/plans/history")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].version").value(2))
        .andExpect(jsonPath("$[1].version").value(1))
        .andExpect(jsonPath("$[1].status").value("DISMISSED"));
  }

  @Test
  void unknownRecipientAndUnknownPlanIdsReturnNotFound() throws Exception {
    TestCaregiver owner = register("plan-invalid-ids@example.com");
    String unknownRecipientId = UUID.randomUUID().toString();
    String unknownPlanId = UUID.randomUUID().toString();

    mvc.perform(get(PLANS + unknownRecipientId + "/plans/history")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isNotFound());

    String recipientId = createRecipient(owner.token());
    mvc.perform(post(path(recipientId, unknownPlanId, "accept"))
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isNotFound());
  }

  @Test
  void concurrentAcceptanceLeavesExactlyOneActiveAcceptedPlan() throws Exception {
    TestCaregiver owner = register("plan-concurrent@example.com");
    String recipientId = createRecipient(owner.token());
    JsonNode first = suggest(owner.token(), recipientId, "Concurrent plan A", null);
    JsonNode second = suggest(owner.token(), recipientId, "Concurrent plan B", null);

    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Integer> acceptFirst = executor.submit(() -> {
        start.await();
        return acceptStatus(owner.token(), recipientId, first.get("id").asText());
      });
      Future<Integer> acceptSecond = executor.submit(() -> {
        start.await();
        return acceptStatus(owner.token(), recipientId, second.get("id").asText());
      });
      start.countDown();

      assertThat(acceptFirst.get(15, TimeUnit.SECONDS)).isEqualTo(200);
      assertThat(acceptSecond.get(15, TimeUnit.SECONDS)).isEqualTo(200);
      var history = plans.findAllByRecipient_IdOrderByVersionNumberDesc(UUID.fromString(recipientId));
      assertThat(history)
          .filteredOn(plan -> plan.getStatus().isActiveAccepted())
          .hasSize(1);
      assertThat(history).hasSize(2)
          .extracting(CarePlan::getVersionNumber)
          .containsExactly(2, 1);
      assertThat(history).extracting(CarePlan::getContent)
          .containsExactlyInAnyOrder("Concurrent plan A", "Concurrent plan B");
      assertThat(history).extracting(CarePlan::getStatus)
          .containsExactlyInAnyOrder(PlanStatus.ACCEPTED, PlanStatus.ARCHIVED);
      assertThat(jdbcTemplate.queryForObject(
          "select count(*) from care_plan_versions where recipient_id = ? and active_accepted_key = true",
          Integer.class, UUID.fromString(recipientId))).isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }

  private PlanDocument planDocument(List<MedicationEntry> medications) {
    return new PlanDocument(1,
        List.of(new SuggestedPlanChange(PlanAction.SCHEDULE_CHECK_IN, Map.of("withinDays", "3"),
            "Review recorded notes", List.of("note-1"))),
        medications);
  }

  private CarePlan saveAcceptedBaseline(String recipientId, int version, PlanDocument document) {
    CareRecipient recipient = recipients.findById(UUID.fromString(recipientId)).orElseThrow();
    return plans.saveAndFlush(new CarePlan(recipient, version, PlanStatus.ACCEPTED,
        documents.write(document), "baseline-summary", null, "note-1", PlanSource.AI_GENERATED));
  }

  private JsonNode submitStructured(String token, String recipientId, PlanDocument document) throws Exception {
    MvcResult result = submitStructuredRequest(token, recipientId, document);
    assertThat(result.getResponse().getStatus()).isEqualTo(201);
    return mapper.readTree(result.getResponse().getContentAsByteArray());
  }

  private MvcResult submitStructuredRequest(String token, String recipientId, PlanDocument document) throws Exception {
    return mvc.perform(post(PLANS + recipientId + "/plans/suggestions")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(Map.of("content", "", "structuredContent", document))))
        .andReturn();
  }

  private long suggestionAuditCount(UUID actorId) {
    return auditEvents.findAllByActorCaregiverIdOrderByOccurredAtDesc(actorId).stream()
        .filter(event -> event.getEventType().equals("PLAN_SUGGESTED"))
        .count();
  }

  private JsonNode saveStructuredSuggestion(String recipientId) throws Exception {
    var recipient = recipients.findById(UUID.fromString(recipientId)).orElseThrow();
    var document = new PlanDocument(1,
        List.of(new SuggestedPlanChange(PlanAction.LOG_MOBILITY_GOAL,
            Map.of("activity", "walk outside", "frequency", "daily"),
            "Encourage safe movement", List.of("note-1"))),
        List.of());
    CarePlan plan = plans.saveAndFlush(new CarePlan(recipient, 1, PlanStatus.SUGGESTED,
        documents.write(document), "summary-edit-version", null, "note-1", PlanSource.AI_GENERATED));
    return mapper.valueToTree(PlanResponse.from(plan, document));
  }

  private int acceptStatus(String token, String recipientId, String planId) throws Exception {
    return mvc.perform(post(path(recipientId, planId, "accept"))
            .header("Authorization", bearer(token)))
        .andReturn().getResponse().getStatus();
  }

  private java.util.List<AuditEvent> auditEventsFor(UUID actorId) {
    return auditEvents.findAllByActorCaregiverIdOrderByOccurredAtDesc(actorId);
  }

  private JsonNode suggest(String token, String recipientId, String content, String summaryId) throws Exception {
    String summary = summaryId == null ? "" : ",\"sourceSummaryId\":\"" + summaryId + "\"";
    MvcResult result = mvc.perform(post(PLANS + recipientId + "/plans/suggestions")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\"" + content + "\"" + summary + ",\"evidenceNoteIds\":[\"note-1\"]}"))
        .andExpect(status().isCreated())
        .andReturn();
    return mapper.readTree(result.getResponse().getContentAsString());
  }

  private JsonNode suggestOpaque(String token, String recipientId, String content) throws Exception {
    MvcResult result = mvc.perform(post(PLANS + recipientId + "/plans/suggestions")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(Map.of("content", content, "evidenceNoteIds", List.of("note-1")))))
        .andExpect(status().isCreated())
        .andReturn();
    return mapper.readTree(result.getResponse().getContentAsString());
  }

  private String createRecipient(String token) throws Exception {
    MvcResult result = mvc.perform(post("/api/recipients")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Plan recipient\"}"))
        .andExpect(status().isCreated())
        .andReturn();
    return mapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private TestCaregiver register(String email) throws Exception {
    MvcResult result = mvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
        .andExpect(status().isCreated())
        .andReturn();
    String token = mapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    return new TestCaregiver(token, jwtService.parse(token).caregiverId());
  }

  private static String path(String recipientId, String planId, String action) {
    return PLANS + recipientId + "/plans/" + planId + "/" + action;
  }

  private static String bearer(String token) { return "Bearer " + token; }
  private record TestCaregiver(String token, UUID id) {}
  private record MedicationDraftCase(String name, List<MedicationEntry> baseline,
      List<MedicationEntry> proposed, boolean hasBaseline) {}
}
