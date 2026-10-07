package com.caregiver.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthContextTest {

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void authenticatedPrincipalReturnsId() {
    UUID id = UUID.randomUUID();
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(
            new CaregiverPrincipal(id), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));

    assertThat(AuthContext.currentCaregiverId()).isEqualTo(id);
  }

  @Test
  void unauthenticatedPrincipalThrows() {
    UUID id = UUID.randomUUID();
    // Two-arg constructor leaves authenticated=false even with our principal.
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(new CaregiverPrincipal(id), null));

    assertThatThrownBy(AuthContext::currentCaregiverId)
        .isInstanceOf(InvalidTokenException.class)
        .hasMessage("Authentication required.");
  }

  @Test
  void emptyContextThrows() {
    assertThatThrownBy(AuthContext::currentCaregiverId)
        .isInstanceOf(InvalidTokenException.class)
        .hasMessage("Authentication required.");
  }
}
