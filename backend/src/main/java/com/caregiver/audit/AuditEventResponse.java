package com.caregiver.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(UUID id, UUID actorCaregiverId, String eventType,
    String targetType, String targetId, String safeMetadata, Instant occurredAt) {
  public static AuditEventResponse from(AuditEvent event) {
    return new AuditEventResponse(event.getId(), event.getActorCaregiverId(), event.getEventType(),
        event.getTargetType(), event.getTargetId(), event.getSafeMetadata(), event.getOccurredAt());
  }
}
