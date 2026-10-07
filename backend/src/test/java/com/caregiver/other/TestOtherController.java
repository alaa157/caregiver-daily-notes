package com.caregiver.other;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Test-only stand-in for any non-auth domain. Lives outside
 * {@code com.caregiver.auth} on purpose: the auth advice must not touch it.
 */
@RestController
@RequestMapping("/api/test")
public class TestOtherController {

  @GetMapping("/other-boom")
  public Map<String, String> boom() {
    throw new IllegalStateException("other-domain-failure");
  }
}
