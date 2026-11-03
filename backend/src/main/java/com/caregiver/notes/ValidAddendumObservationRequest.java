package com.caregiver.notes;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AddendumObservationRequestValidator.class)
public @interface ValidAddendumObservationRequest {
  String message() default "Invalid observation request.";
  Class<?>[] groups() default {};
  Class<? extends Payload>[] payload() default {};
}
