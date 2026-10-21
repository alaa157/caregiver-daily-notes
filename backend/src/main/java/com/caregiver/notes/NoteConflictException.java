package com.caregiver.notes;

/**
 * A daily note already exists for the same recipient and date.
 *
 * <p>Surfaces as HTTP 409 {@code NOTE_CONFLICT}. Kept separate from
 * {@link IdempotencyConflictException} so clients can distinguish a duplicate
 * business record from a reused idempotency key.
 */
public class NoteConflictException extends RuntimeException {
  public NoteConflictException() {
    super("A note for this recipient and date already exists.");
  }
}
