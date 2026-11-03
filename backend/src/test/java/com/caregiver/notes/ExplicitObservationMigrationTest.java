package com.caregiver.notes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ExplicitObservationMigrationTest {

  @Test
  void preservesLegacyIdentifiersAndRawValuesWhileClassifyingMeaning() {
    String url = "jdbc:h2:mem:observationmigration" + UUID.randomUUID()
        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    DriverManagerDataSource dataSource = new DriverManagerDataSource(url, "sa", "");
    Flyway.configure().dataSource(dataSource).target(MigrationVersion.fromVersion("6")).load().migrate();

    UUID caregiverId = UUID.randomUUID();
    UUID recipientId = UUID.randomUUID();
    UUID noteId = UUID.randomUUID();
    UUID addendumId = UUID.randomUUID();
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    jdbc.update("INSERT INTO caregivers (id, email, password_hash) VALUES (?, ?, ?)",
        caregiverId, "migration@example.test", "synthetic");
    jdbc.update("INSERT INTO care_recipients (id, caregiver_id, name) VALUES (?, ?, ?)",
        recipientId, caregiverId, "Synthetic recipient");
    jdbc.update("""
        INSERT INTO daily_notes
          (id, caregiver_id, recipient_id, note_date, mood, medication_status,
           medication_verified, pain_score, fall_reported, free_text)
        VALUES (?, ?, ?, DATE '2026-10-01', ?, ?, ?, ?, ?, ?)
        """, noteId, caregiverId, recipientId, "raw mood", "UNKNOWN", false, 0, false, "raw note");
    jdbc.update("""
        INSERT INTO note_addenda
          (id, note_id, caregiver_id, medication_status, pain_score, fall_reported)
        VALUES (?, ?, ?, ?, ?, ?)
        """, addendumId, noteId, caregiverId, "MISSED", 0, false);

    Flyway.configure().dataSource(dataSource).load().migrate();

    Map<String, Object> note = jdbc.queryForMap(
        "SELECT id, mood, mood_state, mood_value, medication_status, medication_status_state, "
            + "medication_verified, medication_verification_state, pain_score, pain_score_state, "
            + "fall_reported, fall_reported_state, free_text, free_text_state FROM daily_notes WHERE id = ?",
        noteId);
    assertThat(note.get("id")).isEqualTo(noteId);
    assertThat(note.get("mood")).isEqualTo("raw mood");
    assertThat(note.get("mood_state")).isEqualTo("LEGACY_AMBIGUOUS");
    assertThat(note.get("mood_value")).isNull();
    assertThat(note.get("medication_status")).isEqualTo("UNKNOWN");
    assertThat(note.get("medication_status_state")).isEqualTo("UNKNOWN");
    assertThat(note.get("medication_verified")).isEqualTo(false);
    assertThat(note.get("medication_verification_state")).isEqualTo("UNVERIFIED");
    assertThat(note.get("pain_score")).isEqualTo(0);
    assertThat(note.get("pain_score_state")).isEqualTo("LEGACY_AMBIGUOUS");
    assertThat(note.get("fall_reported")).isEqualTo(false);
    assertThat(note.get("fall_reported_state")).isEqualTo("LEGACY_AMBIGUOUS");
    assertThat(note.get("free_text")).isEqualTo("raw note");
    assertThat(note.get("free_text_state")).isEqualTo("LEGACY_AMBIGUOUS");

    Map<String, Object> addendum = jdbc.queryForMap(
        "SELECT id, medication_status, medication_status_state, pain_score, pain_score_state, "
            + "fall_reported, fall_reported_state, mood_provided, medication_status_provided, "
            + "pain_score_provided, free_text_provided FROM note_addenda WHERE id = ?", addendumId);
    assertThat(addendum.get("id")).isEqualTo(addendumId);
    assertThat(addendum.get("medication_status")).isEqualTo("MISSED");
    assertThat(addendum.get("medication_status_state")).isEqualTo("LEGACY_AMBIGUOUS");
    assertThat(addendum.get("pain_score")).isEqualTo(0);
    assertThat(addendum.get("pain_score_state")).isEqualTo("LEGACY_AMBIGUOUS");
    assertThat(addendum.get("fall_reported")).isEqualTo(false);
    assertThat(addendum.get("fall_reported_state")).isEqualTo("LEGACY_AMBIGUOUS");
    assertThat(addendum.get("mood_provided")).isEqualTo(false);
    assertThat(addendum.get("medication_status_provided")).isEqualTo(true);
    assertThat(addendum.get("pain_score_provided")).isEqualTo(true);
    assertThat(addendum.get("free_text_provided")).isEqualTo(false);
  }
}
