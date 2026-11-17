package com.caregiver.recipient;

import com.caregiver.auth.CaregiverRepository;
import com.caregiver.auth.JwtService;
import com.caregiver.config.CaregiverApplication;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Idempotent recipient creation: same key + same payload replays the original
 * recipient, same key + different payload conflicts, and missing or blank keys
 * keep the historical create-every-time behavior.
 */
@SpringBootTest(
    classes = CaregiverApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:recipientidemtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
class RecipientIdempotencyTest {

  private static final String RECIPIENTS = "/api/recipients";

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;
  @Autowired private JwtService jwtService;
  @Autowired private CareRecipientRepository recipients;
  @Autowired private CaregiverRepository caregivers;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("delete from recipient_create_requests");
    jdbcTemplate.update("delete from audit_events");
    recipients.deleteAll();
    caregivers.deleteAll();
  }

  @Test
  void firstCreateReturns201AndReplayReturnsSameRecipient() throws Exception {
    TestCaregiver owner = register("idem-first@example.com");
    String body = "{\"name\":\"Samir Hassan\",\"relationship\":\"Father\"}";

    MvcResult first = mvc.perform(idempotentCreate(owner.token(), "key-1", body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.name").value("Samir Hassan"))
        .andReturn();
    MvcResult second = mvc.perform(idempotentCreate(owner.token(), "key-1", body))
        .andExpect(status().isCreated())
        .andReturn();

    assertThat(idOf(second)).isEqualTo(idOf(first));
    assertThat(recipients.count()).isEqualTo(1);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM recipient_create_requests", Integer.class)).isEqualTo(1);
  }

  @Test
  void replayWritesNoSecondAuditEvent() throws Exception {
    TestCaregiver owner = register("idem-audit@example.com");
    String body = "{\"name\":\"Audit recipient\"}";

    mvc.perform(idempotentCreate(owner.token(), "audit-key", body))
        .andExpect(status().isCreated());
    mvc.perform(idempotentCreate(owner.token(), "audit-key", body))
        .andExpect(status().isCreated());

    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM audit_events WHERE event_type = 'RECIPIENT_CREATED'",
        Integer.class)).isEqualTo(1);
  }

  @Test
  void sameKeyDifferentPayloadConflicts() throws Exception {
    TestCaregiver owner = register("idem-conflict@example.com");

    mvc.perform(idempotentCreate(owner.token(), "key-2", "{\"name\":\"First\",\"relationship\":\"Father\"}"))
        .andExpect(status().isCreated());
    mvc.perform(idempotentCreate(owner.token(), "key-2", "{\"name\":\"Second\",\"relationship\":\"Father\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    // Same name but a different relationship is also a different payload.
    mvc.perform(idempotentCreate(owner.token(), "key-2", "{\"name\":\"First\",\"relationship\":\"Mother\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));

    assertThat(recipients.count()).isEqualTo(1);
  }

  @Test
  void noKeyOrBlankKeyCreatesDistinctRecipients() throws Exception {
    TestCaregiver owner = register("idem-nokey@example.com");
    String body = "{\"name\":\"No key recipient\"}";

    mvc.perform(post(RECIPIENTS)
            .header("Authorization", bearer(owner.token()))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated());
    mvc.perform(post(RECIPIENTS)
            .header("Authorization", bearer(owner.token()))
            .header("Idempotency-Key", "   ")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isCreated());

    assertThat(recipients.count()).isEqualTo(2);
  }

  @Test
  void overlongIdempotencyKeyIsRejected() throws Exception {
    TestCaregiver owner = register("idem-longkey@example.com");

    mvc.perform(idempotentCreate(owner.token(), "k".repeat(101), "{\"name\":\"Too long\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

    assertThat(recipients.count()).isZero();
  }

  @Test
  void differentCaregiverReusingKeyCreatesOwnRecipient() throws Exception {
    TestCaregiver first = register("idem-first@example.com");
    TestCaregiver second = register("idem-second@example.com");
    String body = "{\"name\":\"Shared key recipient\"}";

    MvcResult firstResult = mvc.perform(idempotentCreate(first.token(), "shared-key", body))
        .andExpect(status().isCreated())
        .andReturn();
    MvcResult secondResult = mvc.perform(idempotentCreate(second.token(), "shared-key", body))
        .andExpect(status().isCreated())
        .andReturn();

    assertThat(idOf(secondResult)).isNotEqualTo(idOf(firstResult));
    assertThat(recipients.count()).isEqualTo(2);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM recipient_create_requests", Integer.class)).isEqualTo(2);
  }

  private MockHttpServletRequestBuilder idempotentCreate(String token, String key, String body) {
    return post(RECIPIENTS)
        .header("Authorization", bearer(token))
        .header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  private String idOf(MvcResult result) throws Exception {
    return mapper.readTree(result.getResponse().getContentAsByteArray()).get("id").asText();
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

  private static String bearer(String token) {
    return "Bearer " + token;
  }

  private record TestCaregiver(String token, UUID id) {
  }
}
