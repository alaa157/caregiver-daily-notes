package com.caregiver.recipient;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * Full replacement of a recipient's optional care details. Every field is
 * optional; a {@code null} or blank string clears that field. Text fields are
 * trimmed and blank results become {@code null}.
 */
@Schema(description = "Optional care details; the five fields together replace the previous values.")
public record RecipientCareDetailsRequest(
    @Schema(description = "Recipient's date of birth", example = "1950-04-12")
        LocalDate dateOfBirth,
    @Schema(description = "Known allergies", example = "Penicillin")
        String allergies,
    @Schema(description = "Known medical conditions", example = "Type 2 diabetes")
        String conditions,
    @Schema(description = "Emergency contact name", example = "Layla Hassan")
        String emergencyContactName,
    @Schema(description = "Emergency contact phone", example = "+20 100 123 4567")
        String emergencyContactPhone) {

  private static final java.util.regex.Pattern PHONE = java.util.regex.Pattern.compile("^[0-9+()\\- ]{3,30}$");
  /** Separators are not a phone number; require at least this many digits. */
  private static final int MIN_PHONE_DIGITS = 3;
  private static final LocalDate EARLIEST_BIRTH_DATE = LocalDate.of(1900, 1, 1);

  public RecipientCareDetailsRequest {
    allergies = normalize(allergies);
    conditions = normalize(conditions);
    emergencyContactName = normalize(emergencyContactName);
    emergencyContactPhone = normalize(emergencyContactPhone);
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  /** Throws {@link IllegalArgumentException} (surfaced as 400) on invalid values. */
  void validate() {
    if (dateOfBirth != null
        && (dateOfBirth.isAfter(LocalDate.now()) || dateOfBirth.isBefore(EARLIEST_BIRTH_DATE))) {
      throw new IllegalArgumentException("dateOfBirth must be between 1900-01-01 and today.");
    }
    if (allergies != null && allergies.length() > 500
        || conditions != null && conditions.length() > 500
        || emergencyContactName != null && emergencyContactName.length() > 120
        || emergencyContactPhone != null && emergencyContactPhone.length() > 30) {
      throw new IllegalArgumentException("Care detail exceeds the allowed length.");
    }
    if (emergencyContactPhone != null
        && (!PHONE.matcher(emergencyContactPhone).matches()
            || emergencyContactPhone.replaceAll("\\D", "").length() < MIN_PHONE_DIGITS)) {
      throw new IllegalArgumentException("emergencyContactPhone has an invalid format.");
    }
  }
}
