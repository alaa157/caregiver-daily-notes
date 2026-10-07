package com.caregiver.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes harness metrics as machine-readable JSON for CI artifacts. The
 * default path sits under the gitignored build dir — reports are generated,
 * never committed.
 */
public final class EvalReport {

  public static final String DEFAULT_PATH = "target/ai-eval/eval-report.json";

  private static final ObjectMapper JSON =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

  private EvalReport() {
  }

  public static void write(EvalMetrics metrics, Path path) {
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("redFlagRecall", metrics.redFlagRecall());
    report.put("schemaValidRate", metrics.schemaValidRate());
    report.put("groundednessRate", metrics.groundednessRate());
    report.put("medicationSafetyRate", metrics.medicationSafetyRate());
    report.put("totalCases", metrics.totalCases());
    report.put("failures", metrics.failures());
    try {
      Files.createDirectories(path.toAbsolutePath().getParent());
      Files.writeString(path, JSON.writeValueAsString(report));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot write eval report to " + path, e);
    }
  }
}
