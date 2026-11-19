package com.caregiver.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenBucketLimiterTest {

  @Test
  void allowsUpToCapacityThenDeniesWithRetryAfter() {
    AtomicLong now = new AtomicLong(0);
    var limiter = new TokenBucketLimiter(2, Duration.ofMinutes(1), now::get);

    assertThat(limiter.tryConsume("ip:1").allowed()).isTrue();
    assertThat(limiter.tryConsume("ip:1").allowed()).isTrue();
    var denied = limiter.tryConsume("ip:1");

    assertThat(denied.allowed()).isFalse();
    assertThat(denied.retryAfterSeconds()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void refillsOverTime() {
    AtomicLong now = new AtomicLong(0);
    var limiter = new TokenBucketLimiter(2, Duration.ofMinutes(1), now::get);
    limiter.tryConsume("k");
    limiter.tryConsume("k");
    assertThat(limiter.tryConsume("k").allowed()).isFalse();

    // Capacity 2 over 60s refills one token every 30s.
    now.set(30_000);
    assertThat(limiter.tryConsume("k").allowed()).isTrue();
    assertThat(limiter.tryConsume("k").allowed()).isFalse();

    now.set(60_000);
    // Continuous refill: only one token accrues between t=30s and t=60s.
    assertThat(limiter.tryConsume("k").allowed()).isTrue();
    assertThat(limiter.tryConsume("k").allowed()).isFalse();

    now.set(90_000);
    assertThat(limiter.tryConsume("k").allowed()).isTrue();
  }

  @Test
  void keysAreIndependent() {
    var limiter = new TokenBucketLimiter(1, Duration.ofMinutes(1), () -> 0L);

    assertThat(limiter.tryConsume("account:a").allowed()).isTrue();
    assertThat(limiter.tryConsume("account:b").allowed()).isTrue();
    assertThat(limiter.tryConsume("account:a").allowed()).isFalse();
  }

  @Test
  void retryAfterIsNeverZero() {
    var limiter = new TokenBucketLimiter(100, Duration.ofMinutes(1), () -> 0L);
    for (int i = 0; i < 100; i++) {
      assertThat(limiter.tryConsume("k").allowed()).isTrue();
    }
    assertThat(limiter.tryConsume("k").retryAfterSeconds()).isGreaterThanOrEqualTo(1);
  }

  @Test
  void rejectsNonPositiveConfiguration() {
    assertThatThrownBy(() -> new TokenBucketLimiter(0, Duration.ofMinutes(1), () -> 0L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new TokenBucketLimiter(1, Duration.ZERO, () -> 0L))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
