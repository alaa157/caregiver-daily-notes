package com.caregiver.preview;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Preview plan endpoints. Replaced by #9. */
@RestController
public class PlanController {

  private final PlanService service;

  public PlanController(PlanService service) {
    this.service = service;
  }

  @GetMapping("/api/plans")
  public List<PlanDto> list() {
    return service.list();
  }

  @PostMapping("/api/plans/suggest")
  @ResponseStatus(HttpStatus.CREATED)
  public PlanDto suggest(@Valid @RequestBody SuggestPlanRequest request) {
    return service.suggest(request.recipientId(), request.items());
  }

  @GetMapping("/api/plans/{id}/versions")
  public List<PlanVersionDto> versions(@PathVariable UUID id) {
    return service.versions(id);
  }

  @PostMapping("/api/plans/{id}/versions")
  @ResponseStatus(HttpStatus.CREATED)
  public PlanDto appendVersion(@PathVariable UUID id, @RequestBody AppendVersionRequest request) {
    return service.append(
        id,
        request.status() == null ? "Suggested" : request.status(),
        request.items());
  }

  @PostMapping("/api/plans/{id}/accept")
  public PlanDto accept(@PathVariable UUID id) {
    return service.transition(id, "accept");
  }

  @PostMapping("/api/plans/{id}/edit-accept")
  public PlanDto editAccept(@PathVariable UUID id) {
    return service.transition(id, "edit-accept");
  }

  @PostMapping("/api/plans/{id}/dismiss")
  public PlanDto dismiss(@PathVariable UUID id) {
    return service.transition(id, "dismiss");
  }

  @PostMapping("/api/plans/{id}/archive")
  public PlanDto archive(@PathVariable UUID id) {
    return service.transition(id, "archive");
  }
}
