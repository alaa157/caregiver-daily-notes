package com.caregiver.medications;

import com.caregiver.auth.Caregiver;
import com.caregiver.auth.CaregiverPrincipal;
import com.caregiver.auth.CaregiverRepository;
import com.caregiver.config.CaregiverApplication;
import com.caregiver.recipient.CareRecipient;
import com.caregiver.recipient.CareRecipientRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;

/** Medication writes append events to the shared audit trail, identifiers only. */
@SpringBootTest(
    classes = CaregiverApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:medicationaudit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.flyway.enabled=true",
      "spring.flyway.placeholder-replacement=false",
      "spring.jpa.hibernate.ddl-auto=validate",
      "auth.jwt.secret=test-secret-that-is-definitely-32-bytes-long",
      "auth.jwt.expiry=24h"})
class MedicationAuditTest {
  @Autowired private MedicationService service;
  @Autowired private CaregiverRepository caregivers;
  @Autowired private CareRecipientRepository recipients;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void createEditAndArchiveWriteAuditEventsWithIdentifiersOnly() {
    UUID caregiverId = UUID.randomUUID();
    jdbc.update("INSERT INTO caregivers (id, email, password_hash) VALUES (?, ?, ?)",
        caregiverId, "medication-audit-" + caregiverId + "@example.com",
        "{bcrypt}$2a$10$placeholderhashplaceholderhash123456789012");
    Caregiver caregiver = caregivers.getReferenceById(caregiverId);
    CareRecipient recipient = recipients.saveAndFlush(
        new CareRecipient(caregiver, "Audit recipient", "family"));
    authenticate(caregiverId);

    MedicationResponse created = service.create(recipient.getId(),
        new MedicationRequest("Vitamin D", "10 mg", "daily at 08:30"));
    assertAudit("MEDICATION_CREATED", created.id(), caregiverId);

    service.update(recipient.getId(), created.id(),
        new MedicationRequest("Vitamin D2", "20 mg", "daily at 09:00"));
    assertAudit("MEDICATION_UPDATED", created.id(), caregiverId);

    service.archive(recipient.getId(), created.id());
    assertAudit("MEDICATION_ARCHIVED", created.id(), caregiverId);

    assertThat(jdbc.queryForObject(
        "select count(*) from audit_events where event_type like 'MEDICATION_%' "
            + "and target_id = ? and safe_metadata is not null",
        Integer.class, created.id().toString())).isZero();
  }

  @Test
  void archivingAgainDoesNotAppendAnotherArchiveEvent() {
    UUID caregiverId = UUID.randomUUID();
    jdbc.update("INSERT INTO caregivers (id, email, password_hash) VALUES (?, ?, ?)",
        caregiverId, "medication-audit-again-" + caregiverId + "@example.com",
        "{bcrypt}$2a$10$placeholderhashplaceholderhash123456789012");
    Caregiver caregiver = caregivers.getReferenceById(caregiverId);
    CareRecipient recipient = recipients.saveAndFlush(
        new CareRecipient(caregiver, "Audit recipient", "family"));
    authenticate(caregiverId);
    MedicationResponse created = service.create(recipient.getId(),
        new MedicationRequest("Vitamin D", "10 mg", "daily"));

    service.archive(recipient.getId(), created.id());
    service.archive(recipient.getId(), created.id());

    assertThat(jdbc.queryForObject(
        "select count(*) from audit_events where event_type = 'MEDICATION_ARCHIVED' and target_id = ?",
        Integer.class, created.id().toString())).isEqualTo(1);
  }

  private void assertAudit(String eventType, UUID targetId, UUID actorId) {
    String where = "from audit_events where event_type = ? and target_id = ?";
    assertThat(jdbc.queryForObject("select count(*) " + where,
        Integer.class, eventType, targetId.toString())).isEqualTo(1);
    assertThat(jdbc.queryForObject("select actor_caregiver_id " + where,
        UUID.class, eventType, targetId.toString())).isEqualTo(actorId);
  }

  private void authenticate(UUID caregiverId) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(new CaregiverPrincipal(caregiverId), null, java.util.List.of()));
  }
}
