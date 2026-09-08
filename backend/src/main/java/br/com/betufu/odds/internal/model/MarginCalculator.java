package br.com.betufu.odds.internal.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Applies the versioned overround while keeping fair probabilities untouched. */
public final class MarginCalculator {

  public static final int DECIMAL_PLACES = 2;
  public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

  public List<BigDecimal> publish(List<BigDecimal> fairProbabilities, BigDecimal margin) {
    if (fairProbabilities == null || fairProbabilities.isEmpty()) {
      throw new IllegalArgumentException("fairProbabilities must not be empty");
    }
    if (margin == null || margin.signum() < 0 || margin.compareTo(BigDecimal.ONE) >= 0) {
      throw new IllegalArgumentException("margin must be in [0, 1)");
    }
    BigDecimal multiplier = BigDecimal.ONE.add(margin);
    List<BigDecimal> published = new ArrayList<>();
    for (BigDecimal fair : fairProbabilities) {
      if (fair == null || fair.signum() <= 0 || fair.compareTo(BigDecimal.ONE) > 0) {
        throw new IllegalArgumentException("fair probability must be in (0, 1]");
      }
      published.add(
          BigDecimal.ONE
              .divide(fair.multiply(multiplier), 16, RoundingMode.HALF_UP)
              .setScale(DECIMAL_PLACES, ROUNDING_MODE));
    }
    return List.copyOf(published);
  }
}
