package br.com.betufu.odds.internal.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Complete deterministic result, retaining fair probabilities separately from prices. */
public record OddsPricingOutput(
    List<BigDecimal> fairProbabilities,
    List<BigDecimal> publishedOdds,
    BigDecimal confidence,
    ConfidenceEstimator.Band confidenceBand,
    int sampleSize,
    BigDecimal homeRating,
    BigDecimal awayRating,
    String algorithmVersion,
    String configurationDigest,
    BigDecimal margin,
    int decimalPlaces,
    RoundingMode roundingMode,
    String inputSnapshot) {

  public OddsPricingOutput {
    fairProbabilities = List.copyOf(fairProbabilities);
    publishedOdds = List.copyOf(publishedOdds);
  }

  public BigDecimal fairProbability() {
    return fairProbabilities.get(0);
  }
}
