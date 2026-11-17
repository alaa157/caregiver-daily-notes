package com.caregiver.medications;

/**
 * An intake timestamp outside the accepted range. Extends
 * {@link IllegalArgumentException} so it remains a bad request, with a
 * dedicated handler returning a stable code the client can localize.
 */
public class InvalidIntakeTimeException extends IllegalArgumentException {

  public InvalidIntakeTimeException(String message) {
    super(message);
  }
}
