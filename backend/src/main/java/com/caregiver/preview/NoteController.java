package com.caregiver.preview;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Preview notes, addenda, and history endpoints. Replaced by #30–#32. */
@RestController
@RequestMapping("/api/notes")
public class NoteController {

  private final NoteService service;

  public NoteController(NoteService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public NoteDto create(@Valid @RequestBody CreateNoteRequest request) {
    return service.create(request);
  }

  @GetMapping("/{id}")
  public NoteDetailDto detail(@PathVariable UUID id) {
    return service.detail(id);
  }

  @PostMapping("/{id}/addenda")
  @ResponseStatus(HttpStatus.CREATED)
  public AddendumDto append(@PathVariable UUID id, @Valid @RequestBody CreateAddendumRequest request) {
    return service.append(id, request.text());
  }

  @GetMapping
  public List<NoteDto> history(
      @RequestParam(required = false) UUID recipientId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return service.history(recipientId, from, to);
  }
}
