package com.caregiver.ai;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/summaries")
@Tag(name = "Summaries", description = "On-demand owned-note summaries with authoritative safety and trends")
public class SummaryController {
  private final SummaryApplicationService service;

  public SummaryController(SummaryApplicationService service) {
    this.service = service;
  }

  @PostMapping(path = "/byok", consumes = "application/json", produces = "application/json")
  @Operation(summary = "Summarize owned notes using request-scoped provider credentials")
  public SummaryResult summarizeByok(@Valid @RequestBody ByokSummaryRequest request) {
    return service.generate(request);
  }
}
