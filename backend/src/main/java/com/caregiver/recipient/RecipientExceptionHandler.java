package com.caregiver.recipient;

import com.caregiver.auth.ErrorResponse;
import com.caregiver.auth.InvalidTokenException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackageClasses = RecipientController.class)
public class RecipientExceptionHandler {
  @ExceptionHandler(RecipientNotFoundException.class)
  public ResponseEntity<ErrorResponse> notFound(RecipientNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse("RECIPIENT_NOT_FOUND", "Recipient not found."));
  }

  @ExceptionHandler(InactiveRecipientException.class)
  public ResponseEntity<ErrorResponse> inactive(InactiveRecipientException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse("INACTIVE_RECIPIENT", "Archived or legacy-review recipients cannot be changed in ordinary workflows."));
  }

  @ExceptionHandler(InvalidTokenException.class)
  public ResponseEntity<ErrorResponse> unauthorized(InvalidTokenException exception) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.unauthorized());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> invalidRequest(MethodArgumentNotValidException exception) {
    String message = exception.getBindingResult().getFieldErrors().stream().findFirst()
        .map(error -> error.getField() + ": " + error.getDefaultMessage()).orElse("Invalid request.");
    return ResponseEntity.unprocessableEntity().body(new ErrorResponse("VALIDATION_ERROR", message));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException exception) {
    return ResponseEntity.unprocessableEntity().body(new ErrorResponse("VALIDATION_ERROR", "Malformed request body."));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> invalidParameter(MethodArgumentTypeMismatchException exception) {
    if ("status".equals(exception.getName())) {
      return ResponseEntity.badRequest()
          .body(new ErrorResponse("INVALID_LIFECYCLE_STATUS", "Recipient lifecycle status is invalid."));
    }
    return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_ID", "Recipient ID must be a valid UUID."));
  }
}
