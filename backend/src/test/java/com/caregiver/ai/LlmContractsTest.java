package com.caregiver.ai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmContractsTest {

  @Test
  void request_rejectsBlankSystemAndNullData() {
    assertThatThrownBy(() -> new LlmRequest("  ", "data")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LlmRequest(null, "data")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LlmRequest("sys", null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void result_defaultsNullTextToEmpty() {
    assertThat(new LlmResult(LlmStatus.FALLBACK, null, "AI_UNAVAILABLE").text()).isEmpty();
  }

  @Test
  void result_rejectsNullStatus() {
    assertThatThrownBy(() -> new LlmResult(null, "x", null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void exception_carriesMessageAndCause() {
    var cause = new IllegalStateException("bad");
    var ex = new InvalidModelOutputException("nope", cause);
    assertThat(ex.getMessage()).isEqualTo("nope");
    assertThat(ex.getCause()).isSameAs(cause);
    assertThat(new InvalidModelOutputException("plain").getMessage()).isEqualTo("plain");
  }
}
