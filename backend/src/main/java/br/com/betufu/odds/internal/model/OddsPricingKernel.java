package br.com.betufu.odds.internal.model;

import br.com.betufu.sports.ConfirmedSportsResultForOdds;
import br.com.betufu.sports.ConfirmedSportsResultForOdds.Score;
import br.com.betufu.sports.SportsResultKindForOdds;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** Deterministic pricing kernel; it has no database, network, clock or betting-volume input. */
public final class OddsPricingKernel {

  public static final BigDecimal DEFAULT_MARGIN = new BigDecimal("0.05");
  public static final String ALGORITHM_VERSION = "elo-davidson-v1";
  public static final String CONFIGURATION_INPUT =
      "initial=1500|provisionalK=32|matureK=16|experienceThreshold=30|regression=0.5|theta=0.50|confidence=<5,5-15,>15|margin=0.05|scale=2|rounding=HALF_UP";

  private final EloRatingEngine elo;
  private final DavidsonProbabilityEngine davidson;
  private final ConfidenceEstimator confidence;
  private final MarginCalculator marginCalculator;

  public OddsPricingKernel() {
    this(
        new EloRatingEngine(),
        new DavidsonProbabilityEngine(),
        new ConfidenceEstimator(),
        new MarginCalculator());
  }

  OddsPricingKernel(
      EloRatingEngine elo,
      DavidsonProbabilityEngine davidson,
      ConfidenceEstimator confidence,
      MarginCalculator marginCalculator) {
    this.elo = elo;
    this.davidson = davidson;
    this.confidence = confidence;
    this.marginCalculator = marginCalculator;
  }

  public OddsPricingOutput calculate(OddsPricingInput input) {
    List<ConfirmedSportsResultForOdds> sporting =
        input.historicalResults().stream()
            .filter(result -> result.resultKind() == SportsResultKindForOdds.SPORTING_RESULT)
            .sorted(
                Comparator.comparing(ConfirmedSportsResultForOdds::sportTime)
                    .thenComparing(ConfirmedSportsResultForOdds::knownAt)
                    .thenComparing(ConfirmedSportsResultForOdds::versionNo)
                    .thenComparing(ConfirmedSportsResultForOdds::versionId))
            .toList();
    EloRatingEngine.RatingSnapshot ratings =
        elo.rate(sporting, input.homeTeamId(), input.awayTeamId());
    ConfidenceEstimator.Estimate confidenceEstimate =
        confidence.estimate(ratings.conservativeSampleSize());
    BigDecimal homeRating = ratings.homeRating();
    BigDecimal awayRating = ratings.awayRating();
    if (confidenceEstimate.neutralEstimate()) {
      homeRating = EloRatingEngine.INITIAL_RATING;
      awayRating = EloRatingEngine.INITIAL_RATING;
    }
    DavidsonProbabilityEngine.Probabilities probabilities =
        davidson.probabilities(homeRating, awayRating, input.regulationAllowsDraw());
    List<BigDecimal> fair = new ArrayList<>();
    fair.add(BigDecimal.valueOf(probabilities.home()));
    if (input.regulationAllowsDraw()) {
      fair.add(BigDecimal.valueOf(probabilities.draw()));
    }
    fair.add(BigDecimal.valueOf(probabilities.away()));
    fair = fair.stream().map(value -> value.setScale(10, java.math.RoundingMode.HALF_UP)).toList();
    List<BigDecimal> published = marginCalculator.publish(fair, input.margin());
    String snapshot = snapshot(input, sporting, ratings, confidenceEstimate, fair, published);
    return new OddsPricingOutput(
        fair,
        published,
        confidenceEstimate.value(),
        confidenceEstimate.band(),
        ratings.conservativeSampleSize(),
        homeRating,
        awayRating,
        ALGORITHM_VERSION,
        configurationDigest(),
        input.margin(),
        MarginCalculator.DECIMAL_PLACES,
        MarginCalculator.ROUNDING_MODE,
        snapshot);
  }

  public String configurationDigest() {
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(CONFIGURATION_INPUT.getBytes(StandardCharsets.UTF_8));
      StringBuilder result = new StringBuilder("sha256:");
      for (byte value : digest) {
        result.append(String.format(Locale.ROOT, "%02x", value));
      }
      return result.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required by the approved toolchain", exception);
    }
  }

  private static String snapshot(
      OddsPricingInput input,
      List<ConfirmedSportsResultForOdds> sporting,
      EloRatingEngine.RatingSnapshot ratings,
      ConfidenceEstimator.Estimate confidence,
      List<BigDecimal> fair,
      List<BigDecimal> published) {
    String history =
        sporting.stream()
            .map(
                result ->
                    "{\"versionId\":\""
                        + result.versionId()
                        + "\",\"knownAt\":\""
                        + result.knownAt()
                        + "\",\"sportTime\":\""
                        + result.sportTime()
                        + "\",\"scores\":["
                        + result.scores().stream()
                            .map(OddsPricingKernel::scoreJson)
                            .collect(Collectors.joining(","))
                        + "]}")
            .collect(Collectors.joining(",", "[", "]"));
    return "{"
        + "\"eventId\":\""
        + input.eventId()
        + "\",\"marketVersionId\":\""
        + input.marketVersionId()
        + "\",\"homeTeamId\":\""
        + input.homeTeamId()
        + "\",\"awayTeamId\":\""
        + input.awayTeamId()
        + "\",\"regulationAllowsDraw\":"
        + input.regulationAllowsDraw()
        + ",\"margin\":"
        + input.margin()
        + ",\"history\":"
        + history
        + ",\"ratings\":["
        + ratings.homeRating()
        + ","
        + ratings.awayRating()
        + "],\"sampleSize\":"
        + ratings.conservativeSampleSize()
        + ",\"confidenceBand\":\""
        + confidence.band()
        + "\",\"fairProbabilities\":"
        + fair.stream().map(BigDecimal::toPlainString).collect(Collectors.joining(",", "[", "]"))
        + ",\"publishedOdds\":"
        + published.stream()
            .map(BigDecimal::toPlainString)
            .collect(Collectors.joining(",", "[", "]"))
        + "}";
  }

  private static String scoreJson(Score score) {
    return "{\"slot\":"
        + score.slotNo()
        + ",\"score\":"
        + score.score()
        + ",\"teamId\":\""
        + score.teamId()
        + "\",\"label\":\""
        + escapeJson(score.displayLabel())
        + "\"}";
  }

  private static String escapeJson(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}
