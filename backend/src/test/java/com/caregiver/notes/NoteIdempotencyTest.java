package com.caregiver.notes;

import com.caregiver.auth.CaregiverRepository;
import com.caregiver.auth.JwtService;
import com.caregiver.config.CaregiverApplication;
import com.caregiver.recipient.CareRecipientRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Idempotency contract: same key + same payload replays safely, same key +
 * different payload conflicts, and concurrent duplicates collapse to one row.
 */
@SpringBootTest(
    classes = CaregiverApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:noteidemtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "auth.jwt.secret=test-secret-that-is-definitely-32-bytes-long",
      "auth.jwt.expiry=24h"
    })
@AutoConfigureMockMvc
class NoteIdempotencyTest {

  private static final String NOTES = "/api/notes";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private JwtService jwtService;
  @Autowired private CaregiverRepository caregivers;
  @Autowired private CareRecipientRepository recipients;
  @Autowired private DailyNoteRepository notes;
  @Autowired private NoteAddendumRepository addenda;
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
  void sameKeySamePayloadReplaysOriginalNote() throws Exception {
    TestCaregiver owner = register("idem-replay@example.com");
    String recipientId = createRecipient(owner.token());
    String body = noteJson(recipientId, "2026-10-01", "Evening note");

    MvcResult first = mvc.perform(idempotentCreate(owner.token(), "key-1", body))
        .andExpect(status().isCreated())
        .andReturn();
    MvcResult second = mvc.perform(idempotentCreate(owner.token(), "key-1", body))
        .andExpect(status().isCreated())
        .andReturn();

    assertThat(bodyOf(second)).isEqualTo(bodyOf(first));
    assertThat(notes.count()).isEqualTo(1);
  }

  @Test
  void sameKeyDifferentPayloadConflicts() throws Exception {
    TestCaregiver owner = register("idem-conflict@example.com");
    String recipientId = createRecipient(owner.token());

    mvc.perform(idempotentCreate(owner.token(), "key-2", noteJson(recipientId, "2026-10-01", "First")))
        .andExpect(status().isCreated());
    mvc.perform(idempotentCreate(owner.token(), "key-2", noteJson(recipientId, "2026-10-02", "Second")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

    assertThat(notes.count()).isEqualTo(1);
  }

  @Test
  void sameKeySamePayloadReplaysOriginalAddendum() throws Exception {
    TestCaregiver owner = register("idem-addendum@example.com");
    String noteId = createNote(owner.token(), "2026-10-03");
    String body = "{\"painScore\":8,\"freeText\":\"Correction\"}";

    MvcResult first = mvc.perform(idempotentAppend(owner.token(), noteId, "add-key-1", body))
        .andExpect(status().isCreated())
        .andReturn();
    MvcResult second = mvc.perform(idempotentAppend(owner.token(), noteId, "add-key-1", body))
        .andExpect(status().isCreated())
        .andReturn();

    assertThat(bodyOf(second)).isEqualTo(bodyOf(first));
    assertThat(addenda.count()).isEqualTo(1);
  }

  @Test
  void addendumKeyReuseWithDifferentPayloadOrNoteConflicts() throws Exception {
    TestCaregiver owner = register("idem-addendum2@example.com");
    String firstNote = createNote(owner.token(), "2026-10-04");
    String secondNote = createNote(owner.token(), "2026-10-05");

    mvc.perform(idempotentAppend(owner.token(), firstNote, "add-key-2", "{\"painScore\":5}"))
        .andExpect(status().isCreated());
    // Same key, different payload on the same note.
    mvc.perform(idempotentAppend(owner.token(), firstNote, "add-key-2", "{\"painScore\":6}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    // Same key replayed against a different note.
    mvc.perform(idempotentAppend(owner.token(), secondNote, "add-key-2", "{\"painScore\":5}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

    assertThat(addenda.count()).isEqualTo(1);
  }

  @Test
  void distinctKeysCreateDistinctNotesAndBlankKeysAreNotDeduplicated() throws Exception {
    TestCaregiver owner = register("idem-distinct@example.com");
    String recipientId = createRecipient(owner.token());

    mvc.perform(idempotentCreate(owner.token(), "key-a", noteJson(recipientId, "2026-10-06", "A")))
        .andExpect(status().isCreated());
    mvc.perform(idempotentCreate(owner.token(), "key-b", noteJson(recipientId, "2026-10-07", "B")))
        .andExpect(status().isCreated());
    // Blank keys carry no idempotency: a second distinct date still creates.
    mvc.perform(idempotentCreate(owner.token(), "   ", noteJson(recipientId, "2026-10-08", "C")))
        .andExpect(status().isCreated());

    assertThat(notes.count()).isEqualTo(3);
  }

  @Test
  void overlongIdempotencyKeyIsRejected() throws Exception {
    TestCaregiver owner = register("idem-longkey@example.com");
    String recipientId = createRecipient(owner.token());

    mvc.perform(idempotentCreate(owner.token(), "k".repeat(101), noteJson(recipientId, "2026-10-09", "X")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

    assertThat(notes.count()).isZero();
  }

  @Test
  void concurrentIdenticalCreatesCollapseToOneRow() throws Exception {
    TestCaregiver owner = register("idem-race@example.com");
    String recipientId = createRecipient(owner.token());
    String body = noteJson(recipientId, "2026-10-11", "Race note");

    List<MockHttpServletResponse> responses = race(8,
        () -> mvc.perform(idempotentCreate(owner.token(), "race-key", body)).andReturn().getResponse());

    assertThat(notes.count()).isEqualTo(1);
    String winner = idOf(responses.get(0));
    assertThat(responses).allSatisfy(response -> {
      assertThat(response.getStatus()).isEqualTo(201);
      try {
        assertThat(idOf(response)).isEqualTo(winner);
      } catch (Exception e) {
        throw new AssertionError(e);
      }
    });
    // The stored row matches the requested payload (no partial/duplicated state).
    assertThat(notes.findAll()).singleElement().satisfies(note -> {
      assertThat(note.getFreeText()).isEqualTo("Race note");
      assertThat(note.getIdempotencyKey()).isEqualTo("race-key");
    });
  }

  @Test
  void concurrentConflictingCreatesLeaveOneRowAndRejectLosers() throws Exception {
    TestCaregiver owner = register("idem-race2@example.com");
    String recipientId = createRecipient(owner.token());

    List<Callable<MockHttpServletResponse>> tasks = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      final String payload = noteJson(recipientId, "2026-10-1" + i, "Racer " + i);
      tasks.add(() -> mvc.perform(idempotentCreate(owner.token(), "race-key-2", payload))
          .andReturn().getResponse());
    }
    List<MockHttpServletResponse> responses = race(tasks);

    assertThat(notes.count()).isEqualTo(1);
    String winnerId = notes.findAll().get(0).getId().toString();
    long created = responses.stream().filter(r -> r.getStatus() == 201).count();
    long conflicted = responses.stream().filter(r -> r.getStatus() == 409).count();
    assertThat(created).isEqualTo(1);
    assertThat(conflicted).isEqualTo(7);
    assertThat(responses).allSatisfy(response -> {
      try {
        if (response.getStatus() == 201) {
          assertThat(idOf(response)).isEqualTo(winnerId);
        } else {
          JsonNode error = mapper.readTree(response.getContentAsByteArray());
          assertThat(error.get("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        }
      } catch (Exception e) {
        throw new AssertionError(e);
      }
    });
  }

  @Test
  void concurrentIdenticalAppendsCollapseToOneRow() throws Exception {
    TestCaregiver owner = register("idem-race-add@example.com");
    String noteId = createNote(owner.token(), "2026-10-18");
    String body = "{\"painScore\":6,\"freeText\":\"Race correction\"}";

    List<MockHttpServletResponse> responses = race(8,
        () -> mvc.perform(idempotentAppend(owner.token(), noteId, "race-add-key", body))
            .andReturn().getResponse());

    assertThat(addenda.count()).isEqualTo(1);
    String winner = idOf(responses.get(0));
    assertThat(responses).allSatisfy(response -> {
      assertThat(response.getStatus()).isEqualTo(201);
      try {
        assertThat(idOf(response)).isEqualTo(winner);
      } catch (Exception e) {
        throw new AssertionError(e);
      }
    });
  }

  private List<MockHttpServletResponse> race(int threads, Callable<MockHttpServletResponse> task) throws Exception {
    List<Callable<MockHttpServletResponse>> tasks = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      tasks.add(task);
    }
    return race(tasks);
  }

  private List<MockHttpServletResponse> race(List<Callable<MockHttpServletResponse>> tasks) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
    try {
      List<Future<MockHttpServletResponse>> futures = pool.invokeAll(tasks, 60, TimeUnit.SECONDS);
      List<MockHttpServletResponse> responses = new ArrayList<>();
      for (Future<MockHttpServletResponse> future : futures) {
        assertThat(future.isCancelled()).isFalse();
        responses.add(future.get(10, TimeUnit.SECONDS));
      }
      return responses;
    } finally {
      pool.shutdownNow();
    }
  }

  private MockHttpServletRequestBuilder idempotentCreate(String token, String key, String body) {
    return post(NOTES)
        .header("Authorization", bearer(token))
        .header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private MockHttpServletRequestBuilder idempotentAppend(String token, String noteId, String key, String body) {
    return post(NOTES + "/" + noteId + "/addenda")
        .header("Authorization", bearer(token))
        .header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private String bodyOf(MvcResult result) throws Exception {
    return new String(result.getResponse().getContentAsByteArray(),
        java.nio.charset.StandardCharsets.UTF_8);
  }

  private String idOf(MockHttpServletResponse response) throws Exception {
    return mapper.readTree(response.getContentAsByteArray()).get("id").asText();
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
            .content("{\"name\":\"Idem recipient\"}"))
        .andExpect(status().isCreated())
        .andReturn();
    return mapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asText();
  }

  private String createNote(String token, String date) throws Exception {
    String recipientId = createRecipient(token);
    MvcResult result = mvc.perform(post(NOTES)
            .header("Authorization", bearer(token))
            .contentType(MediaType.APPLICATION_JSON)
            .content(noteJson(recipientId, date, "Base note")))
        .andExpect(status().isCreated())
        .andReturn();
    return mapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asText();
  }

  private static String noteJson(String recipientId, String date, String freeText) {
    return "{\"recipientId\":\"" + recipientId + "\",\"date\":\"" + date + "\","
        + "\"mood\":\"good\",\"medicationStatus\":\"TAKEN\",\"medicationVerified\":true,"
        + "\"painScore\":2,\"fallReported\":false,\"freeText\":\"" + freeText + "\"}";
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  private record TestCaregiver(String token, UUID id) {
  }
}
