package com.caregiver.auth;

/** Thrown when a JWT is missing, malformed, tampered, or expired. */
public class InvalidTokenException extends RuntimeException {

  public InvalidTokenException(String message) {
    super(message);
  }

  public InvalidTokenException(String message, Throwable cause) {
    super(message, cause);
  }
}
