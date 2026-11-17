package com.caregiver.ratelimit;

import com.caregiver.auth.JwtService;
import com.caregiver.config.CaregiverApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end check that denials are 429s with Retry-After, not 500s. */
@SpringBootTest(
    classes = CaregiverApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:ratelimithttp;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.flyway.enabled=true",
      "spring.flyway.placeholder-replacement=false",
      "spring.jpa.hibernate.ddl-auto=validate",
      "auth.jwt.secret=test-secret-that-is-definitely-32-bytes-long",
      "auth.jwt.expiry=24h",
      "rate-limit.enabled=true",
      "rate-limit.auth-ip-max-requests=2",
      "rate-limit.auth-ip-window=1m",
      "rate-limit.auth-account-max-requests=100",
      "rate-limit.auth-account-window=5m",
      "rate-limit.ai-max-requests=2",
      "rate-limit.ai-window=1m"
    })
@AutoConfigureMockMvc
class RateLimitHttpTest {

  @Autowired
  private MockMvc mvc;

  @Autowired
  private JwtService jwtService;

  /** Each test uses its own source IP so the shared limiter state stays isolated. */
  private static MockHttpServletRequestBuilder loginFrom(String ip, String body) {
    return post("/api/auth/login")
        .with(request -> {
          request.setRemoteAddr(ip);
          return request;
        })
        .contentType(MediaType.APPLICATION_JSON)
        .content(body);
  }

  @Test
  void loginBeyondTheIpLimitReturns429WithRetryAfter() throws Exception {
    String body = "{\"email\":\"nobody@example.com\",\"password\":\"wrong-password\"}";
    for (int i = 0; i < 2; i++) {
      mvc.perform(loginFrom("203.0.113.10", body)).andExpect(status().isUnauthorized());
    }

    var denied = mvc.perform(loginFrom("203.0.113.10", body))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
        .andReturn();

    assertThat(Long.parseLong(denied.getResponse().getHeader("Retry-After"))).isGreaterThanOrEqualTo(1);
  }

  @Test
  void aiCallsBeyondTheCaregiverLimitReturn429() throws Exception {
    String token = "Bearer " + jwtService.issue(UUID.randomUUID());
    String body = "{}";
    for (int i = 0; i < 2; i++) {
      mvc.perform(post("/api/summaries/byok")
              .header("Authorization", token)
              .contentType(MediaType.APPLICATION_JSON).content(body))
          .andExpect(status().is4xxClientError());
    }

    mvc.perform(post("/api/summaries/byok")
            .header("Authorization", token)
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
  }

  @Test
  void aDifferentSourceIpHasItsOwnAuthBudget() throws Exception {
    String body = "{\"email\":\"nobody@example.com\",\"password\":\"wrong-password\"}";
    mvc.perform(loginFrom("192.0.2.10", body)).andExpect(status().isUnauthorized());
    mvc.perform(loginFrom("192.0.2.10", body)).andExpect(status().isUnauthorized());
    mvc.perform(loginFrom("192.0.2.10", body)).andExpect(status().isTooManyRequests());

    mvc.perform(loginFrom("198.51.100.25", body)).andExpect(status().isUnauthorized());
  }
}
