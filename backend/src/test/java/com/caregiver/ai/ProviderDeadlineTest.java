package com.caregiver.ai;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProviderDeadlineTest {
  @Test void stalledProviderReturnsFallbackAndIsInterrupted() throws Exception {
    CountDownLatch interrupted = new CountDownLatch(1);
    try (var provider = new ProviderDeadline(request -> {
      try { new CountDownLatch(1).await(); } catch (InterruptedException stopped) { interrupted.countDown(); Thread.currentThread().interrupt(); }
      return new LlmResult(LlmStatus.OK, "late private output", null);
    }, 100)) {
      long start = System.nanoTime();
      assertThat(provider.complete(new LlmRequest("test", "synthetic" )).status()).isEqualTo(LlmStatus.FALLBACK);
      assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)).isLessThan(2_000);
      assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
    }
  }
  @Test void callerInterruptionPropagatesAndPreservesTheInterruptFlag() throws Exception {
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch providerInterrupted = new CountDownLatch(1);
    AtomicReference<Throwable> failure = new AtomicReference<>();
    AtomicBoolean requestThreadInterrupted = new AtomicBoolean();
    try (var provider = new ProviderDeadline(request -> {
      entered.countDown();
      try {
        new CountDownLatch(1).await();
        return new LlmResult(LlmStatus.OK, "late output", null);
      } catch (InterruptedException cancelled) {
        providerInterrupted.countDown();
        Thread.currentThread().interrupt();
        throw new CancellationException("provider cancelled");
      }
    }, 5_000)) {
      Thread requestThread = new Thread(() -> {
        try {
          provider.complete(new LlmRequest("test", "synthetic"));
        } catch (Throwable thrown) {
          failure.set(thrown);
          requestThreadInterrupted.set(Thread.currentThread().isInterrupted());
        }
      });
      requestThread.start();
      assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
      requestThread.interrupt();
      requestThread.join(1_000);

      assertThat(requestThread.isAlive()).isFalse();
      assertThat(failure.get()).isInstanceOf(CancellationException.class);
      assertThat(requestThreadInterrupted).isTrue();
      assertThat(providerInterrupted.await(1, TimeUnit.SECONDS)).isTrue();
    }
  }
  @Test void providerExceptionsNeverExposeDiagnostics() {
    try (var provider = new ProviderDeadline(request -> { throw new IllegalStateException("private credentials or prompt"); }, 1_000)) {
      LlmResult result = provider.complete(new LlmRequest("test", "synthetic"));
      assertThat(result.status()).isEqualTo(LlmStatus.FALLBACK); assertThat(result.error()).isEqualTo("AI_UNAVAILABLE");
    }
  }
}
