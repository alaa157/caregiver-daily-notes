package com.caregiver.common;

import java.util.Objects;

/**
 * One typed observation. Legacy source data is kept separately from the active
 * value so an ambiguous value can never be consumed as a confirmed observation.
 */
public record Observation<T>(ObservationState state, T value, T rawValue) {
  public Observation {
    Objects.requireNonNull(state, "state");
    if (state == ObservationState.RECORDED && value == null) {
      throw new IllegalArgumentException("RECORDED observations require a value.");
    }
    if (state != ObservationState.RECORDED && value != null) {
      throw new IllegalArgumentException("Only RECORDED observations may have an active value.");
    }
    if (state != ObservationState.LEGACY_AMBIGUOUS && rawValue != null) {
      throw new IllegalArgumentException("Raw values are only valid for LEGACY_AMBIGUOUS observations.");
    }
  }

  public static <T> Observation<T> recorded(T value) {
    return new Observation<>(ObservationState.RECORDED, Objects.requireNonNull(value), null);
  }

  public static <T> Observation<T> state(ObservationState state) {
    return new Observation<>(state, null, null);
  }

  public static <T> Observation<T> legacyAmbiguous(T rawValue) {
    return new Observation<>(ObservationState.LEGACY_AMBIGUOUS, null, rawValue);
  }

  public T compatibilityValue() {
    return state == ObservationState.RECORDED ? value : rawValue;
  }
}
