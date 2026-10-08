package com.caregiver.ai;

import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Scripted {@link LlmClient} for tests and downstream issues. Replays the
 * scripted results in order and counts invocations. An exhausted script
 * yields an explicit fallback, never null.
 */
public class FakeLlmClient implements LlmClient {

  private final Queue<LlmResult> script;
  private int calls;

  public FakeLlmClient(Queue<LlmResult> script) {
    this.script = new ArrayDeque<>(script != null ? script : new ArrayDeque<LlmResult>());
  }

  @Override
  public LlmResult complete(LlmRequest request) {
    calls++;
    LlmResult next = script.poll();
    if (next != null) {
      return next;
    }
    return new LlmResult(LlmStatus.FALLBACK, "", "AI_UNAVAILABLE: script exhausted");
  }

  public int calls() {
    return calls;
  }
}
