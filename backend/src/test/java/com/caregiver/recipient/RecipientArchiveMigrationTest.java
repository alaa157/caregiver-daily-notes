package com.caregiver.recipient;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

/** Exercises the V8 backfill with pre-existing active and historically inactive rows. */
class RecipientArchiveMigrationTest {

  @Test
  void preservesLegacyActiveFlagAndDoesNotInventArchiveProvenance() throws Exception {
    String url = "jdbc:h2:mem:recipient_migration_" + UUID.randomUUID().toString().replace("-", "")
        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    Flyway beforeLifecycle = Flyway.configure()
        .dataSource(url, "sa", "")
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("7"))
        .load();
    beforeLifecycle.migrate();

    UUID caregiverId = UUID.randomUUID();
    UUID activeRecipientId = UUID.randomUUID();
    UUID inactiveRecipientId = UUID.randomUUID();
    try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
      try (PreparedStatement caregiver = connection.prepareStatement(
          "INSERT INTO caregivers (id, email, password_hash) VALUES (?, ?, ?)")) {
        caregiver.setObject(1, caregiverId);
        caregiver.setString(2, "migration-test@example.com");
        caregiver.setString(3, "not-a-real-password-hash");
        caregiver.executeUpdate();
      }
      try (PreparedStatement recipient = connection.prepareStatement(
          "INSERT INTO care_recipients (id, caregiver_id, name, active) VALUES (?, ?, ?, ?)")) {
        recipient.setObject(1, activeRecipientId);
        recipient.setObject(2, caregiverId);
        recipient.setString(3, "Active legacy row");
        recipient.setBoolean(4, true);
        recipient.executeUpdate();

        recipient.setObject(1, inactiveRecipientId);
        recipient.setObject(2, caregiverId);
        recipient.setString(3, "Ambiguous inactive legacy row");
        recipient.setBoolean(4, false);
        recipient.executeUpdate();
      }
    }

    Flyway withLifecycle = Flyway.configure()
        .dataSource(url, "sa", "")
        .locations("classpath:db/migration")
        .load();
    withLifecycle.migrate();

    try (Connection connection = DriverManager.getConnection(url, "sa", "");
         PreparedStatement query = connection.prepareStatement(
             "SELECT id, active, lifecycle_status, archived_at, archived_by, restored_at, restored_by "
                 + "FROM care_recipients WHERE id IN (?, ?)")) {
      query.setObject(1, activeRecipientId);
      query.setObject(2, inactiveRecipientId);
      try (ResultSet rows = query.executeQuery()) {
        int seen = 0;
        while (rows.next()) {
          seen++;
          UUID id = rows.getObject("id", UUID.class);
          if (activeRecipientId.equals(id)) {
            assertThat(rows.getBoolean("active")).isTrue();
            assertThat(rows.getString("lifecycle_status")).isEqualTo("ACTIVE");
          } else {
            assertThat(rows.getBoolean("active")).isFalse();
            assertThat(rows.getString("lifecycle_status")).isEqualTo("LEGACY_INACTIVE_REVIEW");
          }
          assertThat(rows.getTimestamp("archived_at")).isNull();
          assertThat(rows.getObject("archived_by")).isNull();
          assertThat(rows.getTimestamp("restored_at")).isNull();
          assertThat(rows.getObject("restored_by")).isNull();
        }
        assertThat(seen).isEqualTo(2);
      }
    }
  }
}
