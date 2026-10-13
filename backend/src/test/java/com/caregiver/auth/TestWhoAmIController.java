package com.caregiver.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;

/** Test-only protected endpoint proving the filter-chain identity. */
@RestController
@RequestMapping("/api/test")
public class TestWhoAmIController {

  @GetMapping("/whoami")
  public Map<String, Object> whoami() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    return Map.of(
        "caregiverId", AuthContext.currentCaregiverId().toString(),
        "authorities", authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .toList());
  }

  @GetMapping("/boom")
  public Map<String, String> boom() {
    throw new IllegalStateException("sensitive failure: JWT_SECRET=db-password token-abc-123");
  }
}
