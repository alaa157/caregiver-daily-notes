package com.caregiver.config;

import com.caregiver.ai.ChatCompletionsLlmClient;
import com.caregiver.ai.FailoverLlmClient;
import com.caregiver.ai.LlmClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;

import static org.assertj.core.api.Assertions.assertThat;

class LlmClientWiringTest {

  private static ApplicationContextRunner runnerWith(String backupApiKey) {
    return new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withUserConfiguration(LlmClientConfig.class)
        .withPropertyValues(
            "llm.base-url=https://primary.example",
            "llm.model=primary-model",
            "llm.backup-base-url=https://backup.example",
            "llm.backup-model=backup-model",
            "llm.backup-api-key=" + backupApiKey);
  }

  @Test
  void llmClientIsFailoverOverChatCompletions() {
    runnerWith("backup-key").run(ctx -> {
      assertThat(ctx).hasSingleBean(LlmClient.class);
      var failover = (FailoverLlmClient) ctx.getBean(LlmClient.class);

      assertThat(failover.primary()).isInstanceOf(ChatCompletionsLlmClient.class);
      assertThat(failover.backup()).isInstanceOf(ChatCompletionsLlmClient.class);
      var primary = (ChatCompletionsLlmClient) failover.primary();
      var backup = (ChatCompletionsLlmClient) failover.backup();
      assertThat(primary.baseUrl()).isEqualTo("https://primary.example");
      assertThat(primary.model()).isEqualTo("primary-model");
      assertThat(backup.baseUrl()).isEqualTo("https://backup.example");
      assertThat(backup.model()).isEqualTo("backup-model");
    });
  }

  @Test
  void blankBackupKeySkipsBackup() {
    for (String blank : new String[] {"", "   "}) {
      runnerWith(blank).run(ctx -> {
        var failover = (FailoverLlmClient) ctx.getBean(LlmClient.class);

        assertThat(failover.primary()).isInstanceOf(ChatCompletionsLlmClient.class);
        assertThat(failover.backup()).isNull();
      });
    }
  }
}
