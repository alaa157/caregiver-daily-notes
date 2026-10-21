package com.caregiver.notes;

import com.caregiver.auth.InvalidTokenException;
import com.caregiver.recipient.RecipientNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * Machine-readable error mapping for the Daily Notes API.
 *
 * <p>Every response uses the established notes envelope
 * {@code {timestamp, status, code, message}} plus {@code fields} for body
 * validation failures. Authentication failures always surface as 401
 * {@code UNAUTHORIZED} and are never folded into validation errors;
 * cross-caregiver access surfaces as 404 so one caregiver's data is never
 * distinguishable from missing data. Unexpected failures surface as 500
 * {@code SERVER_ERROR} without stack traces or internal details.
 */
@RestControllerAdvice(basePackageClasses = NoteController.class)
public class NoteApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(NoteApiExceptionHandler.class);

  @ExceptionHandler({NoteNotFoundException.class, RecipientNotFoundException.class})
  public ResponseEntity<Map<String, Object>> notFound(RuntimeException e) {
    return error(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> invalid(MethodArgumentNotValidException e) {
    Map<String, String> fields = new LinkedHashMap<>();
    e.getBindingResult().getFieldErrors().forEach(x -> fields.putIfAbsent(x.getField(), x.getDefaultMessage()));
    return ResponseEntity.badRequest().body(Map.of(
        "timestamp", Instant.now().toString(),
        "status", 400,
        "code", "VALIDATION_ERROR",
        "message", "Request validation failed.",
        "fields", fields));
  }

  @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class,
      HttpMessageNotReadableException.class})
  public ResponseEntity<Map<String, Object>> bad(Exception e) {
    return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request contains invalid or inconsistent values.");
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<Map<String, Object>> missingParameter(MissingServletRequestParameterException e) {
    return error(HttpStatus.BAD_REQUEST, "MISSING_PARAMETER",
        "Required request parameter '" + e.getParameterName() + "' is missing.");
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<Map<String, Object>> invalidParameter(MethodArgumentTypeMismatchException e) {
    return error(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER",
        "Request parameter '" + e.getName() + "' has an invalid value.");
  }

  @ExceptionHandler(NoteConflictException.class)
  public ResponseEntity<Map<String, Object>> noteConflict(NoteConflictException e) {
    return error(HttpStatus.CONFLICT, "NOTE_CONFLICT", e.getMessage());
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  public ResponseEntity<Map<String, Object>> idempotencyConflict(IdempotencyConflictException e) {
    return error(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT", e.getMessage());
  }

  /** Fallback for raw constraint violations (e.g. duplicate recipient/date with no key). */
  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<Map<String, Object>> conflict(DataIntegrityViolationException e) {
    log.warn("Unhandled data integrity violation on notes endpoint");
    return error(HttpStatus.CONFLICT, "NOTE_CONFLICT",
        "A note for this recipient and date already exists, or an idempotency key was reused.");
  }

  @ExceptionHandler(InvalidTokenException.class)
  public ResponseEntity<Map<String, Object>> unauthorized(InvalidTokenException e) {
    return error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required.");
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Map<String, Object>> forbidden(AccessDeniedException e) {
    return error(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied.");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> unexpected(Exception e) {
    log.warn("Unhandled notes error: {}", e.getClass().getSimpleName());
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "SERVER_ERROR", "Something went wrong.");
  }

  private ResponseEntity<Map<String, Object>> error(HttpStatus status, String code, String message) {
    return ResponseEntity.status(status).body(Map.of(
        "timestamp", Instant.now().toString(),
        "status", status.value(),
        "code", code,
        "message", message));
  }
}
