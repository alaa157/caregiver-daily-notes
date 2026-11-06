package com.caregiver.medications;

import com.caregiver.auth.Caregiver;
import com.caregiver.common.VerificationState;
import com.caregiver.config.CaregiverApplication;
import com.caregiver.recipient.CareRecipient;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ContextConfiguration(classes = CaregiverApplication.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:medicationintakedb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.placeholder-replacement=false",
    "spring.jpa.hibernate.ddl-auto=validate",
    "auth.jwt.secret=test-secret-that-is-definitely-32-bytes-long",
    "auth.jwt.expiry=24h"})
class MedicationIntakeEventRepositoryTest {
  @Autowired private MedicationIntakeEventRepository repository;
  @Autowired private MedicationRepository medicationRepository;
  @Autowired private TestEntityManager entityManager;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void persistsExplicitIntakeReportWithScopedOwnershipAndTimestamps() {
    UUID caregiverId = UUID.randomUUID();
    Caregiver caregiver = caregiver("intake-owner@example.com", caregiverId);
    CareRecipient recipient = recipient(caregiver, "Medication recipient");
    MedicationRecord medication = medicationRepository.save(new MedicationRecord(
        caregiver, recipient, "Vitamin D", "10 mg", "daily at 08:30"));
    entityManager.flush();

    Instant occurredAt = Instant.parse("2024-06-15T08:30:00Z");
    MedicationIntakeEvent saved = repository.save(new MedicationIntakeEvent(
        caregiver, recipient, medication,
        MedicationIntakeEvent.IntakeState.TAKEN,
        occurredAt,
        VerificationState.UNVERIFIED));
    entityManager.flush();
    UUID eventId = saved.getId();
    UUID medicationId = medication.getId();
    UUID recipientId = recipient.getId();
    entityManager.clear();

    MedicationIntakeEvent found = repository.findByIdAndCaregiver_IdAndRecipient_IdAndMedication_Id(
        eventId, caregiverId, recipientId, medicationId).orElseThrow();

    assertThat(found.getId()).isNotNull();
    assertThat(found.getCaregiver().getId()).isEqualTo(caregiverId);
    assertThat(found.getRecipient().getId()).isEqualTo(recipientId);
    assertThat(found.getMedication().getId()).isEqualTo(medicationId);
    assertThat(found.getIntakeState()).isEqualTo(MedicationIntakeEvent.IntakeState.TAKEN);
    assertThat(found.getVerificationState()).isEqualTo(VerificationState.UNVERIFIED);
    assertThat(found.getOccurredAt()).isEqualTo(occurredAt);
    assertThat(found.getCreatedAt()).isNotNull();
  }

  @Test
  void rejectsMismatchedMedicationOwnershipInConstructorAndDatabase() {
    UUID ownerId = UUID.randomUUID();
    UUID otherId = UUID.randomUUID();
    Caregiver owner = caregiver("owner@example.com", ownerId);
    Caregiver other = caregiver("other@example.com", otherId);
    CareRecipient ownerRecipient = recipient(owner, "Owner recipient");
    CareRecipient otherRecipient = recipient(other, "Other recipient");
    MedicationRecord medication = medicationRepository.save(new MedicationRecord(
        other, otherRecipient, "Wrong owner med", "5 mg", "daily"));
    entityManager.flush();

    assertThatThrownBy(() -> new MedicationIntakeEvent(
        owner, ownerRecipient, medication,
        MedicationIntakeEvent.IntakeState.MISSED,
        Instant.now(),
        VerificationState.VERIFIED))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> insertRaw(
        UUID.randomUUID(), ownerId, ownerRecipient.getId(), medication.getId(),
        "TAKEN", Instant.now(), "VERIFIED"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void enforcesAllowedIntakeAndVerificationValues() {
    UUID caregiverId = UUID.randomUUID();
    Caregiver caregiver = caregiver("validation-owner@example.com", caregiverId);
    CareRecipient recipient = recipient(caregiver, "Validation recipient");
    MedicationRecord medication = medicationRepository.save(new MedicationRecord(
        caregiver, recipient, "Validation med", "5 mg", "daily"));
    entityManager.flush();

    assertThatThrownBy(() -> insertRaw(
        UUID.randomUUID(), caregiverId, recipient.getId(), medication.getId(),
        "UNKNOWN", Instant.now(), "VERIFIED"))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThatThrownBy(() -> insertRaw(
        UUID.randomUUID(), caregiverId, recipient.getId(), medication.getId(),
        "TAKEN", Instant.now(), "INVALID"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private Caregiver caregiver(String email, UUID id) {
    jdbc.update("INSERT INTO caregivers (id, email, password_hash) VALUES (?, ?, ?)",
        id, email, "{bcrypt}$2a$10$placeholderhashplaceholderhash123456789012");
    return entityManager.getEntityManager().getReference(Caregiver.class, id);
  }

  private CareRecipient recipient(Caregiver caregiver, String name) {
    return entityManager.persistAndFlush(new CareRecipient(caregiver, name, "family"));
  }

  private void insertRaw(UUID id, UUID caregiverId, UUID recipientId, UUID medicationId,
      String intakeState, Instant occurredAt, String verificationState) {
    jdbc.update(
        "INSERT INTO medication_intake_events "
            + "(id, caregiver_id, recipient_id, medication_id, intake_state, occurred_at, verification_state, created_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)",
        id, caregiverId, recipientId, medicationId, intakeState, occurredAt, verificationState);
  }
}
