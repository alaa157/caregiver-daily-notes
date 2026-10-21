package com.caregiver.notes;

import com.caregiver.audit.AuditEvent;
import com.caregiver.audit.AuditEventRepository;
import com.caregiver.auth.CaregiverRepository;
import com.caregiver.auth.JwtService;
import com.caregiver.config.CaregiverApplication;
import com.caregiver.recipient.CareRecipientRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Database-backed coverage for the Daily Notes API: CRUD, validation, error
 * envelope, unicode preservation, addenda, history, ownership and auditing.
 */
@SpringBootTest(
    classes = CaregiverApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:notest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "auth.jwt.secret=test-secret-that-is-definitely-32-bytes-long",
      "auth.jwt.expiry=24h"
    })
@AutoConfigureMockMvc
class NoteControllerTest {

  private static final String NOTES = "/api/notes";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private JwtService jwtService;
  @Autowired private CaregiverRepository caregivers;
  @Autowired private CareRecipientRepository recipients;
  @Autowired private DailyNoteRepository notes;
  @Autowired private NoteAddendumRepository addenda;
  @Autowired private AuditEventRepository auditEvents;
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
  void createAndRetrieveValidNote() throws Exception {
    TestCaregiver owner = register("note-owner@example.com");
    String recipientId = createRecipient(owner.token());

    MvcResult created = mvc.perform(post(NOTES)
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(noteJson(recipientId, "2026-10-01")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.recipientId").value(recipientId))
        .andExpect(jsonPath("$.date").value("2026-10-01"))
        .andExpect(jsonPath("$.mood").value("good"))
        .andExpect(jsonPath("$.medicationStatus").value("TAKEN"))
        .andExpect(jsonPath("$.medicationVerified").value(true))
        .andExpect(jsonPath("$.painScore").value(3))
        .andExpect(jsonPath("$.fallReported").value(false))
        .andExpect(jsonPath("$.addendumCount").value(0))
        .andReturn();
    String id = mapper.readTree(created.getResponse().getContentAsByteArray()).get("id").asText();

    mvc.perform(get(NOTES + "/" + id).header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.freeText").value("Evening note"));

    mvc.perform(get(NOTES + "/by-recipient/" + recipientId)
            .param("date", "2026-10-01")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id));
  }

  @Test
  void retrievingMissingNoteReturnsMachineReadableNotFound() throws Exception {
    TestCaregiver owner = register("note-missing@example.com");
    String missing = UUID.randomUUID().toString();

    mvc.perform(get(NOTES + "/" + missing).header("Authorization", bearer(owner.token())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));

    String recipientId = createRecipient(owner.token());
    mvc.perform(get(NOTES + "/by-recipient/" + recipientId)
            .param("date", "2026-10-05")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void duplicateNoteForSameRecipientAndDateIsRejected() throws Exception {
    TestCaregiver owner = register("note-dup@example.com");
    String recipientId = createRecipient(owner.token());

    mvc.perform(post(NOTES)
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(noteJson(recipientId, "2026-10-02")))
        .andExpect(status().isCreated());

    mvc.perform(post(NOTES)
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(noteJson(recipientId, "2026-10-02")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("NOTE_CONFLICT"));

    assertThat(notes.count()).isEqualTo(1);
  }

  @Test
  void sameDateForDifferentRecipientsIsAllowed() throws Exception {
    TestCaregiver owner = register("note-tworec@example.com");
    String first = createRecipient(owner.token());
    String second = createRecipient(owner.token());

    mvc.perform(post(NOTES)
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(noteJson(first, "2026-10-02")))
        .andExpect(status().isCreated());
    mvc.perform(post(NOTES)
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(noteJson(second, "2026-10-02")))
        .andExpect(status().isCreated());

    assertThat(notes.count()).isEqualTo(2);
  }

  @Test
  void validationFailuresReturnMachineReadableErrors() throws Exception {
    TestCaregiver owner = register("note-validation@example.com");
    String recipientId = createRecipient(owner.token());

    // Missing required fields.
    mvc.perform(post(NOTES)
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"mood\":\"good\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fields.recipientId").exists())
        .andExpect(jsonPath("$.fields.date").exists());

    // Pain out of range.
    mvc.perform(post(NOTES)
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(noteJson(recipientId, "2026-10-03").replace("\"painScore\":3", "\"painScore\":11")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    // Unknown medication enum.
    mvc.perform(post(NOTES)
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(noteJson(recipientId, "2026-10-03").replace("TAKEN", "MAYBE")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    // Malformed JSON body.
    mvc.perform(post(NOTES)
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{not json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

    assertThat(notes.count()).isZero();
  }

  @Test
  void malformedIdsAndMissingParametersAreMachineReadable() throws Exception {
    TestCaregiver owner = register("note-params@example.com");

    mvc.perform(get(NOTES + "/not-a-uuid").header("Authorization", bearer(owner.token())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));

    String recipientId = createRecipient(owner.token());
    mvc.perform(get(NOTES + "/by-recipient/" + recipientId)
            .param("date", "not-a-date")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"));

    mvc.perform(get(NOTES)
            .param("to", "2026-10-07")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));

    mvc.perform(get(NOTES + "/signals")
            .param("recipientId", recipientId)
            .param("from", "2026-10-01")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"));
  }

  @Test
  void unauthenticatedRequestsAreRejectedWithoutLeakingData() throws Exception {
    mvc.perform(get(NOTES + "/" + UUID.randomUUID()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

    mvc.perform(post(NOTES)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"recipientId\":\"" + UUID.randomUUID() + "\",\"date\":\"2026-10-01\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void crossCaregiverAccessIsIndistinguishableFromMissing() throws Exception {
    TestCaregiver owner = register("note-owner3@example.com");
    TestCaregiver other = register("note-other@example.com");
    String recipientId = createRecipient(owner.token());
    String noteId = createNote(owner.token(), recipientId, "2026-10-04");

    // Read paths.
    mvc.perform(get(NOTES + "/" + noteId).header("Authorization", bearer(other.token())))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    mvc.perform(get(NOTES + "/by-recipient/" + recipientId)
            .param("date", "2026-10-04")
            .header("Authorization", bearer(other.token())))
        .andExpect(status().isNotFound());
    mvc.perform(get(NOTES + "/" + noteId + "/addenda")
            .header("Authorization", bearer(other.token())))
        .andExpect(status().isNotFound());

    // History scoped to the caller: other's notes never appear.
    mvc.perform(get(NOTES)
            .param("from", "2026-10-01").param("to", "2026-10-31")
            .header("Authorization", bearer(other.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());
    mvc.perform(get(NOTES)
            .param("from", "2026-10-01").param("to", "2026-10-31")
            .param("recipientId", recipientId)
            .header("Authorization", bearer(other.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());

    // Write paths: cannot create for, nor append to, another caregiver's data.
    mvc.perform(post(NOTES)
            .header("Authorization", bearer(other.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(noteJson(recipientId, "2026-10-06")))
        .andExpect(status().isNotFound());
    mvc.perform(post(NOTES + "/" + noteId + "/addenda")
            .header("Authorization", bearer(other.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"freeText\":\"tampered\"}"))
        .andExpect(status().isNotFound());
    mvc.perform(get(NOTES + "/signals")
            .param("recipientId", recipientId)
            .param("from", "2026-10-01").param("to", "2026-10-31")
            .header("Authorization", bearer(other.token())))
        .andExpect(status().isNotFound());

    // Owner data untouched.
    mvc.perform(get(NOTES + "/" + noteId).header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk());
    assertThat(notes.count()).isEqualTo(1);
  }

  @Test
  void unicodeFreeTextRoundTripsByteIdentical() throws Exception {
    TestCaregiver owner = register("note-unicode@example.com");
    String recipientId = createRecipient(owner.token());
    List<String> texts = List.of(
        "ملاحظة اليوم: المريض بحالة جيدة، والنوم مستقر. درجة الحرارة ٣٧.٥",
        "Evening note: pain 3/10 — fell? No. Meds taken ✓",
        "المريض قال: I feel better today الحمد لله — mixed نص مع English!",
        "Punctuation «...» — “quotes” — 😊 ✓ ° 37.5° — long dash – test",
        "سطر أول\nسطر ثانٍ\twith tab — Ünïcödé ✓");

    int day = 10;
    for (String text : texts) {
      String date = "2026-10-" + day++;
      String body = "{\"recipientId\":\"" + recipientId + "\",\"date\":\"" + date + "\","
          + "\"freeText\":" + mapper.writeValueAsString(text) + "}";
      MvcResult created = mvc.perform(post(NOTES)
              .header("Authorization", bearer(owner.token()))
              .contentType(MediaType.APPLICATION_JSON)
              .content(body))
          .andExpect(status().isCreated())
          .andReturn();
      String id = mapper.readTree(created.getResponse().getContentAsByteArray()).get("id").asText();

      MvcResult fetched = mvc.perform(get(NOTES + "/" + id)
              .header("Authorization", bearer(owner.token())))
          .andExpect(status().isOk())
          .andReturn();
      assertThat(mapper.readTree(fetched.getResponse().getContentAsByteArray()).get("freeText").asText())
          .isEqualTo(text);
    }
  }

  @Test
  void addendumAppendsWithoutModifyingOriginal() throws Exception {
    TestCaregiver owner = register("note-addendum@example.com");
    String recipientId = createRecipient(owner.token());
    String noteId = createNote(owner.token(), recipientId, "2026-10-11");

    mvc.perform(post(NOTES + "/" + noteId + "/addenda")
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"painScore\":8,\"freeText\":\"Correction: pain is worse in the evening.\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.noteId").value(noteId))
        .andExpect(jsonPath("$.painScore").value(8));

    // Original note body is unchanged; count reflects the addendum.
    mvc.perform(get(NOTES + "/" + noteId).header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.painScore").value(3))
        .andExpect(jsonPath("$.freeText").value("Evening note"))
        .andExpect(jsonPath("$.addendumCount").value(1));

    // Second addendum; chronological listing.
    Thread.sleep(5);
    mvc.perform(post(NOTES + "/" + noteId + "/addenda")
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"fallReported\":true}"))
        .andExpect(status().isCreated());

    MvcResult listed = mvc.perform(get(NOTES + "/" + noteId + "/addenda")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode addendaJson = mapper.readTree(listed.getResponse().getContentAsByteArray());
    assertThat(addendaJson).hasSize(2);
    assertThat(addendaJson.get(0).get("painScore").asInt()).isEqualTo(8);
    assertThat(addendaJson.get(1).get("fallReported").asBoolean()).isTrue();
  }

  @Test
  void addendumValidationAndUnknownNoteAreHandled() throws Exception {
    TestCaregiver owner = register("note-addendum2@example.com");
    String recipientId = createRecipient(owner.token());
    String noteId = createNote(owner.token(), recipientId, "2026-10-12");

    // Empty correction corrects nothing.
    mvc.perform(post(NOTES + "/" + noteId + "/addenda")
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

    // Out-of-range corrected field.
    mvc.perform(post(NOTES + "/" + noteId + "/addenda")
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"painScore\":99}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    // Unknown note.
    mvc.perform(post(NOTES + "/" + UUID.randomUUID() + "/addenda")
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"freeText\":\"correction\"}"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOT_FOUND"));

    assertThat(addenda.count()).isZero();
  }

  @Test
  void historyFiltersOrdersAndPaginatesDeterministically() throws Exception {
    TestCaregiver owner = register("note-history@example.com");
    String first = createRecipient(owner.token());
    String second = createRecipient(owner.token());
    createNote(owner.token(), first, "2026-10-01");
    createNote(owner.token(), first, "2026-10-05");
    createNote(owner.token(), first, "2026-10-10");
    createNote(owner.token(), second, "2026-10-05");

    // Addendum counts are included without bodies.
    MvcResult withAddendum = mvc.perform(get(NOTES + "/by-recipient/" + first)
            .param("date", "2026-10-10")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andReturn();
    String annotatedId =
        mapper.readTree(withAddendum.getResponse().getContentAsByteArray()).get("id").asText();
    mvc.perform(post(NOTES + "/" + annotatedId + "/addenda")
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"freeText\":\"history count check\"}"))
        .andExpect(status().isCreated());

    // Newest first.
    MvcResult all = mvc.perform(get(NOTES)
            .param("from", "2026-10-01").param("to", "2026-10-10")
            .param("size", "10")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andReturn();
    JsonNode content = mapper.readTree(all.getResponse().getContentAsByteArray()).get("content");
    assertThat(content).hasSize(4);
    assertThat(content.get(0).get("date").asText()).isEqualTo("2026-10-10");
    assertThat(content.get(0).get("addendumCount").asInt()).isEqualTo(1);
    assertThat(content.get(0).has("addenda")).isFalse();
    assertThat(content.get(3).get("date").asText()).isEqualTo("2026-10-01");
    assertThat(content.get(3).get("addendumCount").asInt()).isZero();

    // Recipient filter.
    mvc.perform(get(NOTES)
            .param("from", "2026-10-01").param("to", "2026-10-10")
            .param("recipientId", second)
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(1));

    // Inclusive date boundaries.
    mvc.perform(get(NOTES)
            .param("from", "2026-10-05").param("to", "2026-10-05")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content.length()").value(2));

    // Pagination is stable and complete across pages.
    Set<String> seen = new HashSet<>();
    for (int page = 0; page < 2; page++) {
      MvcResult paged = mvc.perform(get(NOTES)
              .param("from", "2026-10-01").param("to", "2026-10-10")
              .param("page", String.valueOf(page)).param("size", "2")
              .header("Authorization", bearer(owner.token())))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content.length()").value(2))
          .andReturn();
      JsonNode pageContent = mapper.readTree(paged.getResponse().getContentAsByteArray()).get("content");
      List<String> order = new ArrayList<>();
      pageContent.forEach(n -> {
        seen.add(n.get("id").asText());
        order.add(n.get("date").asText());
      });
      assertThat(order).isSortedAccordingTo((a, b) -> b.compareTo(a));
    }
    assertThat(seen).hasSize(4);

    // Empty history.
    mvc.perform(get(NOTES)
            .param("from", "2025-01-01").param("to", "2025-01-31")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content").isEmpty());

    // Invalid paging and range parameters.
    mvc.perform(get(NOTES)
            .param("from", "2026-10-10").param("to", "2026-10-01")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    mvc.perform(get(NOTES)
            .param("from", "2026-10-01").param("to", "2026-10-10")
            .param("page", "-1")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isBadRequest());
    mvc.perform(get(NOTES)
            .param("from", "2026-10-01").param("to", "2026-10-10")
            .param("size", "101")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isBadRequest());
    mvc.perform(get(NOTES)
            .param("from", "2026-10-01").param("to", "2026-10-10")
            .param("size", "0")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isBadRequest());
  }

  @Test
  void noteForInactiveRecipientIsRejected() throws Exception {
    TestCaregiver owner = register("note-inactive@example.com");
    String recipientId = createRecipient(owner.token());
    mvc.perform(patch("/api/recipients/" + recipientId + "/deactivate")
            .header("Authorization", bearer(owner.token())))
        .andExpect(status().isOk());

    mvc.perform(post(NOTES)
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(noteJson(recipientId, "2026-10-20")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

    assertThat(notes.count()).isZero();
  }

  @Test
  void writesAreAuditedWithoutFreeText() throws Exception {
    TestCaregiver owner = register("note-audit@example.com");
    String recipientId = createRecipient(owner.token());
    String secretText = "Secret clinical detail that must never appear in audit logs 12345";
    String body = "{\"recipientId\":\"" + recipientId + "\",\"date\":\"2026-10-15\","
        + "\"freeText\":" + mapper.writeValueAsString(secretText) + "}";
    MvcResult created = mvc.perform(post(NOTES)
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated())
        .andReturn();
    String noteId = mapper.readTree(created.getResponse().getContentAsByteArray()).get("id").asText();

    MvcResult appended = mvc.perform(post(NOTES + "/" + noteId + "/addenda")
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"freeText\":\"Addendum secret detail 67890\"}"))
        .andExpect(status().isCreated())
        .andReturn();
    String addendumId = mapper.readTree(appended.getResponse().getContentAsByteArray()).get("id").asText();

    List<AuditEvent> events = auditEvents.findAllByActorCaregiverIdOrderByOccurredAtDesc(owner.id());
    assertThat(events).anySatisfy(e -> {
      assertThat(e.getEventType()).isEqualTo("NOTE_CREATED");
      assertThat(e.getTargetType()).isEqualTo("DAILY_NOTE");
      assertThat(e.getTargetId()).isEqualTo(noteId);
      assertThat(e.getSafeMetadata()).contains(recipientId).contains("2026-10-15");
      assertThat(e.getSafeMetadata()).doesNotContain(secretText);
    });
    assertThat(events).anySatisfy(e -> {
      assertThat(e.getEventType()).isEqualTo("NOTE_ADDENDUM_CREATED");
      assertThat(e.getTargetId()).isEqualTo(addendumId);
      assertThat(e.getSafeMetadata()).contains(noteId);
      assertThat(e.getSafeMetadata()).doesNotContain("67890");
    });
    assertThat(events).allSatisfy(e ->
        assertThat(e.getActorCaregiverId()).isEqualTo(owner.id()));
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
            .content("{\"name\":\"Note recipient\"}"))
        .andExpect(status().isCreated())
        .andReturn();
    return mapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asText();
  }

  private String createNote(String token, String recipientId, String date) throws Exception {
    MvcResult result = mvc.perform(post(NOTES)
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(noteJson(recipientId, date)))
        .andExpect(status().isCreated())
        .andReturn();
    return mapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asText();
  }

  private static String noteJson(String recipientId, String date) {
    return "{\"recipientId\":\"" + recipientId + "\",\"date\":\"" + date + "\","
        + "\"mood\":\"good\",\"appetite\":\"normal\",\"sleep\":\"good\",\"mobility\":\"normal\","
        + "\"medicationStatus\":\"TAKEN\",\"medicationVerified\":true,"
        + "\"painScore\":3,\"fallReported\":false,\"freeText\":\"Evening note\"}";
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  private record TestCaregiver(String token, UUID id) {
  }
}
