package com.caregiver.preview;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Preview recipient endpoints. Replaced by #8. */
@RestController
@RequestMapping("/api/recipients")
public class RecipientController {

  private final RecipientService service;

  public RecipientController(RecipientService service) {
    this.service = service;
  }

  @GetMapping
  public List<RecipientDto> list() {
    return service.list();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public RecipientDto create(@Valid @RequestBody CreateRecipientRequest request) {
    return service.create(request.name());
  }

  @GetMapping("/{id}")
  public RecipientDto get(@PathVariable UUID id) {
    return service.get(id);
  }

  @PutMapping("/{id}")
  public RecipientDto update(@PathVariable UUID id, @Valid @RequestBody UpdateRecipientRequest request) {
    return service.update(id, request.active());
  }
}
