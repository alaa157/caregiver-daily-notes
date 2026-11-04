package com.caregiver.ai;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
    if (Thread.currentThread().isInterrupted()) throw new CancellationException("Provider call interrupted");
    Future<LlmResult> future = null;
    try {
      future = workers.submit(() -> delegate.complete(request));
      LlmResult result = future.get(budgetMillis, TimeUnit.MILLISECONDS);
      if (Thread.currentThread().isInterrupted()) throw new CancellationException("Provider call interrupted");
      return result == null ? unavailable() : result;
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      CancellationException cancelled = new CancellationException("Provider call interrupted");
      cancelled.initCause(interrupted);
      throw cancelled;
    } catch (CancellationException cancelled) {
      throw cancelled;
    } catch (ExecutionException failed) {
      if (failed.getCause() instanceof CancellationException cancelled) throw cancelled;
      if (Thread.currentThread().isInterrupted()) throw new CancellationException("Provider call interrupted");
      return unavailable();
    } catch (TimeoutException providerTimeout) {
      if (Thread.currentThread().isInterrupted()) throw new CancellationException("Provider call interrupted");
      return unavailable();
    } catch (RuntimeException providerFailure) {
      if (Thread.currentThread().isInterrupted()) throw new CancellationException("Provider call interrupted");
      return unavailable();
    } finally {
      if (future != null && !future.isDone()) future.cancel(true);
    }
  }
  private LlmResult unavailable() { return new LlmResult(LlmStatus.FALLBACK, "", "AI_UNAVAILABLE"); }
  @PreDestroy @Override public void close() { workers.shutdownNow(); }
}
