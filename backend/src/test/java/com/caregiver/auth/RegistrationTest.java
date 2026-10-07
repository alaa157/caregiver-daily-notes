package com.caregiver.auth;

import com.caregiver.config.CaregiverApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The real security filter chain arrives in Task 7; filters stay off here so
// this tests the registration behavior only.
@SpringBootTest(
    classes = CaregiverApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:authtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.flyway.enabled=true",
      "spring.flyway.placeholder-replacement=false",
      "spring.jpa.hibernate.ddl-auto=validate",
      "auth.jwt.secret=test-secret-that-is-definitely-32-bytes-long",
      "auth.jwt.expiry=24h"
    })
@AutoConfigureMockMvc(addFilters = false)
class RegistrationTest {

  @Autowired
  private MockMvc mvc;

  @Autowired
  private CaregiverRepository repository;

  @Autowired
  private JwtService jwtService;

  @Autowired
  private PasswordEncoder encoder;

  @Test
  void validRegistrationReturnsTokenForSavedCaregiver() throws Exception {
    String body = "{\"email\":\"New@Example.COM\",\"password\":\"password123\"}";

    String token = mvc.perform(
            post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").isString())
        .andReturn()
        .getResponse()
        .getContentAsString()
        .replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");

    var stored = repository.findByEmail("new@example.com");
    assertThat(stored).isPresent();
    assertThat(jwtService.parse(token).caregiverId()).isEqualTo(stored.get().getId());
    assertThat(stored.get().getPasswordHash()).isNotEqualTo("password123");
    assertThat(encoder.matches("password123", stored.get().getPasswordHash())).isTrue();
  }

  @Test
  void registrationAcceptsPaddedEmailAndStoresCanonicalForm() throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"  Pad@Example.COM  \",\"password\":\"password123\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.token").isString());

    assertThat(repository.findByEmail("pad@example.com")).isPresent();

    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"pad@example.com\",\"password\":\"password123\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
  }

  @Test
  void concurrentDuplicateRegistrationsYieldOneCreated() throws Exception {
    int racers = 4;
    ExecutorService pool = Executors.newFixedThreadPool(racers);
    CountDownLatch gun = new CountDownLatch(1);
    try {
      List<Future<Integer>> futures = new ArrayList<>();
      for (int i = 0; i < racers; i++) {
        futures.add(
            pool.submit(
                () -> {
                  gun.await(10, TimeUnit.SECONDS);
                  return mvc.perform(
                          post("/api/auth/register")
                              .contentType(MediaType.APPLICATION_JSON)
                              .content(
                                  "{\"email\":\"race-burst@example.com\",\"password\":\"password123\"}"))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      }
      gun.countDown();
      List<Integer> statuses = new ArrayList<>();
      for (Future<Integer> future : futures) {
        statuses.add(future.get(30, TimeUnit.SECONDS));
      }
      assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
      assertThat(statuses.stream().filter(s -> s == 422).count()).isEqualTo(racers - 1);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void duplicateEmailMapsTo422() throws Exception {
    String body = "{\"email\":\"dup@example.com\",\"password\":\"password123\"}";
    mvc.perform(
            post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated());

    mvc.perform(
            post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"))
        .andExpect(jsonPath("$.message").value("Email is already registered."));
  }

  @Test
  void normalizedDuplicateMapsTo422() throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"Case@Example.COM\",\"password\":\"password123\"}"))
        .andExpect(status().isCreated());

    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"case@example.com\",\"password\":\"password123\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
  }

  @Test
  void invalidBodiesMapTo422() throws Exception {
    String[] badBodies = {
      "{\"email\":\"not-an-email\",\"password\":\"password123\"}",
      "{\"email\":\"ok@example.com\",\"password\":\"\"}",
      "{\"email\":\"ok@example.com\"}",
      "{\"password\":\"password123\"}",
      "{}",
      "not-json-at-all{"
    };
    for (String bad : badBodies) {
      mvc.perform(
              post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(bad))
          .andExpect(status().isUnprocessableEntity())
          .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
  }

  @Test
  void passwordByteBoundaryAcceptedThenRejected() throws Exception {
    String ok72 = "é".repeat(36);
    assertThat(ok72.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(72);
    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"bytes@example.com\",\"password\":\"" + ok72 + "\"}"))
        .andExpect(status().isCreated());

    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"other@example.com\",\"password\":\"" + ok72 + "x\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }
}
