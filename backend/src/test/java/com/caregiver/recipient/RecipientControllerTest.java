package com.caregiver.recipient;

import com.caregiver.auth.CaregiverRepository;
import com.caregiver.auth.JwtService;
import com.caregiver.config.CaregiverApplication;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = CaregiverApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:recipienttest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.flyway.enabled=true",
      "spring.flyway.placeholder-replacement=false",
      "spring.jpa.hibernate.ddl-auto=validate",
      "auth.jwt.secret=test-secret-that-is-definitely-32-bytes-long",
      "auth.jwt.expiry=24h"
    })
@AutoConfigureMockMvc
class RecipientControllerTest {

  private static final String RECIPIENTS = "/api/recipients";

  @Autowired private MockMvc mvc;
  @Autowired private CareRecipientRepository recipientRepository;
  @Autowired private CaregiverRepository caregiverRepository;
  @Autowired private JwtService jwtService;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void cleanDatabase() {
    recipientRepository.deleteAll();
    caregiverRepository.deleteAll();
  }

  @Test
  void ownerCanCreateListAndRetrieveRecipient() throws Exception {
    TestCaregiver owner = register("owner@example.com");
    MvcResult created = mvc.perform(post(RECIPIENTS)
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Samir Hassan\",\"relationship\":\"Father\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Samir Hassan"))
        .andExpect(jsonPath("$.relationship").value("Father"))
        .andExpect(jsonPath("$.active").value(true))
        .andReturn();
    String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

    mvc.perform(get(RECIPIENTS).header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(id));

    mvc.perform(get(RECIPIENTS + "/" + id).header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id));
  }

  @Test
  void listIsScopedToTheAuthenticatedCaregiver() throws Exception {
    TestCaregiver first = register("first@example.com");
    TestCaregiver second = register("second@example.com");
    String firstId = createRecipient(first.token(), "{\"name\":\"First recipient\"}");
    String secondId = createRecipient(second.token(), "{\"name\":\"Second recipient\"}");

    mvc.perform(get(RECIPIENTS).header("Authorization", bearer(first.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(firstId))
        .andExpect(jsonPath("$[1]").doesNotExist());

    mvc.perform(get(RECIPIENTS).header("Authorization", bearer(second.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(secondId))
        .andExpect(jsonPath("$[1]").doesNotExist());
  }

  @Test
  void crossCaregiverCannotGetUpdateOrDeactivateRecipient() throws Exception {
    TestCaregiver owner = register("owner2@example.com");
    TestCaregiver other = register("other@example.com");
    String id = createRecipient(owner.token(), "{\"name\":\"Private recipient\"}");

    mvc.perform(get(RECIPIENTS + "/" + id).header("Authorization", bearer(other.token())))
        .andExpect(status().isNotFound());
    mvc.perform(put(RECIPIENTS + "/" + id)
            .header("Authorization", bearer(other.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Tampered\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(patch(RECIPIENTS + "/" + id + "/deactivate")
            .header("Authorization", bearer(other.token())))
        .andExpect(status().isNotFound());

    mvc.perform(get(RECIPIENTS + "/" + id).header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Private recipient"))
        .andExpect(jsonPath("$.active").value(true));
  }

  @Test
  void requestCannotOverrideRecipientOwner() throws Exception {
    TestCaregiver owner = register("request-owner@example.com");
    TestCaregiver other = register("request-other@example.com");
    String body = "{\"name\":\"Owner-bound\",\"caregiverId\":\"" + other.id() + "\"}";
    String id = createRecipient(owner.token(), body);

    mvc.perform(get(RECIPIENTS + "/" + id).header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk());
    mvc.perform(get(RECIPIENTS + "/" + id).header("Authorization", bearer(other.token())))
        .andExpect(status().isNotFound());
  }

  @Test
  void ownerCanUpdateAndDeactivateWithoutDeletingRecipientHistory() throws Exception {
    TestCaregiver owner = register("lifecycle@example.com");
    String id = createRecipient(owner.token(), "{\"name\":\"Before\"}");

    mvc.perform(put(RECIPIENTS + "/" + id)
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"After\",\"relationship\":\"Mother\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("After"))
        .andExpect(jsonPath("$.relationship").value("Mother"));

    mvc.perform(patch(RECIPIENTS + "/" + id + "/deactivate")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));

    assertThat(recipientRepository.findById(UUID.fromString(id))).isPresent();

    mvc.perform(get(RECIPIENTS + "/" + id).header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));

    mvc.perform(put(RECIPIENTS + "/" + id)
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Should not update\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void unauthenticatedRequestsAreRejected() throws Exception {
    mvc.perform(get(RECIPIENTS))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

    mvc.perform(post(RECIPIENTS)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"No token\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void validationMalformedIdsAndUnknownIdsAreHandledSafely() throws Exception {
    TestCaregiver owner = register("validation@example.com");

    mvc.perform(post(RECIPIENTS)
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"   \"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    mvc.perform(post(RECIPIENTS)
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"" + "x".repeat(121) + "\"}"))
        .andExpect(status().isUnprocessableEntity());

    mvc.perform(get(RECIPIENTS + "/not-a-uuid").header("Authorization", bearer(owner.token())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ID"));

    mvc.perform(get(RECIPIENTS + "/" + UUID.randomUUID())
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isNotFound());
  }

  private TestCaregiver register(String email) throws Exception {
    MvcResult result = mvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
        .andExpect(status().isCreated())
        .andReturn();
    JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
    String token = json.get("token").asText();
    return new TestCaregiver(token, jwtService.parse(token).caregiverId());
  }

  private String createRecipient(String token, String body) throws Exception {
    MvcResult result = mvc.perform(post(RECIPIENTS)
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  private record TestCaregiver(String token, UUID id) {
  }
}
