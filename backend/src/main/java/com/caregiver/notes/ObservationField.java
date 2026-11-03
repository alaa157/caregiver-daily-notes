package com.caregiver.notes;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;

/** Canonical note fields and their existing wire/storage value types. */
public enum ObservationField {
  MOOD("mood", Kind.TEXT, 40), APPETITE("appetite", Kind.TEXT, 40),
  SLEEP("sleep", Kind.TEXT, 40), MOBILITY("mobility", Kind.TEXT, 40),
  MEDICATION_STATUS("medicationStatus", Kind.MEDICATION, 0),
  PAIN_SCORE("painScore", Kind.PAIN, 0), FALL_REPORTED("fallReported", Kind.BOOLEAN, 0),
  FREE_TEXT("freeText", Kind.TEXT, 20000);

  private final String wireName;
  private final Kind kind;
  private final int maxLength;
  ObservationField(String wireName, Kind kind, int maxLength) {
    this.wireName = wireName; this.kind = kind; this.maxLength = maxLength;
  }
  public String wireName() { return wireName; }
  public static ObservationField fromWire(String name) {
    for (ObservationField field : values()) if (field.wireName.equals(name)) return field;
    throw new IllegalArgumentException("Unsupported observation field: " + name);
  }
  public void validate(JsonNode value) {
    if (value == null || value.isNull()) throw new IllegalArgumentException("SET requires a non-null value for " + wireName + ".");
    boolean valid = switch (kind) {
      case TEXT -> value.isTextual() && value.textValue().length() <= maxLength;
      case BOOLEAN -> value.isBoolean();
      case PAIN -> value.isIntegralNumber() && value.canConvertToInt() && value.intValue() >= 0 && value.intValue() <= 10;
      // In the canonical observation model UNKNOWN is a state, not a recorded medication value.
      case MEDICATION -> value.isTextual() && Set.of("TAKEN", "MISSED").contains(value.textValue());
    };
    if (!valid) throw new IllegalArgumentException("Invalid SET value for " + wireName + ".");
  }
  private enum Kind { TEXT, BOOLEAN, PAIN, MEDICATION }
}
