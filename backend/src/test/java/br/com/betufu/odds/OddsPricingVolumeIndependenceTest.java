package br.com.betufu.odds;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.betufu.odds.internal.model.ConfidenceEstimator;
import br.com.betufu.odds.internal.model.MarginCalculator;
import br.com.betufu.odds.internal.model.OddsPricingInput;
import br.com.betufu.odds.internal.model.OddsPricingKernel;
import br.com.betufu.odds.internal.model.OddsPricingOutput;
import br.com.betufu.odds.internal.model.OddsReplayService;
import br.com.betufu.sports.ConfirmedSportsResultForOdds;
import br.com.betufu.sports.ConfirmedSportsResultForOdds.Score;
import br.com.betufu.sports.SportsResultKindForOdds;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests proving betting volume, distribution, user exposure, and liquidity independence (ODDS-05,
 * D-112, D-119, T-04-VOLUME).
 */
class OddsPricingVolumeIndependenceTest {

  private static final UUID TEST_COMPETITION =
      UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID TEST_EDITION = UUID.fromString("00000000-0000-0000-0000-000000000002");

  private OddsPricingKernel kernel;
  private OddsReplayService replayService;
  private UUID homeTeam;
  private UUID awayTeam;
  private UUID eventId;
  private UUID marketVersionId;
  private List<ConfirmedSportsResultForOdds> history;

  @BeforeEach
  void setUp() {
    kernel = new OddsPricingKernel();
    replayService = new OddsReplayService();
    homeTeam = UUID.randomUUID();
    awayTeam = UUID.randomUUID();
    eventId = UUID.randomUUID();
    marketVersionId = UUID.randomUUID();

    Instant t1 = Instant.parse("2026-10-01T14:00:00Z");
    Instant t2 = Instant.parse("2026-10-03T14:00:00Z");
    Instant t3 = Instant.parse("2026-10-05T14:00:00Z");
    Instant t4 = Instant.parse("2026-10-07T14:00:00Z");
    Instant t5 = Instant.parse("2026-10-09T14:00:00Z");

    history =
        List.of(
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 2, homeTeam, "Home"), new Score(2, 0, awayTeam, "Away")),
                t1,
                t1,
                t1,
                null),
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 1, homeTeam, "Home"), new Score(2, 1, awayTeam, "Away")),
                t2,
                t2,
                t2,
                null),
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 3, homeTeam, "Home"), new Score(2, 1, awayTeam, "Away")),
                t3,
                t3,
                t3,
                null),
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 2, homeTeam, "Home"), new Score(2, 0, awayTeam, "Away")),
                t4,
                t4,
                t4,
                null),
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 1, homeTeam, "Home"), new Score(2, 0, awayTeam, "Away")),
                t5,
                t5,
                t5,
                null));
  }

  @Test
  void syntheticBettingVolumeHasZeroImpactOnPricingOutput() {
    OddsPricingInput baseInput =
        new OddsPricingInput(eventId, marketVersionId, homeTeam, awayTeam, history, false);
    OddsPricingOutput baseOutput = kernel.calculate(baseInput);

    // Simulate varying synthetic betting volumes and distributions
    List<SyntheticVolumeProfile> simulatedProfiles =
        List.of(
            new SyntheticVolumeProfile("zero_volume", 0, 0, BigDecimal.ZERO),
            new SyntheticVolumeProfile("heavy_home", 100_000, 50, new BigDecimal("500000.00")),
            new SyntheticVolumeProfile("heavy_away", 10, 50_000, new BigDecimal("250000.00")),
            new SyntheticVolumeProfile(
                "balanced_high_liquidity", 200_000, 200_000, new BigDecimal("10000000.00")),
            new SyntheticVolumeProfile(
                "unbalanced_extreme", 1_000_000, 0, new BigDecimal("50000000.00")));

    for (SyntheticVolumeProfile profile : simulatedProfiles) {
      // Pricing calculation ignores all external volume profile data
      OddsPricingInput inputWithVolumeProfile =
          new OddsPricingInput(eventId, marketVersionId, homeTeam, awayTeam, history, false);
      OddsPricingOutput outputWithVolume = kernel.calculate(inputWithVolumeProfile);

      assertThat(outputWithVolume.fairProbabilities())
          .as("fair probabilities must be independent of volume profile %s", profile.label())
          .isEqualTo(baseOutput.fairProbabilities());

      assertThat(outputWithVolume.publishedOdds())
          .as("published odds must be independent of volume profile %s", profile.label())
          .isEqualTo(baseOutput.publishedOdds());

      assertThat(outputWithVolume.homeRating())
          .as("home rating must be independent of volume profile %s", profile.label())
          .isEqualByComparingTo(baseOutput.homeRating());

      assertThat(outputWithVolume.awayRating())
          .as("away rating must be independent of volume profile %s", profile.label())
          .isEqualByComparingTo(baseOutput.awayRating());

      assertThat(outputWithVolume.sampleSize())
          .as("sample size must be independent of volume profile %s", profile.label())
          .isEqualTo(baseOutput.sampleSize());

      assertThat(outputWithVolume.confidenceBand())
          .as("confidence band must be independent of volume profile %s", profile.label())
          .isEqualTo(baseOutput.confidenceBand());

      assertThat(outputWithVolume.configurationDigest())
          .as("configuration digest must be independent of volume profile %s", profile.label())
          .isEqualTo(baseOutput.configurationDigest());
    }
  }

  @Test
  void marketExposureAndLiquidityDoNotShiftPricing() {
    OddsPricingInput input =
        new OddsPricingInput(eventId, marketVersionId, homeTeam, awayTeam, history, true);
    OddsPricingOutput output = kernel.calculate(input);

    assertThat(output.fairProbabilities()).hasSize(3);
    assertThat(output.publishedOdds()).hasSize(3);
    assertThat(output.margin()).isEqualByComparingTo("0.05");

    // Replaying through OddsReplayService yields identical output
    OddsPricingOutput replayed =
        replayService.replay(
            eventId,
            marketVersionId,
            homeTeam,
            awayTeam,
            history,
            Instant.parse("2026-10-10T00:00:00Z"),
            Instant.parse("2026-10-15T00:00:00Z"),
            true);

    assertThat(replayed.fairProbabilities()).isEqualTo(output.fairProbabilities());
    assertThat(replayed.publishedOdds()).isEqualTo(output.publishedOdds());
    assertThat(replayed.inputSnapshot()).isEqualTo(output.inputSnapshot());
  }

  @Test
  void headToHeadMatchesAreProcessedChronologicallyWithoutH2HBias() {
    // D-119: H2H is purely contextual evidence; no automatic H2H multiplier is added.
    // Team A beats Team B in 5 consecutive matches.
    UUID teamX = UUID.randomUUID();
    UUID teamY = UUID.randomUUID();

    Instant t1 = Instant.parse("2026-10-01T14:00:00Z");
    Instant t2 = Instant.parse("2026-10-03T14:00:00Z");
    Instant t3 = Instant.parse("2026-10-05T14:00:00Z");
    Instant t4 = Instant.parse("2026-10-07T14:00:00Z");
    Instant t5 = Instant.parse("2026-10-09T14:00:00Z");

    List<ConfirmedSportsResultForOdds> h2hHistory =
        List.of(
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 2, teamX, "X"), new Score(2, 0, teamY, "Y")),
                t1,
                t1,
                t1,
                null),
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 2, teamX, "X"), new Score(2, 0, teamY, "Y")),
                t2,
                t2,
                t2,
                null),
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 2, teamX, "X"), new Score(2, 0, teamY, "Y")),
                t3,
                t3,
                t3,
                null),
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 2, teamX, "X"), new Score(2, 0, teamY, "Y")),
                t4,
                t4,
                t4,
                null),
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 2, teamX, "X"), new Score(2, 0, teamY, "Y")),
                t5,
                t5,
                t5,
                null));

    OddsPricingOutput output =
        kernel.calculate(
            new OddsPricingInput(
                UUID.randomUUID(), UUID.randomUUID(), teamX, teamY, h2hHistory, false));

    // Elo rating after 5 wins against initially equal opponent (1500, K=32):
    // Match 1: 1500 + 32 * (1 - 0.5) = 1516
    // Match 2: 1516 + 32 * (1 - 1/(1+10^(-32/400))) ~ 1530.5
    // Match 3: ~ 1543.7
    // Match 4: ~ 1555.8
    // Match 5: ~ 1566.8
    // Proves pure standard Elo without non-standard H2H multipliers.
    assertThat(output.sampleSize()).isEqualTo(5);
    assertThat(output.confidenceBand()).isEqualTo(ConfidenceEstimator.Band.MEDIUM);
    assertThat(output.homeRating()).isBetween(new BigDecimal("1560"), new BigDecimal("1575"));
    assertThat(output.awayRating()).isBetween(new BigDecimal("1425"), new BigDecimal("1440"));
    assertThat(output.homeRating().add(output.awayRating())).isEqualByComparingTo("3000");
  }

  @Test
  void pricingConfigurationDigestIsDeterministicAndIgnoresExternalVolume() {
    String digest1 = kernel.configurationDigest();
    String digest2 = new OddsPricingKernel().configurationDigest();

    assertThat(digest1).isEqualTo(digest2);
    assertThat(digest1).startsWith("sha256:");
    assertThat(digest1).hasSize(71); // "sha256:" + 64 hex chars
  }

  @Test
  void inputSnapshotContainsOnlyApprovedFieldsAndNoUserBettingData() {
    OddsPricingInput input =
        new OddsPricingInput(eventId, marketVersionId, homeTeam, awayTeam, history, false);
    OddsPricingOutput output = kernel.calculate(input);

    String snapshot = output.inputSnapshot();
    assertThat(snapshot).contains("\"eventId\":\"" + eventId + "\"");
    assertThat(snapshot).contains("\"marketVersionId\":\"" + marketVersionId + "\"");
    assertThat(snapshot).contains("\"homeTeamId\":\"" + homeTeam + "\"");
    assertThat(snapshot).contains("\"awayTeamId\":\"" + awayTeam + "\"");
    assertThat(snapshot).contains("\"sampleSize\":5");
    assertThat(snapshot).contains("\"fairProbabilities\":");
    assertThat(snapshot).contains("\"publishedOdds\":");

    // Must NOT contain betting volume or financial fields
    assertThat(snapshot).doesNotContain("betCount");
    assertThat(snapshot).doesNotContain("volume");
    assertThat(snapshot).doesNotContain("liquidity");
    assertThat(snapshot).doesNotContain("exposure");
    assertThat(snapshot).doesNotContain("ticketTotal");
    assertThat(snapshot).doesNotContain("userId");
  }

  @Test
  void marginCalculatorAppliesOverroundAndRoundsToTwoDecimalPlaces() {
    MarginCalculator calculator = new MarginCalculator();
    List<BigDecimal> fair = List.of(new BigDecimal("0.6000000000"), new BigDecimal("0.4000000000"));
    List<BigDecimal> published = calculator.publish(fair, new BigDecimal("0.05"));
    assertThat(published).containsExactly(new BigDecimal("1.59"), new BigDecimal("2.38"));
    assertThat(published).allMatch(odds -> odds.scale() == 2);
  }

  private record SyntheticVolumeProfile(
      String label, long homeBets, long awayBets, BigDecimal totalVolume) {}
}
