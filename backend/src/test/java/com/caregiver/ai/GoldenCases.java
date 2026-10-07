package com.caregiver.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Loads the reviewable golden dataset from {@code docs/ai/golden/}. The repo
 * root is resolved by walking up from the working directory (surefire runs
 * with the {@code backend/} module dir); a missing dataset fails fast.
 */
public final class GoldenCases {

  private static final ObjectMapper JSON = new ObjectMapper();

  private GoldenCases() {
  }

  public static List<GoldenEvalCase> load() {
    Path dir = findGoldenDir();
    List<GoldenEvalCase> cases = new ArrayList<>();
    try (Stream<Path> files = Files.list(dir)) {
      List<Path> json = files.filter(p -> p.toString().endsWith(".json")).sorted().toList();
      for (Path file : json) {
        cases.addAll(JSON.readValue(Files.readString(file), new TypeReference<List<GoldenEvalCase>>() {
        }));
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot load golden cases from " + dir, e);
    }
    cases.sort(Comparator.comparing(GoldenEvalCase::id));
    return List.copyOf(cases);
  }

  static Path findGoldenDir() {
    Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    for (Path p = dir; p != null; p = p.getParent()) {
      if (Files.isDirectory(p.resolve("docs/ai/golden"))) {
        return p.resolve("docs/ai/golden");
      }
    }
    throw new IllegalStateException("docs/ai/golden not found above " + dir);
  }
}
