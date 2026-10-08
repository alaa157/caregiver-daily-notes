package com.caregiver.preview;

import com.caregiver.config.CaregiverApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = CaregiverApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:previewapi;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
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
class PreviewApiTest {

  @Autowired
  private MockMvc mvc;

  String tokenFor(String email) throws Exception {
    mvc.perform(
            post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
        .andExpect(status().isCreated());
    return mvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"password\":\"password123\"}"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString()
        .replaceAll(".*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
  }

  @Test
  void recipientRoundtripWithOwnership() throws Exception {
    String token = tokenFor("preview-a@example.com");
    String other = tokenFor("preview-b@example.com");

    String id =
        mvc.perform(
                post("/api/recipients")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Fatma\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isString())
            .andExpect(jsonPath("$.name").value("Fatma"))
            .andReturn()
            .getResponse()
            .getContentAsString()
            .replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

    mvc.perform(get("/api/recipients").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    mvc.perform(get("/api/recipients/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Fatma"));

    // Other caregiver sees nothing and cannot read.
    mvc.perform(get("/api/recipients").header("Authorization", "Bearer " + other))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
    mvc.perform(get("/api/recipients/" + id).header("Authorization", "Bearer " + other))
        .andExpect(status().isForbidden());

    // Deactivate keeps the row readable.
    mvc.perform(
            put("/api/recipients/" + id)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
    mvc.perform(get("/api/recipients/" + id).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    // No token at all.
    mvc.perform(get("/api/recipients")).andExpect(status().isUnauthorized());
  }
}
