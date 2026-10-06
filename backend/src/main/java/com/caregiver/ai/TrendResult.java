package com.caregiver.ai;

/**
 * Period-over-period count comparison for one metric.
 * Invariant: {@code delta == currentCount - previousCount} (enforced by
 * {@link TrendCalculator#computeSingle}).
 */
public record TrendResult(String metric, int previousCount, int currentCount, int delta) {
}
