package com.caregiver.notes;

import com.caregiver.common.ObservationState;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ObservationInputValidator
    implements ConstraintValidator<ValidObservationInput, ObservationInput<?>> {
  @Override
  public boolean isValid(ObservationInput<?> input, ConstraintValidatorContext context) {
    if (input == null || input.state() == null) {
      return true;
    }
    String invalidProperty = null;
    if (input.state() == ObservationState.RECORDED && input.value() == null) {
      invalidProperty = "value";
    } else if (input.state() != ObservationState.RECORDED && input.value() != null) {
      invalidProperty = "value";
    } else if (input.state() != ObservationState.LEGACY_AMBIGUOUS && input.rawValue() != null) {
      invalidProperty = "rawValue";
    }
    if (invalidProperty == null) {
      return true;
    }
    context.disableDefaultConstraintViolation();
    context.buildConstraintViolationWithTemplate(context.getDefaultConstraintMessageTemplate())
        .addPropertyNode(invalidProperty)
        .addConstraintViolation();
    return false;
  }
}
