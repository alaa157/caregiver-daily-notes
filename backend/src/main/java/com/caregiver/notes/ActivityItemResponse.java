package com.caregiver.notes;

import java.time.Instant;
import java.util.UUID;

/** Minimal metadata for one caregiver-owned note activity event. */
public record ActivityItemResponse(UUID id, ActivityKind kind, UUID recipientId, Instant createdAt) {
  public enum ActivityKind { note, correction, addendum }
}
