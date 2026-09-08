package br.com.betufu.odds.internal.model;

import java.math.BigDecimal;

/** Conservative confidence label derived only from verified result sample size. */
public final class ConfidenceEstimator {

  public Estimate estimate(int sampleSize) {
    if (sampleSize < 0) {
      throw new IllegalArgumentException("sampleSize must not be negative");
    }
    if (sampleSize < 5) {
      return new Estimate(Band.LOW, new BigDecimal("0.00"), true);
    }
    if (sampleSize <= 15) {
      return new Estimate(Band.MEDIUM, new BigDecimal("0.50"), false);
    }
    return new Estimate(Band.HIGH, new BigDecimal("1.00"), false);
  }

  public enum Band {
    LOW,
    MEDIUM,
    HIGH
  }

  public record Estimate(Band band, BigDecimal value, boolean neutralEstimate) {}
}
