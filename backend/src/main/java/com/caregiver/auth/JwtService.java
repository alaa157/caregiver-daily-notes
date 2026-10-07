package com.caregiver.auth;

import com.caregiver.config.AuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

/** Issues and validates HS256 JWTs carrying only the caregiver id. */
public class JwtService {
  private final SecretKey key;
  private final AuthProperties properties;
  private final Clock clock;

  public JwtService(AuthProperties properties, Clock clock) {
    byte[] secret = properties.secret().getBytes(StandardCharsets.UTF_8);
    if (secret.length < 32) {
      throw new IllegalArgumentException("auth.jwt.secret must be at least 32 bytes");
    }
    this.key = Keys.hmacShaKeyFor(secret);
    this.properties = properties;
    this.clock = clock;
  }

  public String issue(UUID caregiverId) {
    Date now = Date.from(clock.instant());
    return Jwts.builder()
        .subject(caregiverId.toString())
        .issuedAt(now)
        .expiration(Date.from(clock.instant().plus(properties.expiry())))
        .signWith(key, Jwts.SIG.HS256)
        .compact();
  }

  public JwtPrincipal parse(String token) {
    try {
      Jws<Claims> parsed = Jwts.parser()
          .verifyWith(key)
          .clock(() -> Date.from(clock.instant()))
          .build()
          .parseSignedClaims(token);
      // The signature above already verified, so this header is authenticated:
      // pin the algorithm instead of accepting whatever HMAC variant verifies.
      if (!Jwts.SIG.HS256.getId().equals(parsed.getHeader().getAlgorithm())) {
        throw new InvalidTokenException("Unexpected token algorithm");
      }
      return new JwtPrincipal(UUID.fromString(parsed.getPayload().getSubject()));
    } catch (JwtException | IllegalArgumentException e) {
      throw new InvalidTokenException("Invalid or expired token", e);
    }
  }
}
