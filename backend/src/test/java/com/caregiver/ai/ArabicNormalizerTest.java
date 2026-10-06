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
}
