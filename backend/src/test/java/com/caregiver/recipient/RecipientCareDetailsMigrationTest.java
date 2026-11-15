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

/** Ensures recipients created before V15 have unset optional care details. */
class RecipientCareDetailsMigrationTest {

  @Test
  void existingRecipientsHaveNullCareDetailsAfterMigration() throws Exception {
    String url = "jdbc:h2:mem:recipient_care_details_" + UUID.randomUUID().toString().replace("-", "")
        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";
    Flyway beforeCareDetails = Flyway.configure()
        .dataSource(url, "sa", "")
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion("14"))
        .load();
    beforeCareDetails.migrate();

    UUID caregiverId = UUID.randomUUID();
    UUID recipientId = UUID.randomUUID();
    try (Connection connection = DriverManager.getConnection(url, "sa", "");
         PreparedStatement caregiver = connection.prepareStatement(
             "INSERT INTO caregivers (id, email, password_hash) VALUES (?, ?, ?)");
         PreparedStatement recipient = connection.prepareStatement(
             "INSERT INTO care_recipients (id, caregiver_id, name) VALUES (?, ?, ?)")) {
      caregiver.setObject(1, caregiverId);
      caregiver.setString(2, "care-details-migration@example.com");
      caregiver.setString(3, "not-a-real-password-hash");
      caregiver.executeUpdate();

      recipient.setObject(1, recipientId);
      recipient.setObject(2, caregiverId);
      recipient.setString(3, "Existing recipient");
      recipient.executeUpdate();
    }

    Flyway withCareDetails = Flyway.configure()
        .dataSource(url, "sa", "")
        .locations("classpath:db/migration")
        .load();
    withCareDetails.migrate();

    try (Connection connection = DriverManager.getConnection(url, "sa", "");
         PreparedStatement query = connection.prepareStatement(
             "SELECT date_of_birth, allergies, conditions, emergency_contact_name, emergency_contact_phone "
                 + "FROM care_recipients WHERE id = ?")) {
      query.setObject(1, recipientId);
      try (ResultSet row = query.executeQuery()) {
        assertThat(row.next()).isTrue();
        assertThat(row.getObject("date_of_birth")).isNull();
        assertThat(row.getString("allergies")).isNull();
        assertThat(row.getString("conditions")).isNull();
        assertThat(row.getString("emergency_contact_name")).isNull();
        assertThat(row.getString("emergency_contact_phone")).isNull();
        assertThat(row.next()).isFalse();
      }
    }
  }
}
