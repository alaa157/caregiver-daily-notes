package com.caregiver.ai;

import com.caregiver.plans.PlanResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlanGenerationController {
  private final PlanGenerationService service;
  public PlanGenerationController(PlanGenerationService service) { this.service = service; }
  public record Result(PlanResponse plan, boolean aiUnavailable, SafetyEvaluation safety) {}
  @PostMapping(value = "/api/plans/generate", consumes = "application/json", produces = "application/json")
  @Operation(summary = "Generate and validate a suggested plan from owned notes, safety and the last accepted plan")
  public Result generate(@RequestBody SummaryRequest request) { return service.generate(request); }

  @PostMapping(value = "/api/plans/generate/byok", consumes = "application/json", produces = "application/json")
  @Operation(summary = "Generate and validate a suggested plan using request-scoped provider credentials")
  public Result generateByok(@Valid @RequestBody ByokPlanGenerationRequest request) { return service.generate(request); }
}
