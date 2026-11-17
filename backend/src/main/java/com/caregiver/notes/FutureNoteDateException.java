package com.caregiver.notes;

/**
 * A daily note dated after today. Extends {@link IllegalArgumentException} so
 * existing validation contracts still treat it as a bad request, while the
 * dedicated handler in {@link NoteApiExceptionHandler} can return a clear,
 * stable message instead of the generic invalid-request text.
 */
public class FutureNoteDateException extends IllegalArgumentException {

  public FutureNoteDateException() {
    super("A note cannot be dated after today.");
  }
}
