package com.caregiver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * {@code rate-limit.*} configuration. Limits are plain numbers and durations
 * so a deployment can tighten or relax them without a rebuild. Defaults are
 * deliberately generous enough for the demo/test suite; production should
 * override them (see application.yml).
 */
@ConfigurationProperties(prefix = "rate-limit")
public record RateLimitProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("200") int authIpMaxRequests,
    @DefaultValue("1m") Duration authIpWindow,
    @DefaultValue("50") int authAccountMaxRequests,
    @DefaultValue("5m") Duration authAccountWindow,
    @DefaultValue("120") int aiMaxRequests,
    @DefaultValue("1m") Duration aiWindow) {

  public RateLimitProperties {
    if (authIpMaxRequests <= 0 || authAccountMaxRequests <= 0 || aiMaxRequests <= 0) {
      throw new IllegalArgumentException("rate-limit capacities must be positive");
    }
    requirePositive(authIpWindow, "rate-limit.auth-ip-window");
    requirePositive(authAccountWindow, "rate-limit.auth-account-window");
    requirePositive(aiWindow, "rate-limit.ai-window");
  }

  private static void requirePositive(Duration duration, String name) {
    if (duration == null || duration.isZero() || duration.isNegative()) {
      throw new IllegalArgumentException(name + " must be positive");
    }
  }
}
