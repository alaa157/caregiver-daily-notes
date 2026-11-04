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
    if (previousSampleSize < 0) {
      throw new IllegalArgumentException("previousSampleSize must be nonnegative");
    }
    if (currentSampleSize < 0) {
      throw new IllegalArgumentException("currentSampleSize must be nonnegative");
    }
    if (delta != currentCount - previousCount) {
      throw new IllegalArgumentException("delta must equal currentCount - previousCount");
    }
  }
}
