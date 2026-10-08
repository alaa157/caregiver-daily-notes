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

  String recipientFor(String token, String name) throws Exception {
    return mvc.perform(
            post("/api/recipients")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"" + name + "\"}"))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString()
        .replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");
  }

  String noteJson(String recipientId, int pain, boolean fall, String text) {
    return noteJson(recipientId, pain, fall, text, "taken");
  }

  String noteJson(String recipientId, int pain, boolean fall, String text, String medicationTaken) {
    return "{\"recipientId\":\""
        + recipientId
        + "\",\"mood\":\"good\",\"appetite\":\"good\",\"sleep\":\"good\",\"mobility\":\"good\","
        + "\"medicationTaken\":\""
        + medicationTaken
        + "\",\"pain\":"
        + pain
        + ",\"fall\":"
        + fall
        + ",\"text\":\""
        + text
        + "\"}";
  }

  @Test
  void noteFlowWithAddendumAndHistory() throws Exception {
    String token = tokenFor("preview-notes@example.com");
    String recipientId = recipientFor(token, "Karim");

    String noteId =
        mvc.perform(
                post("/api/notes")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(noteJson(recipientId, 3, false, "Evening check, all calm.")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.mood").value("good"))
            .andExpect(jsonPath("$.pain").value(3))
            .andReturn()
            .getResponse()
            .getContentAsString()
            .replaceAll(".*\"id\"\\s*:\\s*\"([^\"]+)\".*", "$1");

    // Duplicate same-day note rejected.
    mvc.perform(
            post("/api/notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(noteJson(recipientId, 2, false, "Again.")))
        .andExpect(status().isUnprocessableEntity());

    // Pain out of range rejected.
    String otherRecipient = recipientFor(token, "Mona");
    mvc.perform(
            post("/api/notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(noteJson(otherRecipient, 11, false, "Bad pain.")))
        .andExpect(status().isUnprocessableEntity());

    // Note for a foreign recipient rejected.
    String stranger = tokenFor("preview-stranger@example.com");
    String foreignRecipient = recipientFor(stranger, "Foreign");
    mvc.perform(
            post("/api/notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(noteJson(foreignRecipient, 1, false, "Intrusion.")))
        .andExpect(status().isForbidden());

    // Detail embeds addenda; original stays read-only.
    mvc.perform(
            post("/api/notes/" + noteId + "/addenda")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"Late update: slept well.\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.text").value("Late update: slept well."));
    mvc.perform(get("/api/notes/" + noteId).header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.text").value("Evening check, all calm."))
        .andExpect(jsonPath("$.addenda.length()").value(1))
        .andExpect(jsonPath("$.addenda[0].text").value("Late update: slept well."));

    // History newest-first with recipient filter.
    mvc.perform(
            get("/api/notes").header("Authorization", "Bearer " + token).param("recipientId", recipientId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    mvc.perform(get("/api/notes").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    // Date-range filters.
    String today = java.time.LocalDate.now().toString();
    String tomorrow = java.time.LocalDate.now().plusDays(1).toString();
    mvc.perform(
            get("/api/notes")
                .header("Authorization", "Bearer " + token)
                .param("from", today)
                .param("to", today))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    mvc.perform(
            get("/api/notes").header("Authorization", "Bearer " + token).param("from", tomorrow))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    // Foreign note unreadable.
    mvc.perform(get("/api/notes/" + noteId).header("Authorization", "Bearer " + stranger))
        .andExpect(status().isForbidden());
  }

  @Test
  void signalsAndSummaryDeriveFromNotes() throws Exception {
    String token = tokenFor("preview-ai@example.com");
    String recipientId = recipientFor(token, "Salem");
    String today = java.time.LocalDate.now().toString();

    mvc.perform(
            post("/api/notes")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    noteJson(recipientId, 8, true, "Salem fell in the bathroom this morning.", "unknown")))
        .andExpect(status().isCreated());

    mvc.perform(
            get("/api/recipients/" + recipientId + "/signals")
                .header("Authorization", "Bearer " + token)
                .param("from", today)
                .param("to", today))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].fallReported").value(true))
        .andExpect(jsonPath("$[0].pain").value(8))
        .andExpect(jsonPath("$[0].medication").value("unknown"))
        .andExpect(jsonPath("$[0].medicationUnverified").value(true));

    mvc.perform(
            post("/api/summaries")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipientId\":\"" + recipientId + "\",\"periodDays\":7}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.redFlags").isArray())
        .andExpect(jsonPath("$.redFlags[0]").value("FALL_REPORTED"))
        .andExpect(jsonPath("$.evidence[0].quote").isString())
        .andExpect(jsonPath("$.uncertainties").isArray());
  }

  @Test
  void emptyRangeGivesCalmSummary() throws Exception {
    String token = tokenFor("preview-empty@example.com");
    String recipientId = recipientFor(token, "Hana");

    mvc.perform(
            post("/api/summaries")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipientId\":\"" + recipientId + "\",\"periodDays\":7}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.redFlags.length()").value(0));
  }
}
