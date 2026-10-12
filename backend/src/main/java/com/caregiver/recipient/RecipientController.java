package com.caregiver.recipient;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/recipients")
@Tag(name = "Care recipients", description = "Authenticated caregiver-owned recipient management.")
public class RecipientController {

  private final RecipientService service;

  public RecipientController(RecipientService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Create a recipient for the authenticated caregiver")
  public RecipientResponse create(@Valid @RequestBody RecipientRequest request) {
    return service.create(request);
  }

  @GetMapping
  @Operation(summary = "List recipients owned by the authenticated caregiver")
  public List<RecipientResponse> listMine() {
    return service.listMine();
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get an owned recipient by ID")
  public RecipientResponse getMine(@PathVariable UUID id) {
    return service.getMine(id);
  }

  @PutMapping("/{id}")
  @Operation(summary = "Update an active, owned recipient")
  public RecipientResponse update(
      @PathVariable UUID id, @Valid @RequestBody RecipientRequest request) {
    return service.update(id, request);
  }

  @PatchMapping("/{id}/deactivate")
  @Operation(summary = "Deactivate an owned recipient without deleting history")
  public RecipientResponse deactivate(@PathVariable UUID id) {
    return service.deactivate(id);
  }
}
