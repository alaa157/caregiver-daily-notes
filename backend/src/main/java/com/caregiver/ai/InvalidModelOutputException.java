package com.caregiver.ai;

/**
 * Thrown when model output cannot be trusted: malformed JSON, unknown
 * properties, invalid enum values, or missing required fields. Callers treat
 * this as a failed attempt (repair or fallback), never as usable content.
 */
public class InvalidModelOutputException extends RuntimeException {

  public InvalidModelOutputException(String message) {
    super(message);
  }

  public InvalidModelOutputException(String message, Throwable cause) {
    super(message, cause);
  }
}
