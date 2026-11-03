package com.caregiver.notes;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.ArrayList;
import java.util.List;

public class NoteObservationRequestValidator
    implements ConstraintValidator<ValidNoteObservationRequest, NoteRequest> {
  @Override
  public boolean isValid(NoteRequest request, ConstraintValidatorContext context) {
    if (request == null) {
      return true;
    }
    List<String> errors = new ArrayList<>();
    if (request.observations() != null) {
      if (request.hasLegacyObservationFields()) {
        errors.add("observations");
      }
      errors.addAll(ObservationRequestValidation.validateSet(request.observations(), true));
    }
    return ObservationRequestValidation.addErrors(context, errors);
  }
}
