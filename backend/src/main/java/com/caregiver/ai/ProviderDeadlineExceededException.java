package com.caregiver.ai;

/** Thrown when the bounded provider call exceeds its deadline. */
public class ProviderDeadlineExceededException extends RuntimeException {

  public ProviderDeadlineExceededException(String message) {
    super(message);
  }
}
