package com.caregiver.ai;

/**
 * Period-over-period event counts and recorded sample sizes for one metric.
 */
public record TrendResult(
    String metric,
    int previousCount,
    int currentCount,
    int delta,
    int previousSampleSize,
    int currentSampleSize) {

  public TrendResult {
    if (previousSampleSize < 0 || currentSampleSize < 0) {
      throw new IllegalArgumentException("Sample sizes must be nonnegative");
    }
    delta = currentCount - previousCount;
  }
}
