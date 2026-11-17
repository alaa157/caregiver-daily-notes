package com.caregiver.notes;

import com.caregiver.auth.InvalidTokenException;
import com.caregiver.common.ApiError;
import com.caregiver.common.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct contract tests for the defensive branches of the single shared
 * handler that normal request flows cannot easily reach.
 */
class NoteApiExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  private static MockHttpServletRequest notesRequest() {
    var request = new MockHttpServletRequest("POST", "/api/notes");
    return request;
  }

  @Test
  void integrityFallbackIsConflictWithoutInternals() {
    ResponseEntity<ApiError> response =
        handler.conflict(new DataIntegrityViolationException("constraint [uk_x]"), notesRequest());
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().code()).isEqualTo("NOTE_CONFLICT");
    assertThat(response.getBody().message()).doesNotContain("uk_x");
  }

  @Test
  void authenticationFailureStaysUnauthorized() {
    ResponseEntity<ApiError> response =
        handler.unauthorized(new InvalidTokenException("Authentication required."));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(response.getBody().code()).isEqualTo("UNAUTHORIZED");
  }

  @Test
  void deniedAccessIsForbidden() {
    ResponseEntity<ApiError> response = handler.forbidden(new AccessDeniedException("denied"));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    assertThat(response.getBody().code()).isEqualTo("FORBIDDEN");
  }

  @Test
  void unexpectedFailureIsServerErrorWithoutInternals() {
    ResponseEntity<ApiError> response =
        handler.unexpected(new IllegalStateException("s3cr3t stack detail"));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(response.getBody().code()).isEqualTo("SERVER_ERROR");
    assertThat(response.getBody().message()).doesNotContain("s3cr3t");
  }

  @Test
  void missingAndMismatchedParametersAreMachineReadable() {
    ResponseEntity<ApiError> missing =
        handler.missingParameter(new MissingServletRequestParameterException("from", "LocalDate"));
    assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(missing.getBody().code()).isEqualTo("MISSING_PARAMETER");

    ResponseEntity<ApiError> mismatch = handler.invalidParameter(
        new MethodArgumentTypeMismatchException("x", String.class, "id", null, new IllegalArgumentException()),
        notesRequest());
    assertThat(mismatch.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(mismatch.getBody().code()).isEqualTo("INVALID_PARAMETER");
  }
}
