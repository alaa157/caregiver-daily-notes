package com.caregiver.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitPropertiesTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withUserConfiguration(PropsConfig.class)
      .withPropertyValues(
          "rate-limit.enabled=true",
          "rate-limit.auth-ip-max-requests=7",
          "rate-limit.auth-ip-window=2m",
          "rate-limit.auth-account-max-requests=3",
          "rate-limit.auth-account-window=10m",
          "rate-limit.ai-max-requests=11",
          "rate-limit.ai-window=30s");

  @Configuration
  @EnableConfigurationProperties(RateLimitProperties.class)
  static class PropsConfig {
  }

  @Test
  void bindsAllLimitsWithoutARebuild() {
    runner.run(ctx -> {
      assertThat(ctx).hasSingleBean(RateLimitProperties.class);
      var props = ctx.getBean(RateLimitProperties.class);
      assertThat(props.enabled()).isTrue();
      assertThat(props.authIpMaxRequests()).isEqualTo(7);
      assertThat(props.authIpWindow()).isEqualTo(Duration.ofMinutes(2));
      assertThat(props.authAccountMaxRequests()).isEqualTo(3);
      assertThat(props.authAccountWindow()).isEqualTo(Duration.ofMinutes(10));
      assertThat(props.aiMaxRequests()).isEqualTo(11);
      assertThat(props.aiWindow()).isEqualTo(Duration.ofSeconds(30));
    });
  }

  @Test
  void defaultsArePlaceholderFreeAndUsable() {
    new ApplicationContextRunner()
        .withUserConfiguration(PropsConfig.class)
        .run(ctx -> {
          var props = ctx.getBean(RateLimitProperties.class);
          assertThat(props.enabled()).isTrue();
          assertThat(props.authIpMaxRequests()).isPositive();
          assertThat(props.authAccountMaxRequests()).isPositive();
          assertThat(props.aiMaxRequests()).isPositive();
        });
  }

  @Test
  void rejectsNonPositiveLimits() {
    new ApplicationContextRunner()
        .withUserConfiguration(PropsConfig.class)
        .withPropertyValues("rate-limit.auth-ip-max-requests=0")
        .run(ctx -> assertThat(ctx).hasFailed());

    new ApplicationContextRunner()
        .withUserConfiguration(PropsConfig.class)
        .withPropertyValues("rate-limit.ai-window=0s")
        .run(ctx -> assertThat(ctx).hasFailed());
  }

  @Test
  void canBeDisabledWithoutRemovingTheFilter() {
    new ApplicationContextRunner()
        .withUserConfiguration(PropsConfig.class)
        .withPropertyValues("rate-limit.enabled=false")
        .run(ctx -> assertThat(ctx.getBean(RateLimitProperties.class).enabled()).isFalse());
  }
}
