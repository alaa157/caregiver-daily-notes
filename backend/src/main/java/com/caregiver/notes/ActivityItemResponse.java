package com.caregiver.notes;

import java.time.Instant;
import java.util.UUID;

/**
 * Minimal metadata for one caregiver-owned note activity event.
 *
 * <p>{@code id} identifies the event (note, addendum or correction), while {@code noteId} always
 * identifies the note the event belongs to so clients can navigate to it.</p>
 */
public record ActivityItemResponse(UUID id, UUID noteId, ActivityKind kind, UUID recipientId, Instant createdAt) {
  public enum ActivityKind { note, correction, addendum }
}
