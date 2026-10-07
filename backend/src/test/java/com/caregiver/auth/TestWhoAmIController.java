package com.caregiver.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Test-only protected endpoint proving the filter-chain identity. */
@RestController
@RequestMapping("/api/test")
public class TestWhoAmIController {

  @GetMapping("/whoami")
  public Map<String, String> whoami() {
    return Map.of("caregiverId", AuthContext.currentCaregiverId().toString());
  }

  @GetMapping("/boom")
  public Map<String, String> boom() {
    throw new IllegalStateException("sensitive failure: JWT_SECRET=db-password token-abc-123");
  }
}
