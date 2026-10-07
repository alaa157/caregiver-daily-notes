package com.caregiver.auth;

import com.caregiver.config.CaregiverApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// The real security filter chain arrives in Task 7; filters stay off here so
// this tests the login behavior only.
@SpringBootTest(
    classes = CaregiverApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:logintest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.flyway.enabled=true",
      "spring.flyway.placeholder-replacement=false",
      "spring.jpa.hibernate.ddl-auto=validate",
      "auth.jwt.secret=test-secret-that-is-definitely-32-bytes-long",
      "auth.jwt.expiry=24h"
    })
@AutoConfigureMockMvc(addFilters = false)
class LoginTest {

  @Autowired
  private MockMvc mvc;

  @Autowired
  private CaregiverRepository repository;

  @Autowired
  private JwtService jwtService;

  @Test
  void registerThenLoginReturnsTokenForCaregiver() throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"Sam@Example.COM\",\"password\":\"password123\"}"))
        .andExpect(status().isCreated());

    String token = mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"sam@example.com\",\"password\":\"password123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.token").isString())
        .andReturn()
        .getResponse()
        .getContentAsString()
        .replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");

    var stored = repository.findByEmail("sam@example.com");
    assertThat(stored).isPresent();
    assertThat(jwtService.parse(token).caregiverId()).isEqualTo(stored.get().getId());
  }

  @Test
  void wrongPasswordAndUnknownEmailShareOne401Body() throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"sam2@example.com\",\"password\":\"password123\"}"))
        .andExpect(status().isCreated());

    String wrong =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"sam2@example.com\",\"password\":\"nope-nope-nope\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    String unknown =
        mvc.perform(
                post("/api/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"ghost@example.com\",\"password\":\"nope-nope-nope\"}"))
            .andExpect(status().isUnauthorized())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(unknown).isEqualTo(wrong);
  }

  @Test
  void oversizedPasswordRejected() throws Exception {
    mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"sam2@example.com\",\"password\":\"" + "x".repeat(73) + "\"}"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }
}
