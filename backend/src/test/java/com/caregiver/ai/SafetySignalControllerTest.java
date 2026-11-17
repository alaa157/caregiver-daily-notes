package com.caregiver.ai;

import com.caregiver.config.CaregiverApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Provider-free safety endpoint: the deterministic rules must work with no AI key at all. */
@SpringBootTest(classes = CaregiverApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:safetysignalshttp;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "auth.jwt.secret=synthetic-safety-signals-test-secret-32-bytes"})
@AutoConfigureMockMvc
class SafetySignalControllerTest {
  private static final String PERIOD_END = "2026-10-26";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @MockBean LlmClient llm;
  private String owner;
  private String other;
  private String recipientId;

  @BeforeEach
  void setup() throws Exception {
    for (String table : new String[] {"safety_acknowledgements", "note_addenda", "daily_notes",
        "audit_events", "care_recipients", "caregivers"}) {
      jdbc.update("delete from " + table);
    }
    owner = register("safety-signals-owner@example.com");
    other = register("safety-signals-other@example.com");
    recipientId = createRecipient(owner);
  }

  @Test
  void fallAndHighPainAreFlaggedWithoutAnyProviderCredentialsOrModelCall() throws Exception {
    createNote(owner, recipientId, PERIOD_END, true, 9);
    // No credential header, no BYOK body, and the LLM bean is never touched.
    mvc.perform(get(path(recipientId))
            .header("Authorization", bearer(owner))
            .param("periodEndInclusive", PERIOD_END)
            .param("periodDays", "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.needsDoctorBanner").value(true))
        .andExpect(jsonPath("$.signals[*].ruleId").value(
            org.hamcrest.Matchers.hasItems("FALL_DETECTED", "HIGH_PAIN")))
        .andExpect(jsonPath("$.signals[?(@.ruleId == 'HIGH_PAIN')].severity").value(
            org.hamcrest.Matchers.hasItem("HIGH")));
    verifyNoInteractions(llm);
  }

  @Test
  void noNotesProducesNoSignalsAndNoDoctorBanner() throws Exception {
    mvc.perform(get(path(recipientId))
            .header("Authorization", bearer(owner))
            .param("periodEndInclusive", PERIOD_END)
            .param("periodDays", "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.signals").isEmpty())
        .andExpect(jsonPath("$.needsDoctorBanner").value(false));
    verifyNoInteractions(llm);
  }

  @Test
  void anotherCaregiverCannotReadTheRecipientsSignals() throws Exception {
    createNote(owner, recipientId, PERIOD_END, true, 9);
    mvc.perform(get(path(recipientId))
            .header("Authorization", bearer(other))
            .param("periodEndInclusive", PERIOD_END)
            .param("periodDays", "7"))
        .andExpect(status().isNotFound());
    verifyNoInteractions(llm);
  }

  @Test
  void unsupportedPeriodIsRejected() throws Exception {
    mvc.perform(get(path(recipientId))
            .header("Authorization", bearer(owner))
            .param("periodEndInclusive", PERIOD_END)
            .param("periodDays", "5"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(llm);
  }

  private String path(String id) {
    return "/api/recipients/" + id + "/safety-signals";
  }

  private String register(String email) throws Exception {
    MvcResult result = mvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
        .andExpect(status().isCreated())
        .andReturn();
    return mapper.readTree(result.getResponse().getContentAsByteArray()).get("token").asText();
  }

  private String createRecipient(String token) throws Exception {
    MvcResult result = mvc.perform(post("/api/recipients")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Safety signals recipient\"}"))
        .andExpect(status().isCreated())
        .andReturn();
    return mapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asText();
  }

  private void createNote(String token, String recipient, String date, boolean fall, int pain) throws Exception {
    String body = "{\"recipientId\":\"" + recipient + "\",\"date\":\"" + date + "\","
        + "\"mood\":\"good\",\"appetite\":\"normal\",\"sleep\":\"good\",\"mobility\":\"normal\","
        + "\"medicationStatus\":\"TAKEN\",\"medicationVerified\":true,"
        + "\"painScore\":" + pain + ",\"fallReported\":" + fall + ",\"freeText\":\"Evening note\"}";
    mvc.perform(post("/api/notes")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated());
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }
}
