package com.caregiver.medications;

import com.caregiver.common.VerificationState;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

/** One explicit caregiver-reported intake event. */
public record MedicationIntakeRequest(
    @NotNull MedicationIntakeEvent.IntakeState intakeState,
    @NotNull Instant occurredAt,
    @NotNull VerificationState verificationState) {
}
