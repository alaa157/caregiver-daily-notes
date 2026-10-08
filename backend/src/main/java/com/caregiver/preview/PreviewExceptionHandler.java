package com.caregiver.preview;

import com.caregiver.auth.AuthErrorResponseFactory;
import com.caregiver.auth.DuplicateEmailException;
import com.caregiver.auth.ErrorResponse;
import com.caregiver.auth.ValidationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Preview-only error mapping reusing the auth envelope. Replaced by member issues. */
@RestControllerAdvice(basePackages = "com.caregiver.preview")
public class PreviewExceptionHandler {

  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<ErrorResponse> validation(ValidationException e) {
    var error = AuthErrorResponseFactory.validationError(e.getMessage());
    return ResponseEntity.status(error.status()).body(error.body());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> invalidRequest(MethodArgumentNotValidException e) {
    String message = e.getBindingResult().getFieldErrors().stream()
        .findFirst()
        .map(field -> field.getField() + ": " + field.getDefaultMessage())
        .orElse("Invalid request.");
    var error = AuthErrorResponseFactory.validationError(message);
    return ResponseEntity.status(error.status()).body(error.body());
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> typeMismatch(MethodArgumentTypeMismatchException e) {
    var error = AuthErrorResponseFactory.validationError("Invalid request path.");
    return ResponseEntity.status(error.status()).body(error.body());
  }

  @ExceptionHandler(DuplicateEmailException.class)
  public ResponseEntity<ErrorResponse> duplicate(DuplicateEmailException e) {
    var error = AuthErrorResponseFactory.duplicateEmail();
    return ResponseEntity.status(error.status()).body(error.body());
  }

  @ExceptionHandler(PreviewNotFoundException.class)
  public ResponseEntity<ErrorResponse> notFound(PreviewNotFoundException e) {
    return ResponseEntity.status(404)
        .body(new ErrorResponse("NOT_FOUND", e.getMessage()));
  }

  @ExceptionHandler(PreviewForbiddenException.class)
  public ResponseEntity<ErrorResponse> forbidden(PreviewForbiddenException e) {
    return ResponseEntity.status(403)
        .body(new ErrorResponse("FORBIDDEN", e.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> unexpected(Exception e) {
    var error = AuthErrorResponseFactory.serverError();
    return ResponseEntity.status(error.status()).body(error.body());
  }
}
