package com.caregiver.plans;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Blocks obvious diagnosis, prescription, medication and dietary instructions
 * in plan text. Both English and Arabic are checked. A match requires a
 * directive shape (verb followed by the medical noun), so a reason that merely
 * describes an event - "increase in confusion after missed medication" - is
 * not mistaken for medical advice.
 */
public final class PlanTextSafety {

  private static final String EN_LINK =
      "(?:(?:the|your|a|an|this|that|my|his|her|their|our|taking|using|of|on|to)\\s+)*";
  private static final String AR_START = "(?<!\\p{L})";
  private static final String AR_END = "(?!\\p{L})";
  private static final String AR_LINK = "(?:(?:في|من|على|الى|ب)\\s+)*";

  private static final Pattern DIAGNOSIS_OR_PRESCRIPTION = Pattern.compile(
      "\\bdiagnos(?:e|es|ed|ing|is)\\b|\\bprescri(?:be|bes|bed|bing|ption)\\b");

  private static final Pattern MEDICATION_INSTRUCTION = Pattern.compile(
      "\\b(?:start|stop|take|give|administer|increase|decrease|double|halve|skip|change|adjust)\\b\\s+"
          + EN_LINK
          + "(?:medications?|medicines?|drugs?|antibiotics?|pills?|tablets?|doses?|dosage)\\b");

  private static final Pattern DIETARY_INSTRUCTION = Pattern.compile(
      "\\b(?:avoid|eat|consume|restrict|increase|decrease|reduce|eliminate)\\b\\s+"
          + EN_LINK
          + "(?:salt|sodium|sugar|carbs?|fat|protein|calories|food|meals?|diet)\\b");

  private static final Pattern AR_DIAGNOSIS_OR_PRESCRIPTION = Pattern.compile(
      AR_START + "(?:تشخيص|تشخيصات|روشت[هة]|وصفه\\s+طبيه|وصف\\s+(?:دواء|عقار))" + AR_END);

  private static final Pattern AR_MEDICATION_INSTRUCTION = Pattern.compile(
      AR_START
          + "(?:زيد|زد|زياد[هة]|قلل|تقليل|نقص|انقاص|اوقف|ايقاف|توقف|وقف|ابدا|بدء|خذ|تناول|اعط|اعطاء"
          + "|ضاعف|مضاعف[هة]|تخطى|تجاوز|غير|تغيير|بدل|تبديل|عدل|تعديل)\\s+"
          + AR_LINK
          + "(?:ال)?(?:دواء|ادوي[هة]|عقار|عقارات|جرع[هة]|جرعات|حبوب|حب[هة]|قرص|اقراص|مضاد\\s+حيوي)"
          + AR_END);

  private static final Pattern AR_DIETARY_INSTRUCTION = Pattern.compile(
      AR_START
          + "(?:تجنب|امتنع|قلل|تقليل|خفض|تخفيض|خفف|الغى|الغاء|تناول|كل|اكل|استهلك|حصر)\\s+"
          + AR_LINK
          + "(?:ال)?(?:ملح|صوديوم|سكر|سكريات|كربوهيدرات|دهون|بروتين|سعرات|طعام|اكل|غذاء"
          + "|وجب[هة]|وجبات|حمي[هة]|نظام\\s+غذائي)"
          + AR_END);

  private PlanTextSafety() {}

  /** True when the text contains an obvious medical instruction or diagnosis. */
  public static boolean isUnsafe(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    String normalized = normalize(value);
    return DIAGNOSIS_OR_PRESCRIPTION.matcher(normalized).find()
        || AR_DIAGNOSIS_OR_PRESCRIPTION.matcher(normalized).find()
        || MEDICATION_INSTRUCTION.matcher(normalized).find()
        || AR_MEDICATION_INSTRUCTION.matcher(normalized).find()
        || DIETARY_INSTRUCTION.matcher(normalized).find()
        || AR_DIETARY_INSTRUCTION.matcher(normalized).find();
  }

  static void requireSafe(String value) {
    if (isUnsafe(value)) {
      throw new IllegalArgumentException("plan text contains medical advice");
    }
  }

  /** Folds Arabic letter variants and strips diacritics so the patterns see bare letters. */
  private static String normalize(String value) {
    String folded = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT)
        .replaceAll("[\\u064B-\\u065F\\u0670\\u0640]", "");
    return folded.replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ؤ', 'و').replace('ئ', 'ي')
        .replace('ة', 'ه');
  }
}
