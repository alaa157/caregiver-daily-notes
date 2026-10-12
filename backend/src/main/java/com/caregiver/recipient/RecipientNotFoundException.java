package com.caregiver.recipient;

/** Also used for another caregiver's recipient to avoid leaking its existence. */
public class RecipientNotFoundException extends RuntimeException {

  public RecipientNotFoundException() {
    super("Recipient not found.");
  }
}
