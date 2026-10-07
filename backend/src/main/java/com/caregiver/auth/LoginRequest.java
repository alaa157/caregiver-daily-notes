package com.caregiver.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record LoginRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank String password) {

  // Normalize before bean validation sees the value, so padded but valid
  // addresses pass @Email. Null is preserved for @NotBlank to report.
  public LoginRequest {
    if (email != null) {
      email = email.trim().toLowerCase(Locale.ROOT);
    }
  }
}
