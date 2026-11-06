package com.caregiver.medications;

import com.caregiver.auth.JwtService;
import com.caregiver.config.CaregiverApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.persistence.EntityManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

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
      "spring.datasource.url=jdbc:h2:mem:medicationapitest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "auth.jwt.secret=test-secret-that-is-definitely-32-bytes-long",
      "auth.jwt.expiry=24h"
    })
@AutoConfigureMockMvc
class MedicationControllerTest {
  private static final String BASE = "/api/recipients/";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private JwtService jwtService;
  @Autowired private MedicationRepository medications;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private EntityManager entityManager;

  @Test
  void createsMedicationForOwnedRecipient() throws Exception {
    Fixture fixture = fixture();
    JsonNode created = create(fixture, request("Vitamin D", "1000 IU", "after breakfast"));

    java.util.Set<String> responseFields = new java.util.HashSet<>();
    created.fieldNames().forEachRemaining(responseFields::add);
    assertThat(responseFields).containsExactlyInAnyOrder(
        "id", "recipientId", "displayName", "doseText", "scheduleText", "active", "createdAt", "updatedAt");
    assertThat(created.get("id").asText()).isNotBlank();
    assertThat(created.get("recipientId").asText()).isEqualTo(fixture.recipientId().toString());
    assertThat(created.get("displayName").asText()).isEqualTo("Vitamin D");
    assertThat(created.get("doseText").asText()).isEqualTo("1000 IU");
    assertThat(created.get("scheduleText").asText()).isEqualTo("after breakfast");
    assertThat(created.get("active").asBoolean()).isTrue();
    assertThat(created.get("createdAt").asText()).isNotBlank();
    assertThat(created.get("updatedAt").asText()).isNotBlank();
    MedicationRecord stored = medication(created);
    assertThat(stored.getCaregiver().getId()).isEqualTo(fixture.caregiver().id());
    assertThat(stored.getRecipient().getId()).isEqualTo(fixture.recipientId());
  }

  @Test
  void preservesExactArabicAndEnglishMedicationText() throws Exception {
    Fixture fixture = fixture();
    String name = "  دواء صباحي / Morning 💊  ";
    String dose = "  الجرعة: ١٠ ملغ (10 mg) — after food  ";
    String schedule = "\tكل يوم at 08:30 — بعد الإفطار\n";
    JsonNode created = create(fixture, request(name, dose, schedule));
    entityManagerClear();
    MedicationRecord stored = medication(created);

    assertThat(stored.getDisplayName()).isEqualTo(name);
    assertThat(stored.getDoseText()).isEqualTo(dose);
    assertThat(stored.getScheduleText()).isEqualTo(schedule);
  }

  @Test
  void rejectsCreationForForeignRecipient() throws Exception {
    Fixture owner = fixture();
    Fixture outsider = fixture();

    mvc.perform(post(collection(owner.caregiver().token(), outsider.recipientId()))
            .header("Authorization", bearer(owner.caregiver().token()))
            .contentType(MediaType.APPLICATION_JSON).content(request("Name", "Dose", "Schedule")))
        .andExpect(status().isNotFound());
    assertThat(jdbc.queryForObject("select count(*) from recipient_medications where recipient_id = ?",
        Integer.class, outsider.recipientId())).isZero();
  }

  @Test
  void rejectsCreationForArchivedRecipient() throws Exception {
    Fixture fixture = fixture();
    archiveRecipient(fixture);

    mvc.perform(post(collection(fixture.caregiver().token(), fixture.recipientId()))
            .header("Authorization", bearer(fixture.caregiver().token()))
            .contentType(MediaType.APPLICATION_JSON).content(request("Name", "Dose", "Schedule")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INACTIVE_RECIPIENT"));
    assertThat(jdbc.queryForObject("select count(*) from recipient_medications where recipient_id = ?",
        Integer.class, fixture.recipientId())).isZero();
  }

  @Test
  void listsOwnedRecipientMedicationsIncludingArchivedRecords() throws Exception {
    Fixture fixture = fixture();
    JsonNode active = create(fixture, request("Active", "1 mg", "daily"));
    JsonNode archived = create(fixture, request("Archived", "2 mg", "weekly"));
    archiveMedication(fixture, archived);

    JsonNode list = list(fixture);
    assertThat(list).hasSize(2);
    assertThat(list.findValuesAsText("id")).contains(active.get("id").asText(), archived.get("id").asText());
    assertThat(byId(list, archived.get("id").asText()).get("active").asBoolean()).isFalse();
  }

  @Test
  void listsNewestUpdatedMedicationFirstAndOrdersTiesById() throws Exception {
    Fixture fixture = fixture();
    JsonNode first = create(fixture, request("First", "1", "daily"));
    JsonNode second = create(fixture, request("Second", "2", "daily"));
    JsonNode third = create(fixture, request("Third", "3", "daily"));
    Instant newest = Instant.parse("2030-01-03T00:00:00Z");
    Instant tied = Instant.parse("2030-01-02T00:00:00Z");
    setUpdatedAt(first, tied);
    setUpdatedAt(second, newest);
    setUpdatedAt(third, tied);
    List<String> tiedIds = List.of(first, third).stream().map(n -> n.get("id").asText()).sorted().toList();

    JsonNode result = list(fixture);
    assertThat(result.get(0).get("id").asText()).isEqualTo(second.get("id").asText());
    assertThat(List.of(result.get(1).get("id").asText(), result.get(2).get("id").asText()))
        .containsExactlyElementsOf(tiedIds);
  }

  @Test
  void returnsEmptyArrayWhenRecipientHasNoMedications() throws Exception {
    assertThat(list(fixture())).isEmpty();
  }

  @Test
  void doesNotExposeAnotherCaregiversMedications() throws Exception {
    Fixture owner = fixture();
    Fixture other = fixture();
    JsonNode medication = create(owner, request("Private", "5 mg", "daily"));

    assertThat(list(other)).isEmpty();
    assertThat(list(owner).findValuesAsText("id")).containsExactly(medication.get("id").asText());
  }

  @Test
  void allowsHistoricalMedicationReadForArchivedRecipient() throws Exception {
    Fixture fixture = fixture();
    JsonNode medication = create(fixture, request("History", "dose", "schedule"));
    archiveRecipient(fixture);

    assertThat(list(fixture).findValuesAsText("id")).containsExactly(medication.get("id").asText());
    assertThat(getMedication(fixture, medication).get("id").asText()).isEqualTo(medication.get("id").asText());
  }

  @Test
  void getsMedicationForOwnedRecipient() throws Exception {
    Fixture fixture = fixture();
    JsonNode medication = create(fixture, request("My medicine", "10 mg", "nightly"));
    JsonNode response = getMedication(fixture, medication);
    assertThat(response.get("displayName").asText()).isEqualTo("My medicine");
    assertThat(response.get("recipientId").asText()).isEqualTo(fixture.recipientId().toString());
  }

  @Test
  void returns404ForForeignCaregiverMedication() throws Exception {
    Fixture owner = fixture();
    Fixture other = fixture();
    JsonNode medication = create(owner, request("Private", "dose", "schedule"));

    mvc.perform(get(item(other.caregiver().token(), owner.recipientId(), medicationId(medication)))
            .header("Authorization", bearer(other.caregiver().token())))
        .andExpect(status().isNotFound());
  }

  @Test
  void returns404WhenMedicationBelongsToDifferentRecipient() throws Exception {
    Fixture fixture = fixture();
    UUID secondRecipient = createRecipient(fixture.caregiver().token());
    JsonNode medication = create(fixture, request("For first", "dose", "schedule"));

    mvc.perform(get(item(fixture.caregiver().token(), secondRecipient, medicationId(medication)))
            .header("Authorization", bearer(fixture.caregiver().token())))
        .andExpect(status().isNotFound());
  }

  @Test
  void returnsArchivedMedicationById() throws Exception {
    Fixture fixture = fixture();
    JsonNode medication = create(fixture, request("Archived", "dose", "schedule"));
    archiveMedication(fixture, medication);

    assertThat(getMedication(fixture, medication).get("active").asBoolean()).isFalse();
  }

  @Test
  void updatesCaregiverEnteredMedicationText() throws Exception {
    Fixture fixture = fixture();
    JsonNode medication = create(fixture, request("Old", "old dose", "old schedule"));
    String name = "  دواء جديد / New 💊 ";
    String dose = " الجرعة ٥ ملغ / 5 mg ";
    String schedule = " صباحًا — every morning ";

    mvc.perform(put(item(fixture.caregiver().token(), fixture.recipientId(), medicationId(medication)))
            .header("Authorization", bearer(fixture.caregiver().token()))
            .contentType(MediaType.APPLICATION_JSON).content(request(name, dose, schedule)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value(name))
        .andExpect(jsonPath("$.doseText").value(dose))
        .andExpect(jsonPath("$.scheduleText").value(schedule));
    entityManagerClear();
    MedicationRecord stored = medication(medication);
    assertThat(stored.getDisplayName()).isEqualTo(name);
    assertThat(stored.getDoseText()).isEqualTo(dose);
    assertThat(stored.getScheduleText()).isEqualTo(schedule);
  }

  @Test
  void preservesMedicationIdentityAndOwnershipOnUpdate() throws Exception {
    Fixture fixture = fixture();
    JsonNode medication = create(fixture, request("Old", "dose", "schedule"));
    MedicationRecord before = medication(medication);
    UUID id = before.getId();
    UUID caregiverId = before.getCaregiver().getId();
    UUID recipientId = before.getRecipient().getId();
    Instant createdAt = before.getCreatedAt();

    update(fixture, medication, request("New", "new dose", "new schedule"));
    entityManagerClear();
    MedicationRecord after = medication(medication);
    assertThat(after.getId()).isEqualTo(id);
    assertThat(after.getCaregiver().getId()).isEqualTo(caregiverId);
    assertThat(after.getRecipient().getId()).isEqualTo(recipientId);
    assertThat(after.getCreatedAt()).isEqualTo(createdAt);
  }

  @Test
  void returns404WhenUpdatingForeignMedication() throws Exception {
    Fixture owner = fixture();
    Fixture other = fixture();
    JsonNode medication = create(owner, request("Original", "dose", "schedule"));

    mvc.perform(put(item(other.caregiver().token(), owner.recipientId(), medicationId(medication)))
            .header("Authorization", bearer(other.caregiver().token())).contentType(MediaType.APPLICATION_JSON).content(request("Changed", "x", "y")))
        .andExpect(status().isNotFound());
    assertThat(medication(medication).getDisplayName()).isEqualTo("Original");
  }

  @Test
  void returns404WhenUpdatingMedicationThroughWrongRecipient() throws Exception {
    Fixture fixture = fixture();
    UUID wrongRecipient = createRecipient(fixture.caregiver().token());
    JsonNode medication = create(fixture, request("Original", "dose", "schedule"));

    mvc.perform(put(item(fixture.caregiver().token(), wrongRecipient, medicationId(medication)))
            .header("Authorization", bearer(fixture.caregiver().token())).contentType(MediaType.APPLICATION_JSON).content(request("Changed", "x", "y")))
        .andExpect(status().isNotFound());
    assertThat(medication(medication).getDisplayName()).isEqualTo("Original");
  }

  @Test
  void rejectsBlankDisplayName() throws Exception {
    Fixture fixture = fixture();
    mvc.perform(post(collection(fixture.caregiver().token(), fixture.recipientId()))
            .header("Authorization", bearer(fixture.caregiver().token()))
            .contentType(MediaType.APPLICATION_JSON).content(request("  ", "dose", "schedule")))
        .andExpect(status().isBadRequest());
    assertThat(list(fixture)).isEmpty();
  }

  @Test
  void rejectsDisplayNameLongerThan160Characters() throws Exception {
    assertInvalidCreate(request("x".repeat(161), "dose", "schedule"));
  }

  @Test
  void rejectsDoseTextLongerThan500Characters() throws Exception {
    assertInvalidCreate(request("Name", "x".repeat(501), "schedule"));
  }

  @Test
  void rejectsScheduleTextLongerThan500Characters() throws Exception {
    assertInvalidCreate(request("Name", "dose", "x".repeat(501)));
  }

  @Test
  void doesNotAllowClientToChangeOwnershipOrActiveStatus() throws Exception {
    Fixture fixture = fixture();
    UUID otherRecipient = createRecipient(fixture.caregiver().token());
    Map<String, Object> body = Map.of(
        "displayName", "Client controlled", "doseText", "dose", "scheduleText", "schedule",
        "id", UUID.randomUUID(), "recipientId", otherRecipient, "caregiverId", UUID.randomUUID(),
        "active", false, "createdAt", "2000-01-01T00:00:00Z", "updatedAt", "2000-01-01T00:00:00Z");
    MvcResult result = mvc.perform(post(collection(fixture.caregiver().token(), fixture.recipientId()))
            .header("Authorization", bearer(fixture.caregiver().token()))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)))
        .andExpect(status().isCreated()).andReturn();
    JsonNode response = mapper.readTree(result.getResponse().getContentAsByteArray());
    assertThat(response.get("active").asBoolean()).isTrue();
    assertThat(response.get("recipientId").asText()).isEqualTo(fixture.recipientId().toString());
    assertThat(response.get("id").asText()).isNotEqualTo(body.get("id").toString());
    assertThat(medication(response).getCaregiver().getId()).isEqualTo(fixture.caregiver().id());
  }

  @Test
  void rejectsUpdatingArchivedMedication() throws Exception {
    Fixture fixture = fixture();
    JsonNode medication = create(fixture, request("Original", "dose", "schedule"));
    archiveMedication(fixture, medication);

    mvc.perform(put(item(fixture.caregiver().token(), fixture.recipientId(), medicationId(medication)))
            .header("Authorization", bearer(fixture.caregiver().token()))
            .contentType(MediaType.APPLICATION_JSON).content(request("Changed", "x", "y")))
        .andExpect(status().isConflict());
    MedicationRecord unchanged = medication(medication);
    assertThat(unchanged.isActive()).isFalse();
    assertThat(unchanged.getDisplayName()).isEqualTo("Original");
  }

  @Test
  void archivesMedicationWithoutDeletingTheRow() throws Exception {
    Fixture fixture = fixture();
    JsonNode medication = create(fixture, request("Keep", "dose", "schedule"));
    JsonNode response = archiveMedication(fixture, medication);
    assertThat(response.get("active").asBoolean()).isFalse();
    assertThat(jdbc.queryForObject("select count(*) from recipient_medications where id = ?",
        Integer.class, medicationId(medication))).isEqualTo(1);
  }

  @Test
  void archivedMedicationRemainsReadable() throws Exception {
    Fixture fixture = fixture();
    JsonNode medication = create(fixture, request("Keep", "dose", "schedule"));
    archiveMedication(fixture, medication);
    assertThat(getMedication(fixture, medication).get("active").asBoolean()).isFalse();
  }

  @Test
  void archivedMedicationRemainsInCollection() throws Exception {
    Fixture fixture = fixture();
    JsonNode medication = create(fixture, request("Keep", "dose", "schedule"));
    archiveMedication(fixture, medication);
    JsonNode listed = byId(list(fixture), medicationId(medication).toString());
    assertThat(listed.get("active").asBoolean()).isFalse();
  }

  @Test
  void rejectsArchivingForeignMedication() throws Exception {
    Fixture owner = fixture();
    Fixture other = fixture();
    JsonNode medication = create(owner, request("Private", "dose", "schedule"));

    mvc.perform(patch(item(other.caregiver().token(), owner.recipientId(),
            medicationId(medication) + "/archive"))
            .header("Authorization", bearer(other.caregiver().token())))
        .andExpect(status().isNotFound());
    assertThat(medication(medication).isActive()).isTrue();
  }

  @Test
  void rejectsArchivingMedicationThroughWrongRecipient() throws Exception {
    Fixture fixture = fixture();
    UUID otherRecipient = createRecipient(fixture.caregiver().token());
    JsonNode medication = create(fixture, request("Original", "dose", "schedule"));

    mvc.perform(patch(item(fixture.caregiver().token(), otherRecipient,
            medicationId(medication) + "/archive"))
            .header("Authorization", bearer(fixture.caregiver().token())))
        .andExpect(status().isNotFound());
    assertThat(medication(medication).isActive()).isTrue();
  }

  @Test
  void archivingDoesNotChangeMedicationTextOrOwnership() throws Exception {
    Fixture fixture = fixture();
    JsonNode medication = create(fixture, request("Name", "Dose", "Schedule"));
    MedicationRecord before = medication(medication);
    UUID id = before.getId();
    Instant createdAt = before.getCreatedAt();
    archiveMedication(fixture, medication);
    entityManagerClear();
    MedicationRecord after = medication(medication);
    assertThat(after.getId()).isEqualTo(id);
    assertThat(after.getCaregiver().getId()).isEqualTo(fixture.caregiver().id());
    assertThat(after.getRecipient().getId()).isEqualTo(fixture.recipientId());
    assertThat(after.getCreatedAt()).isEqualTo(createdAt);
    assertThat(after.getDisplayName()).isEqualTo("Name");
    assertThat(after.getDoseText()).isEqualTo("Dose");
    assertThat(after.getScheduleText()).isEqualTo("Schedule");
  }

  @Test
  void repeatedArchiveRequestDoesNotDeleteOrRecreateRecord() throws Exception {
    Fixture fixture = fixture();
    JsonNode medication = create(fixture, request("Name", "Dose", "Schedule"));
    UUID id = medicationId(medication);
    archiveMedication(fixture, medication);
    archiveMedication(fixture, medication);
    assertThat(jdbc.queryForObject("select count(*) from recipient_medications where id = ?",
        Integer.class, id)).isEqualTo(1);
    assertThat(list(fixture)).hasSize(1);
    assertThat(medication(medication).getId()).isEqualTo(id);
  }

  @Test
  void requiresAuthenticationForCreate() throws Exception {
    UUID recipientId = UUID.randomUUID();
    mvc.perform(post(collection(null, recipientId)).contentType(MediaType.APPLICATION_JSON)
            .content(request("Name", "Dose", "Schedule")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void requiresAuthenticationForList() throws Exception {
    mvc.perform(get(collection(null, UUID.randomUUID()))).andExpect(status().isUnauthorized());
  }

  @Test
  void requiresAuthenticationForGet() throws Exception {
    mvc.perform(get(item(null, UUID.randomUUID(), UUID.randomUUID().toString())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void requiresAuthenticationForUpdate() throws Exception {
    mvc.perform(put(item(null, UUID.randomUUID(), UUID.randomUUID().toString()))
            .contentType(MediaType.APPLICATION_JSON).content(request("Name", "Dose", "Schedule")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void requiresAuthenticationForArchive() throws Exception {
    mvc.perform(patch(item(null, UUID.randomUUID(), UUID.randomUUID() + "/archive")))
        .andExpect(status().isUnauthorized());
  }

  private void assertInvalidCreate(String body) throws Exception {
    Fixture fixture = fixture();
    mvc.perform(post(collection(fixture.caregiver().token(), fixture.recipientId()))
            .header("Authorization", bearer(fixture.caregiver().token()))
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
    assertThat(list(fixture)).isEmpty();
  }

  private Fixture fixture() throws Exception {
    TestCaregiver caregiver = register();
    return new Fixture(caregiver, createRecipient(caregiver.token()));
  }

  private TestCaregiver register() throws Exception {
    String email = "medication-api-" + UUID.randomUUID() + "@example.com";
    MvcResult result = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(Map.of("email", email, "password", "password123"))))
        .andExpect(status().isCreated()).andReturn();
    String token = mapper.readTree(result.getResponse().getContentAsByteArray()).get("token").asText();
    return new TestCaregiver(token, jwtService.parse(token).caregiverId());
  }

  private UUID createRecipient(String token) throws Exception {
    MvcResult result = mvc.perform(post("/api/recipients").header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Medication API recipient\"}"))
        .andExpect(status().isCreated()).andReturn();
    return UUID.fromString(mapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asText());
  }

  private void archiveRecipient(Fixture fixture) throws Exception {
    mvc.perform(patch("/api/recipients/" + fixture.recipientId() + "/archive")
            .header("Authorization", bearer(fixture.caregiver().token())))
        .andExpect(status().isOk());
  }

  private JsonNode create(Fixture fixture, String body) throws Exception {
    MvcResult result = mvc.perform(post(collection(fixture.caregiver().token(), fixture.recipientId()))
            .header("Authorization", bearer(fixture.caregiver().token()))
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated()).andReturn();
    return mapper.readTree(result.getResponse().getContentAsByteArray());
  }

  private JsonNode list(Fixture fixture) throws Exception {
    MvcResult result = mvc.perform(get(collection(fixture.caregiver().token(), fixture.recipientId()))
            .header("Authorization", bearer(fixture.caregiver().token())))
        .andExpect(status().isOk()).andReturn();
    return mapper.readTree(result.getResponse().getContentAsByteArray());
  }

  private JsonNode getMedication(Fixture fixture, JsonNode medication) throws Exception {
    MvcResult result = mvc.perform(get(item(fixture.caregiver().token(), fixture.recipientId(), medicationId(medication).toString()))
            .header("Authorization", bearer(fixture.caregiver().token())))
        .andExpect(status().isOk()).andReturn();
    return mapper.readTree(result.getResponse().getContentAsByteArray());
  }

  private JsonNode update(Fixture fixture, JsonNode medication, String body) throws Exception {
    MvcResult result = mvc.perform(put(item(fixture.caregiver().token(), fixture.recipientId(), medicationId(medication).toString()))
            .header("Authorization", bearer(fixture.caregiver().token()))
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk()).andReturn();
    return mapper.readTree(result.getResponse().getContentAsByteArray());
  }

  private JsonNode archiveMedication(Fixture fixture, JsonNode medication) throws Exception {
    MvcResult result = mvc.perform(patch(item(fixture.caregiver().token(), fixture.recipientId(),
            medicationId(medication) + "/archive"))
            .header("Authorization", bearer(fixture.caregiver().token())))
        .andExpect(status().isOk()).andReturn();
    return mapper.readTree(result.getResponse().getContentAsByteArray());
  }

  private MedicationRecord medication(JsonNode response) {
    UUID id = medicationId(response);
    UUID caregiverId = jdbc.queryForObject(
        "select caregiver_id from recipient_medications where id = ?", UUID.class, id);
    return medications.findByIdAndCaregiver_IdAndRecipient_Id(id, caregiverId,
        UUID.fromString(response.get("recipientId").asText())).orElseThrow();
  }

  private void entityManagerClear() {
    entityManager.clear();
  }

  private void setUpdatedAt(JsonNode response, Instant updatedAt) {
    jdbc.update("update recipient_medications set updated_at = ? where id = ?",
        Timestamp.from(updatedAt), medicationId(response));
  }

  private static JsonNode byId(JsonNode list, String id) {
    for (JsonNode node : list) if (node.get("id").asText().equals(id)) return node;
    throw new AssertionError("Medication not found in response: " + id);
  }

  private static UUID medicationId(JsonNode response) {
    return UUID.fromString(response.get("id").asText());
  }

  private static String request(String name, String dose, String schedule) {
    try {
      return new ObjectMapper().writeValueAsString(Map.of(
          "displayName", name, "doseText", dose, "scheduleText", schedule));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static String collection(String token, UUID recipientId) {
    return BASE + recipientId + "/medications";
  }

  private static String item(String token, UUID recipientId, Object medicationId) {
    String path = BASE + recipientId + "/medications/" + medicationId;
    return token == null ? path : path;
  }

  private static String bearer(String token) { return "Bearer " + token; }
  private record TestCaregiver(String token, UUID id) {}
  private record Fixture(TestCaregiver caregiver, UUID recipientId) {}
}
