package com.caregiver.ai;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai/byok")
public class ByokConnectionTestController {
  private final ByokConnectionTestService service;

  public ByokConnectionTestController(ByokConnectionTestService service) {
    this.service = service;
  }

  @PostMapping("/connection-test")
  public ResponseEntity<ByokConnectionTestResponse> test(
      @RequestBody ByokConnectionTestRequest request) {
    if (request.errorCode() != null) {
      return ResponseEntity.badRequest().body(new ByokConnectionTestResponse(
          false, request.providerId(), request.model(), request.errorCode()));
    }

    LlmResult result = service.test(request.credentials());
    String errorCode = errorCode(result);
    return ResponseEntity.ok(new ByokConnectionTestResponse(
        errorCode == null,
        request.providerId(),
        request.model(),
        errorCode));
  }

  /** Maps the provider's failure reason to the codes the settings dialog understands. */
  private static String errorCode(LlmResult result) {
    if (result.status() == LlmStatus.OK || result.status() == LlmStatus.REPAIRED) {
      return null;
    }
    FailureReason reason = result.failureReason();
    if (reason == null) {
      return "provider_unavailable";
    }
    return switch (reason) {
      case INVALID_KEY -> "invalid_key";
      case INVALID_MODEL -> "invalid_model";
      case RATE_LIMIT -> "rate_limit";
      case TIMEOUT, PROVIDER_UNAVAILABLE, INVALID_OUTPUT -> "provider_unavailable";
    };
  }
}
