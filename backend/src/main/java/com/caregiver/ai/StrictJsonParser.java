package com.caregiver.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Set;

/**
 * Strict model-output parsing. Sanitizes first, then rejects anything that
 * is not exactly the expected shape: unknown properties, invalid enums, and
 * missing required fields all become {@link InvalidModelOutputException}.
 * Rejected output is never trusted.
 */
public final class StrictJsonParser {

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

  private StrictJsonParser() {
  }

  /**
   * Parses sanitized text into a JSON tree. Malformed input throws.
   */
  public static JsonNode parseTree(String raw) {
    try {
      return MAPPER.readTree(ModelSanitizer.strip(raw));
    } catch (JsonProcessingException e) {
      throw new InvalidModelOutputException("Model output is not valid JSON", e);
    }
  }

  /**
   * Parses sanitized text into {@code type} after verifying every
   * {@code requiredFields} entry is present and non-null at the top level.
   */
  public static <T> T parse(String raw, Class<T> type, Set<String> requiredFields) {
    JsonNode tree = parseTree(raw);
    for (String field : requiredFields) {
      if (!tree.has(field) || tree.get(field).isNull()) {
        throw new InvalidModelOutputException("Model output misses required field: " + field);
      }
    }
    try {
      return MAPPER.treeToValue(tree, type);
    } catch (JsonProcessingException e) {
      throw new InvalidModelOutputException("Model output does not match expected shape", e);
    }
  }
}
