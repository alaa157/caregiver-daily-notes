package com.caregiver.ai;

/**
 * Thrown when model output cannot be trusted: malformed JSON, unknown
 * properties, invalid enum values, or missing required fields. Callers treat
 * this as a failed attempt (repair or fallback), never as usable content.
 *
 * <p>Carries a stable, non-sensitive {@code reasonCode} so callers can log why
 * generation failed. The code never includes model or note content; the
 * message may, so it must not be logged.</p>
 */
public class InvalidModelOutputException extends RuntimeException {

  private final String reasonCode;

  public InvalidModelOutputException(String message) {
    this(message, PlanFailureCodes.INVALID_MODEL_OUTPUT, null);
  }

  public InvalidModelOutputException(String message, Throwable cause) {
    this(message, PlanFailureCodes.INVALID_MODEL_OUTPUT, cause);
  }

  public InvalidModelOutputException(String message, String reasonCode) {
    this(message, reasonCode, null);
  }

  public InvalidModelOutputException(String message, String reasonCode, Throwable cause) {
    super(message, cause);
    this.reasonCode = reasonCode;
  }

  /** Stable reason code safe to log; never contains health data. */
  public String reasonCode() {
    return reasonCode;
  }
}
