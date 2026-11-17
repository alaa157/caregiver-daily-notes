package com.caregiver.medications;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

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
}
