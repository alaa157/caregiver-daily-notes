package com.caregiver.auth;

/** Thrown when request data fails validation. Mapped to 422 in Task 8. */
public class ValidationException extends RuntimeException {

  public ValidationException(String message) {
    super(message);
  }
}
