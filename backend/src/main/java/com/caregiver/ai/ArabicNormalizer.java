package com.caregiver.ai;

import java.util.Locale;

/**
 * Comparison-only Arabic normalization for quote validation. Output is used
 * to compare model quotes against stored note text; stored text is never
 * altered — this method returns a new string and leaves its input alone.
 */
public final class ArabicNormalizer {

  private ArabicNormalizer() {
  }

  public static String normalize(String s) {
    if (s == null) {
      return "";
    }
    String out = s.replace("ـ", "");
    out = out.replaceAll("[\\u064B-\\u065F\\u0670]", "");
    out = out.replaceAll("[أإآٱ]", "ا");
    out = out.replace("ؤ", "و");
    out = out.replace("ئ", "ي");
    out = out.replace("ى", "ي");
    out = out.replace("ة", "ه");
    out = out.toLowerCase(Locale.ROOT);
    out = out.replaceAll("\\s+", " ");
    return out.trim();
  }
}
