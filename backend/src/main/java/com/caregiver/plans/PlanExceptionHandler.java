package com.caregiver.plans;

import com.caregiver.auth.ErrorResponse;
import com.caregiver.recipient.RecipientNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PlanExceptionHandler {
  @ExceptionHandler(PlanNotFoundException.class)
  public ResponseEntity<ErrorResponse> notFound(PlanNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse("NOT_FOUND", "Plan not found."));
  }

  @ExceptionHandler(IllegalPlanTransitionException.class)
  public ResponseEntity<ErrorResponse> illegalTransition(IllegalPlanTransitionException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse("INVALID_PLAN_TRANSITION", ex.getMessage()));
  }
}
