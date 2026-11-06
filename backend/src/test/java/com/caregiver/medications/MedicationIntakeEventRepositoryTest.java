package com.caregiver.medications;

import com.caregiver.auth.Caregiver;
import com.caregiver.common.VerificationState;
import com.caregiver.config.CaregiverApplication;
import com.caregiver.recipient.CareRecipient;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
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
  void persistsTakenMissedAndUnsureAsDistinctEventValues() {
    UUID caregiverId = UUID.randomUUID();
    Caregiver caregiver = caregiver("intake-values@example.com", caregiverId);
    CareRecipient recipient = recipient(caregiver, "Values recipient");
    MedicationRecord medication = medicationRepository.save(new MedicationRecord(
        caregiver, recipient, "Multi-state med", "5 mg", "daily"));
    entityManager.flush();

    repository.save(new MedicationIntakeEvent(caregiver, recipient, medication,
        MedicationIntakeEvent.IntakeState.TAKEN, Instant.parse("2024-06-01T08:00:00Z"),
        VerificationState.UNVERIFIED));
    repository.save(new MedicationIntakeEvent(caregiver, recipient, medication,
        MedicationIntakeEvent.IntakeState.MISSED, Instant.parse("2024-06-02T08:00:00Z"),
        VerificationState.VERIFIED));
    repository.save(new MedicationIntakeEvent(caregiver, recipient, medication,
        MedicationIntakeEvent.IntakeState.UNSURE, Instant.parse("2024-06-03T08:00:00Z"),
        VerificationState.NOT_ASSERTED));
    entityManager.flush();
    entityManager.clear();

    List<MedicationIntakeEvent> found = repository.findAllByCaregiver_IdAndRecipient_IdAndMedication_IdOrderByOccurredAtAscIdAsc(
        caregiverId, recipient.getId(), medication.getId());

    assertThat(found).extracting(MedicationIntakeEvent::getIntakeState)
        .containsExactlyInAnyOrder(
            MedicationIntakeEvent.IntakeState.TAKEN,
            MedicationIntakeEvent.IntakeState.MISSED,
            MedicationIntakeEvent.IntakeState.UNSURE);
  }

  @Test
  void storesVerificationSeparatelyFromIntakeState() {
    UUID caregiverId = UUID.randomUUID();
    Caregiver caregiver = caregiver("intake-verification@example.com", caregiverId);
    CareRecipient recipient = recipient(caregiver, "Verification recipient");
    MedicationRecord medication = medicationRepository.save(new MedicationRecord(
        caregiver, recipient, "Verification med", "5 mg", "daily"));
    entityManager.flush();

    MedicationIntakeEvent taken = repository.save(new MedicationIntakeEvent(
        caregiver, recipient, medication,
        MedicationIntakeEvent.IntakeState.TAKEN,
        Instant.parse("2024-06-10T08:00:00Z"),
        VerificationState.UNVERIFIED));
    MedicationIntakeEvent missed = repository.save(new MedicationIntakeEvent(
        caregiver, recipient, medication,
        MedicationIntakeEvent.IntakeState.MISSED,
        Instant.parse("2024-06-11T08:00:00Z"),
        VerificationState.VERIFIED));
    MedicationIntakeEvent unsure = repository.save(new MedicationIntakeEvent(
        caregiver, recipient, medication,
        MedicationIntakeEvent.IntakeState.UNSURE,
        Instant.parse("2024-06-12T08:00:00Z"),
        VerificationState.NOT_ASSERTED));
    MedicationIntakeEvent takenVerified = repository.save(new MedicationIntakeEvent(
        caregiver, recipient, medication, MedicationIntakeEvent.IntakeState.TAKEN,
        Instant.parse("2024-06-13T08:00:00Z"), VerificationState.VERIFIED));
    MedicationIntakeEvent missedNotAsserted = repository.save(new MedicationIntakeEvent(
        caregiver, recipient, medication, MedicationIntakeEvent.IntakeState.MISSED,
        Instant.parse("2024-06-14T08:00:00Z"), VerificationState.NOT_ASSERTED));
    MedicationIntakeEvent unsureUnverified = repository.save(new MedicationIntakeEvent(
        caregiver, recipient, medication, MedicationIntakeEvent.IntakeState.UNSURE,
        Instant.parse("2024-06-15T08:00:00Z"), VerificationState.UNVERIFIED));
    entityManager.flush();
    entityManager.clear();

    assertThat(repository.findByIdAndCaregiver_IdAndRecipient_IdAndMedication_Id(
        taken.getId(), caregiverId, recipient.getId(), medication.getId()).orElseThrow()
        .getVerificationState()).isEqualTo(VerificationState.UNVERIFIED);
    assertThat(repository.findByIdAndCaregiver_IdAndRecipient_IdAndMedication_Id(
        missed.getId(), caregiverId, recipient.getId(), medication.getId()).orElseThrow()
        .getVerificationState()).isEqualTo(VerificationState.VERIFIED);
    assertThat(repository.findByIdAndCaregiver_IdAndRecipient_IdAndMedication_Id(
        unsure.getId(), caregiverId, recipient.getId(), medication.getId()).orElseThrow()
        .getVerificationState()).isEqualTo(VerificationState.NOT_ASSERTED);
    assertThat(repository.findByIdAndCaregiver_IdAndRecipient_IdAndMedication_Id(
        takenVerified.getId(), caregiverId, recipient.getId(), medication.getId()).orElseThrow()
        .getVerificationState()).isEqualTo(VerificationState.VERIFIED);
    assertThat(repository.findByIdAndCaregiver_IdAndRecipient_IdAndMedication_Id(
        missedNotAsserted.getId(), caregiverId, recipient.getId(), medication.getId()).orElseThrow()
        .getVerificationState()).isEqualTo(VerificationState.NOT_ASSERTED);
    assertThat(repository.findByIdAndCaregiver_IdAndRecipient_IdAndMedication_Id(
        unsureUnverified.getId(), caregiverId, recipient.getId(), medication.getId()).orElseThrow()
        .getVerificationState()).isEqualTo(VerificationState.UNVERIFIED);
  }

  @Test
  void returnsNoEventWhenIntakeWasNotRecorded() {
    UUID caregiverId = UUID.randomUUID();
    Caregiver caregiver = caregiver("missing-intake-owner@example.com", caregiverId);
    CareRecipient recipient = recipient(caregiver, "No-event recipient");
    MedicationRecord medication = medicationRepository.save(new MedicationRecord(
        caregiver, recipient, "No event med", "5 mg", "daily"));
    entityManager.flush();

    assertThat(repository.findAllByCaregiver_IdAndRecipient_IdAndMedication_IdOrderByOccurredAtAscIdAsc(
        caregiverId, recipient.getId(), medication.getId())).isEmpty();
  }

  @Test
  void doesNotInferMissedIntakeFromAnAbsentEvent() {
    UUID caregiverId = UUID.randomUUID();
    Caregiver caregiver = caregiver("schedule-only-owner@example.com", caregiverId);
    CareRecipient recipient = recipient(caregiver, "Schedule-only recipient");
    MedicationRecord medication = medicationRepository.save(new MedicationRecord(
        caregiver, recipient, "Scheduled only med", "5 mg", "daily at 08:30"));
    entityManager.flush();

    assertThat(repository.findAllByCaregiver_IdAndRecipient_IdAndMedication_IdOrderByOccurredAtAscIdAsc(
        caregiverId, recipient.getId(), medication.getId())).isEmpty();
  }

  @Test
  void doesNotReturnAnotherCaregiversIntakeEvents() {
    UUID ownerId = UUID.randomUUID();
    UUID otherId = UUID.randomUUID();
    Caregiver owner = caregiver("owner@example.com", ownerId);
    Caregiver other = caregiver("other@example.com", otherId);
    CareRecipient ownerRecipient = recipient(owner, "Owner recipient");
    CareRecipient siblingOwnerRecipient = recipient(owner, "Sibling owner recipient");
    CareRecipient otherRecipient = recipient(other, "Other recipient");
    MedicationRecord ownerMedication = medicationRepository.save(new MedicationRecord(
        owner, ownerRecipient, "Owner med", "5 mg", "daily"));
    MedicationRecord siblingOwnerMedication = medicationRepository.save(new MedicationRecord(
        owner, ownerRecipient, "Sibling owner medication", "5 mg", "daily"));
    MedicationRecord siblingRecipientMedication = medicationRepository.save(new MedicationRecord(
        owner, siblingOwnerRecipient, "Sibling recipient medication", "5 mg", "daily"));
    MedicationRecord otherMedication = medicationRepository.save(new MedicationRecord(
        other, otherRecipient, "Other med", "5 mg", "daily"));
    entityManager.flush();

    MedicationIntakeEvent ownerEvent = repository.save(new MedicationIntakeEvent(
        owner, ownerRecipient, ownerMedication,
        MedicationIntakeEvent.IntakeState.TAKEN,
        Instant.parse("2024-07-01T08:00:00Z"),
        VerificationState.UNVERIFIED));
    repository.save(new MedicationIntakeEvent(
        other, otherRecipient, otherMedication,
        MedicationIntakeEvent.IntakeState.MISSED,
        Instant.parse("2024-07-02T08:00:00Z"),
        VerificationState.VERIFIED));
    entityManager.flush();
    entityManager.clear();

    assertThat(repository.findAllByCaregiver_IdAndRecipient_IdAndMedication_IdOrderByOccurredAtAscIdAsc(
        ownerId, ownerRecipient.getId(), ownerMedication.getId()))
        .extracting(MedicationIntakeEvent::getId)
        .containsExactly(ownerEvent.getId());
    assertThat(repository.findByIdAndCaregiver_IdAndRecipient_IdAndMedication_Id(
        ownerEvent.getId(), otherId, ownerRecipient.getId(), ownerMedication.getId())).isEmpty();
    assertThat(repository.findByIdAndCaregiver_IdAndRecipient_IdAndMedication_Id(
        ownerEvent.getId(), ownerId, otherRecipient.getId(), ownerMedication.getId())).isEmpty();
    assertThat(repository.findByIdAndCaregiver_IdAndRecipient_IdAndMedication_Id(
        ownerEvent.getId(), ownerId, ownerRecipient.getId(), siblingOwnerMedication.getId())).isEmpty();
    assertThat(repository.findByIdAndCaregiver_IdAndRecipient_IdAndMedication_Id(
        ownerEvent.getId(), ownerId, siblingOwnerRecipient.getId(), siblingRecipientMedication.getId())).isEmpty();
    assertThat(repository.findByIdAndCaregiver_IdAndRecipient_IdAndMedication_Id(
        ownerEvent.getId(), ownerId, ownerRecipient.getId(), otherMedication.getId())).isEmpty();
    assertThat(repository.findAllByCaregiver_IdAndRecipient_IdAndMedication_IdOrderByOccurredAtAscIdAsc(
        ownerId, ownerRecipient.getId(), siblingOwnerMedication.getId())).isEmpty();
    assertThat(repository.findAllByCaregiver_IdAndRecipient_IdAndMedication_IdOrderByOccurredAtAscIdAsc(
        ownerId, siblingOwnerRecipient.getId(), siblingRecipientMedication.getId())).isEmpty();
  }

  @Test
  void returnsEventsInChronologicalOrderWithIdTieBreaking() {
    UUID caregiverId = UUID.randomUUID();
    Caregiver caregiver = caregiver("intake-order-owner@example.com", caregiverId);
    CareRecipient recipient = recipient(caregiver, "Ordering recipient");
    MedicationRecord medication = medicationRepository.save(new MedicationRecord(
        caregiver, recipient, "Ordering med", "5 mg", "daily"));
    entityManager.flush();

    MedicationIntakeEvent latest = repository.save(new MedicationIntakeEvent(
        caregiver, recipient, medication, MedicationIntakeEvent.IntakeState.TAKEN,
        Instant.parse("2024-06-04T08:00:00Z"), VerificationState.VERIFIED));
    MedicationIntakeEvent tiedLaterId = repository.save(new MedicationIntakeEvent(
        caregiver, recipient, medication, MedicationIntakeEvent.IntakeState.MISSED,
        Instant.parse("2024-06-02T08:00:00Z"), VerificationState.UNVERIFIED));
    MedicationIntakeEvent earliest = repository.save(new MedicationIntakeEvent(
        caregiver, recipient, medication, MedicationIntakeEvent.IntakeState.UNSURE,
        Instant.parse("2024-06-01T08:00:00Z"), VerificationState.NOT_ASSERTED));
    MedicationIntakeEvent tiedEarlierId = repository.save(new MedicationIntakeEvent(
        caregiver, recipient, medication, MedicationIntakeEvent.IntakeState.TAKEN,
        Instant.parse("2024-06-02T08:00:00Z"), VerificationState.NOT_ASSERTED));
    entityManager.flush();
    entityManager.clear();

    List<UUID> tieOrder = java.util.stream.Stream.of(tiedLaterId.getId(), tiedEarlierId.getId())
        .sorted(Comparator.comparing(UUID::toString)).toList();
    assertThat(repository.findAllByCaregiver_IdAndRecipient_IdAndMedication_IdOrderByOccurredAtAscIdAsc(
        caregiverId, recipient.getId(), medication.getId()))
        .extracting(MedicationIntakeEvent::getId)
        .containsExactly(earliest.getId(), tieOrder.get(0), tieOrder.get(1), latest.getId());
  }

  @Test
  void rejectsMismatchedMedicationOwnershipInConstructorAndDatabase() {
    UUID ownerId = UUID.randomUUID();
    UUID otherId = UUID.randomUUID();
    Caregiver owner = caregiver("owner-check@example.com", ownerId);
    Caregiver other = caregiver("other-check@example.com", otherId);
    CareRecipient ownerRecipient = recipient(owner, "Owner recipient");
    CareRecipient siblingRecipient = recipient(owner, "Sibling recipient");
    CareRecipient otherRecipient = recipient(other, "Other recipient");
    MedicationRecord medication = medicationRepository.save(new MedicationRecord(
        other, otherRecipient, "Wrong owner med", "5 mg", "daily"));
    MedicationRecord siblingMedication = medicationRepository.save(new MedicationRecord(
        owner, siblingRecipient, "Same caregiver, different recipient", "5 mg", "daily"));
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
    assertThatThrownBy(() -> new MedicationIntakeEvent(
        owner, ownerRecipient, siblingMedication, MedicationIntakeEvent.IntakeState.TAKEN,
        Instant.now(), VerificationState.VERIFIED))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("medication must belong to recipient");
    assertThatThrownBy(() -> insertRaw(
        UUID.randomUUID(), ownerId, ownerRecipient.getId(), siblingMedication.getId(),
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
        "NOT_RECORDED", Instant.now(), "NOT_ASSERTED"))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThatThrownBy(() -> insertRaw(
        UUID.randomUUID(), caregiverId, recipient.getId(), medication.getId(),
        "TAKEN", Instant.now(), "INVALID"))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThatThrownBy(() -> insertRaw(
        UUID.randomUUID(), caregiverId, recipient.getId(), medication.getId(),
        null, Instant.now(), "VERIFIED"))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThatThrownBy(() -> insertRaw(
        UUID.randomUUID(), caregiverId, recipient.getId(), medication.getId(),
        "TAKEN", null, "VERIFIED"))
        .isInstanceOf(DataIntegrityViolationException.class);

    assertThatThrownBy(() -> insertRaw(
        UUID.randomUUID(), caregiverId, recipient.getId(), medication.getId(),
        "TAKEN", Instant.now(), null))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void preventsMutationOfPersistedIntakeEvent() throws Exception {
    UUID caregiverId = UUID.randomUUID();
    Caregiver caregiver = caregiver("immutable-owner@example.com", caregiverId);
    CareRecipient recipient = recipient(caregiver, "Immutable recipient");
    MedicationRecord medication = medicationRepository.save(new MedicationRecord(
        caregiver, recipient, "Immutable med", "5 mg", "daily"));
    entityManager.flush();

    Instant originalOccurred = Instant.parse("2024-09-01T08:00:00Z");
    MedicationIntakeEvent event = repository.save(new MedicationIntakeEvent(
        caregiver, recipient, medication,
        MedicationIntakeEvent.IntakeState.TAKEN,
        originalOccurred,
        VerificationState.UNVERIFIED));
    entityManager.flush();
    entityManager.clear();

    MedicationIntakeEvent managed = repository.findByIdAndCaregiver_IdAndRecipient_IdAndMedication_Id(
        event.getId(), caregiverId, recipient.getId(), medication.getId()).orElseThrow();
    setField(managed, "intakeState", MedicationIntakeEvent.IntakeState.MISSED);
    setField(managed, "verificationState", VerificationState.VERIFIED);
    setField(managed, "occurredAt", Instant.parse("2024-09-15T10:00:00Z"));

    entityManager.flush();
    entityManager.clear();

    MedicationIntakeEvent reloaded = repository.findByIdAndCaregiver_IdAndRecipient_IdAndMedication_Id(
        event.getId(), caregiverId, recipient.getId(), medication.getId()).orElseThrow();
    assertThat(reloaded.getIntakeState()).isEqualTo(MedicationIntakeEvent.IntakeState.TAKEN);
    assertThat(reloaded.getVerificationState()).isEqualTo(VerificationState.UNVERIFIED);
    assertThat(reloaded.getOccurredAt()).isEqualTo(originalOccurred);

    assertThat(Arrays.stream(MedicationIntakeEvent.class.getDeclaredMethods())
        .filter(method -> Modifier.isPublic(method.getModifiers()))
        .map(Method::getName))
        .noneMatch(name -> name.startsWith("set"));
    assertThat(Arrays.stream(MedicationIntakeEventRepository.class.getDeclaredMethods())
        .map(Method::getName))
        .noneMatch(name -> name.equals("delete") || name.equals("deleteById")
            || name.toLowerCase().contains("update"));
  }

  private Caregiver caregiver(String email, UUID id) {
    jdbc.update("INSERT INTO caregivers (id, email, password_hash) VALUES (?, ?, ?)",
        id, email, "{bcrypt}$2a$10$placeholderhashplaceholderhash123456789012");
    return entityManager.getEntityManager().getReference(Caregiver.class, id);
  }

  private CareRecipient recipient(Caregiver caregiver, String name) {
    return entityManager.persistAndFlush(new CareRecipient(caregiver, name, "family"));
  }

  private void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
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
