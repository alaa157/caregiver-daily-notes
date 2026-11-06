package com.caregiver.ai;

import com.caregiver.config.CaregiverApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = CaregiverApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:safetyackhttp;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "auth.jwt.secret=synthetic-safety-ack-test-secret-32-bytes"})
@AutoConfigureMockMvc
class SafetyAcknowledgementControllerTest {
  private static final String PERIOD_END = "2026-10-22";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @MockBean LlmClient llm;
  private String owner;
  private String recipientId;

  @BeforeEach
  void setup() throws Exception {
    for (String table : List.of("safety_acknowledgements", "care_plan_versions", "note_addenda",
        "daily_notes", "audit_events", "care_recipients", "caregivers")) {
      jdbc.update("delete from " + table);
    }
    owner = register();
    recipientId = json(call("/api/recipients", owner, Map.of("name", "Synthetic recipient"))
        .andExpect(status().isCreated())).get("id").asText();
    when(llm.complete(any())).thenThrow(new IllegalStateException("synthetic provider failure"));
  }

  @Test
  void createsAcknowledgementForOwnedRecipient() throws Exception {
    JsonNode response = json(call(acknowledgementPath(recipientId), owner, request())
        .andExpect(status().isCreated()));

    assertThat(response.path("id").asText()).isNotBlank();
    assertThat(response.path("recipientId").asText()).isEqualTo(recipientId);
    assertThat(response.path("ruleId").asText()).isEqualTo("FALL_DETECTED");
    assertThat(response.path("periodEndInclusive").asText()).isEqualTo(PERIOD_END);
    assertThat(response.path("periodDays").asInt()).isEqualTo(7);
    assertThat(response.path("actorCaregiverId").asText())
        .isEqualTo(jdbc.queryForObject(
            "select caregiver_id from care_recipients where id = ?",
            String.class, UUID.fromString(recipientId)));
    assertThat(response.path("acknowledgedAt").asText()).isNotBlank();
    assertThat(response.size()).isEqualTo(7);
  }

  @Test
  void returnsAcknowledgementForSelectedPeriod() throws Exception {
    call(acknowledgementPath(recipientId), owner, request()).andExpect(status().isCreated());

    mvc.perform(get(acknowledgementPath(recipientId))
            .header("Authorization", "Bearer " + owner)
            .param("periodEndInclusive", PERIOD_END)
            .param("periodDays", "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].recipientId").value(recipientId))
        .andExpect(jsonPath("$[0].ruleId").value("FALL_DETECTED"))
        .andExpect(jsonPath("$[0].periodEndInclusive").value(PERIOD_END))
        .andExpect(jsonPath("$[0].periodDays").value(7));

    mvc.perform(get(acknowledgementPath(recipientId))
            .header("Authorization", "Bearer " + owner)
            .param("periodEndInclusive", PERIOD_END)
            .param("periodDays", "14"))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));
  }

  @Test
  void rejectsAcknowledgementForForeignRecipient() throws Exception {
    String foreignOwner = register();
    String foreignRecipientId = json(call("/api/recipients", foreignOwner, Map.of("name", "Other synthetic recipient"))
        .andExpect(status().isCreated())).get("id").asText();

    call(acknowledgementPath(foreignRecipientId), owner, request()).andExpect(status().isNotFound());
    mvc.perform(get(acknowledgementPath(foreignRecipientId))
            .header("Authorization", "Bearer " + owner)
            .param("periodEndInclusive", PERIOD_END)
            .param("periodDays", "7"))
        .andExpect(status().isNotFound());

    assertThat(jdbc.queryForObject(
        "select count(*) from safety_acknowledgements where recipient_id = ?",
        Long.class, UUID.fromString(foreignRecipientId))).isZero();
  }

  @Test
  void repeatedAcknowledgementReturnsTheSameRecord() throws Exception {
    JsonNode first = json(call(acknowledgementPath(recipientId), owner, request())
        .andExpect(status().isCreated()));
    JsonNode second = json(call(acknowledgementPath(recipientId), owner, request())
        .andExpect(status().isCreated()));

    assertThat(second.path("id").asText()).isEqualTo(first.path("id").asText());
    assertThat(jdbc.queryForObject(
        "select count(*) from safety_acknowledgements where recipient_id = ?",
        Long.class, UUID.fromString(recipientId))).isEqualTo(1L);
  }

  @Test
  void acknowledgementDoesNotRemoveSafetySignal() throws Exception {
    call("/api/notes", owner, Map.of(
        "recipientId", recipientId,
        "date", PERIOD_END,
        "freeText", "Synthetic fall report.",
        "fallReported", true)).andExpect(status().isCreated());
    call(acknowledgementPath(recipientId), owner, request()).andExpect(status().isCreated());

    JsonNode summary = json(call("/api/summaries", owner, Map.of(
        "recipientId", recipientId,
        "periodEndInclusive", PERIOD_END,
        "periodDays", 7)).andExpect(status().isOk()));

    assertThat(summary.path("safety").path("signals").toString()).contains("FALL_DETECTED");
  }

  @Test
  void rejectsUnsupportedPeriodsBeforePersistence() throws Exception {
    call(acknowledgementPath(recipientId), owner, Map.of(
        "ruleId", "FALL_DETECTED",
        "periodEndInclusive", PERIOD_END,
        "periodDays", 9)).andExpect(status().isUnprocessableEntity());

    assertThat(jdbc.queryForObject(
        "select count(*) from safety_acknowledgements where recipient_id = ?",
        Long.class, UUID.fromString(recipientId))).isZero();
  }

  @Test
  void bothRoutesRequireAuthentication() throws Exception {
    call(acknowledgementPath(recipientId), null, request()).andExpect(status().isUnauthorized());
    mvc.perform(get(acknowledgementPath(recipientId))
            .param("periodEndInclusive", PERIOD_END)
            .param("periodDays", "7"))
        .andExpect(status().isUnauthorized());
  }

  private String acknowledgementPath(String id) {
    return "/api/recipients/" + id + "/safety-acknowledgements";
  }

  private Map<String, Object> request() {
    return Map.of("ruleId", "FALL_DETECTED", "periodEndInclusive", PERIOD_END, "periodDays", 7);
  }

  private String register() throws Exception {
    return json(call("/api/auth/register", null, Map.of(
        "email", "safety-ack-" + UUID.randomUUID() + "@example.com",
        "password", "Synthetic-password-123")).andExpect(status().isCreated())).get("token").asText();
  }

  private ResultActions call(String path, String token, Object body) throws Exception {
    var request = post(path);
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    if (body != null) {
      request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
    }
    return mvc.perform(request);
  }

  private JsonNode json(ResultActions response) throws Exception {
    return mapper.readTree(response.andReturn().getResponse().getContentAsString());
  }
}
