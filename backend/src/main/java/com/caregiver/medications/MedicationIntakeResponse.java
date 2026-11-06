package com.caregiver.medications;

import com.caregiver.common.VerificationState;
import java.time.Instant;
import java.util.UUID;

/** Public representation of an immutable medication intake event. */
public record MedicationIntakeResponse(
    UUID id,
    UUID medicationId,
    UUID recipientId,
    MedicationIntakeEvent.IntakeState intakeState,
    Instant occurredAt,
    VerificationState verificationState,
    Instant createdAt) {
}
