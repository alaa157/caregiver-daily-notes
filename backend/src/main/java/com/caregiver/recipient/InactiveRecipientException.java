package com.caregiver.recipient;

public class InactiveRecipientException extends RuntimeException {
  public InactiveRecipientException() {
    this("Archived or inactive recipients cannot be changed in ordinary workflows.");
  }
  public InactiveRecipientException(String message) { super(message); }
}
