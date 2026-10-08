package com.caregiver.config;

import com.caregiver.ai.FailoverLlmClient;
import com.caregiver.ai.LlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;

import static org.assertj.core.api.Assertions.assertThat;

class LlmClientWiringTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withInitializer(new ConfigDataApplicationContextInitializer())
      .withUserConfiguration(LlmClientConfig.class)
      .withPropertyValues(
          "llm.base-url=https://primary.example",
          "llm.backup-base-url=https://backup.example");

  @Test
  void llmClientIsFailoverOverChatCompletions() {
    runner.run(ctx -> {
      assertThat(ctx).hasSingleBean(LlmClient.class);
      assertThat(ctx.getBean(LlmClient.class)).isInstanceOf(FailoverLlmClient.class);
    });
  }
}
