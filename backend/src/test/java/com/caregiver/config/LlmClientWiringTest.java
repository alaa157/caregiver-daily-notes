package com.caregiver.config;

import com.caregiver.ai.ChatCompletionsLlmClient;
import com.caregiver.ai.LlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;

import static org.assertj.core.api.Assertions.assertThat;

class LlmClientWiringTest {

  private static ApplicationContextRunner runner() {
    return new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withUserConfiguration(LlmClientConfig.class);
  }

  @Test
  void llmClientIsASingleChatCompletionsClientWithoutConfiguredProvider() {
    runner().run(ctx -> {
      assertThat(ctx).hasSingleBean(LlmClient.class);
      var client = (ChatCompletionsLlmClient) ctx.getBean(LlmClient.class);

      // BYOK-only: no server-configured endpoint or model; request credentials win.
      assertThat(client.baseUrl()).isEmpty();
      assertThat(client.model()).isEmpty();
    });
  }

  @Test
  void llmTunablesBindFromProperties() {
    runner()
        .withPropertyValues(
            "llm.timeout=5s",
            "llm.max-attempts=2",
            "llm.backoff-base-ms=50",
            "llm.max-tokens=512")
        .run(ctx -> {
          var props = ctx.getBean(LlmProperties.class);

          assertThat(props.timeout().toSeconds()).isEqualTo(5);
          assertThat(props.maxAttempts()).isEqualTo(2);
          assertThat(props.backoffBaseMs()).isEqualTo(50);
          assertThat(props.maxTokens()).isEqualTo(512);
        });
  }
}
