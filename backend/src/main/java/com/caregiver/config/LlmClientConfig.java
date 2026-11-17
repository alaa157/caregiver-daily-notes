package com.caregiver.config;

import com.caregiver.ai.ChatCompletionsLlmClient;
import com.caregiver.ai.HttpExchange;
import com.caregiver.ai.LlmClient;
import com.caregiver.ai.Sleeper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Production LLM wiring: one chat-completions client with no configured
 * provider. Every request must carry request-scoped BYOK credentials; calls
 * without them fail closed. No test touches the network (beans only
 * construct the clients).
 */
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmClientConfig {

  @Bean
  public HttpExchange httpExchange() {
    // Reuse the single exchange implementation: it throws ProviderHttpException
    // for non-2xx responses so the client can classify the failure. A local
    // copy that threw a plain IOException made every status look like an outage.
    return HttpExchange.defaultExchange();
  }

  @Bean
  public Sleeper sleeper() {
    return Thread::sleep;
  }

  @Bean
  public LlmClient llmClient(LlmProperties props, HttpExchange exchange, Sleeper sleeper) {
    return new ChatCompletionsLlmClient(
        "",
        "",
        "",
        props.maxTokens(),
        null,
        props.timeout(),
        props.maxAttempts(),
        props.backoffBaseMs(),
        exchange,
        sleeper);
  }
}
