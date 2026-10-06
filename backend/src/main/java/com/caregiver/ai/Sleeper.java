package com.caregiver.ai;

/**
 * Retry backoff sleep. Production passes {@code Thread::sleep}; tests pass a
 * no-op (or recorder) so retry behavior stays deterministic and fast.
 */
@FunctionalInterface
public interface Sleeper {

  void sleep(long ms) throws InterruptedException;
}
