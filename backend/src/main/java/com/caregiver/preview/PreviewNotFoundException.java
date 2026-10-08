package com.caregiver.preview;

/** Preview: resource missing for this caregiver. Replaced by member issues. */
public class PreviewNotFoundException extends RuntimeException {

  public PreviewNotFoundException(String message) {
    super(message);
  }
}
