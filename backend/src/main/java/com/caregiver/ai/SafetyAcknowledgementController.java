package com.caregiver.ai;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
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

@RestController
@RequestMapping("/api/recipients/{recipientId}/safety-acknowledgements")
@Tag(name = "Safety acknowledgements", description = "Caregiver-owned safety review metadata")
public class SafetyAcknowledgementController {
  private final SafetyAcknowledgementService service;

  public SafetyAcknowledgementController(SafetyAcknowledgementService service) {
    this.service = service;
  }

  @PostMapping(consumes = "application/json", produces = "application/json")
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Record a caregiver acknowledgement for a safety rule and period")
  public SafetyAcknowledgementResponse acknowledge(
      @PathVariable UUID recipientId,
      @RequestBody SafetyAcknowledgementRequest request) {
    return service.acknowledge(recipientId, request);
  }

  @GetMapping(produces = "application/json")
  @Operation(summary = "List caregiver acknowledgements for a safety period")
  public List<SafetyAcknowledgementResponse> list(
      @PathVariable UUID recipientId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEndInclusive,
      @RequestParam int periodDays) {
    return service.list(recipientId, periodEndInclusive, periodDays);
  }
}
