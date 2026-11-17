package com.caregiver.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Issue #730: outside the dev profile the generated API documentation must not
 * be publicly reachable, and closing it must not loosen any other rule.
 * OpenApiTest covers the dev side of the contract. This class deliberately
 * runs with the prod profile.
 */
@SpringBootTest(
    classes = CaregiverApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:apidocsprofiletest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
@ActiveProfiles("prod")
class ApiDocsProfileTest {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper mapper;

  @Test
  void documentationPathsAreNotPublicOutsideDev() throws Exception {
    for (String path : new String[] {
        "/v3/api-docs", "/v3/api-docs/swagger-config",
        "/swagger-ui.html", "/swagger-ui/index.html"}) {
      int status = mvc.perform(get(path)).andReturn().getResponse().getStatus();
      assertThat(status)
          .as("anonymous access to %s", path)
          .isIn(401, 403, 404);
    }
  }

  @Test
  void documentationEndpointsDoNotExistEvenForAuthenticatedUsers() throws Exception {
    String token = register("docs-profile@example.com");

    mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
    mvc.perform(get("/swagger-ui.html").header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  @Test
  void otherAuthorizationRulesAreUnchanged() throws Exception {
    mvc.perform(get("/api/recipients"))
        .andExpect(status().isUnauthorized());
    register("docs-rules@example.com");
  }

  private String register(String email) throws Exception {
    String body = mvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
        .andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString();
    JsonNode json = mapper.readTree(body);
    return json.get("token").asText();
  }
}
