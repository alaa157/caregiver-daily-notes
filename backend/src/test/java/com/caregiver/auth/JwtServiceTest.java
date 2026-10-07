package com.caregiver.auth;

import com.caregiver.config.AuthProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
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

  @Test
  void issuedTokensAlwaysUseHS256() {
    for (int bytes : new int[] {32, 48, 64}) {
      var svc = new JwtService(
          new AuthProperties("s".repeat(bytes), Duration.ofHours(24)),
          Clock.fixed(NOW, ZoneOffset.UTC));
      UUID id = UUID.randomUUID();

      String token = svc.issue(id);
      String header = new String(
          Base64.getUrlDecoder().decode(token.split("\\.")[0]), StandardCharsets.UTF_8);

      assertThat(header).contains("\"alg\":\"HS256\"");
      assertThat(svc.parse(token).caregiverId()).isEqualTo(id);
    }
  }

  @Test
  void nonHS256TokensRejected() {
    // A 64-byte key could verify HS384/HS512 — the parser must still refuse them.
    String secret = "s".repeat(64);
    var svc = new JwtService(
        new AuthProperties(secret, Duration.ofHours(24)),
        Clock.fixed(NOW, ZoneOffset.UTC));
    var key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    UUID id = UUID.randomUUID();
    String hs384 = Jwts.builder()
        .subject(id.toString())
        .issuedAt(Date.from(NOW))
        .expiration(Date.from(NOW.plus(Duration.ofHours(1))))
        .signWith(key, Jwts.SIG.HS384)
        .compact();
    String hs512 = Jwts.builder()
        .subject(id.toString())
        .issuedAt(Date.from(NOW))
        .expiration(Date.from(NOW.plus(Duration.ofHours(1))))
        .signWith(key, Jwts.SIG.HS512)
        .compact();

    assertThatThrownBy(() -> svc.parse(hs384)).isInstanceOf(InvalidTokenException.class);
    assertThatThrownBy(() -> svc.parse(hs512)).isInstanceOf(InvalidTokenException.class);
  }
}
