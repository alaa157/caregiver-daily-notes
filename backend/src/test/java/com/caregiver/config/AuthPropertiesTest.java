package com.caregiver.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AuthPropertiesTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withInitializer(new ConfigDataApplicationContextInitializer())
      .withUserConfiguration(PropsConfig.class)
      .withPropertyValues(
          "auth.jwt.secret=test-secret-32-bytes-minimum",
          "auth.jwt.expiry=24h");

  @Configuration
  @EnableConfigurationProperties(AuthProperties.class)
  static class PropsConfig {
  }

  @Test
  void properties_bindJwtSettings() {
    runner.run(ctx -> {
      assertThat(ctx).hasSingleBean(AuthProperties.class);
      var props = ctx.getBean(AuthProperties.class);
      assertThat(props.secret()).isEqualTo("test-secret-32-bytes-minimum");
      assertThat(props.expiry()).isEqualTo(Duration.ofHours(24));
    });
  }

  @Test
  void properties_rejectNonPositiveExpiry() {
    var badRunner = new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withUserConfiguration(PropsConfig.class)
        .withPropertyValues(
            "auth.jwt.secret=test-secret-32-bytes-minimum",
            "auth.jwt.expiry=0s");
    badRunner.run(ctx -> assertThat(ctx).hasFailed());
  }
}
