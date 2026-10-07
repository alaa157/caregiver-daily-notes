package com.caregiver.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

/**
 * Downstream (#8+) ownership API: the caregiver id verified from the JWT.
 * Fails closed when no verified principal exists.
 */
public final class AuthContext {

  private AuthContext() {
  }

  public static UUID currentCaregiverId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null && authentication.getPrincipal() instanceof CaregiverPrincipal principal) {
      return principal.id();
    }
    throw new InvalidTokenException("Authentication required.");
  }
}
