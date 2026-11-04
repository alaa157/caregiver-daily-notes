package com.caregiver.notes;

import com.caregiver.common.ObservationState;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/** Canonical observation state plus the event metadata that produced it. */
public record EffectiveObservation(
    ObservationState state,
    JsonNode value,
    JsonNode rawValue,
    CorrectionOperation lastOperation,
    UUID eventId,
    UUID actorId,
    Instant updatedAt) { }
