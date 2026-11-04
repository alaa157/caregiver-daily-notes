package com.caregiver.recipient;

import com.caregiver.auth.CaregiverRepository;
import org.springframework.jdbc.core.JdbcTemplate;
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

import java.time.Instant;
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
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JwtService jwtService;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from note_addenda");
    jdbcTemplate.update("delete from daily_notes");
    jdbcTemplate.update("delete from care_plan_versions");
    jdbcTemplate.update("delete from audit_events");
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
        .andExpect(jsonPath("$.lifecycleStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.archivedAt").value(org.hamcrest.Matchers.nullValue()))
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
  void rejectsRecipientLifecycleWritesFromAnotherCaregiver() throws Exception {
    TestCaregiver owner = register("lifecycle-owner@example.com");
    TestCaregiver other = register("lifecycle-other@example.com");
    String id = createRecipient(owner.token(), "{\"name\":\"Owner recipient\",\"relationship\":\"Mother\"}");
    String path = RECIPIENTS + "/" + id;

    mvc.perform(put(path)
            .header("Authorization", bearer(other.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Tampered\",\"relationship\":\"Other\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RECIPIENT_NOT_FOUND"))
        .andExpect(jsonPath("$.name").doesNotExist());
    mvc.perform(patch(path + "/archive").header("Authorization", bearer(other.token())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RECIPIENT_NOT_FOUND"))
        .andExpect(jsonPath("$.name").doesNotExist());
    mvc.perform(patch(path + "/restore").header("Authorization", bearer(other.token())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RECIPIENT_NOT_FOUND"))
        .andExpect(jsonPath("$.name").doesNotExist());

    mvc.perform(get(path).header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Owner recipient"))
        .andExpect(jsonPath("$.relationship").value("Mother"))
        .andExpect(jsonPath("$.lifecycleStatus").value("ACTIVE"));
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
  void createUpdateAndDeactivateResponsesContainPersistedTimestamps() throws Exception {
    TestCaregiver owner = register("timestamps@example.com");

    MvcResult created = mvc.perform(post(RECIPIENTS)
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Timestamped\",\"relationship\":\"Father\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.createdAt").isNotEmpty())
        .andExpect(jsonPath("$.updatedAt").isNotEmpty())
        .andReturn();

    JsonNode createdJson =
        objectMapper.readTree(created.getResponse().getContentAsString());
    String id = createdJson.get("id").asText();
    assertThat(Instant.parse(createdJson.get("createdAt").asText())).isNotNull();
    assertThat(Instant.parse(createdJson.get("updatedAt").asText())).isNotNull();

    MvcResult fetched = mvc.perform(get(RECIPIENTS + "/" + id)
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.createdAt").isNotEmpty())
        .andExpect(jsonPath("$.updatedAt").isNotEmpty())
        .andReturn();
    String persistedCreatedAt =
        objectMapper.readTree(fetched.getResponse().getContentAsString()).get("createdAt").asText();

    mvc.perform(put(RECIPIENTS + "/" + id)
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Timestamped after update\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.createdAt").value(persistedCreatedAt))
        .andExpect(jsonPath("$.updatedAt").isNotEmpty());

    mvc.perform(patch(RECIPIENTS + "/" + id + "/deactivate")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.createdAt").value(persistedCreatedAt))
        .andExpect(jsonPath("$.updatedAt").isNotEmpty());
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

  @Test
  void archiveAndRestoreAreIdempotentAndExposeActorAndTimestamps() throws Exception {
    TestCaregiver owner = register("archive-restore@example.com");
    String id = createRecipient(owner.token(), "{\"name\":\"Lifecycle recipient\"}");

    MvcResult archived = mvc.perform(patch(RECIPIENTS + "/" + id + "/archive")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.lifecycleStatus").value("ARCHIVED"))
        .andExpect(jsonPath("$.archivedAt").isNotEmpty())
        .andExpect(jsonPath("$.archivedBy").value(owner.id().toString()))
        .andReturn();
    String archivedAt = objectMapper.readTree(archived.getResponse().getContentAsString()).get("archivedAt").asText();
    String archivedBy = objectMapper.readTree(archived.getResponse().getContentAsString()).get("archivedBy").asText();
    assertThat(archivedAt).isNotBlank();
    assertThat(archivedBy).isEqualTo(owner.id().toString());

    MvcResult idempotentArchiveResult = mvc.perform(patch(RECIPIENTS + "/" + id + "/archive")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archivedAt").isNotEmpty())
        .andReturn();
    JsonNode idempotentArchive =
        objectMapper.readTree(idempotentArchiveResult.getResponse().getContentAsString());
    assertThat(Instant.parse(idempotentArchive.get("archivedAt").asText()).truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
        .isEqualTo(Instant.parse(archivedAt).truncatedTo(java.time.temporal.ChronoUnit.MILLIS));

    MvcResult restored = mvc.perform(patch(RECIPIENTS + "/" + id + "/restore")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.lifecycleStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.archivedAt").isNotEmpty())
        .andExpect(jsonPath("$.restoredAt").isNotEmpty())
        .andExpect(jsonPath("$.restoredBy").value(owner.id().toString()))
        .andReturn();
    JsonNode restoredJson = objectMapper.readTree(restored.getResponse().getContentAsString());
    assertThat(Instant.parse(restoredJson.get("archivedAt").asText()).truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
        .isEqualTo(Instant.parse(archivedAt).truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
    Instant restoredInstant = Instant.parse(restoredJson.get("restoredAt").asText());
    assertThat(restoredInstant).isNotNull();
    MvcResult idempotentRestoreResult = mvc.perform(patch(RECIPIENTS + "/" + id + "/restore")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.restoredAt").isNotEmpty())
        .andReturn();
    JsonNode idempotentRestore =
        objectMapper.readTree(idempotentRestoreResult.getResponse().getContentAsString());
    assertThat(Instant.parse(idempotentRestore.get("restoredAt").asText()).truncatedTo(java.time.temporal.ChronoUnit.MILLIS))
        .isEqualTo(restoredInstant.truncatedTo(java.time.temporal.ChronoUnit.MILLIS));
    mvc.perform(get(RECIPIENTS).param("status", "ACTIVE")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(id));
    mvc.perform(get(RECIPIENTS).param("status", "ARCHIVED")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void legacyInactiveRecordsRemainReviewRequiredAndAreNotAutoRestored() throws Exception {
    TestCaregiver owner = register("legacy-review@example.com");
    String id = createRecipient(owner.token(), "{\"name\":\"Legacy inactive\"}");
    jdbcTemplate.update("UPDATE care_recipients SET active = FALSE, lifecycle_status = 'LEGACY_INACTIVE_REVIEW' WHERE id = ?",
        UUID.fromString(id));

    mvc.perform(get(RECIPIENTS + "/" + id).header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.lifecycleStatus").value("LEGACY_INACTIVE_REVIEW"))
        .andExpect(jsonPath("$.archivedAt").doesNotExist())
        .andExpect(jsonPath("$.archivedBy").value(org.hamcrest.Matchers.nullValue()));

    mvc.perform(get(RECIPIENTS).param("status", "LEGACY_INACTIVE_REVIEW")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value(id));

    mvc.perform(put(RECIPIENTS + "/" + id)
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Must remain legacy\",\"relationship\":\"Changed\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INACTIVE_RECIPIENT"));
    mvc.perform(patch(RECIPIENTS + "/" + id + "/restore")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isConflict());
    mvc.perform(patch(RECIPIENTS + "/" + id + "/archive")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isConflict());
    mvc.perform(get(RECIPIENTS + "/" + id).header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Legacy inactive"))
        .andExpect(jsonPath("$.relationship").value(org.hamcrest.Matchers.nullValue()))
        .andExpect(jsonPath("$.lifecycleStatus").value("LEGACY_INACTIVE_REVIEW"));
  }

  @Test
  void archivePreservesNotesAddendaAndPlansAndBlocksNewWritesUntilRestore() throws Exception {
    TestCaregiver owner = register("archive-history@example.com");
    String id = createRecipient(owner.token(), "{\"name\":\"History recipient\"}");
    MvcResult createdNote = mvc.perform(post("/api/notes")
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"recipientId\":\"" + id + "\",\"date\":\"2026-10-17\",\"freeText\":\"Original note preserved\"}"))
        .andExpect(status().isCreated())
        .andReturn();
    String noteId = objectMapper.readTree(createdNote.getResponse().getContentAsString()).get("id").asText();

    mvc.perform(post("/api/notes/" + noteId + "/addenda")
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"painScore\":8,\"freeText\":\"Correction preserved\"}"))
        .andExpect(status().isCreated());

    UUID planId = UUID.randomUUID();
    jdbcTemplate.update("INSERT INTO care_plan_versions (id, recipient_id, version_number, status, content) VALUES (?, ?, 1, 'SUGGESTED', ?)",
        planId, UUID.fromString(id), "Historical plan content");

    mvc.perform(patch(RECIPIENTS + "/" + id + "/archive")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lifecycleStatus").value("ARCHIVED"));

    mvc.perform(get("/api/notes/" + noteId).header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.freeText").value("Original note preserved"))
        .andExpect(jsonPath("$.addendumCount").value(1))
        .andExpect(jsonPath("$.addenda[0].freeText").value("Correction preserved"));
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM care_plan_versions WHERE id = ?", Integer.class, planId)).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT content FROM care_plan_versions WHERE id = ?", String.class, planId)).isEqualTo("Historical plan content");

    mvc.perform(post("/api/notes")
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"recipientId\":\"" + id + "\",\"date\":\"2026-10-18\",\"freeText\":\"Should be blocked\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INACTIVE_RECIPIENT"));
    mvc.perform(post("/api/notes/" + noteId + "/addenda")
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"freeText\":\"Should also be blocked\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INACTIVE_RECIPIENT"));

    mvc.perform(patch(RECIPIENTS + "/" + id + "/restore")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lifecycleStatus").value("ACTIVE"));
    mvc.perform(post("/api/notes")
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"recipientId\":\"" + id + "\",\"date\":\"2026-10-18\",\"freeText\":\"After restore\"}"))
        .andExpect(status().isCreated());
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
