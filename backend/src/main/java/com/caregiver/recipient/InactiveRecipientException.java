package com.caregiver.recipient;

public class InactiveRecipientException extends RuntimeException {

  public InactiveRecipientException() {
    super("Inactive recipients cannot be updated.");
  }
}
