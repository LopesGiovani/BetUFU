package br.com.betufu.odds;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.betufu.odds.internal.model.ConfidenceEstimator;
import br.com.betufu.odds.internal.model.DavidsonProbabilityEngine;
import br.com.betufu.odds.internal.model.EloRatingEngine;
import br.com.betufu.odds.internal.model.MarginCalculator;
import br.com.betufu.odds.internal.model.OddsPricingInput;
import br.com.betufu.odds.internal.model.OddsPricingKernel;
import br.com.betufu.odds.internal.model.OddsPricingOutput;
import br.com.betufu.sports.ConfirmedSportsResultForOdds;
import br.com.betufu.sports.ConfirmedSportsResultForOdds.Score;
import br.com.betufu.sports.SportsResultKindForOdds;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class OddsPricingModelTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final UUID TEST_EDITION_ID =
      UUID.fromString("00000000-0000-0000-0000-000000000401");
  private static final List<String> GOLDEN_FILES =
      List.of(
          "elo-1500.json",
          "elo-k-threshold.json",
          "edition-regression-50.json",
          "davidson-draw.json",
          "confidence-bands.json",
          "margin-rounding.json");

  @Test
  void kernelConsumesAllSixIndependentGoldenVectors() throws Exception {
    for (String file : GOLDEN_FILES) {
      JsonNode root = JSON.readTree(getClass().getResourceAsStream("/odds/golden/" + file));
      assertThat(root.path("schemaVersion").asText()).isEqualTo("04-12.golden.v1");
      assertThat(root.path("sourceAuthority").asText()).isEqualTo("owner_ratified_parameter_set");
      assertThat(root.path("usesLaufu").asBoolean()).isFalse();
    }

    OddsPricingOutput noDraw = new OddsPricingKernel().calculate(input(false, List.of()));
    assertThat(noDraw.fairProbabilities())
        .containsExactly(new BigDecimal("0.5000000000"), new BigDecimal("0.5000000000"));

    assertThat(new EloRatingEngine().kFactor(0)).isEqualByComparingTo("32");
    assertThat(new EloRatingEngine().kFactor(30)).isEqualByComparingTo("16");
    assertThat(new EloRatingEngine().regressForEdition(new BigDecimal("1700")))
        .isEqualByComparingTo("1600.0");

    DavidsonProbabilityEngine.Probabilities draw =
        new DavidsonProbabilityEngine()
            .probabilities(new BigDecimal("1500"), new BigDecimal("1500"), true);
    assertThat(draw.home()).isCloseTo(0.4, org.assertj.core.data.Offset.offset(1e-12));
    assertThat(draw.draw()).isCloseTo(0.2, org.assertj.core.data.Offset.offset(1e-12));
    assertThat(draw.away()).isCloseTo(0.4, org.assertj.core.data.Offset.offset(1e-12));

    ConfidenceEstimator estimator = new ConfidenceEstimator();
    assertThat(estimator.estimate(0).band()).isEqualTo(ConfidenceEstimator.Band.LOW);
    assertThat(estimator.estimate(5).band()).isEqualTo(ConfidenceEstimator.Band.MEDIUM);
    assertThat(estimator.estimate(16).band()).isEqualTo(ConfidenceEstimator.Band.HIGH);

    assertThat(
            new MarginCalculator()
                .publish(
                    List.of(new BigDecimal("0.6"), new BigDecimal("0.4")), new BigDecimal("0.05")))
        .containsExactly(new BigDecimal("1.59"), new BigDecimal("2.38"));
  }

  @Test
  void lowSampleIsNeutralAndAdministrativeFactsNeverFeedRating() {
    UUID home = UUID.randomUUID();
    UUID away = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID marketVersionId = UUID.randomUUID();
    OddsPricingKernel kernel = new OddsPricingKernel();
    OddsPricingOutput empty =
        kernel.calculate(
            new OddsPricingInput(
                UUID.randomUUID(), UUID.randomUUID(), home, away, List.of(), false));
    OddsPricingOutput administrativeOnly =
        kernel.calculate(
            input(
                false,
                List.of(
                    result(SportsResultKindForOdds.ADMINISTRATIVE_WALKOVER, home, away, 99, 0))));
    assertThat(empty.fairProbabilities()).isEqualTo(administrativeOnly.fairProbabilities());
    assertThat(empty.confidenceBand()).isEqualTo(ConfidenceEstimator.Band.LOW);
    assertThat(empty.sampleSize()).isZero();
  }

  @Test
  void historyIsChronologicalAndVolumeCannotChangeTheOutput() {
    UUID home = UUID.randomUUID();
    UUID away = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID marketVersionId = UUID.randomUUID();
    List<ConfirmedSportsResultForOdds> history =
        List.of(
            result(SportsResultKindForOdds.SPORTING_RESULT, home, away, 3, 0),
            result(SportsResultKindForOdds.SPORTING_RESULT, home, away, 2, 0),
            result(SportsResultKindForOdds.SPORTING_RESULT, home, away, 1, 0),
            result(SportsResultKindForOdds.SPORTING_RESULT, home, away, 4, 0),
            result(SportsResultKindForOdds.SPORTING_RESULT, home, away, 2, 1));
    OddsPricingKernel kernel = new OddsPricingKernel();
    OddsPricingOutput first =
        kernel.calculate(input(false, history, eventId, marketVersionId, home, away));
    OddsPricingOutput second =
        kernel.calculate(input(false, history, eventId, marketVersionId, home, away));
    assertThat(first).isEqualTo(second);
    assertThat(first.publishedOdds()).allMatch(value -> value.scale() == 2);
    assertThat(first.configurationDigest()).startsWith("sha256:");
    assertThat(first.inputSnapshot()).contains("\"sampleSize\":5");
  }

  @Test
  void drawBranchHasThreeOutcomesAndFairProbabilityStaysSeparateFromMargin() {
    OddsPricingOutput output = new OddsPricingKernel().calculate(input(true, List.of()));
    assertThat(output.fairProbabilities()).hasSize(3);
    assertThat(output.publishedOdds()).hasSize(3);
    assertThat(output.margin()).isEqualByComparingTo("0.05");
    assertThat(output.fairProbabilities().get(1)).isEqualByComparingTo("0.2000000000");
    assertThat(output.publishedOdds().get(1)).isEqualByComparingTo("4.76");
  }

  private static OddsPricingInput input(
      boolean regulationAllowsDraw, List<ConfirmedSportsResultForOdds> history) {
    return input(
        regulationAllowsDraw,
        history,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID());
  }

  private static OddsPricingInput input(
      boolean regulationAllowsDraw,
      List<ConfirmedSportsResultForOdds> history,
      UUID eventId,
      UUID marketVersionId,
      UUID home,
      UUID away) {
    return new OddsPricingInput(
        eventId, marketVersionId, home, away, history, regulationAllowsDraw);
  }

  private static ConfirmedSportsResultForOdds result(
      SportsResultKindForOdds kind, UUID home, UUID away, int homeScore, int awayScore) {
    Instant time = Instant.parse("2026-01-01T00:00:00Z").plusSeconds(homeScore + awayScore);
    return new ConfirmedSportsResultForOdds(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        TEST_EDITION_ID,
        1,
        ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
        kind,
        kind.hasSportsScore()
            ? List.of(new Score(1, homeScore, home, "Home"), new Score(2, awayScore, away, "Away"))
            : List.of(),
        time,
        time,
        time,
        null);
  }
}
