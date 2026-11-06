package com.caregiver.ai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** One summary request paired with credentials used only for its provider call. */
public final class ByokSummaryRequest {
  @NotNull @Valid
  private final SummaryRequest summary;
  @NotNull @Valid
  private final ProviderCredentials credentials;

  public ByokSummaryRequest(SummaryRequest summary, ProviderCredentials credentials) {
    this.summary = summary;
    this.credentials = credentials;
  }

  @JsonCreator
  private static ByokSummaryRequest fromJson(
      @JsonProperty("summary") SummaryRequest summary,
      @JsonProperty("credentials") JsonNode credentials) {
    if (credentials == null || credentials.isNull()) {
      return new ByokSummaryRequest(summary, null);
    }
    String providerId = text(credentials, "providerId");
    String model = text(credentials, "model");
    String apiKey = text(credentials, "apiKey");
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("apiKey is required");
    }
    return new ByokSummaryRequest(summary,
        new ProviderCredentials(providerId, approvedBaseUrl(providerId), model, apiKey));
  }

  public SummaryRequest summary() {
    return summary;
  }

  public ProviderCredentials credentials() {
    return credentials;
  }

  private static String text(JsonNode object, String field) {
    JsonNode value = object.get(field);
    return value != null && value.isTextual() ? value.textValue() : null;
  }

  private static String approvedBaseUrl(String providerId) {
    if (providerId == null) {
      return null;
    }
    return switch (providerId) {
      case "OPENAI" -> "https://api.openai.com/v1";
      case "GEMINI" -> "https://generativelanguage.googleapis.com";
      case "XAI" -> "https://api.x.ai/v1";
      case "OPEN_ROUTER" -> "https://openrouter.ai/api/v1";
      default -> null;
    };
  }
}
