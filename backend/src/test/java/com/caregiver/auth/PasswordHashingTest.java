package com.caregiver.auth;

import com.caregiver.config.SecurityBeans;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordHashingTest {

  private final PasswordEncoder encoder = new SecurityBeans().passwordEncoder();

  @Test
  void samePasswordHashesDifferently() {
    String first = encoder.encode("correct horse battery staple");
    String second = encoder.encode("correct horse battery staple");

    assertThat(first).isNotEqualTo(second);
  }

  @Test
  void matchesVerifiesAndRejects() {
    String hash = encoder.encode("s3cr3t-password");

    assertThat(encoder.matches("s3cr3t-password", hash)).isTrue();
    assertThat(encoder.matches("wrong-password", hash)).isFalse();
  }

  @Test
  void hashNeverContainsPlaintext() {
    String raw = "s3cr3t-password";
    String hash = encoder.encode(raw);

    assertThat(hash).doesNotContain(raw);
    assertThat(hash).isNotEqualTo(raw);
    assertThat(hash).matches("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
  }

  @Test
  void dummyHashVerifiesOnlyItsKnownValue() {
    assertThat(SecurityBeans.DUMMY_HASH).matches("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");
    assertThat(encoder.matches("dummy-password-for-timing-parity", SecurityBeans.DUMMY_HASH))
        .isTrue();
    assertThat(encoder.matches("anything-else", SecurityBeans.DUMMY_HASH)).isFalse();
  }
}
