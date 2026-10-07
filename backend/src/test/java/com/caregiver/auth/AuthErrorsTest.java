package com.caregiver.auth;

import com.caregiver.config.CaregiverApplication;
import com.caregiver.other.TestOtherController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = CaregiverApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:errortest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
@Import({TestWhoAmIController.class, TestOtherController.class})
class AuthErrorsTest {

  @Autowired
  private MockMvc mvc;

  @Autowired
  private JwtService jwtService;

  @Test
  void serverErrorHidesDetails() throws Exception {
    String body =
        mvc.perform(
                get("/api/test/boom")
                    .header("Authorization", "Bearer " + jwtService.issue(UUID.randomUUID())))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("SERVER_ERROR"))
            .andExpect(jsonPath("$.message").value("Something went wrong."))
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(body)
        .doesNotContain("JWT_SECRET", "db-password", "token-abc-123", "IllegalStateException");
  }

  @Test
  void nonAuthControllerBypassesAuthAdvice() {
    // Outside com.caregiver.auth, so the scoped advice must not convert this:
    // MockMvc propagates the raw failure instead of returning an envelope.
    assertThatThrownBy(
            () ->
                mvc.perform(
                    get("/api/test/other-boom")
                        .header(
                            "Authorization",
                            "Bearer " + jwtService.issue(UUID.randomUUID()))))
        .rootCause()
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("other-domain-failure");
  }
}
