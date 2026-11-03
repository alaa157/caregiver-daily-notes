package com.caregiver.notes;

import com.caregiver.common.ObservationState;
import jakarta.validation.ConstraintValidatorContext;
import java.util.ArrayList;
import java.util.List;

final class ObservationRequestValidation {
  private ObservationRequestValidation() {}

  static List<String> validateSet(ObservationSetRequest set, boolean requireAll) {
    if (set == null) {
      return List.of();
    }
    List<String> errors = new ArrayList<>();
    validate("mood", set.mood(), requireAll, 40, null, errors);
    validate("appetite", set.appetite(), requireAll, 40, null, errors);
    validate("sleep", set.sleep(), requireAll, 40, null, errors);
    validate("mobility", set.mobility(), requireAll, 40, null, errors);
    validate("medicationStatus", set.medicationStatus(), requireAll, 20, null, errors);
    validate("painScore", set.painScore(), requireAll, 0, 10, errors);
    validate("fallReported", set.fallReported(), requireAll, 0, null, errors);
    validate("freeText", set.freeText(), requireAll, 20000, null, errors);
    return errors;
  }

  static boolean addErrors(ConstraintValidatorContext context, List<String> errors) {
    if (errors.isEmpty()) {
      return true;
    }
    context.disableDefaultConstraintViolation();
    for (String error : errors) {
      var violation = context.buildConstraintViolationWithTemplate(
              error.endsWith(".state") || error.endsWith(".value") || error.endsWith(".rawValue")
                  ? "Invalid observation state/value combination."
                  : "Observation value is outside the supported range.");
      String[] path = error.split("\\.");
      ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext node =
          violation.addPropertyNode(path[0]);
      for (int i = 1; i < path.length; i++) {
        node = node.addPropertyNode(path[i]);
      }
      node.addConstraintViolation();
    }
    return false;
  }

  private static void validate(
      String name,
      ObservationInput<?> input,
      boolean require,
      int maxOrMin,
      Integer max,
      List<String> errors) {
    if (input == null) {
      if (require) {
        errors.add("observations." + name);
      }
      return;
    }
    if (input.state() == null) {
      errors.add("observations." + name + ".state");
      return;
    }
    if (input.state() == ObservationState.RECORDED) {
      Object value = input.value();
      if (value == null) {
        errors.add("observations." + name + ".value");
      } else if (value instanceof String text && text.length() > maxOrMin) {
        errors.add("observations." + name + ".value");
      } else if (value instanceof Integer number
          && (number < maxOrMin || number > max)) {
        errors.add("observations." + name + ".value");
      }
    } else if (input.value() != null) {
      errors.add("observations." + name + ".value");
    }
    if (input.state() != ObservationState.LEGACY_AMBIGUOUS && input.rawValue() != null) {
      errors.add("observations." + name + ".rawValue");
    } else if (input.rawValue() instanceof String raw && raw.length() > maxOrMin) {
      errors.add("observations." + name + ".rawValue");
    }
    if (name.equals("medicationStatus") && input.state() == ObservationState.RECORDED
        && !"TAKEN".equals(input.value()) && !"MISSED".equals(input.value())) {
      errors.add("observations." + name + ".value");
    }
  }
}
