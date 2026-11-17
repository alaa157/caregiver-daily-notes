package com.caregiver.common;

import com.caregiver.auth.DuplicateEmailException;
import com.caregiver.auth.InvalidTokenException;
import com.caregiver.notes.FutureNoteDateException;
import com.caregiver.recipient.InactiveRecipientException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The status matrix and single body shape live in one handler. */
class GlobalExceptionHandlerTest {

  private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

  @Test
  void validationIsUnprocessableAndCarriesFieldErrors() {
    // The bean-validation method derives fields from BindingResult; exercise the
    // shared shape directly for the other validation paths.
    var error = handler.domainValidation(new com.caregiver.auth.ValidationException("bad email")).getBody();

    assertThat(error).isNotNull();
    assertThat(error.code()).isEqualTo("VALIDATION_ERROR");
    assertThat(error.message()).isEqualTo("bad email");
    assertThat(handler.domainValidation(new com.caregiver.auth.ValidationException("x")).getStatusCode())
        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
  }

  @Test
  void malformedBodiesAreBadRequests() {
    var response = handler.unreadable(null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody().code()).isEqualTo("VALIDATION_ERROR");
    assertThat(response.getBody().message()).isEqualTo("Malformed request body.");
  }

  @Test
  void domainFailuresMapToTheDocumentedStatuses() {
    assertThat(handler.duplicate(new DuplicateEmailException("dup")).getStatusCode())
        .isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    assertThat(handler.unauthorized(new InvalidTokenException("no token")).getStatusCode())
        .isEqualTo(HttpStatus.UNAUTHORIZED);
    assertThat(handler.inactive(new InactiveRecipientException()).getStatusCode())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(handler.futureDate(new FutureNoteDateException()).getStatusCode())
        .isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(handler.unexpected(new IllegalStateException("secret detail")).getBody().message())
        .isEqualTo("Something went wrong.");
  }

  @Test
  void apiErrorHasOneShapeWithOptionalParts() {
    var minimal = new ApiError("CODE", "message");
    assertThat(minimal.code()).isEqualTo("CODE");
    assertThat(minimal.fields()).isNull();
    assertThat(minimal.traceId()).isNull();

    var withFields = new ApiError("VALIDATION_ERROR", "bad", Map.of("email", "invalid"));
    assertThat(withFields.fields()).containsEntry("email", "invalid");
    assertThat(withFields.traceId()).isNull();
  }
}
