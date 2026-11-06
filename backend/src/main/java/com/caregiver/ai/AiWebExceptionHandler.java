package com.caregiver.ai;

import com.caregiver.auth.ErrorResponse;
import com.caregiver.plans.IllegalPlanTransitionException;
import com.caregiver.plans.ProtectedPlanException;
import com.caregiver.recipient.InactiveRecipientException;
import com.caregiver.recipient.RecipientNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
    SummaryController.class,
    PlanGenerationController.class,
    SafetyAcknowledgementController.class
})
public class AiWebExceptionHandler {
  @ExceptionHandler(RecipientNotFoundException.class)
  public ResponseEntity<ErrorResponse> missing(RuntimeException exception) {
    return ResponseEntity.status(404).body(new ErrorResponse("RECIPIENT_NOT_FOUND", "Recipient not found."));
  }
  @ExceptionHandler({IllegalPlanTransitionException.class, InactiveRecipientException.class})
  public ResponseEntity<ErrorResponse> conflict(RuntimeException exception) {
    return ResponseEntity.status(409).body(new ErrorResponse("INVALID_PLAN_TRANSITION", "The saved state changed. Reload before continuing."));
  }
  @ExceptionHandler({ProtectedPlanException.class, InvalidModelOutputException.class})
  public ResponseEntity<ErrorResponse> protectedFields(RuntimeException exception) {
    return ResponseEntity.unprocessableEntity().body(new ErrorResponse("PROTECTED_PLAN_FIELDS", "Protected plan fields cannot be changed by this operation."));
  }
  @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
  public ResponseEntity<ErrorResponse> invalid(Exception exception) {
    return ResponseEntity.unprocessableEntity().body(new ErrorResponse("VALIDATION_ERROR", "Invalid request."));
  }
}
