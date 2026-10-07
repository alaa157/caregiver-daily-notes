package com.caregiver.auth;

/** Thrown when the email is already registered. Mapped to 422 in Task 8. */
public class DuplicateEmailException extends RuntimeException {

  public DuplicateEmailException(String message) {
    super(message);
  }
}
