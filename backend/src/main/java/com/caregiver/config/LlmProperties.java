package com.caregiver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * {@code llm.*} configuration. There are no server-configured providers: every
 * provider call carries request-scoped BYOK credentials. These properties only
 * tune the shared chat-completions client (timeouts, retries, token budget).
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
    @DefaultValue("30s") Duration timeout,
    @DefaultValue("3") int maxAttempts,
    @DefaultValue("200") long backoffBaseMs,
    @DefaultValue("10000") int maxTokens) {
}
