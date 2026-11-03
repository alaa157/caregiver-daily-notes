package com.caregiver.notes;

import com.caregiver.common.NoteObservations;
import com.caregiver.common.Observation;
import com.caregiver.common.ObservationState;
import com.caregiver.common.VerificationState;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@ValidAddendumObservationRequest
public record AddendumRequest(
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

  public AddendumRequest(
      String mood, String appetite, String sleep, String mobility, String medicationStatus,
      Boolean medicationVerified, Integer painScore, Boolean fallReported, String freeText) {
    this(mood, appetite, sleep, mobility, medicationStatus, medicationVerified, painScore,
        fallReported, freeText, null, null);
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
    if (observations != null) {
      return observations.toDomain(verification);
    }
    return ObservationSetRequest.fromLegacy(mood, appetite, sleep, mobility, medicationStatus,
        painScore, fallReported, freeText, verification);
  }

  public boolean verificationWasSupplied() {
    return medicationVerification != null || medicationVerified != null;
  }

  public ObservationPresence observationPresence() {
    if (observations == null) {
      return new ObservationPresence(
          mood != null, appetite != null, sleep != null, mobility != null,
          medicationStatus != null, painScore != null, fallReported != null, freeText != null);
    }
    return new ObservationPresence(
        observations.mood() != null, observations.appetite() != null, observations.sleep() != null,
        observations.mobility() != null, observations.medicationStatus() != null,
        observations.painScore() != null, observations.fallReported() != null,
        observations.freeText() != null);
  }

  public boolean hasObservationOrVerification() {
    if (verificationWasSupplied()) {
      return true;
    }
    return observationPresence().any();
  }
}
