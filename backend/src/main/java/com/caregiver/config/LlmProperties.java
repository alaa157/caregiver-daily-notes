package com.caregiver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * {@code llm.*} configuration. The provider key comes from the
 * {@code HF_API_KEY} environment variable only and is never committed.
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(
    @DefaultValue("https://api-inference.huggingface.co") String baseUrl,
    @DefaultValue("HuggingFaceTB/SmolLM2-1.7B-Instruct") String model,
    @DefaultValue("30s") Duration timeout,
    @DefaultValue("3") int maxAttempts,
    @DefaultValue("200") long backoffBaseMs,
    @DefaultValue("") String apiKey) {
}
