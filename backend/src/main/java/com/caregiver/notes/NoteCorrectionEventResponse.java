package com.caregiver.notes;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record NoteCorrectionEventResponse(UUID id, UUID noteId, long sequenceNo, Instant createdAt,
    UUID actorId, String field, CorrectionOperation operation, JsonNode value,
    EffectiveObservation previousEffective, EffectiveObservation effectiveAfter) {
  static NoteCorrectionEventResponse from(NoteCorrectionEvent event, JsonNode value,
      EffectiveObservation previousEffective, EffectiveObservation effectiveAfter) {
    return new NoteCorrectionEventResponse(event.getId(), event.getNote().getId(), event.getSequenceNo(),
        event.getCreatedAt(), event.getCaregiver().getId(), event.getField(),
        CorrectionOperation.valueOf(event.getOperation()), value, previousEffective, effectiveAfter);
  }
}
