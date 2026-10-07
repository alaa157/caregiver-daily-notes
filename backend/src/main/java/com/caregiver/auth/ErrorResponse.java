package com.caregiver.auth;

/** API error envelope. Task 8 builds the shared response factory around this shape. */
public record ErrorResponse(String code, String message) {

  public static ErrorResponse unauthorized() {
    return new ErrorResponse("UNAUTHORIZED", "Authentication required.");
  }
}
