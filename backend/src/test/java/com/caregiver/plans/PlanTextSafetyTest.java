package com.caregiver.plans;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanTextSafetyTest {

  @Test
  void descriptiveMedicationReasonIsAccepted() {
    assertThat(PlanTextSafety.isUnsafe("Increase in confusion after missed medication")).isFalse();
  }

  @Test
  void descriptiveMealsReasonIsAccepted() {
    assertThat(PlanTextSafety.isUnsafe("Increase supervision at meals")).isFalse();
  }

  @Test
  void medicationInstructionIsRejected() {
    assertThat(PlanTextSafety.isUnsafe("increase the dose of metformin")).isTrue();
  }

  @Test
  void dietaryInstructionIsRejected() {
    assertThat(PlanTextSafety.isUnsafe("reduce salt intake")).isTrue();
  }

  @Test
  void arabicMedicalAdviceIsRejected() {
    assertThat(PlanTextSafety.isUnsafe("زيد الجرعة")).isTrue();
    assertThat(PlanTextSafety.isUnsafe("قلل الملح")).isTrue();
    assertThat(PlanTextSafety.isUnsafe("تشخيص الحالة")).isTrue();
  }

  @Test
  void arabicDescriptiveTextIsAccepted() {
    assertThat(PlanTextSafety.isUnsafe("زيادة في الالتباس بعد دواء فائت")).isFalse();
  }

  @Test
  void requireSafeThrowsOnlyForUnsafeText() {
    PlanTextSafety.requireSafe("Increase in confusion after missed medication");
    assertThatThrownBy(() -> PlanTextSafety.requireSafe("increase the dose of metformin"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
