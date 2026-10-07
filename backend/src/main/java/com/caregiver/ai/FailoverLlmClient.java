package com.caregiver.ai;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Primary → backup failover behind {@link LlmClient}. Tries the primary
 * first; on a {@code FALLBACK} result tries the backup once and returns its
 * result verbatim (validation is the caller's job, as with any client);
 * otherwise returns the standard {@code AI_UNAVAILABLE} fallback. A null
 * backup skips the backup leg (fail closed). The optional events consumer
 * receives exactly one content-free token per call — {@code "primary-ok"},
 * {@code "backup-ok"}, or {@code "fallback"} — for provider attribution
 * without leaking prompts or note contents. {@code complete} never throws
 * (except on a null request), even against a misbehaving delegate.
 */
public class FailoverLlmClient implements LlmClient {

  private final LlmClient primary;
  private final LlmClient backup;
  private final Consumer<String> events;

  public FailoverLlmClient(LlmClient primary, LlmClient backup, Consumer<String> events) {
    this.primary = Objects.requireNonNull(primary, "primary");
    this.backup = backup;
    this.events = events != null ? events : token -> {
    };
  }

  public FailoverLlmClient(LlmClient primary, LlmClient backup) {
    this(primary, backup, null);
  }

  @Override
  public LlmResult complete(LlmRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("request");
    }
    LlmResult first;
    try {
      first = primary.complete(request);
    } catch (RuntimeException e) {
      first = null;
    }
    if (first != null && first.status() != LlmStatus.FALLBACK) {
      events.accept("primary-ok");
      return first;
    }
    if (backup == null) {
      events.accept("fallback");
      return first != null ? first : fallback("primary failed");
    }
    LlmResult second;
    try {
      second = backup.complete(request);
    } catch (RuntimeException e) {
      events.accept("fallback");
      return fallback("backup failed");
    }
    if (second == null || second.status() == LlmStatus.FALLBACK) {
      events.accept("fallback");
      return second != null ? second : fallback("backup failed");
    }
    events.accept("backup-ok");
    return second;
  }

  private static LlmResult fallback(String reason) {
    return new LlmResult(LlmStatus.FALLBACK, "", "AI_UNAVAILABLE: " + reason);
  }
}
