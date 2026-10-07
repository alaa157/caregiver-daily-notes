package com.caregiver.auth;

import com.caregiver.config.CaregiverApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

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
}
