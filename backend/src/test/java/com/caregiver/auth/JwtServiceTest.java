package com.caregiver.auth;

import com.caregiver.config.AuthProperties;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
  private static final String SECRET = "test-secret-that-is-definitely-32-bytes-long";

  private static JwtService service(Clock clock) {
    return new JwtService(new AuthProperties(SECRET, Duration.ofHours(24)), clock);
  }

  @Test
  void issueThenParseRoundtrip() {
    UUID id = UUID.randomUUID();

    JwtPrincipal principal = service(Clock.fixed(NOW, ZoneOffset.UTC)).parse(
        service(Clock.fixed(NOW, ZoneOffset.UTC)).issue(id));

    assertThat(principal.caregiverId()).isEqualTo(id);
  }

  @Test
  void tokenCarriesExpiryButNoEmail() {
    UUID id = UUID.randomUUID();
    String token = service(Clock.fixed(NOW, ZoneOffset.UTC)).issue(id);
    String payload = new String(
        Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);

    assertThat(payload).contains("\"exp\":" + NOW.plus(Duration.ofHours(24)).getEpochSecond());
    assertThat(payload).doesNotContain("email");
  }

  @Test
  void tamperedTokenThrows() {
    String token = service(Clock.fixed(NOW, ZoneOffset.UTC)).issue(UUID.randomUUID());
    String tampered = token.substring(0, token.length() - 2) + "xx";

    assertThatThrownBy(() -> service(Clock.fixed(NOW, ZoneOffset.UTC)).parse(tampered))
        .isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void wrongSecretThrows() {
    String token = service(Clock.fixed(NOW, ZoneOffset.UTC)).issue(UUID.randomUUID());
    var other = new JwtService(
        new AuthProperties("a-different-secret-that-is-also-long-enough", Duration.ofHours(24)),
        Clock.fixed(NOW, ZoneOffset.UTC));

    assertThatThrownBy(() -> other.parse(token)).isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void expiredTokenThrows() {
    var issuer = service(Clock.fixed(NOW, ZoneOffset.UTC));
    String token = issuer.issue(UUID.randomUUID());
    var later = service(Clock.fixed(NOW.plus(Duration.ofHours(25)), ZoneOffset.UTC));

    assertThatThrownBy(() -> later.parse(token)).isInstanceOf(InvalidTokenException.class);
  }

  @Test
  void shortSecretFailsConstruction() {
    assertThatThrownBy(() -> new JwtService(
        new AuthProperties("too-short", Duration.ofHours(24)), Clock.systemUTC()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
