package com.caregiver.notes;

import com.caregiver.auth.JwtService;
import com.caregiver.auth.CaregiverRepository;
import com.caregiver.config.CaregiverApplication;
import com.caregiver.recipient.CareRecipientRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Daily-note date rule (issue #737): a note may be dated today or earlier,
 * never in the future. "Today" is the explicit UTC clock date, matching the
 * web client, and the rejection happens in the service before any write.
 */
@SpringBootTest(
    classes = CaregiverApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:dailynotedatetest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.flyway.enabled=true",
      "spring.jpa.hibernate.ddl-auto=validate",
      "auth.jwt.secret=test-secret-that-is-definitely-32-bytes-long",
      "auth.jwt.expiry=24h"
    })
@AutoConfigureMockMvc
class DailyNoteServiceTest {

  private static final String NOTES = "/api/notes";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private JwtService jwtService;
  @Autowired private CaregiverRepository caregivers;
  @Autowired private CareRecipientRepository recipients;
  @Autowired private DailyNoteRepository notes;
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
  void rejectsNoteDatedTomorrow() throws Exception {
    TestCaregiver owner = register("future-tomorrow@example.com");
    String recipientId = createRecipient(owner.token());
    String tomorrow = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString();

    mvc.perform(create(owner.token(), recipientId, tomorrow))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("A note cannot be dated after today."));

    assertThat(notes.count()).isZero();
  }

  @Test
  void rejectsNoteDatedFarInTheFuture() throws Exception {
    TestCaregiver owner = register("future-far@example.com");
    String recipientId = createRecipient(owner.token());
    String farFuture = LocalDate.now(ZoneOffset.UTC).plusYears(36).toString();

    mvc.perform(create(owner.token(), recipientId, farFuture))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

    assertThat(notes.count()).isZero();
  }

  @Test
  void acceptsNoteDatedToday() throws Exception {
    TestCaregiver owner = register("future-today@example.com");
    String recipientId = createRecipient(owner.token());
    String today = LocalDate.now(ZoneOffset.UTC).toString();

    mvc.perform(create(owner.token(), recipientId, today))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.date").value(today));

    assertThat(notes.count()).isEqualTo(1);
    assertThat(notes.findAll().get(0).getDate().toString()).isEqualTo(today);
  }

  @Test
  void acceptsBackdatedNote() throws Exception {
    TestCaregiver owner = register("future-backdated@example.com");
    String recipientId = createRecipient(owner.token());
    String yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1).toString();

    mvc.perform(create(owner.token(), recipientId, yesterday))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.date").value(yesterday));

    assertThat(notes.count()).isEqualTo(1);
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder create(
      String token, String recipientId, String date) {
    return post(NOTES)
        .header("Authorization", bearer(token))
        .contentType(MediaType.APPLICATION_JSON)
        .content(noteJson(recipientId, date));
  }

  private TestCaregiver register(String email) throws Exception {
    MvcResult result = mvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"password1234\"}"))
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
            .content("{\"name\":\"Date rule recipient\"}"))
        .andExpect(status().isCreated())
        .andReturn();
    return mapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asText();
  }

  private static String noteJson(String recipientId, String date) {
    return "{\"recipientId\":\"" + recipientId + "\",\"date\":\"" + date + "\","
        + "\"mood\":\"good\",\"freeText\":\"Date rule note\"}";
  }

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  private record TestCaregiver(String token, UUID id) {
  }
}
