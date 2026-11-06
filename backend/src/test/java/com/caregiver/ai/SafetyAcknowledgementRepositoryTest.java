package com.caregiver.ai;

import com.caregiver.auth.Caregiver;
import com.caregiver.config.CaregiverApplication;
import com.caregiver.recipient.CareRecipient;
import java.sql.Date;
import java.time.LocalDate;
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
    "spring.datasource.url=jdbc:h2:mem:safetyackdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=true",
    "spring.flyway.placeholder-replacement=false",
    "spring.jpa.hibernate.ddl-auto=validate",
    "auth.jwt.secret=test-secret-that-is-definitely-32-bytes-long",
    "auth.jwt.expiry=24h"})
class SafetyAcknowledgementRepositoryTest {
  private static final LocalDate PERIOD_END = LocalDate.of(2026, 11, 6);

  @Autowired
  private SafetyAcknowledgementRepository repository;

  @Autowired
  private TestEntityManager entityManager;

  @Autowired
  private JdbcTemplate jdbc;

  private Caregiver caregiver(String email) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO caregivers (id, email, password_hash) VALUES (?, ?, ?)",
        id, email, "{bcrypt}$2a$10$placeholderhashplaceholderhash123456789012");
    return entityManager.getEntityManager().getReference(Caregiver.class, id);
  }

  private CareRecipient recipient(Caregiver caregiver, String name) {
    return entityManager.persistAndFlush(new CareRecipient(caregiver, name, "family"));
  }

  private SafetyAcknowledgement acknowledgement(Caregiver caregiver, CareRecipient recipient, int periodDays) {
    return new SafetyAcknowledgement(
        caregiver, recipient, SafetyRuleId.FALL_DETECTED, PERIOD_END, periodDays);
  }

  @Test
  void persistsAcknowledgementWithCaregiverAndRecipientIds() {
    Caregiver caregiver = caregiver("ack-owner@example.com");
    CareRecipient recipient = recipient(caregiver, "Owned recipient");
    SafetyAcknowledgement saved = entityManager.persistAndFlush(acknowledgement(caregiver, recipient, 7));
    entityManager.clear();

    SafetyAcknowledgement found = repository
        .findByCaregiver_IdAndRecipient_IdAndRuleIdAndPeriodEndInclusiveAndPeriodDays(
            caregiver.getId(), recipient.getId(), SafetyRuleId.FALL_DETECTED, PERIOD_END, 7)
        .orElseThrow();

    assertThat(found.getId()).isEqualTo(saved.getId()).isNotNull();
    assertThat(found.getCaregiver().getId()).isEqualTo(caregiver.getId());
    assertThat(found.getRecipient().getId()).isEqualTo(recipient.getId());
    assertThat(found.getRuleId()).isEqualTo(SafetyRuleId.FALL_DETECTED);
    assertThat(found.getPeriodEndInclusive()).isEqualTo(PERIOD_END);
    assertThat(found.getPeriodDays()).isEqualTo(7);
    assertThat(found.getAcknowledgedAt()).isNotNull();
  }

  @Test
  void enforcesUniqueAcknowledgementKeyPerPeriod() {
    Caregiver caregiver = caregiver("ack-duplicate@example.com");
    CareRecipient recipient = recipient(caregiver, "Duplicate recipient");
    entityManager.persistAndFlush(acknowledgement(caregiver, recipient, 7));

    assertThatThrownBy(() -> insertRaw(
        UUID.randomUUID(), caregiver.getId(), recipient.getId(),
        SafetyRuleId.FALL_DETECTED.name(), PERIOD_END, 7))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void doesNotReturnAcknowledgementForAnotherCaregiver() {
    Caregiver owner = caregiver("ack-isolation-owner@example.com");
    Caregiver other = caregiver("ack-isolation-other@example.com");
    CareRecipient ownedRecipient = recipient(owner, "Owner recipient");
    recipient(other, "Other recipient");
    entityManager.persistAndFlush(acknowledgement(owner, ownedRecipient, 7));

    assertThat(repository.findByCaregiver_IdAndRecipient_IdAndRuleIdAndPeriodEndInclusiveAndPeriodDays(
        other.getId(), ownedRecipient.getId(), SafetyRuleId.FALL_DETECTED, PERIOD_END, 7))
        .isEmpty();
    assertThat(repository.findAllByCaregiver_IdAndRecipient_IdOrderByPeriodEndInclusiveDescPeriodDaysAscRuleIdAsc(
        other.getId(), ownedRecipient.getId()))
        .isEmpty();
  }

  @Test
  void keepsAcknowledgementsInSeparateSummaryPeriods() {
    Caregiver caregiver = caregiver("ack-periods@example.com");
    CareRecipient recipient = recipient(caregiver, "Period recipient");
    entityManager.persistAndFlush(acknowledgement(caregiver, recipient, 7));
    entityManager.persistAndFlush(acknowledgement(caregiver, recipient, 30));

    var sevenDay = repository
        .findByCaregiver_IdAndRecipient_IdAndRuleIdAndPeriodEndInclusiveAndPeriodDays(
            caregiver.getId(), recipient.getId(), SafetyRuleId.FALL_DETECTED, PERIOD_END, 7);
    var thirtyDay = repository
        .findByCaregiver_IdAndRecipient_IdAndRuleIdAndPeriodEndInclusiveAndPeriodDays(
            caregiver.getId(), recipient.getId(), SafetyRuleId.FALL_DETECTED, PERIOD_END, 30);

    assertThat(sevenDay).isPresent();
    assertThat(thirtyDay).isPresent();
    assertThat(sevenDay.orElseThrow().getId()).isNotEqualTo(thirtyDay.orElseThrow().getId());
    assertThat(repository.findAllByCaregiver_IdAndRecipient_IdOrderByPeriodEndInclusiveDescPeriodDaysAscRuleIdAsc(
        caregiver.getId(), recipient.getId()))
        .hasSize(2);
  }

  @Test
  void rejectsInvalidCaregiverAndRecipientForeignKeys() {
    Caregiver caregiver = caregiver("ack-foreign-key@example.com");
    CareRecipient recipient = recipient(caregiver, "Foreign key recipient");

    assertThatThrownBy(() -> insertRaw(
        UUID.randomUUID(), UUID.randomUUID(), recipient.getId(),
        SafetyRuleId.FALL_DETECTED.name(), PERIOD_END, 7))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> insertRaw(
        UUID.randomUUID(), caregiver.getId(), UUID.randomUUID(),
        SafetyRuleId.FALL_DETECTED.name(), PERIOD_END, 7))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void rejectsNullRequiredAcknowledgementFields() {
    Caregiver caregiver = caregiver("ack-not-null@example.com");
    CareRecipient recipient = recipient(caregiver, "Not-null recipient");

    assertThatThrownBy(() -> insertRaw(
        UUID.randomUUID(), caregiver.getId(), recipient.getId(), null, PERIOD_END, 7))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void insertRaw(UUID id, UUID caregiverId, UUID recipientId,
      String ruleId, LocalDate periodEndInclusive, Integer periodDays) {
    jdbc.update(
        "INSERT INTO safety_acknowledgements"
            + " (id, caregiver_id, recipient_id, rule_id, period_end_inclusive, period_days)"
            + " VALUES (?, ?, ?, ?, ?, ?)",
        id, caregiverId, recipientId, ruleId, Date.valueOf(periodEndInclusive), periodDays);
  }
}
