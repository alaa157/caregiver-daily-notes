package com.caregiver.ai;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Request-only credentials for a fixed provider connection probe. */
public final class ByokConnectionTestRequest {
  private final ProviderCredentials credentials;
  private final String providerId;
  private final String model;
  private final String errorCode;

  @JsonCreator
  public ByokConnectionTestRequest(@JsonProperty("credentials") CredentialInput input) {
    ProviderCredentials parsed = null;
    String acceptedProviderId = null;
    String acceptedModel = null;
    String validationError;

    if (input == null) {
      validationError = "invalid_provider";
    } else {
      String baseUrl = baseUrlFor(input.providerId);
      if (baseUrl == null) {
        validationError = "invalid_provider";
      } else {
        acceptedProviderId = input.providerId;
        if (input.model == null || input.model.isBlank()) {
          validationError = "invalid_model";
        } else {
          acceptedModel = input.model;
          if (input.apiKey == null || input.apiKey.isBlank()) {
            validationError = "invalid_key";
          } else {
            try {
              parsed = new ProviderCredentials(input.providerId, baseUrl, input.model, input.apiKey);
              validationError = null;
            } catch (IllegalArgumentException invalidCredentials) {
              validationError = "invalid_provider";
            }
          }
        }
      }
    }

    this.credentials = parsed;
    this.providerId = acceptedProviderId;
    this.model = acceptedModel;
    this.errorCode = validationError;
  }

  @JsonIgnore
  ProviderCredentials credentials() {
    return credentials;
  }

  String providerId() {
    return providerId;
  }

  String model() {
    return model;
  }

  String errorCode() {
    return errorCode;
  }

  private static String baseUrlFor(String providerId) {
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

  public static final class CredentialInput {
    private final String providerId;
    private final String model;
    private final String apiKey;

    @JsonCreator
    public CredentialInput(
        @JsonProperty("providerId") String providerId,
        @JsonProperty("model") String model,
        @JsonProperty("apiKey") String apiKey) {
      this.providerId = providerId;
      this.model = model;
      this.apiKey = apiKey;
    }
  }
}
