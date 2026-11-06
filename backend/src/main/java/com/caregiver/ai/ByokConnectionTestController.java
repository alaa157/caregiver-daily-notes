package com.caregiver.ai;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
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

    boolean success = service.test(request.credentials());
    return ResponseEntity.ok(new ByokConnectionTestResponse(
        success,
        request.providerId(),
        request.model(),
        success ? null : "provider_unavailable"));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ByokConnectionTestResponse> malformedRequest() {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
        new ByokConnectionTestResponse(false, null, null, "invalid_provider"));
  }
}
