package com.caregiver.medications;

import com.caregiver.auth.Caregiver;
import com.caregiver.config.CaregiverApplication;
import com.caregiver.plans.CarePlan;
import com.caregiver.plans.MedicationEntry;
import com.caregiver.plans.PlanDocument;
import com.caregiver.plans.PlanDocumentCodec;
import com.caregiver.plans.PlanSource;
import com.caregiver.plans.PlanStatus;
import com.caregiver.recipient.CareRecipient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
    "spring.datasource.url=jdbc:h2:mem:medicationdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.placeholder-replacement=false",
    "spring.jpa.hibernate.ddl-auto=validate",
    "auth.jwt.secret=test-secret-that-is-definitely-32-bytes-long",
    "auth.jwt.expiry=24h"})
class MedicationRepositoryTest {
  @Autowired private MedicationRepository repository;
  @Autowired private TestEntityManager entityManager;
  @Autowired private JdbcTemplate jdbc;
  private final PlanDocumentCodec planDocuments = new PlanDocumentCodec(new ObjectMapper());

  @Test
  void persistsExactCaregiverEnteredTextAndTimestamps() {
    UUID caregiverId = UUID.randomUUID();
    Caregiver caregiver = caregiver("medication-text-owner@example.com", caregiverId);
    CareRecipient recipient = recipient(caregiver, "Medication recipient");
    String displayName = "  دواء صباحي — Vitamin D 💊  ";
    String doseText = "  ١٠ ملغ (10 mg) بعد الطعام  ";
    String scheduleText = "  يوميًا at 08:30 — حسب ما أدخله مقدم الرعاية  ";

    MedicationRecord saved = repository.save(new MedicationRecord(
        caregiver, recipient, displayName, doseText, scheduleText));
    entityManager.flush();
    UUID medicationId = saved.getId();
    UUID recipientId = recipient.getId();
    entityManager.clear();

    MedicationRecord found = repository.findByIdAndCaregiver_IdAndRecipient_Id(
        medicationId, caregiverId, recipientId).orElseThrow();

    assertThat(found.getId()).isNotNull();
    assertThat(found.getCaregiver().getId()).isEqualTo(caregiverId);
    assertThat(found.getRecipient().getId()).isEqualTo(recipientId);
    assertThat(found.getDisplayName()).isEqualTo(displayName);
    assertThat(found.getDoseText()).isEqualTo(doseText);
    assertThat(found.getScheduleText()).isEqualTo(scheduleText);
    assertThat(found.isActive()).isTrue();
    assertThat(found.getCreatedAt()).isNotNull();
    assertThat(found.getUpdatedAt()).isNotNull();
  }

  @Test
  void scopesSingleAndListQueriesToBothCaregiverAndRecipient() {
    UUID ownerId = UUID.randomUUID();
    UUID otherId = UUID.randomUUID();
    Caregiver owner = caregiver("medication-scope-owner@example.com", ownerId);
    Caregiver other = caregiver("medication-scope-other@example.com", otherId);
    CareRecipient ownerRecipient = recipient(owner, "Owner recipient");
    CareRecipient otherRecipient = recipient(other, "Other recipient");
    MedicationRecord record = repository.save(new MedicationRecord(
        owner, ownerRecipient, "Own medication", "5 mg", "daily"));
    entityManager.flush();
    UUID medicationId = record.getId();
    UUID ownerRecipientId = ownerRecipient.getId();
    UUID otherRecipientId = otherRecipient.getId();
    entityManager.clear();

    assertThat(repository.findByIdAndCaregiver_IdAndRecipient_Id(
        medicationId, ownerId, ownerRecipientId)).isPresent();
    assertThat(repository.findByIdAndCaregiver_IdAndRecipient_Id(
        medicationId, otherId, ownerRecipientId)).isEmpty();
    assertThat(repository.findByIdAndCaregiver_IdAndRecipient_Id(
        medicationId, ownerId, otherRecipientId)).isEmpty();
    assertThat(repository.findByIdAndCaregiver_IdAndRecipient_Id(
        UUID.randomUUID(), ownerId, ownerRecipientId)).isEmpty();
    assertThat(repository.findAllByCaregiver_IdAndRecipient_IdOrderByUpdatedAtDescIdAsc(
        otherId, otherRecipientId)).isEmpty();
    assertThat(repository.findAllByCaregiver_IdAndRecipient_IdOrderByUpdatedAtDescIdAsc(
        ownerId, ownerRecipientId)).extracting(MedicationRecord::getId).containsExactly(medicationId);
  }

  @Test
  void rejectsMismatchedOwnershipInConstructorAndDatabase() {
    UUID ownerId = UUID.randomUUID();
    UUID otherId = UUID.randomUUID();
    Caregiver owner = caregiver("medication-owner-check@example.com", ownerId);
    Caregiver other = caregiver("medication-other-check@example.com", otherId);
    CareRecipient recipient = recipient(other, "Other caregiver recipient");

    assertThatThrownBy(() -> new MedicationRecord(
        owner, recipient, "Name", "Dose", "Schedule"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("caregiver must own recipient");

    assertThatThrownBy(() -> insertRaw(UUID.randomUUID(), ownerId, recipient.getId(),
        "Name", "Dose", "Schedule"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void inactiveRecordRemainsPersistedAndScopedRetrievable() {
    UUID caregiverId = UUID.randomUUID();
    Caregiver caregiver = caregiver("medication-inactive-owner@example.com", caregiverId);
    CareRecipient recipient = recipient(caregiver, "Inactive medication recipient");
    String displayName = "Old entry — keep as history";
    String doseText = "  unknown by caregiver  ";
    String scheduleText = "previously entered wording";
    MedicationRecord saved = repository.save(new MedicationRecord(
        caregiver, recipient, displayName, doseText, scheduleText, false));
    entityManager.flush();
    UUID medicationId = saved.getId();
    UUID recipientId = recipient.getId();
    entityManager.clear();

    MedicationRecord found = repository.findByIdAndCaregiver_IdAndRecipient_Id(
        medicationId, caregiverId, recipientId).orElseThrow();

    assertThat(found.isActive()).isFalse();
    assertThat(found.getDisplayName()).isEqualTo(displayName);
    assertThat(found.getDoseText()).isEqualTo(doseText);
    assertThat(found.getScheduleText()).isEqualTo(scheduleText);
    assertThat(jdbc.queryForObject(
        "select count(*) from recipient_medications where id = ?", Integer.class, medicationId)).isEqualTo(1);
  }

  @Test
  void medicationTableContainsNoIntakeEventColumns() {
    Set<String> columns = jdbc.queryForList(
            "select column_name from information_schema.columns "
                + "where table_schema = 'PUBLIC' and table_name = 'RECIPIENT_MEDICATIONS'",
            String.class)
        .stream().map(String::toLowerCase).collect(Collectors.toSet());

    assertThat(columns).containsExactlyInAnyOrder(
        "id", "caregiver_id", "recipient_id", "display_name", "dose_text", "schedule_text",
        "active", "created_at", "updated_at");
    assertThat(columns).doesNotContain(
        "intake_state", "taken_at", "missed_at", "occurred_at", "verification_state");
  }

  @Test
  void existingPlanMedicationDocumentStillRoundTripsUnchanged() {
    MedicationEntry entry = new MedicationEntry("دواء A / Vitamin D", " ١٠ ملغ / 10 mg ", "كل يوم at 08:30");
    PlanDocument original = new PlanDocument(1, List.of(), List.of(entry));
    String serialized = planDocuments.write(original);
    CarePlan plan = new CarePlan(null, 1, PlanStatus.SUGGESTED, serialized,
        null, null, null, PlanSource.AI_GENERATED);

    assertThat(planDocuments.read(plan)).isEqualTo(original);
    assertThat(planDocuments.read(plan).medications()).containsExactly(entry);
  }

  private Caregiver caregiver(String email, UUID id) {
    jdbc.update("INSERT INTO caregivers (id, email, password_hash) VALUES (?, ?, ?)",
        id, email, "{bcrypt}$2a$10$placeholderhashplaceholderhash123456789012");
    return entityManager.getEntityManager().getReference(Caregiver.class, id);
  }

  private CareRecipient recipient(Caregiver caregiver, String name) {
    return entityManager.persistAndFlush(new CareRecipient(caregiver, name, "family"));
  }

  private void insertRaw(UUID id, UUID caregiverId, UUID recipientId,
      String displayName, String doseText, String scheduleText) {
    jdbc.update("INSERT INTO recipient_medications "
            + "(id, caregiver_id, recipient_id, display_name, dose_text, schedule_text, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
        id, caregiverId, recipientId, displayName, doseText, scheduleText);
  }
}
