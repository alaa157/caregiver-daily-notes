package com.caregiver.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class LlmPropertiesTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withInitializer(new ConfigDataApplicationContextInitializer())
      .withUserConfiguration(PropsConfig.class);

  @Configuration
  @EnableConfigurationProperties(LlmProperties.class)
  static class PropsConfig {
  }

  @Test
  void properties_bindFromApplicationYml() {
    runner.run(ctx -> {
      assertThat(ctx).hasSingleBean(LlmProperties.class);
      var props = ctx.getBean(LlmProperties.class);
      assertThat(props.baseUrl()).isEqualTo("https://api-inference.huggingface.co");
      assertThat(props.model()).isEqualTo("HuggingFaceTB/SmolLM2-1.7B-Instruct");
      assertThat(props.timeout()).isEqualTo(Duration.ofSeconds(30));
      assertThat(props.maxAttempts()).isEqualTo(3);
      assertThat(props.backoffBaseMs()).isEqualTo(200);
    });
  }

  @Test
  void properties_defaultApiKeyToEmpty() {
    runner.run(ctx -> {
      var props = ctx.getBean(LlmProperties.class);
      assertThat(props.apiKey()).isEmpty();
    });
  }
}
