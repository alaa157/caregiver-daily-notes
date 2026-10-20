package com.caregiver.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArabicNormalizerTest {

  @Test
  void nullYieldsEmpty() {
    assertThat(ArabicNormalizer.normalize(null)).isEmpty();
  }

  @Test
  void stripsTashkeelAndTatweel() {
    assertThat(ArabicNormalizer.normalize("يَأْكُلُ جَيِّدًا")).isEqualTo("ياكل جيدا");
    assertThat(ArabicNormalizer.normalize("كــتـاب")).isEqualTo("كتاب");
  }

  @Test
  void unifiesAlefAndHamzaForms() {
    assertThat(ArabicNormalizer.normalize("أحمد وإبراهيم وآمال")).isEqualTo("احمد وابراهيم وامال");
    assertThat(ArabicNormalizer.normalize("مؤمن وشيئ")).isEqualTo("مومن وشيي");
  }

  @Test
  void mapsTaaMarbutaAndAlefMaqsura() {
    assertThat(ArabicNormalizer.normalize("ممتازة")).isEqualTo("ممتازه");
    assertThat(ArabicNormalizer.normalize("مستشفى")).isEqualTo("مستشفي");
  }

  @Test
  void lowercasesLatinAndCollapsesWhitespace() {
    assertThat(ArabicNormalizer.normalize("  Ate   WELL\ntoday ")).isEqualTo("ate well today");
  }

  @Test
  void storedTextNeverAlteredByNormalize() {
    var stored = "يَأْكُلُ جَيِّدًا  ";
    ArabicNormalizer.normalize(stored);
    assertThat(stored).isEqualTo("يَأْكُلُ جَيِّدًا  ");
  }

  @Test
  void foldsArabicIndicAndPersianDigits() {
    // "ألم ٧" vs "ألم 7" used to be different strings, so a model quote that
    // re-wrote the digit failed quote matching and the observation dropped.
    assertThat(ArabicNormalizer.normalize("ألم ٧")).isEqualTo("الم 7");
    assertThat(ArabicNormalizer.normalize("٧")).isEqualTo("7");
    assertThat(ArabicNormalizer.normalize("۷")).isEqualTo("7");
    assertThat(ArabicNormalizer.normalize("١٢")).isEqualTo("12");
    assertThat(ArabicNormalizer.normalize("۱۲")).isEqualTo("12");
    assertThat(ArabicNormalizer.normalize("الألم ٧ من ١٠"))
        .isEqualTo(ArabicNormalizer.normalize("الألم 7 من 10"));
  }

  @Test
  void stripsZeroWidthAndDirectionMarks() {
    var plain = ArabicNormalizer.normalize("سقطت اليوم");
    assertThat(ArabicNormalizer.normalize("سقطت\u200B اليوم")).isEqualTo(plain);
    assertThat(ArabicNormalizer.normalize("سقط\u200Cت اليوم")).isEqualTo(plain);
    assertThat(ArabicNormalizer.normalize("سقط\u200Dت اليوم")).isEqualTo(plain);
    assertThat(ArabicNormalizer.normalize("\u200Fسقطت اليوم")).isEqualTo(plain);
    assertThat(ArabicNormalizer.normalize("سقطت \u200Eاليوم")).isEqualTo(plain);
    assertThat(ArabicNormalizer.normalize("\uFEFFسقطت اليوم")).isEqualTo(plain);
    assertThat(ArabicNormalizer.normalize("سـ\u00ADـقطت اليوم")).isEqualTo(plain);
  }

  @Test
  void unifiesArabicAndLatinPunctuation() {
    // Codepoint escapes keep both members of each pair visibly different
    // characters: ، (\u060C) vs ',', ؟ (\u061F) vs '?', « (\u00AB) and
    // » (\u00BB) vs '"', ؛ (\u061B) vs ';'.
    assertThat(ArabicNormalizer.normalize("ألم\u060C سقطت\u061F"))
        .isEqualTo(ArabicNormalizer.normalize("ألم, سقطت?"));
    assertThat(ArabicNormalizer.normalize("الرأي \u00ABجيد\u00BB\u061B"))
        .isEqualTo(ArabicNormalizer.normalize("الرأي \"جيد\";"));
  }

  @Test
  void foldsPersianLetterVariants() {
    // Persian kaf \u06A9 folds to Arabic kaf \u0643; Persian yeh \u06CC
    // folds to Arabic yeh \u064A.
    assertThat(ArabicNormalizer.normalize("\u06A9\u062A\u0627\u0628"))
        .isEqualTo(ArabicNormalizer.normalize("\u0643\u062A\u0627\u0628"));
    assertThat(ArabicNormalizer.normalize("\u0631\u0623\u06CC\u064A"))
        .isEqualTo(ArabicNormalizer.normalize("\u0631\u0623\u064A\u064A"));
  }
}
