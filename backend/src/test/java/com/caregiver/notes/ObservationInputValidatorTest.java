package com.caregiver.notes;

import com.caregiver.common.ObservationState;
import com.caregiver.common.VerificationState;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Whitespace-only recorded text is not an observation (issue #738). */
class ObservationInputValidatorTest {

  private static ValidatorFactory factory;
  private static Validator validator;

  @BeforeAll
  static void setup() {
    factory = Validation.buildDefaultValidatorFactory();
    validator = factory.getValidator();
  }

  @AfterAll
  static void close() {
    factory.close();
  }

  @Test
  void rejectsWhitespaceOnlyRecordedFreeText() {
    List<String> paths = validate(input(ObservationState.RECORDED, "   ", null));
    assertThat(paths).contains("observations.freeText.value");
  }

  @Test
  void rejectsWhitespaceOnlyRecordedShortTextField() {
    var set = setWithFreeText(input(ObservationState.NOT_RECORDED, null, null));
    var withMood = new ObservationSetRequest(
        input(ObservationState.RECORDED, " \t ", null), set.appetite(), set.sleep(), set.mobility(),
        set.medicationStatus(), set.painScore(), set.fallReported(), set.freeText());
    assertThat(validate(withMood)).contains("observations.mood.value");
  }

  @Test
  void acceptsTextWithSurroundingSpaces() {
    assertThat(validate(input(ObservationState.RECORDED, "  real note  ", null))).isEmpty();
  }

  @Test
  void acceptsEmptyRecordedValueAndNotRecordedState() {
    assertThat(validate(input(ObservationState.RECORDED, "", null))).isEmpty();
    assertThat(validate(input(ObservationState.NOT_RECORDED, null, null))).isEmpty();
  }

  @Test
  void storesTrimmedValueButPreservesInternalWhitespace() {
    var set = setWithFreeText(input(ObservationState.RECORDED, "  real  note  ", null));
    var observations = set.toDomain(VerificationState.NOT_ASSERTED);
    assertThat(observations.freeText().value()).isEqualTo("real  note");
  }

  private static List<String> validate(ObservationInput<String> freeText) {
    var request = new NoteRequest(UUID.randomUUID(), LocalDate.now(), null, null, null, null,
        null, null, null, null, null, setWithFreeText(freeText), null);
    return validator.validate(request).stream()
        .map(violation -> violation.getPropertyPath().toString())
        .toList();
  }

  private static List<String> validate(ObservationSetRequest set) {
    var request = new NoteRequest(UUID.randomUUID(), LocalDate.now(), null, null, null, null,
        null, null, null, null, null, set, null);
    return validator.validate(request).stream()
        .map(violation -> violation.getPropertyPath().toString())
        .toList();
  }

  private static ObservationSetRequest setWithFreeText(ObservationInput<String> freeText) {
    return new ObservationSetRequest(
        input(ObservationState.NOT_RECORDED, null, null),
        input(ObservationState.NOT_RECORDED, null, null),
        input(ObservationState.NOT_RECORDED, null, null),
        input(ObservationState.NOT_RECORDED, null, null),
        input(ObservationState.NOT_RECORDED, null, null),
        input(ObservationState.NOT_RECORDED, null, null),
        input(ObservationState.NOT_RECORDED, null, null),
        freeText);
  }

  private static <T> ObservationInput<T> input(ObservationState state, T value, T rawValue) {
    return new ObservationInput<>(state, value, rawValue);
  }
}
