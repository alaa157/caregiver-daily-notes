package com.caregiver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * {@code llm.*} configuration. Primary provider is Gemini, backup is OpenRouter.
 * Model ids and base URLs are repo defaults (they change with updates); API keys
 * come from the {@code GEMINI_API_KEY} / {@code OPENROUTER_API_KEY} environment
 * variables only and are never committed.
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
    @DefaultValue("https://generativelanguage.googleapis.com/v1beta/openai") String baseUrl,
    @DefaultValue("gemini-3.5-flash-lite") String model,
    @DefaultValue("30s") Duration timeout,
    @DefaultValue("3") int maxAttempts,
    @DefaultValue("200") long backoffBaseMs,
    @DefaultValue("") String apiKey,
    @DefaultValue("10000") int maxTokens,
    Long reasoningMaxTokens,
    @DefaultValue("https://openrouter.ai/api/v1") String backupBaseUrl,
    @DefaultValue("liquid/lfm-2.5-2.6b:free") String backupModel,
    @DefaultValue("") String backupApiKey,
    @DefaultValue("10000") int backupMaxTokens,
    @DefaultValue("500") Long backupReasoningMaxTokens) {
}
