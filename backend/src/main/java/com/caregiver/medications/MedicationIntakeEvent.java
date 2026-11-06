package com.caregiver.medications;

import com.caregiver.auth.Caregiver;
import com.caregiver.common.VerificationState;
import com.caregiver.recipient.CareRecipient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A caregiver-reported medication intake event tied to a specific medication record. */
@Entity
@Table(name = "medication_intake_events", indexes = @Index(
    name = "idx_medication_intake_events_caregiver_recipient_medication_time",
    columnList = "caregiver_id, recipient_id, medication_id, occurred_at"))
public class MedicationIntakeEvent {
  public enum IntakeState {
    TAKEN,
    MISSED,
    UNSURE
  }

  @Id
  @UuidGenerator
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "caregiver_id", nullable = false, updatable = false,
      foreignKey = @ForeignKey(name = "fk_medication_intake_events_caregiver"))
  private Caregiver caregiver;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "recipient_id", nullable = false, updatable = false,
      foreignKey = @ForeignKey(
          name = "fk_medication_intake_events_recipient_owner",
          foreignKeyDefinition = "FOREIGN KEY (recipient_id, caregiver_id) "
              + "REFERENCES care_recipients (id, caregiver_id) ON DELETE RESTRICT"))
  private CareRecipient recipient;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "medication_id", nullable = false, updatable = false,
      foreignKey = @ForeignKey(
          name = "fk_medication_intake_events_medication_owner",
          foreignKeyDefinition = "FOREIGN KEY (medication_id, caregiver_id, recipient_id) "
              + "REFERENCES recipient_medications (id, caregiver_id, recipient_id) "
              + "ON DELETE RESTRICT"))
  private MedicationRecord medication;

  @Enumerated(EnumType.STRING)
  @Column(name = "intake_state", nullable = false, length = 32)
  private IntakeState intakeState;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "verification_state", nullable = false, length = 32)
  private VerificationState verificationState;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected MedicationIntakeEvent() {
  }

  public MedicationIntakeEvent(Caregiver caregiver, CareRecipient recipient,
      MedicationRecord medication, IntakeState intakeState,
      Instant occurredAt, VerificationState verificationState) {
    this.caregiver = Objects.requireNonNull(caregiver, "caregiver");
    this.recipient = Objects.requireNonNull(recipient, "recipient");
    this.medication = Objects.requireNonNull(medication, "medication");
    this.intakeState = Objects.requireNonNull(intakeState, "intakeState");
    this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    this.verificationState = Objects.requireNonNull(verificationState, "verificationState");

    Caregiver recipientCaregiver = Objects.requireNonNull(recipient.getCaregiver(), "recipient.caregiver");
    if (caregiver != recipientCaregiver
        && (caregiver.getId() == null || !caregiver.getId().equals(recipientCaregiver.getId()))) {
      throw new IllegalArgumentException("caregiver must own recipient");
    }

    Caregiver medicationCaregiver = Objects.requireNonNull(medication.getCaregiver(), "medication.caregiver");
    if (caregiver != medicationCaregiver
        && (caregiver.getId() == null || !caregiver.getId().equals(medicationCaregiver.getId()))) {
      throw new IllegalArgumentException("caregiver must own medication");
    }

    CareRecipient medicationRecipient = Objects.requireNonNull(medication.getRecipient(), "medication.recipient");
    if (recipient != medicationRecipient
        && (recipient.getId() == null || !recipient.getId().equals(medicationRecipient.getId()))) {
      throw new IllegalArgumentException("medication must belong to recipient");
    }
  }

  public UUID getId() {
    return id;
  }

  public Caregiver getCaregiver() {
    return caregiver;
  }

  public CareRecipient getRecipient() {
    return recipient;
  }

  public MedicationRecord getMedication() {
    return medication;
  }

  public IntakeState getIntakeState() {
    return intakeState;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public VerificationState getVerificationState() {
    return verificationState;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
