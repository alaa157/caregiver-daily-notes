package com.caregiver.plans;

import com.caregiver.auth.ErrorResponse;
import com.caregiver.recipient.RecipientNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.caregiver.ai.InvalidModelOutputException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestControllerAdvice(assignableTypes = PlanController.class)
public class PlanExceptionHandler {
  @ExceptionHandler({ProtectedPlanException.class, InvalidModelOutputException.class})
  public ResponseEntity<ErrorResponse> protectedFields(RuntimeException ex) {
    return ResponseEntity.unprocessableEntity().body(new ErrorResponse("PROTECTED_PLAN_FIELDS", "Protected plan fields cannot be changed by this operation."));
  }

  @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class, IllegalArgumentException.class})
  public ResponseEntity<ErrorResponse> invalidRequest(Exception ex) {
    return ResponseEntity.unprocessableEntity().body(new ErrorResponse("VALIDATION_ERROR", "Invalid request."));
  }
  @ExceptionHandler({PlanNotFoundException.class, RecipientNotFoundException.class})
  public ResponseEntity<ErrorResponse> notFound(RuntimeException ex) {
    if (ex instanceof RecipientNotFoundException) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
          .body(new ErrorResponse("RECIPIENT_NOT_FOUND", "Recipient not found."));
    }
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse("NOT_FOUND", "Plan not found."));
  }

  @ExceptionHandler(IllegalPlanTransitionException.class)
  public ResponseEntity<ErrorResponse> illegalTransition(IllegalPlanTransitionException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse("INVALID_PLAN_TRANSITION", ex.getMessage()));
  }
}
