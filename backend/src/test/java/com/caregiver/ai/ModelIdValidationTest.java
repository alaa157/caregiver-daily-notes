package com.caregiver.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A model ID is forwarded verbatim to the provider, so the approved shape must
 * be enforced before the value can reach the provider boundary.
 */
class ModelIdValidationTest {
  private static final String OPENAI_BASE_URL = "https://api.openai.com/v1";
  private static final String SYNTHETIC_KEY = "synthetic-model-id-key";

  @ParameterizedTest
  @ValueSource(strings = {
      "gemini-3.8-flash",
      "gemini-3.5-flash-lite",
      "gemini-4-hydrogen",
      "gemini-4-argon",
      "gpt-6-luna",
      "gpt-5.4-mini",
      "grok-4.7",
      "grok-4.3",
      "nvidia/nemotron-3-ultra-550b-a55b:free",
      "nvidia/nemotron-3.5-lightning:free",
      "baidu/ernie-5.5-flash-preview:free",
      "inclusionai/ling-3.2-flash:free"})
  @DisplayName("accepts approved provider model identifiers")
  void acceptsApprovedModelIds(String modelId) {
    assertThat(ProviderCredentials.MODEL_ID_PATTERN.matcher(modelId).matches()).isTrue();

    var credentials = new ProviderCredentials("OPENAI", OPENAI_BASE_URL, modelId, SYNTHETIC_KEY);
    assertThat(credentials.modelId()).isEqualTo(modelId);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "",
      "a b",
      "../x",
      "x?y=1",
      "x#y",
      "-leading-hyphen",
      "/leading-slash",
      "trailing-newline\n"})
  @DisplayName("rejects malformed model identifiers")
  void rejectsMalformedModelIds(String modelId) {
    assertThat(ProviderCredentials.MODEL_ID_PATTERN.matcher(modelId).matches()).isFalse();

    assertThatThrownBy(() -> new ProviderCredentials("OPENAI", OPENAI_BASE_URL, modelId, SYNTHETIC_KEY))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("modelId is invalid");
  }

  @Test
  @DisplayName("rejects a model identifier longer than 128 characters")
  void rejectsOverlongModelId() {
    String overlong = "a".repeat(129);

    assertThat(ProviderCredentials.MODEL_ID_PATTERN.matcher(overlong).matches()).isFalse();
    assertThatThrownBy(() -> new ProviderCredentials("OPENAI", OPENAI_BASE_URL, overlong, SYNTHETIC_KEY))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("modelId is invalid");
  }

  @Test
  @DisplayName("accepts a model identifier at the 128 character limit")
  void acceptsModelIdAtLengthLimit() {
    String atLimit = "a".repeat(128);

    assertThat(ProviderCredentials.MODEL_ID_PATTERN.matcher(atLimit).matches()).isTrue();
    assertThat(new ProviderCredentials("OPENAI", OPENAI_BASE_URL, atLimit, SYNTHETIC_KEY).modelId())
        .isEqualTo(atLimit);
  }

  @Test
  @DisplayName("rejects a null model identifier")
  void rejectsNullModelId() {
    assertThatThrownBy(() -> new ProviderCredentials("OPENAI", OPENAI_BASE_URL, null, SYNTHETIC_KEY))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("modelId is invalid");
  }
}
