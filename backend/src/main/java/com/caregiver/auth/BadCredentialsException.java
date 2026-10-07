package com.caregiver.auth;

/** Thrown for unknown email or wrong password. Mapped to 401 in Task 8. */
public class BadCredentialsException extends RuntimeException {

  public BadCredentialsException() {
    super("Invalid email or password.");
  }
}
