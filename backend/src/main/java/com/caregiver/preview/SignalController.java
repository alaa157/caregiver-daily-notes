package com.caregiver.preview;

import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Preview signals + template summaries. Replaced by #34 and the AI track. */
@RestController
public class SignalController {

  private final SignalService service;

  public SignalController(SignalService service) {
    this.service = service;
  }

  @GetMapping("/api/recipients/{id}/signals")
  public List<SignalDto> signals(
      @PathVariable UUID id,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return service.signals(id, from, to);
  }

  @PostMapping("/api/summaries")
  public SummaryDto summarize(@Valid @RequestBody PreviewSummaryRequest request) {
    return service.summarize(request.recipientId(), request.periodDays());
  }
}
