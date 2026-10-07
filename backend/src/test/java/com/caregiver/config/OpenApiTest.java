package com.caregiver.config;

import com.caregiver.auth.TestWhoAmIController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = CaregiverApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:openapitest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
@Import(TestWhoAmIController.class)
class OpenApiTest {

  @Autowired
  private MockMvc mvc;

  @Test
  void docsExposeAuthPathsAndBearerScheme() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title").value("Caregiver Daily Notes API"))
        .andExpect(jsonPath("$.info.version").value("0.0.1"))
        .andExpect(jsonPath("$['paths']['/api/auth/register']").exists())
        .andExpect(jsonPath("$['paths']['/api/auth/login']").exists())
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
        .andExpect(jsonPath("$['paths']['/api/auth/register']['post']['security']").doesNotExist())
        .andExpect(jsonPath("$['paths']['/api/auth/login']['post']['security']").doesNotExist())
        // Protected operations inherit the document-level requirement: no
        // per-operation key, and the global one names bearerAuth.
        .andExpect(jsonPath("$['paths']['/api/test/whoami']['get']['security']").doesNotExist())
        .andExpect(jsonPath("$.security[0].bearerAuth").exists())
        .andExpect(jsonPath("$.components.schemas.RegisterRequest").exists())
        .andExpect(jsonPath("$.components.schemas.LoginRequest").exists())
        .andExpect(jsonPath("$.components.schemas.AuthResponse").exists())
        .andExpect(jsonPath("$.components.schemas.ErrorResponse").exists());
  }

  @Test
  void swaggerUiReachableWithoutToken() throws Exception {
    String location =
        mvc.perform(get("/swagger-ui.html"))
            .andExpect(status().is3xxRedirection())
            .andReturn()
            .getResponse()
            .getRedirectedUrl();

    assertThat(location).isNotNull();
    mvc.perform(get(location)).andExpect(status().isOk());
  }
}
