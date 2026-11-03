package com.caregiver.notes;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ObservationInputValidator.class)
public @interface ValidObservationInput {
  String message() default "Invalid observation state/value combination.";
  Class<?>[] groups() default {};
  Class<? extends Payload>[] payload() default {};
}
