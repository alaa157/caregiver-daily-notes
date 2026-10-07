package com.caregiver.auth;

import org.springframework.http.HttpStatus;

/** Shared error rendering for MVC advice and the security entry point. */
public final class AuthErrorResponseFactory {

  private AuthErrorResponseFactory() {
  }

  public record AuthError(HttpStatus status, ErrorResponse body) {
  }

  public static AuthError validationError(String message) {
    return new AuthError(HttpStatus.UNPROCESSABLE_ENTITY, new ErrorResponse("VALIDATION_ERROR", message));
  }

  public static AuthError duplicateEmail() {
    return new AuthError(
        HttpStatus.UNPROCESSABLE_ENTITY, new ErrorResponse("DUPLICATE_EMAIL", "Email is already registered."));
  }

  public static AuthError invalidCredentials() {
    return new AuthError(
        HttpStatus.UNAUTHORIZED, new ErrorResponse("INVALID_CREDENTIALS", "Invalid email or password."));
  }

  public static AuthError unauthorized() {
    return new AuthError(HttpStatus.UNAUTHORIZED, ErrorResponse.unauthorized());
  }

  public static AuthError serverError() {
    return new AuthError(
        HttpStatus.INTERNAL_SERVER_ERROR, new ErrorResponse("SERVER_ERROR", "Something went wrong."));
  }
}
