package com.caregiver.auth;

import com.caregiver.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceRegisterTest {

  private AuthService service(CaregiverRepository repository) {
    return new AuthService(
        repository,
        mock(PasswordEncoder.class),
        new JwtService(
            new AuthProperties("test-secret-that-is-definitely-32-bytes-long", Duration.ofHours(24)),
            Clock.systemUTC()));
  }

  @Test
  void concurrentCollisionMapsToDuplicate() {
    var repository = mock(CaregiverRepository.class);
    when(repository.existsByEmail("race@example.com")).thenReturn(false);
    when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));

    assertThatThrownBy(() -> service(repository).register("race@example.com", "password123"))
        .isInstanceOf(DuplicateEmailException.class)
        .hasMessage("Email is already registered.");
  }

  @Test
  void blankEmailAndPasswordRejected() {
    var repository = mock(CaregiverRepository.class);

    assertThatThrownBy(() -> service(repository).register("   ", "password123"))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> service(repository).register("ok@example.com", ""))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> service(repository).register("not-an-email", "password123"))
        .isInstanceOf(ValidationException.class);
  }
}
