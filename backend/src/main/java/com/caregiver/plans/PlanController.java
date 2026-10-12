package com.caregiver.plans;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/recipients/{recipientId}/plans")
@Tag(name = "Care plans", description = "Caregiver-owned immutable plan versions and lifecycle actions")
public class PlanController {
  private final PlanService service;

  public PlanController(PlanService service) { this.service = service; }

  @PostMapping("/suggestions")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Persist a suggested plan version")
  public PlanResponse suggest(@PathVariable UUID recipientId, @Valid @RequestBody PlanRequest request) {
    return service.saveSuggested(recipientId, request);
  }

  @GetMapping("/active")
  @Operation(summary = "Get the active accepted plan")
  public PlanResponse active(@PathVariable UUID recipientId) {
    return service.activeAccepted(recipientId);
  }

  @GetMapping("/history")
  @Operation(summary = "Get all plan versions, newest first")
  public List<PlanResponse> history(@PathVariable UUID recipientId) {
    return service.history(recipientId);
  }

  @PostMapping("/{planId}/accept")
  @Operation(summary = "Accept a suggested plan version")
  public PlanResponse accept(@PathVariable UUID recipientId, @PathVariable UUID planId) {
    return service.accept(recipientId, planId);
  }

  @PostMapping("/{planId}/edit-and-accept")
  @Operation(summary = "Preserve the suggestion and create an edited accepted version")
  public PlanResponse editAndAccept(@PathVariable UUID recipientId, @PathVariable UUID planId,
      @Valid @RequestBody EditPlanRequest request) {
    return service.editAndAccept(recipientId, planId, request);
  }

  @PostMapping("/{planId}/dismiss")
  @Operation(summary = "Dismiss a suggested plan version")
  public PlanResponse dismiss(@PathVariable UUID recipientId, @PathVariable UUID planId) {
    return service.dismiss(recipientId, planId);
  }
}
