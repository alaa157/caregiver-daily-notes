package com.caregiver.common;

import java.util.Objects;

public record NoteObservations(
    Observation<String> mood,
    Observation<String> appetite,
    Observation<String> sleep,
    Observation<String> mobility,
    Observation<String> medicationStatus,
    Observation<Integer> painScore,
    Observation<Boolean> fallReported,
    Observation<String> freeText,
    VerificationState medicationVerification) {

  public NoteObservations {
    Objects.requireNonNull(mood, "mood");
    Objects.requireNonNull(appetite, "appetite");
    Objects.requireNonNull(sleep, "sleep");
    Objects.requireNonNull(mobility, "mobility");
    Objects.requireNonNull(medicationStatus, "medicationStatus");
    Objects.requireNonNull(painScore, "painScore");
    Objects.requireNonNull(fallReported, "fallReported");
    Objects.requireNonNull(freeText, "freeText");
    Objects.requireNonNull(medicationVerification, "medicationVerification");
  }

  public static NoteObservations notRecorded() {
    return new NoteObservations(
        Observation.state(ObservationState.NOT_RECORDED),
        Observation.state(ObservationState.NOT_RECORDED),
        Observation.state(ObservationState.NOT_RECORDED),
        Observation.state(ObservationState.NOT_RECORDED),
        Observation.state(ObservationState.NOT_RECORDED),
        Observation.state(ObservationState.NOT_RECORDED),
        Observation.state(ObservationState.NOT_RECORDED),
        Observation.state(ObservationState.NOT_RECORDED),
        VerificationState.NOT_ASSERTED);
  }
}
