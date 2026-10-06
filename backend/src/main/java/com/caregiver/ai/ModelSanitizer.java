package com.caregiver.ai;

import java.util.regex.Pattern;

/**
 * Strips model-output wrappers before parsing: chain-of-thought
 * {@code <think>} blocks and triple-backtick code fences. Pure function —
 * prompt-injection text in the content itself is left untouched for the
 * parser to reject on shape, never interpreted here.
 */
public final class ModelSanitizer {

  private static final Pattern THINK_BLOCK =
      Pattern.compile("<think>.*?</think>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern FENCE = Pattern.compile("```[A-Za-z]*");

  private ModelSanitizer() {
  }

  /**
   * Removes think blocks and fences, then trims. Null yields {@code ""}.
   */
  public static String strip(String raw) {
    if (raw == null) {
      return "";
    }
    return FENCE.matcher(THINK_BLOCK.matcher(raw).replaceAll("")).replaceAll("").trim();
  }
}
