package com.caregiver.medications;

import com.caregiver.auth.ErrorResponse;
import com.caregiver.auth.InvalidTokenException;
import com.caregiver.recipient.InactiveRecipientException;
import com.caregiver.recipient.RecipientNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/recipients/{recipientId}/medications")
@Tag(name = "Medications", description = "Authenticated caregiver-maintained medication records")
public class MedicationController {
  private final MedicationService service;

  public MedicationController(MedicationService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a medication record for an owned recipient")
  public MedicationResponse create(@PathVariable UUID recipientId,
      @Valid @RequestBody MedicationRequest request) {
    return service.create(recipientId, request);
  }

  @GetMapping
  @Operation(summary = "List all medication records for an owned recipient")
  public List<MedicationResponse> list(@PathVariable UUID recipientId) {
    return service.list(recipientId);
  }

  @GetMapping("/{medicationId}")
  @Operation(summary = "Get an owned medication record")
  public MedicationResponse get(@PathVariable UUID recipientId, @PathVariable UUID medicationId) {
    return service.get(recipientId, medicationId);
  }

  @PutMapping("/{medicationId}")
  @Operation(summary = "Replace caregiver-entered medication details")
  public MedicationResponse update(@PathVariable UUID recipientId, @PathVariable UUID medicationId,
      @Valid @RequestBody MedicationRequest request) {
    return service.update(recipientId, medicationId, request);
  }

  @PatchMapping("/{medicationId}/archive")
  @Operation(summary = "Archive a medication record without deleting its history")
  public MedicationResponse archive(@PathVariable UUID recipientId, @PathVariable UUID medicationId) {
    return service.archive(recipientId, medicationId);
  }

  @ExceptionHandler(RecipientNotFoundException.class)
  public ResponseEntity<ErrorResponse> recipientNotFound(RecipientNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(new ErrorResponse("RECIPIENT_NOT_FOUND", "Recipient not found."));
  }

  @ExceptionHandler(InactiveRecipientException.class)
  public ResponseEntity<ErrorResponse> inactiveRecipient(InactiveRecipientException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse("INACTIVE_RECIPIENT",
            "Archived or legacy-review recipients cannot be changed in ordinary workflows."));
  }

  @ExceptionHandler(InvalidTokenException.class)
  public ResponseEntity<ErrorResponse> unauthorized(InvalidTokenException exception) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.unauthorized());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> invalidRequest(MethodArgumentNotValidException exception) {
    String message = exception.getBindingResult().getFieldErrors().stream().findFirst()
        .map(error -> error.getField() + ": " + error.getDefaultMessage()).orElse("Invalid request.");
    return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", message));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException exception) {
    return ResponseEntity.badRequest()
        .body(new ErrorResponse("VALIDATION_ERROR", "Malformed request body."));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> invalidId(MethodArgumentTypeMismatchException exception) {
    return ResponseEntity.badRequest().body(new ErrorResponse("INVALID_ID", "IDs must be valid UUIDs."));
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ErrorResponse> medicationFailure(ResponseStatusException exception) {
    HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
    String message = exception.getReason() == null ? status.getReasonPhrase() : exception.getReason();
    String code = status == HttpStatus.NOT_FOUND ? "MEDICATION_NOT_FOUND"
        : status == HttpStatus.CONFLICT ? "MEDICATION_CONFLICT" : "MEDICATION_ERROR";
    return ResponseEntity.status(status).body(new ErrorResponse(code, message));
  }
}
