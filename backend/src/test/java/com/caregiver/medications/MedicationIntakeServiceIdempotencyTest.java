package com.caregiver.medications;

import com.caregiver.auth.Caregiver;
import com.caregiver.auth.CaregiverPrincipal;
import com.caregiver.auth.CaregiverRepository;
import com.caregiver.common.VerificationState;
import com.caregiver.config.CaregiverApplication;
import com.caregiver.recipient.CareRecipient;
import com.caregiver.recipient.CareRecipientRepository;
import com.caregiver.recipient.IdempotencyConflictException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Service-level idempotency contract for medication intake writes. */
@SpringBootTest(
    classes = CaregiverApplication.class,
    properties = {
      "spring.datasource.url=jdbc:h2:mem:medicationintakeidem;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      "spring.datasource.driver-class-name=org.h2.Driver",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "spring.flyway.enabled=true",
      "spring.flyway.placeholder-replacement=false",
      "spring.jpa.hibernate.ddl-auto=validate",
      "auth.jwt.secret=test-secret-that-is-definitely-32-bytes-long",
      "auth.jwt.expiry=24h"})
class MedicationIntakeServiceIdempotencyTest {
  @Autowired private MedicationIntakeService service;
  @Autowired private CaregiverRepository caregivers;
  @Autowired private CareRecipientRepository recipients;
  @Autowired private MedicationRepository medications;
  @Autowired private MedicationIntakeEventRepository intakeEvents;
  @Autowired private JdbcTemplate jdbc;

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void sameKeyReplaysOriginalEventAndStoresOneRow() {
    Fixture fixture = fixture();
    authenticate(fixture.caregiverId());
    Instant occurredAt = Instant.parse("2026-11-01T08:30:00Z");
    MedicationIntakeRequest request = request(occurredAt);

    MedicationIntakeResponse first = service.create(
        fixture.recipientId(), fixture.medicationId(), request, "dose-key");
    // A retry after a lost response carries the same payload and returns the
    // original record without writing a second event.
    MedicationIntakeResponse retry = service.create(
        fixture.recipientId(), fixture.medicationId(), request(occurredAt), "dose-key");

    assertThat(retry.id()).isEqualTo(first.id());
    assertThat(retry.medicationId()).isEqualTo(first.medicationId());
    assertThat(retry.recipientId()).isEqualTo(first.recipientId());
    assertThat(retry.occurredAt()).isEqualTo(occurredAt);
    assertThat(retry.intakeState()).isEqualTo(first.intakeState());
    assertThat(intakeEvents.findAllByCaregiver_IdAndRecipient_IdAndMedication_IdOrderByOccurredAtAscIdAsc(
        fixture.caregiverId(), fixture.recipientId(), fixture.medicationId()))
        .extracting(MedicationIntakeEvent::getId)
        .containsExactly(first.id());
    assertThat(jdbc.queryForObject(
        "select idempotency_key from medication_intake_events where id = ?",
        String.class, first.id())).isEqualTo("dose-key");
  }

  @Test
  void sameKeyWithDifferentPayloadIsRejected() {
    Fixture fixture = fixture();
    authenticate(fixture.caregiverId());
    service.create(fixture.recipientId(), fixture.medicationId(),
        request(Instant.parse("2026-11-01T08:30:00Z")), "dose-key");

    assertThatThrownBy(() -> service.create(fixture.recipientId(), fixture.medicationId(),
        new MedicationIntakeRequest(MedicationIntakeEvent.IntakeState.MISSED,
            Instant.parse("2026-11-01T08:30:00Z"), VerificationState.UNVERIFIED), "dose-key"))
        .isInstanceOf(IdempotencyConflictException.class);
    assertThatThrownBy(() -> service.create(fixture.recipientId(), fixture.medicationId(),
        request(Instant.parse("2026-11-02T09:00:00Z")), "dose-key"))
        .isInstanceOf(IdempotencyConflictException.class);
    assertThat(intakeEvents.findAllByCaregiver_IdAndRecipient_IdAndMedication_IdOrderByOccurredAtAscIdAsc(
        fixture.caregiverId(), fixture.recipientId(), fixture.medicationId()))
        .hasSize(1);
  }

  @Test
  void recordingIntakeWritesOneIdentifierOnlyAuditEventAndReplayDoesNotDuplicateIt() {
    Fixture fixture = fixture();
    authenticate(fixture.caregiverId());
    MedicationIntakeRequest request = request(Instant.parse("2026-11-01T08:30:00Z"));

    MedicationIntakeResponse first = service.create(
        fixture.recipientId(), fixture.medicationId(), request, "dose-key");
    service.create(fixture.recipientId(), fixture.medicationId(), request, "dose-key");

    String auditQuery = "from audit_events where event_type = 'MEDICATION_INTAKE_RECORDED' and target_id = ?";
    assertThat(jdbc.queryForObject("select count(*) " + auditQuery,
        Integer.class, first.id().toString())).isEqualTo(1);
    assertThat(jdbc.queryForObject("select actor_caregiver_id " + auditQuery,
        UUID.class, first.id().toString())).isEqualTo(fixture.caregiverId());
    String metadata = jdbc.queryForObject("select safe_metadata " + auditQuery,
        String.class, first.id().toString());
    assertThat(metadata).contains("medicationId=" + fixture.medicationId())
        .doesNotContain("TAKEN", "UNVERIFIED", "2026-11-01");
  }

  @Test
  void differentKeysCreateSeparateRowsAtTheSameTimestamp() {
    Fixture fixture = fixture();
    authenticate(fixture.caregiverId());
    Instant occurredAt = Instant.parse("2026-11-03T08:30:00Z");
    MedicationIntakeRequest request = request(occurredAt);

    MedicationIntakeResponse first = service.create(
        fixture.recipientId(), fixture.medicationId(), request, "dose-key-a");
    MedicationIntakeResponse second = service.create(
        fixture.recipientId(), fixture.medicationId(), request, "dose-key-b");

    assertThat(second.id()).isNotEqualTo(first.id());
    assertThat(intakeEvents.findAllByCaregiver_IdAndRecipient_IdAndMedication_IdOrderByOccurredAtAscIdAsc(
        fixture.caregiverId(), fixture.recipientId(), fixture.medicationId()))
        .hasSize(2);
  }

  private Fixture fixture() {
    UUID caregiverId = UUID.randomUUID();
    jdbc.update("INSERT INTO caregivers (id, email, password_hash) VALUES (?, ?, ?)",
        caregiverId, "intake-idem-" + caregiverId + "@example.com",
        "{bcrypt}$2a$10$placeholderhashplaceholderhash123456789012");
    Caregiver caregiver = caregivers.getReferenceById(caregiverId);
    CareRecipient recipient = recipients.saveAndFlush(
        new CareRecipient(caregiver, "Idempotency recipient", "family"));
    MedicationRecord medication = medications.save(new MedicationRecord(
        caregiver, recipient, "Idempotency med", "10 mg", "daily at 08:30"));
    return new Fixture(caregiverId, recipient.getId(), medication.getId());
  }

  private void authenticate(UUID caregiverId) {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(new CaregiverPrincipal(caregiverId), null, List.of()));
  }

  private static MedicationIntakeRequest request(Instant occurredAt) {
    return new MedicationIntakeRequest(
        MedicationIntakeEvent.IntakeState.TAKEN, occurredAt, VerificationState.UNVERIFIED);
  }

  private record Fixture(UUID caregiverId, UUID recipientId, UUID medicationId) {
  }
}
