package com.caregiver.medications;

import com.caregiver.common.VerificationState;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

/**
 * Public representation of a medication intake event. Void metadata is absent
 * for active events and present for voided ones, so the non-void response shape
 * is unchanged.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MedicationIntakeResponse(
    UUID id,
    UUID medicationId,
    UUID recipientId,
    MedicationIntakeEvent.IntakeState intakeState,
    Instant occurredAt,
    VerificationState verificationState,
    Instant createdAt,
    Boolean voided,
    Instant voidedAt,
    UUID voidedBy,
    String voidReason) {

  public MedicationIntakeResponse(UUID id, UUID medicationId, UUID recipientId,
      MedicationIntakeEvent.IntakeState intakeState, Instant occurredAt,
      VerificationState verificationState, Instant createdAt) {
    this(id, medicationId, recipientId, intakeState, occurredAt, verificationState, createdAt,
        null, null, null, null);
  }
}
