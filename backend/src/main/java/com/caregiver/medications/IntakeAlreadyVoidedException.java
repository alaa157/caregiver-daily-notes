package com.caregiver.medications;

/** A recorded intake event was already voided; voiding is not repeatable. */
public class IntakeAlreadyVoidedException extends RuntimeException {

  public IntakeAlreadyVoidedException() {
    super("This intake event has already been voided.");
  }
}
