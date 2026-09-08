package br.com.betufu.odds.internal.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Pure conditional Davidson transform; theta 0.50 yields a conservative 20% draw at parity. */
public final class DavidsonProbabilityEngine {

  public static final BigDecimal THETA = new BigDecimal("0.50");

  public Probabilities probabilities(
      BigDecimal homeRating, BigDecimal awayRating, boolean regulationAllowsDraw) {
    double homeStrength = Math.pow(10.0, homeRating.doubleValue() / 400.0);
    double awayStrength = Math.pow(10.0, awayRating.doubleValue() / 400.0);
    if (!regulationAllowsDraw) {
      double denominator = homeStrength + awayStrength;
      return new Probabilities(homeStrength / denominator, 0.0, awayStrength / denominator);
    }
    double drawStrength = THETA.doubleValue() * Math.sqrt(homeStrength * awayStrength);
    double denominator = homeStrength + drawStrength + awayStrength;
    return new Probabilities(
        homeStrength / denominator, drawStrength / denominator, awayStrength / denominator);
  }

  public record Probabilities(double home, double draw, double away) {
    public ListValue asDecimals() {
      return new ListValue(decimal(home), decimal(draw), decimal(away));
    }

    private static BigDecimal decimal(double value) {
      return BigDecimal.valueOf(value).setScale(10, RoundingMode.HALF_UP);
    }
  }

  public record ListValue(BigDecimal home, BigDecimal draw, BigDecimal away) {}
}
