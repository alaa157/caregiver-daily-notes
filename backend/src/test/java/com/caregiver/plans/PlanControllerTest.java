package com.caregiver.plans;

import com.caregiver.audit.AuditEventRepository;
import com.caregiver.auth.CaregiverRepository;
import com.caregiver.config.CaregiverApplication;
import com.caregiver.auth.JwtService;
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

import java.util.UUID;

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
  @Autowired private CareRecipientRepository recipients;
  @Autowired private CaregiverRepository caregivers;
  @Autowired private AuditEventRepository auditEvents;

  @BeforeEach
  void cleanDatabase() {
    plans.deleteAll();
    auditEvents.deleteAll();
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
  void editedAcceptanceCreatesNewVersionAndPreservesOriginalSuggestion() throws Exception {
    TestCaregiver owner = register("plan-edit@example.com");
    String recipientId = createRecipient(owner.token());
    JsonNode original = suggest(owner.token(), recipientId, "AI suggestion", "summary-edit");

    mvc.perform(post(path(recipientId, original.get("id").asText(), "edit-and-accept"))
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"content\":\"Caregiver-edited plan\",\"evidenceNoteIds\":[\"note-1\"]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2))
        .andExpect(jsonPath("$.status").value("EDITED_AND_ACCEPTED"))
        .andExpect(jsonPath("$.sourcePlanId").value(original.get("id").asText()));

    mvc.perform(get(PLANS + recipientId + "/plans/history")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[1].content").value("AI suggestion"))
        .andExpect(jsonPath("$[1].status").value("ARCHIVED"));
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

    assertThat(auditEvents.findAll()).anySatisfy(event -> {
      assertThat(event.getActorCaregiverId()).isEqualTo(owner.id());
      assertThat(event.getEventType()).isEqualTo("PLAN_ACCEPTED");
      assertThat(event.getTargetId()).isEqualTo(id);
    });
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
}
