package com.caregiver.medications;

import com.caregiver.auth.JwtService;
import com.caregiver.config.CaregiverApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = CaregiverApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:medicationintakecontroller;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "auth.jwt.secret=test-secret-that-is-definitely-32-bytes-long",
      "auth.jwt.expiry=24h"
    })
@AutoConfigureMockMvc
class MedicationIntakeControllerTest {
  private static final String BASE = "/api/recipients/";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private JwtService jwtService;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void createsIntakeEventForOwnedMedication() throws Exception {
    Fixture fixture = fixture();
    String occurredAt = "2026-10-22T08:30:00Z";
    JsonNode response = json(mvc.perform(post(path(fixture))
            .header("Authorization", bearer(fixture.caregiver().token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(request("TAKEN", occurredAt, "VERIFIED"))))
        .andExpect(status().isCreated())
        .andReturn());

    Set<String> responseFields = new HashSet<>();
    response.fieldNames().forEachRemaining(responseFields::add);
    assertThat(responseFields).containsExactlyInAnyOrder(
        "id", "medicationId", "recipientId", "intakeState", "occurredAt", "verificationState", "createdAt");
    assertThat(response.path("id").asText()).isNotBlank();
    assertThat(response.path("medicationId").asText()).isEqualTo(fixture.medicationId().toString());
    assertThat(response.path("recipientId").asText()).isEqualTo(fixture.recipientId().toString());
    assertThat(response.path("intakeState").asText()).isEqualTo("TAKEN");
    assertThat(response.path("occurredAt").asText()).isEqualTo(occurredAt);
    assertThat(response.path("verificationState").asText()).isEqualTo("VERIFIED");
    assertThat(response.path("createdAt").asText()).isNotBlank();
    assertThat(jdbc.queryForObject(
        "select caregiver_id from medication_intake_events where id = ?",
        UUID.class, UUID.fromString(response.path("id").asText()))).isEqualTo(fixture.caregiver().id());
  }

  @Test
  void listsIntakeEventsInChronologicalOrder() throws Exception {
    Fixture fixture = fixture();
    JsonNode later = createEvent(fixture, "TAKEN", "2026-10-22T10:00:00Z", "VERIFIED");
    JsonNode earlier = createEvent(fixture, "UNSURE", "2026-10-22T08:00:00Z", "NOT_ASSERTED");

    JsonNode events = json(mvc.perform(get(path(fixture))
            .header("Authorization", bearer(fixture.caregiver().token())))
        .andExpect(status().isOk())
        .andReturn());

    assertThat(events).hasSize(2);
    assertThat(events.get(0).path("id").asText()).isEqualTo(earlier.path("id").asText());
    assertThat(events.get(0).path("occurredAt").asText()).isEqualTo("2026-10-22T08:00:00Z");
    assertThat(events.get(1).path("id").asText()).isEqualTo(later.path("id").asText());
    assertThat(events.get(1).path("occurredAt").asText()).isEqualTo("2026-10-22T10:00:00Z");
  }

  @Test
  void rejectsEventCreationForForeignMedication() throws Exception {
    Fixture owner = fixture();
    Fixture foreign = fixture();

    mvc.perform(post(path(owner.recipientId(), foreign.medicationId()))
            .header("Authorization", bearer(owner.caregiver().token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(request("TAKEN", "2026-10-22T08:30:00Z", "VERIFIED"))))
        .andExpect(status().isNotFound());

    assertThat(jdbc.queryForObject(
        "select count(*) from medication_intake_events where medication_id = ?",
        Integer.class, foreign.medicationId())).isZero();
  }

  @Test
  void rejectsForeignRecipientForPostAndGetWithoutLeakingEvents() throws Exception {
    Fixture owner = fixture();
    Fixture foreign = fixture();
    JsonNode foreignEvent = createEvent(foreign, "TAKEN", "2026-10-22T08:30:00Z", "VERIFIED");
    long foreignEventCount = eventCount(foreign);

    JsonNode postError = json(mvc.perform(post(path(foreign))
            .header("Authorization", bearer(owner.caregiver().token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(request("TAKEN", "2026-10-23T08:30:00Z", "VERIFIED"))))
        .andExpect(status().isNotFound())
        .andReturn());
    assertThat(postError.toString()).doesNotContain(foreignEvent.path("id").asText());
    assertThat(eventCount(owner.caregiver().id(), foreign.recipientId(), foreign.medicationId())).isZero();
    assertThat(eventCount(foreign)).isEqualTo(foreignEventCount);

    JsonNode getError = json(mvc.perform(get(path(foreign))
            .header("Authorization", bearer(owner.caregiver().token())))
        .andExpect(status().isNotFound())
        .andReturn());
    assertThat(getError.toString()).doesNotContain(foreignEvent.path("id").asText(), "TAKEN");
    assertThat(eventCount(owner.caregiver().id(), foreign.recipientId(), foreign.medicationId())).isZero();
    assertThat(eventCount(foreign)).isEqualTo(foreignEventCount);
  }

  @Test
  void rejectsCrossRecipientMedicationPairForPostAndGet() throws Exception {
    CaregiverFixture caregiver = register();
    Fixture firstRecipient = fixture(caregiver);
    Fixture secondRecipient = fixture(caregiver);
    JsonNode event = createEvent(secondRecipient, "TAKEN", "2026-10-22T08:30:00Z", "VERIFIED");
    long eventCountBefore = eventCount(secondRecipient);

    JsonNode postError = json(mvc.perform(post(path(firstRecipient.recipientId(), secondRecipient.medicationId()))
            .header("Authorization", bearer(caregiver.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(request("TAKEN", "2026-10-23T08:30:00Z", "VERIFIED"))))
        .andExpect(status().isNotFound())
        .andReturn());
    assertThat(postError.toString()).doesNotContain(event.path("id").asText());
    assertThat(eventCount(caregiver.id(), firstRecipient.recipientId(), secondRecipient.medicationId())).isZero();
    assertThat(eventCount(secondRecipient)).isEqualTo(eventCountBefore);

    JsonNode getError = json(mvc.perform(get(path(firstRecipient.recipientId(), secondRecipient.medicationId()))
            .header("Authorization", bearer(caregiver.token())))
        .andExpect(status().isNotFound())
        .andReturn());
    assertThat(getError.toString()).doesNotContain(event.path("id").asText(), "TAKEN");
    assertThat(eventCount(caregiver.id(), firstRecipient.recipientId(), secondRecipient.medicationId())).isZero();
    assertThat(eventCount(secondRecipient)).isEqualTo(eventCountBefore);
  }

  @Test
  void rejectsUnsupportedIntakeEventValue() throws Exception {
    Fixture fixture = fixture();

    mvc.perform(post(path(fixture))
            .header("Authorization", bearer(fixture.caregiver().token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(request("NOT_RECORDED", "2026-10-22T08:30:00Z", "VERIFIED"))))
        .andExpect(status().isBadRequest());

    assertThat(eventCount(fixture)).isZero();
  }

  @Test
  void doesNotCreateMissedEventWhenNoIntakeWasRecorded() throws Exception {
    Fixture fixture = fixture();

    mvc.perform(get(path(fixture)).header("Authorization", bearer(fixture.caregiver().token())))
        .andExpect(status().isOk())
        .andExpect(content().json("[]"));
    assertThat(eventCount(fixture)).isZero();
  }

  @Test
  void requiresAuthenticationForIntakeEventRoutes() throws Exception {
    Fixture fixture = fixture();

    mvc.perform(post(path(fixture))
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(request("TAKEN", "2026-10-22T08:30:00Z", "VERIFIED"))))
        .andExpect(status().isUnauthorized());
    mvc.perform(get(path(fixture))).andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsInvalidVerificationAndMissingOrMalformedOccurredAt() throws Exception {
    Fixture fixture = fixture();
    List<Map<String, Object>> invalidRequests = List.of(
        request("TAKEN", "2026-10-22T08:30:00Z", "INVALID"),
        Map.of("intakeState", "TAKEN", "verificationState", "VERIFIED"),
        request("TAKEN", "not-a-timestamp", "VERIFIED"));

    for (Map<String, Object> invalidRequest : invalidRequests) {
      mvc.perform(post(path(fixture))
              .header("Authorization", bearer(fixture.caregiver().token()))
              .contentType(MediaType.APPLICATION_JSON)
              .content(mapper.writeValueAsBytes(invalidRequest)))
          .andExpect(status().isBadRequest());
    }

    assertThat(eventCount(fixture)).isZero();
  }

  private Fixture fixture() throws Exception {
    return fixture(register());
  }

  private Fixture fixture(CaregiverFixture caregiver) throws Exception {
    UUID recipientId = createRecipient(caregiver.token());
    UUID medicationId = createMedication(caregiver.token(), recipientId);
    return new Fixture(caregiver, recipientId, medicationId);
  }

  private CaregiverFixture register() throws Exception {
    String email = "medication-intake-" + UUID.randomUUID() + "@example.com";
    MvcResult result = mvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(Map.of("email", email, "password", "password123"))))
        .andExpect(status().isCreated())
        .andReturn();
    String token = json(result).path("token").asText();
    return new CaregiverFixture(token, jwtService.parse(token).caregiverId());
  }

  private UUID createRecipient(String token) throws Exception {
    MvcResult result = mvc.perform(post("/api/recipients")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Medication intake recipient\"}"))
        .andExpect(status().isCreated())
        .andReturn();
    return UUID.fromString(json(result).path("id").asText());
  }

  private UUID createMedication(String token, UUID recipientId) throws Exception {
    MvcResult result = mvc.perform(post(BASE + recipientId + "/medications")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(Map.of(
                "displayName", "Synthetic medication",
                "doseText", "one tablet",
                "scheduleText", "daily"))))
        .andExpect(status().isCreated())
        .andReturn();
    return UUID.fromString(json(result).path("id").asText());
  }

  private JsonNode createEvent(Fixture fixture, String state, String occurredAt, String verification)
      throws Exception {
    MvcResult result = mvc.perform(post(path(fixture))
            .header("Authorization", bearer(fixture.caregiver().token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(request(state, occurredAt, verification))))
        .andExpect(status().isCreated())
        .andReturn();
    return json(result);
  }

  private long eventCount(Fixture fixture) {
    return eventCount(fixture.caregiver().id(), fixture.recipientId(), fixture.medicationId());
  }

  private long eventCount(UUID caregiverId, UUID recipientId, UUID medicationId) {
    return jdbc.queryForObject(
        "select count(*) from medication_intake_events where caregiver_id = ? and recipient_id = ? and medication_id = ?",
        Long.class, caregiverId, recipientId, medicationId);
  }

  private static Map<String, Object> request(String intakeState, String occurredAt, String verificationState) {
    return Map.of(
        "intakeState", intakeState,
        "occurredAt", occurredAt,
        "verificationState", verificationState);
  }

  private static String path(Fixture fixture) {
    return path(fixture.recipientId(), fixture.medicationId());
  }

  private static String path(UUID recipientId, UUID medicationId) {
    return BASE + recipientId + "/medications/" + medicationId + "/intake-events";
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  private JsonNode json(MvcResult result) throws Exception {
    return mapper.readTree(result.getResponse().getContentAsByteArray());
  }

  private record CaregiverFixture(String token, UUID id) {
  }

  private record Fixture(CaregiverFixture caregiver, UUID recipientId, UUID medicationId) {
  }
}
