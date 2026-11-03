package com.caregiver.notes;

import com.caregiver.common.NoteObservations;
import com.caregiver.common.Observation;
import com.caregiver.common.ObservationState;
import com.caregiver.common.VerificationState;
import jakarta.validation.Valid;

public record ObservationSetRequest(
    @Valid ObservationInput<String> mood,
    @Valid ObservationInput<String> appetite,
    @Valid ObservationInput<String> sleep,
    @Valid ObservationInput<String> mobility,
    @Valid ObservationInput<String> medicationStatus,
    @Valid ObservationInput<Integer> painScore,
    @Valid ObservationInput<Boolean> fallReported,
    @Valid ObservationInput<String> freeText) {

  NoteObservations toDomain(VerificationState verification) {
    return new NoteObservations(
        toDomain(mood), toDomain(appetite), toDomain(sleep), toDomain(mobility),
        toDomain(medicationStatus), toDomain(painScore), toDomain(fallReported),
        toDomain(freeText), verification);
  }

  static NoteObservations fromLegacy(
      String mood, String appetite, String sleep, String mobility, String medicationStatus,
      Integer painScore, Boolean fallReported, String freeText, VerificationState verification) {
    Observation<String> medication = "UNKNOWN".equals(medicationStatus)
        ? Observation.state(ObservationState.UNKNOWN)
        : legacy(medicationStatus);
    return new NoteObservations(
        legacy(mood), legacy(appetite), legacy(sleep), legacy(mobility), medication,
        legacy(painScore), legacy(fallReported), legacy(freeText), verification);
  }

  private static <T> Observation<T> toDomain(ObservationInput<T> input) {
    return input == null
        ? Observation.state(ObservationState.NOT_RECORDED)
        : new Observation<>(input.state(), input.value(), input.rawValue());
  }

  private static <T> Observation<T> legacy(T value) {
    return value == null
        ? Observation.state(ObservationState.NOT_RECORDED)
        : Observation.recorded(value);
  }
}
