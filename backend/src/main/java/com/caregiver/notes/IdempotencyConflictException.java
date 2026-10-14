package com.caregiver.notes;

/**
 * An idempotency key was reused with a different request payload.
 *
 * <p>Surfaces as HTTP 409 {@code IDEMPOTENCY_CONFLICT}. Repeating a request
 * with the same key <em>and</em> the same payload is safe and returns the
 * original result; only a payload change is rejected.
 */
public class IdempotencyConflictException extends RuntimeException {
  public IdempotencyConflictException() {
    super("Idempotency-Key was already used with a different request.");
  }
}
