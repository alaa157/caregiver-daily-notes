package com.caregiver.ai;

import com.caregiver.auth.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/summaries")
@Tag(name = "Summaries", description = "On-demand owned-note summaries with authoritative safety and trends")
public class SummaryController {
  private final SummaryApplicationService service;
  public SummaryController(SummaryApplicationService service) { this.service = service; }
  @PostMapping(consumes = "application/json", produces = "application/json")
  @Operation(summary = "Summarize an owned recipient's inclusive 7, 14 or 30-day note period")
  public SummaryResult summarize(@RequestBody SummaryRequest request) { return service.summarize(request); }
  @PostMapping(path = "/byok", consumes = "application/json", produces = "application/json")
  @Operation(summary = "Summarize owned notes using request-scoped provider credentials")
  public SummaryResult summarizeByok(@Valid @RequestBody ByokSummaryRequest request) {
    return service.generate(request);
  }
  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> invalidByokBody(HttpMessageNotReadableException exception,
      HttpServletRequest request) {
    if (request.getRequestURI().equals("/api/summaries/byok")) {
      return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", "Invalid request."));
    }
    return ResponseEntity.unprocessableEntity().body(new ErrorResponse("VALIDATION_ERROR", "Invalid request."));
  }
}
