package com.caregiver.auth;

import com.caregiver.config.CaregiverApplication;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = CaregiverApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:securitytest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.flyway.enabled=true",
      "spring.flyway.placeholder-replacement=false",
      "spring.jpa.hibernate.ddl-auto=validate",
      "auth.jwt.secret=test-secret-that-is-definitely-32-bytes-long",
      "auth.jwt.expiry=24h"
    })
@AutoConfigureMockMvc
@Import(TestWhoAmIController.class)
class SecurityFilterTest {

  private static final String SECRET = "test-secret-that-is-definitely-32-bytes-long";
  private static final String DENIED =
      "{\"code\":\"UNAUTHORIZED\",\"message\":\"Authentication required.\"}";

  @Autowired
  private MockMvc mvc;

  @Autowired
  private JwtService jwtService;

  private String tokenFor(UUID id) {
    return jwtService.issue(id);
  }

  private String expiredTokenFor(UUID id) {
    return Jwts.builder()
        .subject(id.toString())
        .issuedAt(new Date(0))
        .expiration(new Date(1000))
        .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
        .compact();
  }

  @Test
  void validBearerReturnsTokenIdentity() throws Exception {
    UUID id = UUID.randomUUID();

    mvc.perform(get("/api/test/whoami").header("Authorization", "Bearer " + tokenFor(id)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.caregiverId").value(id.toString()));
  }

  @Test
  void lowercaseBearerSchemeWorks() throws Exception {
    UUID id = UUID.randomUUID();

    mvc.perform(get("/api/test/whoami").header("Authorization", "bearer " + tokenFor(id)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.caregiverId").value(id.toString()));
  }

  @Test
  void identityComesFromTokenNotRequestParams() throws Exception {
    UUID id = UUID.randomUUID();

    mvc.perform(
            get("/api/test/whoami")
                .queryParam("caregiverId", UUID.randomUUID().toString())
                .header("Authorization", "Bearer " + tokenFor(id)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.caregiverId").value(id.toString()));
  }

  @Test
  void allBadCredentialsShareOne401Envelope() throws Exception {
    UUID id = UUID.randomUUID();
    String tampered = tokenFor(id).substring(0, tokenFor(id).length() - 2) + "xx";

    MvcResult missing =
        mvc.perform(get("/api/test/whoami")).andExpect(status().isUnauthorized()).andReturn();
    MvcResult empty =
        mvc.perform(get("/api/test/whoami").header("Authorization", ""))
            .andExpect(status().isUnauthorized())
            .andReturn();
    MvcResult malformed =
        mvc.perform(get("/api/test/whoami").header("Authorization", "Token abc"))
            .andExpect(status().isUnauthorized())
            .andReturn();
    MvcResult tamperedResult =
        mvc.perform(get("/api/test/whoami").header("Authorization", "Bearer " + tampered))
            .andExpect(status().isUnauthorized())
            .andReturn();
    MvcResult expired =
        mvc.perform(
                get("/api/test/whoami").header("Authorization", "Bearer " + expiredTokenFor(id)))
            .andExpect(status().isUnauthorized())
            .andReturn();

    for (MvcResult result : new MvcResult[] {missing, empty, malformed, tamperedResult, expired}) {
      assertThat(result.getResponse().getContentAsString()).isEqualTo(DENIED);
    }
  }
}
