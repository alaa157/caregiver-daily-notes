package com.caregiver.ai;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** A bounded HTTP-facing provider budget; provider workers never read ownership or write plans. */
@Component
public class ProviderDeadline implements AutoCloseable {
  private final LlmClient delegate;
  private final long budgetMillis;
  private final ThreadPoolExecutor workers = new ThreadPoolExecutor(4, 4, 0, TimeUnit.MILLISECONDS,
      new ArrayBlockingQueue<>(16), task -> { Thread thread = new Thread(task, "caregiver-provider"); thread.setDaemon(true); return thread; });
  @Autowired public ProviderDeadline(LlmClient delegate) { this(delegate, 40_000); }
  ProviderDeadline(LlmClient delegate, long budgetMillis) { this.delegate = delegate; this.budgetMillis = budgetMillis; }
  public LlmResult complete(LlmRequest request) {
    Future<LlmResult> future = null;
    try {
      future = workers.submit(() -> delegate.complete(request));
      LlmResult result = future.get(budgetMillis, TimeUnit.MILLISECONDS);
      return result == null ? unavailable() : result;
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt(); return unavailable();
    } catch (Exception providerFailureOrTimeout) {
      return unavailable();
    } finally { if (future != null && !future.isDone()) future.cancel(true); }
  }
  private LlmResult unavailable() { return new LlmResult(LlmStatus.FALLBACK, "", "AI_UNAVAILABLE"); }
  @PreDestroy @Override public void close() { workers.shutdownNow(); }
}
