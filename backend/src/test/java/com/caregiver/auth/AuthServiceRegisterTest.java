package com.caregiver.auth;

import com.caregiver.audit.AuditService;
import com.caregiver.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
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
            Clock.systemUTC()),
        mock(AuditService.class));
  }

  @Test
  void concurrentCollisionMapsToDuplicate() {
    var repository = mock(CaregiverRepository.class);
    when(repository.existsByEmail("race@example.com")).thenReturn(false);
    when(repository.saveAndFlush(any())).thenThrow(dataViolation("uk_caregivers_email"));

    assertThatThrownBy(() -> service(repository).register("race@example.com", "password1234"))
        .isInstanceOf(DuplicateEmailException.class)
        .hasMessage("Email is already registered.");
  }

  @Test
  void unrelatedIntegrityFailureIsRethrown() {
    var repository = mock(CaregiverRepository.class);
    when(repository.existsByEmail("ok@example.com")).thenReturn(false);
    var failure = dataViolation("uk_some_other_constraint");
    when(repository.saveAndFlush(any())).thenThrow(failure);

    assertThatThrownBy(() -> service(repository).register("ok@example.com", "password1234"))
        .isSameAs(failure);
  }

  private static DataIntegrityViolationException dataViolation(String constraintName) {
    var sql = new java.sql.SQLException("integrity failure", "23505");
    var cause = new org.hibernate.exception.ConstraintViolationException(
        "integrity failure", sql, constraintName);
    return new DataIntegrityViolationException("integrity failure", cause);
  }

  @Test
  void passwordBelowMinimumIsRejectedWithTheFieldAndActualMinimum() {
    var repository = mock(CaregiverRepository.class);

    var failure = catchThrowable(() -> service(repository).register("short@example.com", "short"));
    assertThat(failure).isInstanceOf(FieldValidationException.class);
    assertThat(((FieldValidationException) failure).field()).isEqualTo("password");
    assertThat(failure).hasMessageContaining(String.valueOf(AuthService.MIN_PASSWORD_CHARS));
  }

  @Test
  void passwordAtMinimumIsAccepted() {
    var repository = mock(CaregiverRepository.class);
    when(repository.existsByEmail("min@example.com")).thenReturn(false);
    when(repository.saveAndFlush(any())).thenAnswer(invocation -> {
      Caregiver saved = invocation.getArgument(0);
      org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", java.util.UUID.randomUUID());
      return saved;
    });

    var response = service(repository).register("min@example.com", "x".repeat(AuthService.MIN_PASSWORD_CHARS));

    assertThat(response.token()).isNotBlank();
  }

  @Test
  void blankEmailAndPasswordRejected() {
    var repository = mock(CaregiverRepository.class);

    assertThatThrownBy(() -> service(repository).register("   ", "password1234"))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> service(repository).register("ok@example.com", ""))
        .isInstanceOf(ValidationException.class);
    assertThatThrownBy(() -> service(repository).register("not-an-email", "password1234"))
        .isInstanceOf(ValidationException.class);
  }
}
