package com.caregiver.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * The single error body used by every domain. {@code code} and {@code message}
 * are always present; {@code fields} carries per-field validation messages and
 * {@code traceId} is reserved for future correlation. No timestamps, HTTP
 * status echoes, exception details or request bodies are ever serialized.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(
    String code,
    String message,
    @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, String> fields,
    String traceId) {

  public ApiError(String code, String message) {
    this(code, message, null, null);
  }

  public ApiError(String code, String message, Map<String, String> fields) {
    this(code, message, fields, null);
  }
}
