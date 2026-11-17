package com.caregiver.auth;

import com.caregiver.common.ApiError;
import org.springframework.http.HttpStatus;

/** Shared auth error rendering for the security entry point and JWT filter. */
public final class AuthErrorResponseFactory {

  private AuthErrorResponseFactory() {
  }

  public record AuthError(HttpStatus status, ApiError body) {
  }

  public static AuthError validationError(String message) {
    return new AuthError(HttpStatus.UNPROCESSABLE_ENTITY, new ApiError("VALIDATION_ERROR", message));
  }

  public static AuthError duplicateEmail() {
    return new AuthError(
        HttpStatus.UNPROCESSABLE_ENTITY, new ApiError("DUPLICATE_EMAIL", "Email is already registered."));
  }

  public static AuthError invalidCredentials() {
    return new AuthError(
        HttpStatus.UNAUTHORIZED, new ApiError("INVALID_CREDENTIALS", "Invalid email or password."));
  }

  public static AuthError unauthorized() {
    return new AuthError(HttpStatus.UNAUTHORIZED,
        new ApiError("UNAUTHORIZED", "Authentication required."));
  }

  public static AuthError serverError() {
    return new AuthError(HttpStatus.INTERNAL_SERVER_ERROR,
        new ApiError("SERVER_ERROR", "Something went wrong."));
  }
}
