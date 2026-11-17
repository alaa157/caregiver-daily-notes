package com.caregiver.common;

import com.caregiver.ai.InvalidModelOutputException;
import com.caregiver.auth.BadCredentialsException;
import com.caregiver.auth.DuplicateEmailException;
import com.caregiver.auth.FieldValidationException;
import com.caregiver.auth.InvalidTokenException;
import com.caregiver.auth.ValidationException;
import com.caregiver.medications.InvalidIntakeTimeException;
import com.caregiver.notes.FutureNoteDateException;
import com.caregiver.notes.NoteConflictException;
import com.caregiver.notes.NoteNotFoundException;
import com.caregiver.plans.IllegalPlanTransitionException;
import com.caregiver.plans.PlanNotFoundException;
import com.caregiver.plans.ProtectedPlanException;
import com.caregiver.recipient.InactiveRecipientException;
import com.caregiver.recipient.RecipientNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
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
import org.springframework.web.server.ResponseStatusException;

/**
 * The only MVC exception handler in the application. It applies one status
 * matrix and one {@link ApiError} body to every domain; domain-specific codes
 * are preserved in that shared shape so existing clients keep working.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private static final String INACTIVE_MESSAGE =
      "Archived or legacy-review recipients cannot be changed in ordinary workflows.";

  // 422 — validation -------------------------------------------------------

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
    Map<String, String> fields = new LinkedHashMap<>();
    e.getBindingResult().getFieldErrors()
        .forEach(field -> fields.putIfAbsent(field.getField(), field.getDefaultMessage()));
    String message = fields.isEmpty() ? "Request validation failed."
        : fields.entrySet().iterator().next().getKey() + ": " + fields.values().iterator().next();
    return unprocessable(new ApiError("VALIDATION_ERROR", message, fields));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ApiError> constraint(ConstraintViolationException e) {
    Map<String, String> fields = new LinkedHashMap<>();
    e.getConstraintViolations().forEach(violation ->
        fields.putIfAbsent(violation.getPropertyPath().toString(), violation.getMessage()));
    return unprocessable(new ApiError("VALIDATION_ERROR", "Request validation failed.", fields));
  }

  @ExceptionHandler(DuplicateEmailException.class)
  public ResponseEntity<ApiError> duplicate(DuplicateEmailException e) {
    return unprocessable(new ApiError("DUPLICATE_EMAIL", "Email is already registered."));
  }

  @ExceptionHandler(ValidationException.class)
  public ResponseEntity<ApiError> domainValidation(ValidationException e) {
    return unprocessable(new ApiError("VALIDATION_ERROR", e.getMessage()));
  }

  @ExceptionHandler(FieldValidationException.class)
  public ResponseEntity<ApiError> fieldValidation(FieldValidationException e) {
    return unprocessable(new ApiError("VALIDATION_ERROR", e.getMessage(),
        Map.of(e.field(), e.getMessage())));
  }

  @ExceptionHandler({ProtectedPlanException.class, InvalidModelOutputException.class})
  public ResponseEntity<ApiError> protectedFields(RuntimeException e) {
    return unprocessable(new ApiError("PROTECTED_PLAN_FIELDS",
        "Protected plan fields cannot be changed by this operation."));
  }

  // 400 — malformed or invalid requests ------------------------------------

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException e) {
    return bad(new ApiError("VALIDATION_ERROR", "Malformed request body."));
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ApiError> missingParameter(MissingServletRequestParameterException e) {
    return bad(new ApiError("MISSING_PARAMETER",
        "Required request parameter '" + e.getParameterName() + "' is missing."));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ApiError> invalidParameter(MethodArgumentTypeMismatchException e,
      HttpServletRequest request) {
    String code;
    if ("status".equals(e.getName())) {
      code = "INVALID_LIFECYCLE_STATUS";
    } else if (isDomain(request, "/api/recipients", "/api/medications")) {
      code = "INVALID_ID";
    } else {
      code = "INVALID_PARAMETER";
    }
    return bad(new ApiError(code, "Request parameter '" + e.getName() + "' has an invalid value."));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiError> illegalArgument(IllegalArgumentException e, HttpServletRequest request) {
    String code = isDomain(request, "/api/notes") ? "INVALID_REQUEST" : "VALIDATION_ERROR";
    return bad(new ApiError(code, e.getMessage() == null ? "Request contains invalid values." : e.getMessage()));
  }

  @ExceptionHandler(FutureNoteDateException.class)
  public ResponseEntity<ApiError> futureDate(FutureNoteDateException e) {
    return bad(new ApiError("INVALID_REQUEST", e.getMessage()));
  }

  @ExceptionHandler(InvalidIntakeTimeException.class)
  public ResponseEntity<ApiError> invalidIntakeTime(InvalidIntakeTimeException e) {
    return bad(new ApiError("INVALID_INTAKE_TIME", e.getMessage()));
  }

  // 401 / 403 --------------------------------------------------------------

  @ExceptionHandler(InvalidTokenException.class)
  public ResponseEntity<ApiError> unauthorized(InvalidTokenException e) {
    return status(HttpStatus.UNAUTHORIZED, new ApiError("UNAUTHORIZED", "Authentication required."));
  }

  @ExceptionHandler(BadCredentialsException.class)
  public ResponseEntity<ApiError> credentials(BadCredentialsException e) {
    return status(HttpStatus.UNAUTHORIZED, new ApiError("INVALID_CREDENTIALS", "Invalid email or password."));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiError> forbidden(AccessDeniedException e) {
    return status(HttpStatus.FORBIDDEN, new ApiError("FORBIDDEN", "Access denied."));
  }

  // 404 --------------------------------------------------------------------

  @ExceptionHandler({NoteNotFoundException.class, PlanNotFoundException.class})
  public ResponseEntity<ApiError> notFound(RuntimeException e) {
    return status(HttpStatus.NOT_FOUND, new ApiError("NOT_FOUND", e.getMessage()));
  }

  @ExceptionHandler(RecipientNotFoundException.class)
  public ResponseEntity<ApiError> recipientNotFound(RecipientNotFoundException e) {
    return status(HttpStatus.NOT_FOUND, new ApiError("RECIPIENT_NOT_FOUND", "Recipient not found."));
  }

  @ExceptionHandler({org.springframework.web.servlet.resource.NoResourceFoundException.class,
      org.springframework.web.servlet.NoHandlerFoundException.class})
  public ResponseEntity<ApiError> noRoute(Exception e) {
    return status(HttpStatus.NOT_FOUND, new ApiError("NOT_FOUND", "Resource not found."));
  }

  // 409 --------------------------------------------------------------------

  @ExceptionHandler(InactiveRecipientException.class)
  public ResponseEntity<ApiError> inactive(InactiveRecipientException e) {
    return status(HttpStatus.CONFLICT, new ApiError("INACTIVE_RECIPIENT", INACTIVE_MESSAGE));
  }

  @ExceptionHandler(NoteConflictException.class)
  public ResponseEntity<ApiError> noteConflict(NoteConflictException e) {
    return status(HttpStatus.CONFLICT, new ApiError("NOTE_CONFLICT", e.getMessage()));
  }

  @ExceptionHandler({
      com.caregiver.notes.IdempotencyConflictException.class,
      com.caregiver.recipient.IdempotencyConflictException.class})
  public ResponseEntity<ApiError> idempotencyConflict(RuntimeException e) {
    return status(HttpStatus.CONFLICT, new ApiError("IDEMPOTENCY_CONFLICT", e.getMessage()));
  }

  @ExceptionHandler(IllegalPlanTransitionException.class)
  public ResponseEntity<ApiError> illegalTransition(IllegalPlanTransitionException e) {
    return status(HttpStatus.CONFLICT, new ApiError("INVALID_PLAN_TRANSITION", e.getMessage()));
  }

  @ExceptionHandler(com.caregiver.medications.IntakeAlreadyVoidedException.class)
  public ResponseEntity<ApiError> alreadyVoided(RuntimeException e) {
    return status(HttpStatus.CONFLICT, new ApiError("INTAKE_ALREADY_VOIDED", e.getMessage()));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ApiError> conflict(DataIntegrityViolationException e, HttpServletRequest request) {
    log.warn("Unhandled data integrity violation on {} {}", request.getMethod(), request.getRequestURI());
    String code = isDomain(request, "/api/notes") ? "NOTE_CONFLICT" : "RECIPIENT_CONFLICT";
    String message = isDomain(request, "/api/notes")
        ? "A note for this recipient and date already exists, or an idempotency key was reused."
        : "The recipient could not be created.";
    return status(HttpStatus.CONFLICT, new ApiError(code, message));
  }

  // Domain-raised status (medications) and fallback ------------------------

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ApiError> responseStatus(ResponseStatusException e) {
    HttpStatus status = HttpStatus.valueOf(e.getStatusCode().value());
    String message = e.getReason() == null ? status.getReasonPhrase() : e.getReason();
    String code = switch (status) {
      case NOT_FOUND -> "MEDICATION_NOT_FOUND";
      case CONFLICT -> "MEDICATION_CONFLICT";
      case BAD_REQUEST -> "INVALID_REQUEST";
      default -> "SERVER_ERROR";
    };
    return status(status, new ApiError(code, message));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiError> unexpected(Exception e) {
    log.warn("Unhandled error: {}", e.getClass().getSimpleName());
    return status(HttpStatus.INTERNAL_SERVER_ERROR,
        new ApiError("SERVER_ERROR", "Something went wrong."));
  }

  private static ResponseEntity<ApiError> unprocessable(ApiError error) {
    return status(HttpStatus.UNPROCESSABLE_ENTITY, error);
  }

  private static ResponseEntity<ApiError> bad(ApiError error) {
    return status(HttpStatus.BAD_REQUEST, error);
  }

  private static ResponseEntity<ApiError> status(HttpStatus status, ApiError error) {
    return ResponseEntity.status(status).body(error);
  }

  private static boolean isDomain(HttpServletRequest request, String... prefixes) {
    String uri = request.getRequestURI();
    for (String prefix : prefixes) {
      if (uri.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }
}
