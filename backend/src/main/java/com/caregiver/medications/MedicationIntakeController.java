package com.caregiver.medications;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recipients/{recipientId}/medications/{medicationId}/intake-events")
@Tag(name = "Medication intake", description = "Explicit caregiver-reported medication intake events")
public class MedicationIntakeController {
  private final MedicationIntakeService service;

  public MedicationIntakeController(MedicationIntakeService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Record an explicit intake event for an owned medication; "
      + "an optional Idempotency-Key makes retries safe")
  public MedicationIntakeResponse create(
      @PathVariable UUID recipientId,
      @PathVariable UUID medicationId,
      @Valid @RequestBody MedicationIntakeRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String key) {
    return service.create(recipientId, medicationId, request, key);
  }

  @GetMapping
  @Operation(summary = "List explicit intake events for an owned medication, newest first, paged")
  public List<MedicationIntakeResponse> list(
      @PathVariable UUID recipientId,
      @PathVariable UUID medicationId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return service.list(recipientId, medicationId, page, size);
  }

  @DeleteMapping("/{eventId}")
  @Operation(summary = "Void an intake event; the original row is retained with provenance")
  public MedicationIntakeResponse voidEvent(
      @PathVariable UUID recipientId,
      @PathVariable UUID medicationId,
      @PathVariable UUID eventId,
      @RequestParam(value = "reason", required = false) String reason) {
    return service.voidEvent(recipientId, medicationId, eventId, reason);
  }
}
