package com.caregiver.auth;

import com.caregiver.config.AuthProperties;
import com.caregiver.config.SecurityBeans;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceLoginTest {

  private static JwtService jwtService() {
    // Concrete classes cannot be Mockito-mocked on this JDK (byte-buddy
    // instrumentation fails), so use a real instance with a fixed clock.
    return new JwtService(
        new AuthProperties("test-secret-that-is-definitely-32-bytes-long", Duration.ofHours(24)),
        Clock.systemUTC());
  }
  @Test
  void unknownEmailThrowsAndVerifiesAgainstDummyHash() {
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    when(encoder.matches(any(), eq(SecurityBeans.DUMMY_HASH))).thenReturn(false);
    var repository = mock(CaregiverRepository.class);
    when(repository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
    var service = new AuthService(repository, encoder, jwtService());

    assertThatThrownBy(() -> service.login("ghost@example.com", "whatever"))
        .isInstanceOf(BadCredentialsException.class);
    verify(encoder).matches("whatever", SecurityBeans.DUMMY_HASH);
  }

  @Test
  void sentinelPasswordWithUnknownEmailStillRejected() {
    // The dummy hash matches this sentinel by design; the login must still fail
    // with BadCredentialsException (never NoSuchElementException), and the
    // dummy matches() call must still happen for timing parity.
    PasswordEncoder encoder = new BCryptPasswordEncoder();
    var repository = mock(CaregiverRepository.class);
    when(repository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
    var service = new AuthService(repository, encoder, jwtService());

    assertThatThrownBy(
            () -> service.login("ghost@example.com", "dummy-password-for-timing-parity"))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessage("Invalid email or password.");
  }

  @Test
  void wrongPasswordThrowsSameException() {
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    var caregiver = new Caregiver();
    caregiver.setEmail("sam@example.com");
    caregiver.setPasswordHash("stored-hash");
    var repository = mock(CaregiverRepository.class);
    when(repository.findByEmail("sam@example.com")).thenReturn(Optional.of(caregiver));
    when(encoder.matches("wrong", "stored-hash")).thenReturn(false);
    var service = new AuthService(repository, encoder, jwtService());

    assertThatThrownBy(() -> service.login("sam@example.com", "wrong"))
        .isInstanceOf(BadCredentialsException.class)
        .hasMessage("Invalid email or password.");
  }
}
