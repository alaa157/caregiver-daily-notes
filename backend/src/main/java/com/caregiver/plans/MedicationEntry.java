package com.caregiver.plans;

/**
 * One medication line as reported, never inferred. Compared by exact
 * strings — doses are code, not prose, so no normalization applies.
 */
public record MedicationEntry(String name, String dose, String schedule) {

  public MedicationEntry {
    if (name == null || name.isBlank() || dose == null || dose.isBlank() || schedule == null || schedule.isBlank()) {
      throw new IllegalArgumentException("name, dose, and schedule must be non-blank");
    }
  }
}
