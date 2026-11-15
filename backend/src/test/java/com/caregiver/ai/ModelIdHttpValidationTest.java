package com.caregiver.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caregiver.config.CaregiverApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/**
 * A malformed model ID must be reported as a model failure, never as a provider
 * failure, and must never reach the provider.
 */
@SpringBootTest(classes = CaregiverApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:modelidvalidation;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "auth.jwt.secret=synthetic-model-id-validation-secret"})
@AutoConfigureMockMvc
class ModelIdHttpValidationTest {
  private static final String SYNTHETIC_KEY = "synthetic-model-id-secret";

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @Autowired JdbcTemplate jdbc;
  @MockBean LlmClient llm;

  private String caregiverToken;
  private String recipientId;

  @BeforeEach
  void setup() throws Exception {
    for (String table : List.of("care_plan_versions", "note_addenda", "daily_notes",
        "audit_events", "care_recipients", "caregivers")) {
      jdbc.update("delete from " + table);
    }
    caregiverToken = register();
    recipientId = createRecipient();
    when(llm.complete(any())).thenReturn(
        new LlmResult(LlmStatus.OK, "{\"connected\":true}", null));
    clearInvocations(llm);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "a b", "../x", "x?y=1", "x#y", "-leading-hyphen", "/leading-slash"})
  @DisplayName("connection test reports invalid_model, not invalid_provider")
  void connectionTestReportsInvalidModel(String modelId) throws Exception {
    String body = call("/api/ai/byok/connection-test",
        connectionTestBody("GEMINI", modelId, SYNTHETIC_KEY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value("invalid_model"))
        .andReturn().getResponse().getContentAsString();

    assertThat(body).doesNotContain(SYNTHETIC_KEY, "invalid_provider");
    verifyNoInteractions(llm);
  }

  @Test
  @DisplayName("connection test still reports invalid_provider for an unsupported provider")
  void connectionTestStillReportsInvalidProvider() throws Exception {
    call("/api/ai/byok/connection-test",
        connectionTestBody("ANTHROPIC", "gemini-3.8-flash", SYNTHETIC_KEY))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errorCode").value("invalid_provider"));

    verifyNoInteractions(llm);
  }

  @Test
  @DisplayName("connection test accepts an approved model ID")
  void connectionTestAcceptsApprovedModelId() throws Exception {
    call("/api/ai/byok/connection-test",
        connectionTestBody("GEMINI", "gemini-3.8-flash", SYNTHETIC_KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "a b", "../x", "x?y=1", "x#y", "-leading-hyphen", "/leading-slash"})
  @DisplayName("BYOK summary returns 400 VALIDATION_ERROR for an invalid model ID")
  void byokSummaryReturnsValidationErrorForInvalidModel(String modelId) throws Exception {
    String body = call("/api/summaries/byok", byokBody(modelId))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andReturn().getResponse().getContentAsString();

    assertThat(body).doesNotContain(SYNTHETIC_KEY, "apiKey");
    verifyNoInteractions(llm);
  }

  @Test
  @DisplayName("BYOK summary still accepts an approved model ID")
  void byokSummaryAcceptsApprovedModelId() throws Exception {
    call("/api/summaries/byok", byokBody("gemini-3.8-flash"))
        .andExpect(status().isOk());
  }

  private Map<String, Object> connectionTestBody(String providerId, String model, String apiKey) {
    return Map.of("credentials", Map.of(
        "providerId", providerId,
        "model", model,
        "apiKey", apiKey));
  }

  private Map<String, Object> byokBody(String model) {
    return Map.of(
        "summary", Map.of(
            "recipientId", recipientId,
            "periodEndInclusive", "2026-10-26",
            "periodDays", 7),
        "credentials", Map.of(
            "providerId", "GEMINI",
            "model", model,
            "apiKey", SYNTHETIC_KEY));
  }

  private String createRecipient() throws Exception {
    var response = mapper.readTree(mvc.perform(post("/api/recipients")
        .header("Authorization", "Bearer " + caregiverToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(Map.of("name", "Synthetic model id recipient"))))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString());
    return response.path("id").asText();
  }

  private String register() throws Exception {
    var response = mapper.readTree(mvc.perform(post("/api/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(Map.of(
            "email", "model-id-" + UUID.randomUUID() + "@example.com",
            "password", "Synthetic-password-123"))))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString());
    return response.path("token").asText();
  }

  private ResultActions call(String path, Object body) throws Exception {
    return mvc.perform(post(path)
        .header("Authorization", "Bearer " + caregiverToken)
        .contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(body)));
  }
}
