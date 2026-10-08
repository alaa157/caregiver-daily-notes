package com.caregiver.preview;

/** Preview: resource belongs to another caregiver. Replaced by member issues. */
public class PreviewForbiddenException extends RuntimeException {

  public PreviewForbiddenException(String message) {
    super(message);
  }
}
