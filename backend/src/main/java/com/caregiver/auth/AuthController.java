package com.caregiver.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Authentication endpoints. Error mapping lands in Task 8. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthService service;

  public AuthController(AuthService service) {
    this.service = service;
  }

  @Operation(summary = "Register a caregiver", security = {})
  @ApiResponse(responseCode = "201", description = "Registered")
  @ApiResponse(
      responseCode = "422",
      description = "Validation or duplicate email",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
    return service.register(request.email(), request.password());
  }

  @Operation(summary = "Log in a caregiver", security = {})
  @ApiResponse(responseCode = "200", description = "Authenticated")
  @ApiResponse(
      responseCode = "401",
      description = "Invalid credentials",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "422",
      description = "Validation failure",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody LoginRequest request) {
    return service.login(request.email(), request.password());
  }
}
