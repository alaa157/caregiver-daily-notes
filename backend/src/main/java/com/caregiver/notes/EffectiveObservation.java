package com.caregiver.notes;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/** Canonical observation-state wrapper plus the correction operation that produced it. */
public record EffectiveObservation(String state, JsonNode value, String lastOperation,
    UUID eventId, UUID actorId, Instant updatedAt) { }
