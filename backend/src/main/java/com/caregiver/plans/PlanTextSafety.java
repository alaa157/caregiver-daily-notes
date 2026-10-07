package com.caregiver.plans;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Rejects obvious diagnosis, prescription, medication, and dietary advice in plan text. */
final class PlanTextSafety {

  private static final Pattern DIAGNOSIS_OR_PRESCRIPTION =
      Pattern.compile("\\bdiagnos(?:e|es|ed|ing|is)\\b|\\bprescri(?:be|bes|bed|bing|ption)\\b");
  private static final Pattern MEDICATION_INSTRUCTION = Pattern.compile(
      "\\b(?:start|stop|take|give|administer|increase|decrease|double|halve|skip|change|adjust)"
          + "\\b.{0,60}\\b(?:medications?|medicines?|drugs?|antibiotics?|pills?|tablets?|doses?|dosage)\\b");
  private static final Pattern DIETARY_INSTRUCTION = Pattern.compile(
      "\\b(?:avoid|eat|consume|restrict|increase|decrease|reduce|eliminate)\\b.{0,40}"
          + "\\b(?:salt|sodium|sugar|carbs?|fat|protein|calories|food|meals?|diet)\\b");

  private PlanTextSafety() {}

  static void requireSafe(String value) {
    String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    if (DIAGNOSIS_OR_PRESCRIPTION.matcher(normalized).find()
        || MEDICATION_INSTRUCTION.matcher(normalized).find()
        || DIETARY_INSTRUCTION.matcher(normalized).find()) {
      throw new IllegalArgumentException("plan text contains medical advice");
    }
  }
}
