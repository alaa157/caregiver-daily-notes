package com.caregiver.notes;

import com.caregiver.common.NoteObservations;
import com.caregiver.common.VerificationState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

@ValidNoteObservationRequest
public record NoteRequest(
    @NotNull UUID recipientId,
    @NotNull LocalDate date,
    @Size(max = 40) String mood,
    @Size(max = 40) String appetite,
    @Size(max = 40) String sleep,
    @Size(max = 40) String mobility,
    @Pattern(regexp = "TAKEN|MISSED|UNKNOWN") String medicationStatus,
    Boolean medicationVerified,
    @Min(0) @Max(10) Integer painScore,
    Boolean fallReported,
    @Size(max = 20000) String freeText,
    @Valid ObservationSetRequest observations,
    VerificationState medicationVerification) {

  public NoteRequest(
      UUID recipientId, LocalDate date, String mood, String appetite, String sleep, String mobility,
      String medicationStatus, Boolean medicationVerified, Integer painScore, Boolean fallReported,
      String freeText) {
    this(recipientId, date, mood, appetite, sleep, mobility, medicationStatus, medicationVerified,
        painScore, fallReported, freeText, null, null);
  }

  boolean hasLegacyObservationFields() {
    return mood != null || appetite != null || sleep != null || mobility != null
        || medicationStatus != null || medicationVerified != null || painScore != null
        || fallReported != null || freeText != null;
  }

  public NoteObservations toObservations() {
    VerificationState verification = medicationVerification != null
        ? medicationVerification
        : medicationVerified == null ? VerificationState.NOT_ASSERTED
        : medicationVerified ? VerificationState.VERIFIED : VerificationState.UNVERIFIED;
    return observations == null
        ? ObservationSetRequest.fromLegacy(mood, appetite, sleep, mobility, medicationStatus,
            painScore, fallReported, freeText, verification)
        : observations.toDomain(verification);
  }

  public boolean verificationWasSupplied() {
    return medicationVerification != null || medicationVerified != null;
  }
}
