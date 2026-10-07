package com.caregiver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code auth.jwt.*} configuration. The signing secret comes from the
 * {@code JWT_SECRET} environment variable only and is never committed.
 */
@ConfigurationProperties(prefix = "auth.jwt")
public record AuthProperties(String secret, Duration expiry) {

  public AuthProperties {
    if (expiry == null || expiry.isZero() || expiry.isNegative()) {
      throw new IllegalArgumentException("auth.jwt.expiry must be positive");
    }
  }
}
