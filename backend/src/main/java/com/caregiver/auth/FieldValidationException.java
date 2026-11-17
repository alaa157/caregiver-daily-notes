package com.caregiver.auth;

/** A validation failure tied to a named request field, rendered with {@code fields}. */
public class FieldValidationException extends ValidationException {

  private final String field;

  public FieldValidationException(String field, String message) {
    super(message);
    if (field == null || field.isBlank()) {
      throw new IllegalArgumentException("field must be non-blank");
    }
    this.field = field;
  }

  public String field() {
    return field;
  }
}
