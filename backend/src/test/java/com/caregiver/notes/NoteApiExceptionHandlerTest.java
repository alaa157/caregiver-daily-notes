package com.caregiver.notes;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.caregiver.auth.InvalidTokenException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct contract tests for defensive handler branches that normal
 * request flows cannot reach (raw integrity violations, broken
 * authentication, denied access, unexpected failures).
 */
class NoteApiExceptionHandlerTest {

  private final NoteApiExceptionHandler handler = new NoteApiExceptionHandler();

  @Test
  void integrityFallbackIsConflictWithoutInternals() {
    ResponseEntity<Map<String, Object>> response =
        handler.conflict(new DataIntegrityViolationException("constraint [uk_x]"));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).containsEntry("status", 409);
    assertThat(response.getBody()).containsEntry("code", "NOTE_CONFLICT");
    assertThat(response.getBody().get("message").toString()).doesNotContain("uk_x");
  }

  @Test
  void authenticationFailureStaysUnauthorized() {
    ResponseEntity<Map<String, Object>> response =
        handler.unauthorized(new InvalidTokenException("Authentication required."));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody()).containsEntry("code", "UNAUTHORIZED");
  }

  @Test
  void deniedAccessIsForbidden() {
    ResponseEntity<Map<String, Object>> response =
        handler.forbidden(new AccessDeniedException("denied"));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody()).containsEntry("code", "FORBIDDEN");
  }

  @Test
  void unexpectedFailureIsServerErrorWithoutInternals() {
    ResponseEntity<Map<String, Object>> response =
        handler.unexpected(new IllegalStateException("s3cr3t stack detail"));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody()).containsEntry("code", "SERVER_ERROR");
    assertThat(response.getBody().get("message").toString()).doesNotContain("s3cr3t");
  }

  @Test
  void missingAndMismatchedParametersAreMachineReadable() {
    ResponseEntity<Map<String, Object>> missing =
        handler.missingParameter(new MissingServletRequestParameterException("from", "LocalDate"));
    assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(missing.getBody()).containsEntry("code", "MISSING_PARAMETER");

    ResponseEntity<Map<String, Object>> mismatch = handler.invalidParameter(
        new MethodArgumentTypeMismatchException("x", String.class, "id", null, new IllegalArgumentException()));
    assertThat(mismatch.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(mismatch.getBody()).containsEntry("code", "INVALID_PARAMETER");
  }
}
