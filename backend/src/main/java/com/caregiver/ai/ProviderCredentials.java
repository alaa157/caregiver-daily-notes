package com.caregiver.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import java.net.URI;
import java.util.Objects;

/** In-memory credentials for exactly one OpenAI-compatible provider call. */
@JsonIgnoreType
public final class ProviderCredentials {
  private enum Provider {
    OPENAI("api.openai.com", "/v1", "/v1"),
    GEMINI("generativelanguage.googleapis.com", "", "/v1beta/openai"),
    XAI("api.x.ai", "/v1", "/v1"),
    OPEN_ROUTER("openrouter.ai", "/api/v1", "/api/v1");

    private final String host;
    private final String referencePath;
    private final String chatCompletionsPath;

    Provider(String host, String referencePath, String chatCompletionsPath) {
      this.host = host;
      this.referencePath = referencePath;
      this.chatCompletionsPath = chatCompletionsPath;
    }
  }

  private final Provider provider;
  private final String baseUrl;
  private final String modelId;
  private final String apiKey;

  /**
   * Creates credentials for one call. The supplied URL must be the provider's
   * approved Android-reference base URL; Gemini's OpenAI-compatible path is
   * derived from the backend's existing provider configuration.
   */
  public ProviderCredentials(String providerId, String baseUrl, String modelId, String apiKey) {
    this.provider = parseProvider(providerId);
    this.baseUrl = normalizeAndValidateBaseUrl(this.provider, baseUrl);
    this.modelId = requireNonBlank(modelId, "modelId");
    this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
    validate();
  }

  String providerId() {
    return provider.name();
  }

  String baseUrl() {
    return baseUrl;
  }

  String modelId() {
    return modelId;
  }

  String apiKey() {
    return apiKey;
  }

  /** Backend-compatible base URL; Gemini uses the already configured OpenAI-compatible route. */
  String chatCompletionsBaseUrl() {
    return "https://" + provider.host + provider.chatCompletionsPath;
  }

  /** Re-checks the immutable values before they enter the provider boundary. */
  void validate() {
    if (provider == null) {
      throw new IllegalArgumentException("providerId is unsupported");
    }
    if (!baseUrl.equals(normalizeAndValidateBaseUrl(provider, baseUrl))) {
      throw new IllegalArgumentException("baseUrl is not approved for providerId");
    }
    requireNonBlank(modelId, "modelId");
    Objects.requireNonNull(apiKey, "apiKey");
  }

  @Override
  public String toString() {
    return "ProviderCredentials{providerId=" + provider.name()
        + ", modelId=" + modelId + ", apiKey=[REDACTED]}";
  }

  private static Provider parseProvider(String providerId) {
    if (providerId == null) {
      throw new IllegalArgumentException("providerId is required");
    }
    try {
      return Provider.valueOf(providerId);
    } catch (IllegalArgumentException unsupported) {
      throw new IllegalArgumentException("providerId is unsupported");
    }
  }

  private static String normalizeAndValidateBaseUrl(Provider provider, String suppliedUrl) {
    if (suppliedUrl == null || suppliedUrl.isBlank()) {
      throw new IllegalArgumentException("baseUrl is required");
    }

    String withoutTrailingSlashes = suppliedUrl;
    while (withoutTrailingSlashes.endsWith("/")) {
      withoutTrailingSlashes = withoutTrailingSlashes.substring(0, withoutTrailingSlashes.length() - 1);
    }

    final URI uri;
    try {
      uri = URI.create(withoutTrailingSlashes);
    } catch (IllegalArgumentException malformed) {
      throw new IllegalArgumentException("baseUrl is malformed");
    }

    if (!uri.isAbsolute()
        || !"https".equalsIgnoreCase(uri.getScheme())
        || uri.isOpaque()
        || uri.getHost() == null
        || !provider.host.equalsIgnoreCase(uri.getHost())
        || uri.getUserInfo() != null
        || (uri.getPort() != -1 && uri.getPort() != 443)
        || uri.getRawQuery() != null
        || uri.getRawFragment() != null
        || !provider.referencePath.equals(uri.getPath())) {
      throw new IllegalArgumentException("baseUrl is not approved for providerId");
    }

    return "https://" + provider.host + provider.referencePath;
  }

  private static String requireNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must be non-blank");
    }
    return value;
  }
}
