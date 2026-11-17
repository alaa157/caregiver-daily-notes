package com.caregiver.ai;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recipients/{recipientId}/safety-signals")
@Tag(name = "Safety signals", description = "Deterministic red flags computed without an AI provider")
public class SafetySignalController {
  private final SafetySignalService service;

  public SafetySignalController(SafetySignalService service) {
    this.service = service;
  }

  @GetMapping(produces = "application/json")
  @Operation(summary = "Evaluate deterministic safety signals for an owned recipient and period; no AI key required")
  public SafetyEvaluation evaluate(
      @PathVariable UUID recipientId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEndInclusive,
      @RequestParam int periodDays) {
    return service.evaluate(recipientId, periodEndInclusive, periodDays);
  }
}
