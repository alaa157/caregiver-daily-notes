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
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A caregiver-reported medication intake event tied to a specific medication record. */
@Entity
@Table(name = "medication_intake_events",
    indexes = @Index(
        name = "idx_medication_intake_events_caregiver_recipient_medication_time",
        columnList = "caregiver_id, recipient_id, medication_id, occurred_at, id"),
    uniqueConstraints = @UniqueConstraint(
        name = "uk_medication_intake_events_owner_key",
        columnNames = {"caregiver_id", "idempotency_key"}))
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
  @Column(name = "intake_state", nullable = false, length = 32, updatable = false)
  private IntakeState intakeState;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "verification_state", nullable = false, length = 32, updatable = false)
  private VerificationState verificationState;

  @Column(name = "idempotency_key", length = 100, updatable = false)
  private String idempotencyKey;

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "voided_at")
  private Instant voidedAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "voided_by",
      foreignKey = @ForeignKey(name = "fk_medication_intake_events_voided_by"))
  private Caregiver voidedBy;

  @Column(name = "void_reason", length = 500)
  private String voidReason;

  protected MedicationIntakeEvent() {
  }

  public MedicationIntakeEvent(Caregiver caregiver, CareRecipient recipient,
      MedicationRecord medication, IntakeState intakeState,
      Instant occurredAt, VerificationState verificationState) {
    this(caregiver, recipient, medication, intakeState, occurredAt, verificationState, null);
  }

  public MedicationIntakeEvent(Caregiver caregiver, CareRecipient recipient,
      MedicationRecord medication, IntakeState intakeState,
      Instant occurredAt, VerificationState verificationState, String idempotencyKey) {
    this.caregiver = Objects.requireNonNull(caregiver, "caregiver");
    this.recipient = Objects.requireNonNull(recipient, "recipient");
    this.medication = Objects.requireNonNull(medication, "medication");
    this.intakeState = Objects.requireNonNull(intakeState, "intakeState");
    this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    this.verificationState = Objects.requireNonNull(verificationState, "verificationState");
    this.idempotencyKey = idempotencyKey;

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

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getVoidedAt() {
    return voidedAt;
  }

  public Caregiver getVoidedBy() {
    return voidedBy;
  }

  public String getVoidReason() {
    return voidReason;
  }

  public boolean isVoided() {
    return voidedAt != null;
  }

  /** Marks this event as retracted without deleting or rewriting what happened. */
  public void markVoided(Instant at, Caregiver actor, String reason) {
    if (isVoided()) {
      throw new IntakeAlreadyVoidedException();
    }
    this.voidedAt = java.util.Objects.requireNonNull(at, "voidedAt");
    this.voidedBy = java.util.Objects.requireNonNull(actor, "voidedBy");
    this.voidReason = reason;
  }
}
