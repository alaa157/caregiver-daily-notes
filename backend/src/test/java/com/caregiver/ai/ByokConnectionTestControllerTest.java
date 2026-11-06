package com.caregiver.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.caregiver.config.CaregiverApplication;
import com.caregiver.notes.DailyNoteRepository;
import com.caregiver.recipient.CareRecipientRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest(classes = CaregiverApplication.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:byokconnectiontest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.jpa.hibernate.ddl-auto=validate",
    "auth.jwt.secret=synthetic-byok-connection-test-secret"})
@AutoConfigureMockMvc
class ByokConnectionTestControllerTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  @MockBean LlmClient llm;
  @MockBean DailyNoteRepository notes;
  @MockBean CareRecipientRepository recipients;

  private String caregiverToken;

  @BeforeEach
  void setup() throws Exception {
    caregiverToken = register();
    when(llm.complete(any())).thenReturn(
        new LlmResult(LlmStatus.OK, "{\"connected\":true}", null));
    clearInvocations(llm, notes, recipients);
  }

  @Test
  void testsProviderConnectionWithoutLoadingCareData() throws Exception {
    call(caregiverToken, credentials("synthetic-secret-123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));

    var captured = org.mockito.ArgumentCaptor.forClass(LlmRequest.class);
    verify(llm).complete(captured.capture());
    assertThat(captured.getValue().systemPrompt()).isEqualTo("Return a brief JSON connection confirmation.");
    assertThat(captured.getValue().dataBlock()).isEqualTo("connection test ping");
    verifyNoInteractions(notes, recipients);
  }

  @Test
  void returnsSafeSuccessResponseForValidProviderResponse() throws Exception {
    String body = call(caregiverToken, credentials("synthetic-secret-123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.providerId").value("GEMINI"))
        .andExpect(jsonPath("$.model").value("gemini-3.8-flash"))
        .andReturn().getResponse().getContentAsString();

    JsonNode response = mapper.readTree(body);
    assertThat(response.fieldNames()).toIterable()
        .containsExactlyInAnyOrder("success", "providerId", "model", "errorCode");
    assertThat(response.path("errorCode").isNull()).isTrue();
    assertThat(body).doesNotContain("apiKey", "synthetic-secret-123");
  }

  @Test
  void returnsSanitizedFailureForInvalidCredentials() throws Exception {
    when(llm.complete(any())).thenReturn(
        new LlmResult(LlmStatus.FALLBACK, "", "invalid key: synthetic provider diagnostic"));

    call(caregiverToken, credentials("invalid-synthetic-key"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value("provider_unavailable"));
  }

  @Test
  void doesNotIncludeKeyOrProviderResponseInErrorBody() throws Exception {
    when(llm.complete(any())).thenReturn(
        new LlmResult(LlmStatus.FALLBACK, "", "synthetic-provider-response: key rejected"));

    String body = call(caregiverToken, credentials("synthetic-secret-123"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value("provider_unavailable"))
        .andReturn().getResponse().getContentAsString();

    assertThat(body).doesNotContain("synthetic-secret-123", "synthetic-provider-response", "key rejected");
  }

  @Test
  void requiresAuthenticatedCaregiverSession() throws Exception {
    mvc.perform(post("/api/ai/byok/connection-test")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(credentials("synthetic-secret-123"))))
        .andExpect(status().isUnauthorized());

    verifyNoInteractions(llm, notes, recipients);
  }

  @Test
  void rejectsUnsupportedProviderBeforeCallingProvider() throws Exception {
    Map<String, Object> input = Map.of("credentials", Map.of(
        "providerId", "ANTHROPIC",
        "model", "synthetic-model",
        "apiKey", "synthetic-secret-123"));

    String body = call(caregiverToken, input)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.providerId").doesNotExist())
        .andExpect(jsonPath("$.errorCode").value("invalid_provider"))
        .andReturn().getResponse().getContentAsString();

    assertThat(body).doesNotContain("synthetic-secret-123");
    verifyNoInteractions(llm);
  }

  @Test
  void returnsSafeFailureForMalformedCredentialBody() throws Exception {
    String body = mvc.perform(post("/api/ai/byok/connection-test")
            .header("Authorization", "Bearer " + caregiverToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"credentials\":\"synthetic-secret-123\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorCode").value("invalid_provider"))
        .andReturn().getResponse().getContentAsString();

    assertThat(body).doesNotContain("synthetic-secret-123");
    verifyNoInteractions(llm);
  }

  private Map<String, Object> credentials(String apiKey) {
    return Map.of("credentials", Map.of(
        "providerId", "GEMINI",
        "model", "gemini-3.8-flash",
        "apiKey", apiKey));
  }

  private String register() throws Exception {
    JsonNode response = mapper.readTree(mvc.perform(post("/api/auth/register")
        .contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(Map.of(
            "email", "byok-" + UUID.randomUUID() + "@example.com",
            "password", "Synthetic-password-123"))))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString());
    return response.path("token").asText();
  }

  private ResultActions call(String token, Object body) throws Exception {
    return mvc.perform(post("/api/ai/byok/connection-test")
        .header("Authorization", "Bearer " + token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsString(body)));
  }
}
