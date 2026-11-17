package com.caregiver.ai;

import java.util.Locale;

/** Validates the requested generated-text language and preserves legacy note-language behavior. */
final class AiOutputLanguage {
  static final String NOTES = "notes";

  private AiOutputLanguage() {}

  static String normalize(String language) {
    if (language == null || language.isBlank()) return NOTES;
    return switch (language.trim().toLowerCase(Locale.ROOT)) {
      case "ar" -> "ar";
      case "en" -> "en";
      case NOTES -> NOTES;
      default -> throw new IllegalArgumentException("language must be ar or en");
    };
  }

  static String promptLanguage(String language) {
    return switch (normalize(language)) {
      case "ar" -> "Arabic";
      case "en" -> "English";
      default -> "the language of the note text they describe (English or Arabic)";
    };
  }
}
