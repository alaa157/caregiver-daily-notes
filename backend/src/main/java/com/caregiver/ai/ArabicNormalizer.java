package com.caregiver.ai;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Comparison-only Arabic normalization for quote validation. Output is used
 * to compare model quotes against stored note text; stored text is never
 * altered — this method returns a new string and leaves its input alone.
 *
 * Folded on top of the original core rules: Arabic-Indic and Eastern
 * digits become Latin, zero-width and direction controls are stripped,
 * Arabic punctuation translates to its Latin twin, and the Persian letter
 * variants (ک ی) fold onto their Arabic twins — all so a model quote that
 * re-typesets ٧ as 7 (or drops a bidi mark) still matches the stored note.
 * Patterns compile once; behavior must match what the app mirrors.
 */
public final class ArabicNormalizer {

  private static final Pattern DIACRITICS = Pattern.compile("[\\u064B-\\u065F\\u0670]");
  /** Zero-width joiners/separators, bidi marks, word joiners, BOM, soft hyphen. */
  private static final Pattern INVISIBLE_AND_CONTROLS =
      Pattern.compile("[\\u200B-\\u200F\\u2066-\\u2069\\u202A-\\u202E\\uFEFF\\u061C\\u00AD]");
  private static final Pattern ALEF_FORMS = Pattern.compile("[أإآٱ]");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private ArabicNormalizer() {
  }

  public static String normalize(String s) {
    if (s == null) {
      return "";
    }
    String out = INVISIBLE_AND_CONTROLS.matcher(s).replaceAll("");
    out = out.replace("ـ", "");
    out = DIACRITICS.matcher(out).replaceAll("");
    out = toLatinDigits(out);
    out = unifyPunctuation(out);
    // Persian letter variants onto their Arabic twins.
    out = out.replace("ک", "ك");
    out = out.replace("ی", "ي");
    out = ALEF_FORMS.matcher(out).replaceAll("ا");
    out = out.replace("ؤ", "و");
    out = out.replace("ئ", "ي");
    out = out.replace("ى", "ي");
    out = out.replace("ة", "ه");
    out = out.toLowerCase(Locale.ROOT);
    out = WHITESPACE.matcher(out).replaceAll(" ");
    return out.trim();
  }

  /** Arabic-Indic ٠-٩ and Eastern/Persian ۰-۹ digits become ASCII. */
  private static String toLatinDigits(String s) {
    StringBuilder b = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c >= '٠' && c <= '٩') {
        b.append((char) ('0' + (c - '٠')));
      } else if (c >= '۰' && c <= '۹') {
        b.append((char) ('0' + (c - '۰')));
      } else {
        b.append(c);
      }
    }
    return b.toString();
  }

  /** Arabic-script punctuation becomes its Latin pair so both sides compare equal. */
  private static String unifyPunctuation(String s) {
    StringBuilder b = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '،' -> b.append(',');
        case '؛' -> b.append(';');
        case '؟' -> b.append('?');
        case '«', '»' -> b.append('"');
        case '٫' -> b.append('.');
        case '٬' -> b.append(',');
        default -> b.append(c);
      }
    }
    return b.toString();
  }
}
