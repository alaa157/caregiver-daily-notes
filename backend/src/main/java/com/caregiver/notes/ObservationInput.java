package com.caregiver.notes;

import com.caregiver.common.ObservationState;
import jakarta.validation.constraints.NotNull;

@ValidObservationInput
public record ObservationInput<T>(
    @NotNull ObservationState state,
    T value,
    T rawValue) {
}
