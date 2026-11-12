package com.caregiver.notes;

import com.caregiver.auth.CaregiverRepository;
import com.caregiver.auth.JwtService;
import com.caregiver.config.CaregiverApplication;
import com.caregiver.recipient.CareRecipientRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Signal derivation rules against a real database (issue #34): exact
 * field mapping, per-field addendum override, unverified encoding,
 * symmetric contradiction handling, absent-as-null, ordering and scoping.
 */
@SpringBootTest(
    classes = CaregiverApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:notesignaltest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "auth.jwt.secret=test-secret-that-is-definitely-32-bytes-long",
      "auth.jwt.expiry=24h"
    })
@AutoConfigureMockMvc
class NoteSignalServiceTest {

  private static final String NOTES = "/api/notes";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private JwtService jwtService;
  @Autowired private CaregiverRepository caregivers;
  @Autowired private CareRecipientRepository recipients;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from audit_events");
    jdbcTemplate.update("delete from note_addenda");
    jdbcTemplate.update("delete from daily_notes");
    recipients.deleteAll();
    caregivers.deleteAll();
  }

  @Test
  void validObservationsMapFieldByField() throws Exception {
    TestCaregiver owner = register("signal-valid@example.com");
    String recipientId = createRecipient(owner.token());
    createNote(owner.token(), recipientId, "2026-10-01",
        "{\"mood\":\"good\",\"appetite\":\"poor\",\"sleep\":\"good\",\"mobility\":\"normal\","
            + "\"medicationStatus\":\"TAKEN\",\"medicationVerified\":true,"
            + "\"painScore\":7,\"fallReported\":true,\"freeText\":\"Signal text\"}");

    JsonNode signals = signals(owner.token(), recipientId, "2026-10-01", "2026-10-01");
    assertThat(signals).hasSize(1);
    JsonNode day = signals.get(0);
    assertThat(day.get("recipientId").asText()).isEqualTo(recipientId);
    assertThat(day.get("mood").asText()).isEqualTo("good");
    assertThat(day.get("appetite").asText()).isEqualTo("poor");
    assertThat(day.get("sleep").asText()).isEqualTo("good");
    assertThat(day.get("mobility").asText()).isEqualTo("normal");
    assertThat(day.get("medicationStatus").asText()).isEqualTo("TAKEN");
    assertThat(day.get("date").asText()).isEqualTo("2026-10-01");
    assertThat(day.get("fallReported").asBoolean()).isTrue();
    assertThat(day.get("painScore").asInt()).isEqualTo(7);
    assertThat(day.get("missedMedication").asBoolean()).isFalse();
    assertThat(day.get("medicationUnverified").asBoolean()).isFalse();
    assertThat(day.get("poorAppetite").asBoolean()).isTrue();
    assertThat(day.get("freeText").asText()).isEqualTo("Signal text");
  }

  @Test
  void missingObservationsRemainAbsent() throws Exception {
    TestCaregiver owner = register("signal-missing@example.com");
    String recipientId = createRecipient(owner.token());
    createNote(owner.token(), recipientId, "2026-10-02", "{\"mood\":\"good\"}");

    JsonNode day = signals(owner.token(), recipientId, "2026-10-02", "2026-10-02").get(0);
    assertThat(day.get("fallReported").isNull()).isTrue();
    assertThat(day.get("painScore").isNull()).isTrue();
    assertThat(day.get("missedMedication").isNull()).isTrue();
    assertThat(day.get("medicationUnverified").asBoolean()).isFalse();
    assertThat(day.get("poorAppetite").isNull()).isTrue();
  }

  @Test
  void explicitObservationStatesAndTypedValuesRoundTripThroughApi() throws Exception {
    TestCaregiver owner = register("signal-explicit@example.com");
    String recipientId = createRecipient(owner.token());
    String noteId = createNote(owner.token(), recipientId, "2026-10-13",
        "{\"observations\":{"
            + "\"mood\":{\"state\":\"RECORDED\",\"value\":\"\"},"
            + "\"appetite\":{\"state\":\"UNKNOWN\"},"
            + "\"sleep\":{\"state\":\"NOT_RECORDED\"},"
            + "\"mobility\":{\"state\":\"LEGACY_AMBIGUOUS\",\"rawValue\":\"old mobility\"},"
            + "\"medicationStatus\":{\"state\":\"UNKNOWN\"},"
            + "\"painScore\":{\"state\":\"RECORDED\",\"value\":0},"
            + "\"fallReported\":{\"state\":\"RECORDED\",\"value\":false},"
            + "\"freeText\":{\"state\":\"RECORDED\",\"value\":\"\"}},"
            + "\"medicationVerification\":\"VERIFIED\"}");

    JsonNode note = getNote(owner.token(), noteId);
    JsonNode observations = note.get("observations");
    assertThat(observations.get("mood").get("state").asText()).isEqualTo("RECORDED");
    assertThat(observations.get("mood").get("value").asText()).isEmpty();
    assertThat(observations.get("appetite").get("state").asText()).isEqualTo("UNKNOWN");
    assertThat(observations.get("sleep").get("state").asText()).isEqualTo("NOT_RECORDED");
    assertThat(observations.get("mobility").get("state").asText()).isEqualTo("LEGACY_AMBIGUOUS");
    assertThat(observations.get("mobility").get("rawValue").asText()).isEqualTo("old mobility");
    assertThat(observations.get("medicationStatus").get("state").asText()).isEqualTo("UNKNOWN");
    assertThat(observations.get("painScore").get("value").asInt()).isZero();
    assertThat(observations.get("fallReported").get("value").asBoolean()).isFalse();
    assertThat(observations.get("freeText").get("value").asText()).isEmpty();
    assertThat(observations.get("medicationVerification").asText()).isEqualTo("VERIFIED");
  }

  @Test
  void explicitAddendumCanSetStateWithoutChangingOmittedFields() throws Exception {
    TestCaregiver owner = register("signal-explicit-addendum@example.com");
    String recipientId = createRecipient(owner.token());
    String noteId = createNote(owner.token(), recipientId, "2026-10-14",
        "{\"mood\":\"good\",\"painScore\":4,\"medicationStatus\":\"TAKEN\","
            + "\"medicationVerified\":true}");
    append(owner.token(), noteId,
        "{\"observations\":{\"mood\":{\"state\":\"NOT_RECORDED\"},"
            + "\"painScore\":{\"state\":\"NOT_APPLICABLE\"}}}");

    JsonNode day = signals(owner.token(), recipientId, "2026-10-14", "2026-10-14").get(0);
    JsonNode observations = day.get("observations");
    assertThat(observations.get("mood").get("state").asText()).isEqualTo("NOT_RECORDED");
    assertThat(observations.get("painScore").get("state").asText()).isEqualTo("NOT_APPLICABLE");
    assertThat(observations.get("medicationStatus").get("state").asText()).isEqualTo("RECORDED");
    assertThat(observations.get("medicationStatus").get("value").asText()).isEqualTo("TAKEN");
    assertThat(observations.get("medicationVerification").asText()).isEqualTo("VERIFIED");

    MvcResult result = mvc.perform(get(NOTES + "/" + noteId + "/addenda")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode providedFields = mapper.readTree(result.getResponse().getContentAsByteArray())
        .get(0).get("providedFields");
    assertThat(providedFields).hasSize(2);
    assertThat(providedFields.toString()).contains("\"mood\"", "\"painScore\"");
  }

  @Test
  void invalidExplicitStateValueCombinationIsRejected() throws Exception {
    TestCaregiver owner = register("signal-invalid-state@example.com");
    String recipientId = createRecipient(owner.token());
    mvc.perform(post(NOTES)
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"recipientId\":\"" + recipientId + "\",\"date\":\"2026-10-15\","
                + "\"observations\":{\"mood\":{\"state\":\"UNKNOWN\",\"value\":\"good\"},"
                + "\"appetite\":{\"state\":\"NOT_RECORDED\"},"
                + "\"sleep\":{\"state\":\"NOT_RECORDED\"},"
                + "\"mobility\":{\"state\":\"NOT_RECORDED\"},"
                + "\"medicationStatus\":{\"state\":\"NOT_RECORDED\"},"
                + "\"painScore\":{\"state\":\"NOT_RECORDED\"},"
                + "\"fallReported\":{\"state\":\"NOT_RECORDED\"},"
                + "\"freeText\":{\"state\":\"NOT_RECORDED\"}}}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void notAssertedMedicationValuesRemainRecordedButUnverifiedValuesAreSuppressed() throws Exception {
    TestCaregiver owner = register("signal-unverified@example.com");
    String recipientId = createRecipient(owner.token());
    // TAKEN but explicitly unverified.
    createNote(owner.token(), recipientId, "2026-10-01",
        "{\"medicationStatus\":\"TAKEN\",\"medicationVerified\":false}");
    // NOT_ASSERTED does not negate a recorded value.
    createNote(owner.token(), recipientId, "2026-10-02", "{\"medicationStatus\":\"MISSED\"}");
    // Verification remains independent even when the observation is UNKNOWN.
    createNote(owner.token(), recipientId, "2026-10-03",
        "{\"medicationStatus\":\"UNKNOWN\",\"medicationVerified\":true}");

    JsonNode days = signals(owner.token(), recipientId, "2026-10-01", "2026-10-03");
    assertThat(days).hasSize(3);
    assertThat(days.get(0).get("missedMedication").isNull()).isTrue();
    assertThat(days.get(1).get("missedMedication").asBoolean()).isTrue();
    assertThat(days.get(2).get("missedMedication").isNull()).isTrue();
    assertThat(days.get(0).get("observations").get("medicationVerification").asText())
        .isEqualTo("UNVERIFIED");
    assertThat(days.get(0).get("medicationUnverified").asBoolean()).isTrue();
    assertThat(days.get(1).get("observations").get("medicationVerification").asText())
        .isEqualTo("NOT_ASSERTED");
    assertThat(days.get(1).get("medicationUnverified").asBoolean()).isFalse();
    assertThat(days.get(2).get("observations").get("medicationVerification").asText())
        .isEqualTo("VERIFIED");
    assertThat(days.get(2).get("observations").get("medicationStatus").get("state").asText())
        .isEqualTo("UNKNOWN");
    assertThat(days.get(2).get("medicationUnverified").asBoolean()).isFalse();

    // NOT_ASSERTED TAKEN is the symmetric non-event; verified values remain unchanged.
    createNote(owner.token(), recipientId, "2026-10-04",
        "{\"medicationStatus\":\"TAKEN\"}");
    createNote(owner.token(), recipientId, "2026-10-05",
        "{\"medicationStatus\":\"MISSED\",\"medicationVerified\":true}");
    createNote(owner.token(), recipientId, "2026-10-06",
        "{\"medicationStatus\":\"TAKEN\",\"medicationVerified\":true}");
    JsonNode facts = signals(owner.token(), recipientId, "2026-10-04", "2026-10-06");
    assertThat(facts.get(0).get("missedMedication").asBoolean()).isFalse();
    assertThat(facts.get(0).get("medicationUnverified").asBoolean()).isFalse();
    assertThat(facts.get(1).get("missedMedication").asBoolean()).isTrue();
    assertThat(facts.get(1).get("medicationUnverified").asBoolean()).isFalse();
    assertThat(facts.get(2).get("missedMedication").asBoolean()).isFalse();
    assertThat(facts.get(2).get("medicationUnverified").asBoolean()).isFalse();
  }

  @Test
  void missedReportCorrectedToTakenIsUnknownNotTaken() throws Exception {
    TestCaregiver owner = register("signal-correct1@example.com");
    String recipientId = createRecipient(owner.token());
    String noteId = createNote(owner.token(), recipientId, "2026-10-06",
        "{\"medicationStatus\":\"MISSED\",\"medicationVerified\":true}");
    append(owner.token(), noteId,
        "{\"medicationStatus\":\"TAKEN\",\"medicationVerified\":true}");

    // History is preserved: the original still says MISSED.
    JsonNode note = getNote(owner.token(), noteId);
    assertThat(note.get("medicationStatus").asText()).isEqualTo("MISSED");

    // The conflicting observation remains unknown without changing verification.
    JsonNode day = signals(owner.token(), recipientId, "2026-10-06", "2026-10-06").get(0);
    assertThat(day.get("missedMedication").isNull()).isTrue();
    assertThat(day.get("medicationUnverified").asBoolean()).isFalse();
    assertThat(day.get("observations").get("medicationStatus").get("state").asText()).isEqualTo("UNKNOWN");
    assertThat(day.get("observations").get("medicationVerification").asText()).isEqualTo("VERIFIED");
  }

  @Test
  void takenReportCorrectedToMissedIsUnknownSymmetrically() throws Exception {
    TestCaregiver owner = register("signal-correct2@example.com");
    String recipientId = createRecipient(owner.token());
    String noteId = createNote(owner.token(), recipientId, "2026-10-07",
        "{\"medicationStatus\":\"TAKEN\",\"medicationVerified\":true}");
    append(owner.token(), noteId,
        "{\"medicationStatus\":\"MISSED\",\"medicationVerified\":true}");

    JsonNode day = signals(owner.token(), recipientId, "2026-10-07", "2026-10-07").get(0);
    assertThat(day.get("missedMedication").isNull()).isTrue();
    assertThat(day.get("medicationUnverified").asBoolean()).isFalse();
    assertThat(day.get("observations").get("medicationStatus").get("state").asText()).isEqualTo("UNKNOWN");
    assertThat(day.get("observations").get("medicationVerification").asText()).isEqualTo("VERIFIED");
  }

  @Test
  void unknownCorrectionDoesNotInheritOldVerification() throws Exception {
    TestCaregiver owner = register("signal-unknown@example.com");
    String recipientId = createRecipient(owner.token());
    String noteId = createNote(owner.token(), recipientId, "2026-10-08",
        "{\"medicationStatus\":\"MISSED\",\"medicationVerified\":true}");
    append(owner.token(), noteId, "{\"medicationStatus\":\"UNKNOWN\"}");

    JsonNode day = signals(owner.token(), recipientId, "2026-10-08", "2026-10-08").get(0);
    assertThat(day.get("missedMedication").isNull()).isTrue();
    assertThat(day.get("medicationUnverified").asBoolean()).isFalse();
    assertThat(day.get("observations").get("medicationStatus").get("state").asText()).isEqualTo("UNKNOWN");
    assertThat(day.get("observations").get("medicationVerification").asText()).isEqualTo("NOT_ASSERTED");
  }

  @Test
  void verificationOnlyAddendumUpdatesVerificationIndependently() throws Exception {
    TestCaregiver owner = register("signal-verify@example.com");
    String recipientId = createRecipient(owner.token());
    String noteId = createNote(owner.token(), recipientId, "2026-10-09",
        "{\"medicationStatus\":\"MISSED\"}");
    append(owner.token(), noteId, "{\"medicationVerified\":true}");

    JsonNode day = signals(owner.token(), recipientId, "2026-10-09", "2026-10-09").get(0);
    assertThat(day.get("missedMedication").asBoolean()).isTrue();
    assertThat(day.get("medicationUnverified").asBoolean()).isFalse();
    assertThat(day.get("observations").get("medicationVerification").asText()).isEqualTo("VERIFIED");
  }

  @Test
  void latestNonNullAddendumWinsPerField() throws Exception {
    TestCaregiver owner = register("signal-override@example.com");
    String recipientId = createRecipient(owner.token());
    String noteId = createNote(owner.token(), recipientId, "2026-10-10",
        "{\"mood\":\"neutral\",\"appetite\":\"good\",\"sleep\":\"poor\",\"mobility\":\"limited\","
            + "\"painScore\":3,\"fallReported\":false,\"freeText\":\"original\"}");
    append(owner.token(), noteId, "{\"mood\":\"good\",\"sleep\":\"good\",\"painScore\":9}");
    append(owner.token(), noteId, "{\"appetite\":\"poor\",\"mobility\":\"walking with help\","
        + "\"fallReported\":true,\"freeText\":\"corrected\"}");

    JsonNode day = signals(owner.token(), recipientId, "2026-10-10", "2026-10-10").get(0);
    assertThat(day.get("mood").asText()).isEqualTo("good");
    assertThat(day.get("appetite").asText()).isEqualTo("poor");
    assertThat(day.get("sleep").asText()).isEqualTo("good");
    assertThat(day.get("mobility").asText()).isEqualTo("walking with help");
    assertThat(day.get("painScore").asInt()).isEqualTo(9);
    assertThat(day.get("fallReported").asBoolean()).isTrue();
    assertThat(day.get("poorAppetite").asBoolean()).isTrue();
    assertThat(day.get("freeText").asText()).isEqualTo("corrected");
  }

  @Test
  void fallAndAppetiteBoundariesAndUnexpectedInput() throws Exception {
    TestCaregiver owner = register("signal-edge@example.com");
    String recipientId = createRecipient(owner.token());
    // Pain boundaries, explicit fall=false, unrecognized appetite vocabulary.
    createNote(owner.token(), recipientId, "2026-10-11",
        "{\"painScore\":0,\"fallReported\":false,\"appetite\":\"meh\"}");
    createNote(owner.token(), recipientId, "2026-10-12",
        "{\"painScore\":10,\"fallReported\":true,\"appetite\":\"  POOR  \"}");

    JsonNode days = signals(owner.token(), recipientId, "2026-10-11", "2026-10-12");
    assertThat(days.get(0).get("painScore").asInt()).isZero();
    assertThat(days.get(0).get("fallReported").asBoolean()).isFalse();
    assertThat(days.get(0).get("poorAppetite").isNull()).isTrue();
    assertThat(days.get(1).get("painScore").asInt()).isEqualTo(10);
    assertThat(days.get(1).get("fallReported").asBoolean()).isTrue();
    assertThat(days.get(1).get("poorAppetite").asBoolean()).isTrue();
  }

  @Test
  void signalsAreChronologicalAndScoped() throws Exception {
    TestCaregiver owner = register("signal-order@example.com");
    TestCaregiver other = register("signal-order-other@example.com");
    String recipientId = createRecipient(owner.token());
    createNote(owner.token(), recipientId, "2026-10-03", "{\"painScore\":3}");
    createNote(owner.token(), recipientId, "2026-10-01", "{\"painScore\":1}");
    createNote(owner.token(), recipientId, "2026-10-02", "{\"painScore\":2}");

    JsonNode days = signals(owner.token(), recipientId, "2026-10-01", "2026-10-03");
    assertThat(days).hasSize(3);
    assertThat(days.get(0).get("date").asText()).isEqualTo("2026-10-01");
    assertThat(days.get(1).get("date").asText()).isEqualTo("2026-10-02");
    assertThat(days.get(2).get("date").asText()).isEqualTo("2026-10-03");

    // Cross-caregiver and unauthenticated access are rejected.
    mvc.perform(get(NOTES + "/signals")
            .param("recipientId", recipientId)
            .param("from", "2026-10-01").param("to", "2026-10-03")
            .header("Authorization", bearer(other.token())))
        .andExpect(status().isNotFound());
    mvc.perform(get(NOTES + "/signals")
            .param("recipientId", recipientId)
            .param("from", "2026-10-01").param("to", "2026-10-03"))
        .andExpect(status().isUnauthorized());

    // Inverted range is rejected.
    mvc.perform(get(NOTES + "/signals")
            .param("recipientId", recipientId)
            .param("from", "2026-10-03").param("to", "2026-10-01")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isBadRequest());

    // Unknown recipient is not found.
    mvc.perform(get(NOTES + "/signals")
            .param("recipientId", UUID.randomUUID().toString())
            .param("from", "2026-10-01").param("to", "2026-10-03")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isNotFound());
  }

  private JsonNode signals(String token, String recipientId, String from, String to) throws Exception {
    MvcResult result = mvc.perform(get(NOTES + "/signals")
            .param("recipientId", recipientId)
            .param("from", from).param("to", to)
            .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andReturn();
    return mapper.readTree(result.getResponse().getContentAsByteArray());
  }

  private JsonNode getNote(String token, String noteId) throws Exception {
    MvcResult result = mvc.perform(get(NOTES + "/" + noteId)
            .header("Authorization", bearer(token)))
        .andExpect(status().isOk())
        .andReturn();
    return mapper.readTree(result.getResponse().getContentAsByteArray());
  }

  private void append(String token, String noteId, String fields) throws Exception {
    mvc.perform(post(NOTES + "/" + noteId + "/addenda")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(fields))
        .andExpect(status().isCreated());
  }

  private TestCaregiver register(String email) throws Exception {
    MvcResult result = mvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
        .andExpect(status().isCreated())
        .andReturn();
    JsonNode json = mapper.readTree(result.getResponse().getContentAsByteArray());
    String token = json.get("token").asText();
    return new TestCaregiver(token, jwtService.parse(token).caregiverId());
  }

  private String createRecipient(String token) throws Exception {
    MvcResult result = mvc.perform(post("/api/recipients")
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"name\":\"Signal recipient\"}"))
        .andExpect(status().isCreated())
        .andReturn();
    return mapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asText();
  }

  private String createNote(String token, String recipientId, String date, String fields) throws Exception {
    String merged = "{\"recipientId\":\"" + recipientId + "\",\"date\":\"" + date + "\","
        + fields.substring(1);
    MvcResult result = mvc.perform(post(NOTES)
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(merged))
        .andExpect(status().isCreated())
        .andReturn();
    return mapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asText();
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  private record TestCaregiver(String token, UUID id) {
  }
}
