package com.caregiver.ratelimit;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Small thread-safe in-memory token bucket. One bucket per key, refilling
 * continuously at {@code capacity / window}. Intended for a single instance;
 * a distributed deployment can swap this for a shared implementation without
 * touching the filter.
 */
public final class TokenBucketLimiter {

  private static final int MAX_KEYS = 10_000;

  private final int capacity;
  private final long windowMillis;
  private final LongSupplier clockMillis;
  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

  public TokenBucketLimiter(int capacity, Duration window, LongSupplier clockMillis) {
    if (capacity <= 0 || window == null || window.isZero() || window.isNegative()) {
      throw new IllegalArgumentException("capacity and window must be positive");
    }
    this.capacity = capacity;
    this.windowMillis = Math.max(1, window.toMillis());
    this.clockMillis = clockMillis;
  }

  public int capacity() {
    return capacity;
  }

  /** {@code retryAfterSeconds} is only meaningful when the decision is denied. */
  public record Decision(boolean allowed, long retryAfterSeconds) {

    public static Decision allow() {
      return new Decision(true, 0);
    }
  }

  public Decision tryConsume(String key) {
    long now = clockMillis.getAsLong();
    Decision[] result = new Decision[1];
    buckets.compute(key, (ignored, existing) -> {
      Bucket bucket = existing == null ? new Bucket(capacity, now) : existing;
      long elapsed = Math.max(0, now - bucket.lastRefillMillis);
      bucket.tokens = Math.min(capacity, bucket.tokens + (double) elapsed * capacity / windowMillis);
      bucket.lastRefillMillis = now;
      if (bucket.tokens >= 1.0) {
        bucket.tokens -= 1.0;
        result[0] = Decision.allow();
      } else {
        long waitMillis = (long) Math.ceil((1.0 - bucket.tokens) * windowMillis / capacity);
        // Retry-After is in whole seconds and must never be zero.
        result[0] = new Decision(false, Math.max(1, (waitMillis + 999) / 1000));
      }
      return bucket;
    });
    prune(now);
    return result[0];
  }

  /** Bound memory when many distinct IPs/accounts pass through. */
  private void prune(long now) {
    if (buckets.size() <= MAX_KEYS) {
      return;
    }
    buckets.entrySet().removeIf(entry -> {
      Bucket bucket = entry.getValue();
      return now - bucket.lastRefillMillis > windowMillis && bucket.tokens >= capacity;
    });
  }

  private static final class Bucket {
    private double tokens;
    private long lastRefillMillis;

    private Bucket(double tokens, long lastRefillMillis) {
      this.tokens = tokens;
      this.lastRefillMillis = lastRefillMillis;
    }
  }
}
