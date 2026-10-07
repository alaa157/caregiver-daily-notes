package com.caregiver.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * MVC auth errors, scoped to the auth controllers so other domains keep
 * their own handling. Bodies carry codes and safe messages only — never
 * request bodies, passwords, tokens, secrets, or exception details.
 */
@RestControllerAdvice(basePackageClasses = AuthController.class)
public class AuthExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(AuthExceptionHandler.class);

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

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException e) {
    var error = AuthErrorResponseFactory.validationError("Malformed request body.");
    return ResponseEntity.status(error.status()).body(error.body());
  }

  @ExceptionHandler(DuplicateEmailException.class)
  public ResponseEntity<ErrorResponse> duplicate(DuplicateEmailException e) {
    var error = AuthErrorResponseFactory.duplicateEmail();
    return ResponseEntity.status(error.status()).body(error.body());
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ErrorResponse> credentials(BadCredentialsException e) {
    var error = AuthErrorResponseFactory.invalidCredentials();
    return ResponseEntity.status(error.status()).body(error.body());
  }

  @ExceptionHandler(InvalidTokenException.class)
  public ResponseEntity<ErrorResponse> token(InvalidTokenException e) {
    var error = AuthErrorResponseFactory.unauthorized();
    return ResponseEntity.status(error.status()).body(error.body());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> unexpected(Exception e) {
    log.warn("Unhandled error: {}", e.getClass().getSimpleName());
    var error = AuthErrorResponseFactory.serverError();
    return ResponseEntity.status(error.status()).body(error.body());
  }
}
