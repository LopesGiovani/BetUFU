package br.com.betufu.odds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.odds.internal.model.ConfidenceEstimator;
import br.com.betufu.odds.internal.model.EloRatingEngine;
import br.com.betufu.odds.internal.model.OddsInputFilter;
import br.com.betufu.odds.internal.model.OddsPricingKernel;
import br.com.betufu.odds.internal.model.OddsPricingOutput;
import br.com.betufu.odds.internal.model.OddsReplayService;
import br.com.betufu.odds.internal.model.OddsReplayService.ReplaySnapshot;
import br.com.betufu.sports.ConfirmedSportsResultForOdds;
import br.com.betufu.sports.ConfirmedSportsResultForOdds.Score;
import br.com.betufu.sports.SportsEventForOdds;
import br.com.betufu.sports.SportsResultForOddsPort;
import br.com.betufu.sports.SportsResultKindForOdds;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OddsReplayCorrectionTest {

  private static final UUID TEST_COMPETITION =
      UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID TEST_EDITION = UUID.fromString("00000000-0000-0000-0000-000000000002");

  private OddsReplayService replayService;
  private UUID teamA;
  private UUID teamB;
  private UUID teamC;
  private UUID targetEventId;
  private UUID targetMarketVersionId;
  private Instant targetScheduledAt;

  @BeforeEach
  void setUp() {
    replayService = new OddsReplayService();
    teamA = UUID.randomUUID();
    teamB = UUID.randomUUID();
    teamC = UUID.randomUUID();
    targetEventId = UUID.randomUUID();
    targetMarketVersionId = UUID.randomUUID();
    targetScheduledAt = Instant.parse("2026-10-15T18:00:00Z");
  }

  @Test
  void replayingAtPriorCutoffIsIdenticalBeforeAndAfterCorrection() {
    Instant tb1 = Instant.parse("2026-10-01T14:00:00Z");
    Instant tb2 = Instant.parse("2026-10-02T14:00:00Z");
    Instant tb3 = Instant.parse("2026-10-03T14:00:00Z");
    Instant tb4 = Instant.parse("2026-10-04T14:00:00Z");

    List<ConfirmedSportsResultForOdds> baseline =
        List.of(
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 2, teamA, "Team A"), new Score(2, 0, teamC, "Team C")),
                tb1,
                tb1,
                tb1,
                null),
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 1, teamA, "Team A"), new Score(2, 0, teamC, "Team C")),
                tb2,
                tb2,
                tb2,
                null),
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 2, teamA, "Team A"), new Score(2, 1, teamC, "Team C")),
                tb3,
                tb3,
                tb3,
                null),
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 3, teamA, "Team A"), new Score(2, 0, teamC, "Team C")),
                tb4,
                tb4,
                tb4,
                null));

    UUID historicalEventId = UUID.randomUUID();
    Instant sportTime = Instant.parse("2026-10-10T14:00:00Z");
    Instant t1Known = Instant.parse("2026-10-10T16:00:00Z");
    Instant t1Cutoff = Instant.parse("2026-10-10T17:00:00Z");
    Instant t2Known = Instant.parse("2026-10-11T10:00:00Z");
    Instant t2Cutoff = Instant.parse("2026-10-11T12:00:00Z");

    UUID v1Id = UUID.randomUUID();
    ConfirmedSportsResultForOdds v1 =
        new ConfirmedSportsResultForOdds(
            v1Id,
            historicalEventId,
            TEST_COMPETITION,
            TEST_EDITION,
            1,
            ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
            SportsResultKindForOdds.SPORTING_RESULT,
            List.of(new Score(1, 2, teamA, "Team A"), new Score(2, 0, teamC, "Team C")),
            t1Known,
            t1Known,
            sportTime,
            null);

    List<ConfirmedSportsResultForOdds> initialHistory =
        List.of(baseline.get(0), baseline.get(1), baseline.get(2), baseline.get(3), v1);

    // Initial replay at T1 cutoff with baseline + V1 in history.
    ReplaySnapshot snapshotBefore =
        replayService.replayWithLineage(
            targetEventId,
            targetMarketVersionId,
            teamA,
            teamC,
            initialHistory,
            t1Cutoff,
            targetScheduledAt,
            false,
            OddsPricingKernel.DEFAULT_MARGIN);

    assertThat(snapshotBefore.admissibleResultsCount()).isEqualTo(5);
    assertThat(snapshotBefore.latestConfirmedVersionId()).isEqualTo(v1Id);
    assertThat(snapshotBefore.output().homeRating()).isGreaterThan(new BigDecimal("1500"));
    assertThat(snapshotBefore.output().sampleSize()).isEqualTo(5);
    assertThat(snapshotBefore.output().confidenceBand()).isEqualTo(ConfidenceEstimator.Band.MEDIUM);

    // At T2, result is corrected: Team A actually lost 1 x 3.
    UUID v2Id = UUID.randomUUID();
    ConfirmedSportsResultForOdds v2 =
        new ConfirmedSportsResultForOdds(
            v2Id,
            historicalEventId,
            TEST_COMPETITION,
            TEST_EDITION,
            2,
            ConfirmedSportsResultForOdds.VersionStatus.CORRIGIDO,
            SportsResultKindForOdds.SPORTING_RESULT,
            List.of(new Score(1, 1, teamA, "Team A"), new Score(2, 3, teamC, "Team C")),
            t2Known,
            t2Known,
            sportTime,
            v1Id);

    List<ConfirmedSportsResultForOdds> fullHistory =
        List.of(baseline.get(0), baseline.get(1), baseline.get(2), baseline.get(3), v1, v2);

    // Replay at T1 cutoff using full history containing both V1 and V2:
    // Because V2.knownAt > T1 cutoff, V2 is ignored and V1 is reproduced identically.
    ReplaySnapshot snapshotT1After =
        replayService.replayWithLineage(
            targetEventId,
            targetMarketVersionId,
            teamA,
            teamC,
            fullHistory,
            t1Cutoff,
            targetScheduledAt,
            false,
            OddsPricingKernel.DEFAULT_MARGIN);

    assertThat(snapshotT1After.output().fairProbabilities())
        .isEqualTo(snapshotBefore.output().fairProbabilities());
    assertThat(snapshotT1After.output().publishedOdds())
        .isEqualTo(snapshotBefore.output().publishedOdds());
    assertThat(snapshotT1After.output().homeRating())
        .isEqualByComparingTo(snapshotBefore.output().homeRating());
    assertThat(snapshotT1After.output().awayRating())
        .isEqualByComparingTo(snapshotBefore.output().awayRating());
    assertThat(snapshotT1After.output().configurationDigest())
        .isEqualTo(snapshotBefore.output().configurationDigest());
    assertThat(snapshotT1After.output().inputSnapshot())
        .isEqualTo(snapshotBefore.output().inputSnapshot());
    assertThat(snapshotT1After.latestConfirmedVersionId()).isEqualTo(v1Id);
    assertThat(snapshotT1After.admissibleResultsCount()).isEqualTo(5);

    // Replay at T2 cutoff: V2 is now active and supersedes V1.
    ReplaySnapshot snapshotT2 =
        replayService.replayWithLineage(
            targetEventId,
            targetMarketVersionId,
            teamA,
            teamC,
            fullHistory,
            t2Cutoff,
            targetScheduledAt,
            false,
            OddsPricingKernel.DEFAULT_MARGIN);

    assertThat(snapshotT2.latestConfirmedVersionId()).isEqualTo(v2Id);
    assertThat(snapshotT2.admissibleResultsCount()).isEqualTo(5);
    assertThat(snapshotT2.output().sampleSize()).isEqualTo(5);
    assertThat(snapshotT2.output().homeRating()).isLessThan(snapshotBefore.output().homeRating());
    assertThat(snapshotT2.output().fairProbability())
        .isLessThan(snapshotBefore.output().fairProbability());
  }

  @Test
  void sportingToAdministrativeCorrectionExcludesEventFromRating() {
    Instant tb1 = Instant.parse("2026-10-01T14:00:00Z");
    Instant tb2 = Instant.parse("2026-10-02T14:00:00Z");
    Instant tb3 = Instant.parse("2026-10-03T14:00:00Z");
    Instant tb4 = Instant.parse("2026-10-04T14:00:00Z");

    List<ConfirmedSportsResultForOdds> baseline =
        List.of(
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 2, teamA, "Team A"), new Score(2, 0, teamC, "Team C")),
                tb1,
                tb1,
                tb1,
                null),
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 1, teamA, "Team A"), new Score(2, 0, teamC, "Team C")),
                tb2,
                tb2,
                tb2,
                null),
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 2, teamA, "Team A"), new Score(2, 1, teamC, "Team C")),
                tb3,
                tb3,
                tb3,
                null),
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 3, teamA, "Team A"), new Score(2, 0, teamC, "Team C")),
                tb4,
                tb4,
                tb4,
                null));

    UUID historicalEventId = UUID.randomUUID();
    Instant sportTime = Instant.parse("2026-10-10T14:00:00Z");
    Instant t1Known = Instant.parse("2026-10-10T16:00:00Z");
    Instant t1Cutoff = Instant.parse("2026-10-10T17:00:00Z");
    Instant t2Known = Instant.parse("2026-10-11T10:00:00Z");
    Instant t2Cutoff = Instant.parse("2026-10-11T12:00:00Z");

    UUID v1Id = UUID.randomUUID();
    ConfirmedSportsResultForOdds v1 =
        new ConfirmedSportsResultForOdds(
            v1Id,
            historicalEventId,
            TEST_COMPETITION,
            TEST_EDITION,
            1,
            ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
            SportsResultKindForOdds.SPORTING_RESULT,
            List.of(new Score(1, 2, teamA, "Team A"), new Score(2, 0, teamC, "Team C")),
            t1Known,
            t1Known,
            sportTime,
            null);

    // V2 is an administrative walkover (e.g. overturned administratively).
    UUID v2Id = UUID.randomUUID();
    ConfirmedSportsResultForOdds v2 =
        new ConfirmedSportsResultForOdds(
            v2Id,
            historicalEventId,
            TEST_COMPETITION,
            TEST_EDITION,
            2,
            ConfirmedSportsResultForOdds.VersionStatus.CORRIGIDO,
            SportsResultKindForOdds.ADMINISTRATIVE_WALKOVER,
            List.of(),
            t2Known,
            t2Known,
            sportTime,
            v1Id);

    List<ConfirmedSportsResultForOdds> fullHistory =
        List.of(baseline.get(0), baseline.get(1), baseline.get(2), baseline.get(3), v1, v2);

    // As of T1: V1 was active (sample size = 5, rating adjusted).
    OddsPricingOutput outputT1 =
        replayService.replay(
            targetEventId,
            targetMarketVersionId,
            teamA,
            teamC,
            fullHistory,
            t1Cutoff,
            targetScheduledAt,
            false);
    assertThat(outputT1.sampleSize()).isEqualTo(5);
    assertThat(outputT1.confidenceBand()).isEqualTo(ConfidenceEstimator.Band.MEDIUM);
    assertThat(outputT1.homeRating()).isGreaterThan(new BigDecimal("1500"));

    // As of T2: V2 is active, but ADMINISTRATIVE_WALKOVER does not feed rating.
    // Resulting sporting sample size drops to 4 (< 5) and ratings revert to neutral 1500.
    OddsPricingOutput outputT2 =
        replayService.replay(
            targetEventId,
            targetMarketVersionId,
            teamA,
            teamC,
            fullHistory,
            t2Cutoff,
            targetScheduledAt,
            false);
    assertThat(outputT2.sampleSize()).isEqualTo(4);
    assertThat(outputT2.homeRating()).isEqualByComparingTo("1500");
    assertThat(outputT2.awayRating()).isEqualByComparingTo("1500");
    assertThat(outputT2.confidenceBand()).isEqualTo(ConfidenceEstimator.Band.LOW);
    assertThat(outputT2.fairProbabilities())
        .containsExactly(new BigDecimal("0.5000000000"), new BigDecimal("0.5000000000"));
  }

  @Test
  void administrativeToSportingCorrectionIncludesEventInRating() {
    Instant tb1 = Instant.parse("2026-10-01T14:00:00Z");
    Instant tb2 = Instant.parse("2026-10-02T14:00:00Z");
    Instant tb3 = Instant.parse("2026-10-03T14:00:00Z");
    Instant tb4 = Instant.parse("2026-10-04T14:00:00Z");

    List<ConfirmedSportsResultForOdds> baseline =
        List.of(
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 2, teamA, "Team A"), new Score(2, 0, teamC, "Team C")),
                tb1,
                tb1,
                tb1,
                null),
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 1, teamA, "Team A"), new Score(2, 0, teamC, "Team C")),
                tb2,
                tb2,
                tb2,
                null),
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 2, teamA, "Team A"), new Score(2, 1, teamC, "Team C")),
                tb3,
                tb3,
                tb3,
                null),
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 3, teamA, "Team A"), new Score(2, 0, teamC, "Team C")),
                tb4,
                tb4,
                tb4,
                null));

    UUID historicalEventId = UUID.randomUUID();
    Instant sportTime = Instant.parse("2026-10-10T14:00:00Z");
    Instant t1Known = Instant.parse("2026-10-10T16:00:00Z");
    Instant t1Cutoff = Instant.parse("2026-10-10T17:00:00Z");
    Instant t2Known = Instant.parse("2026-10-11T10:00:00Z");
    Instant t2Cutoff = Instant.parse("2026-10-11T12:00:00Z");

    UUID v1Id = UUID.randomUUID();
    ConfirmedSportsResultForOdds v1 =
        new ConfirmedSportsResultForOdds(
            v1Id,
            historicalEventId,
            TEST_COMPETITION,
            TEST_EDITION,
            1,
            ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
            SportsResultKindForOdds.ADMINISTRATIVE_WALKOVER,
            List.of(),
            t1Known,
            t1Known,
            sportTime,
            null);

    UUID v2Id = UUID.randomUUID();
    ConfirmedSportsResultForOdds v2 =
        new ConfirmedSportsResultForOdds(
            v2Id,
            historicalEventId,
            TEST_COMPETITION,
            TEST_EDITION,
            2,
            ConfirmedSportsResultForOdds.VersionStatus.CORRIGIDO,
            SportsResultKindForOdds.SPORTING_RESULT,
            List.of(new Score(1, 3, teamA, "Team A"), new Score(2, 1, teamC, "Team C")),
            t2Known,
            t2Known,
            sportTime,
            v1Id);

    List<ConfirmedSportsResultForOdds> fullHistory =
        List.of(baseline.get(0), baseline.get(1), baseline.get(2), baseline.get(3), v1, v2);

    // As of T1: Administrative result only -> 4 sporting results, neutral ratings (sample size 4 <
    // 5).
    OddsPricingOutput outputT1 =
        replayService.replay(
            targetEventId,
            targetMarketVersionId,
            teamA,
            teamC,
            fullHistory,
            t1Cutoff,
            targetScheduledAt,
            false);
    assertThat(outputT1.sampleSize()).isEqualTo(4);
    assertThat(outputT1.confidenceBand()).isEqualTo(ConfidenceEstimator.Band.LOW);
    assertThat(outputT1.homeRating()).isEqualByComparingTo("1500");

    // As of T2: Corrected to sporting result -> 5 sporting results, rating updated.
    OddsPricingOutput outputT2 =
        replayService.replay(
            targetEventId,
            targetMarketVersionId,
            teamA,
            teamC,
            fullHistory,
            t2Cutoff,
            targetScheduledAt,
            false);
    assertThat(outputT2.sampleSize()).isEqualTo(5);
    assertThat(outputT2.confidenceBand()).isEqualTo(ConfidenceEstimator.Band.MEDIUM);
    assertThat(outputT2.homeRating()).isGreaterThan(new BigDecimal("1500"));
    assertThat(outputT2.awayRating()).isLessThan(new BigDecimal("1500"));
  }

  @Test
  void versionedDrawUnderRegulationProducesDeterministicThreeWayLineage() {
    Instant tb1 = Instant.parse("2026-10-01T14:00:00Z");
    Instant tb2 = Instant.parse("2026-10-02T14:00:00Z");
    Instant tb3 = Instant.parse("2026-10-03T14:00:00Z");
    Instant tb4 = Instant.parse("2026-10-04T14:00:00Z");

    List<ConfirmedSportsResultForOdds> baseline =
        List.of(
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 1, teamA, "Team A"), new Score(2, 1, teamB, "Team B")),
                tb1,
                tb1,
                tb1,
                null),
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 0, teamA, "Team A"), new Score(2, 0, teamB, "Team B")),
                tb2,
                tb2,
                tb2,
                null),
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 2, teamA, "Team A"), new Score(2, 2, teamB, "Team B")),
                tb3,
                tb3,
                tb3,
                null),
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 1, teamA, "Team A"), new Score(2, 1, teamB, "Team B")),
                tb4,
                tb4,
                tb4,
                null));

    UUID historicalEventId = UUID.randomUUID();
    Instant sportTime = Instant.parse("2026-10-10T14:00:00Z");
    Instant t1Known = Instant.parse("2026-10-10T16:00:00Z");
    Instant t1Cutoff = Instant.parse("2026-10-10T17:00:00Z");
    Instant t2Known = Instant.parse("2026-10-11T10:00:00Z");
    Instant t2Cutoff = Instant.parse("2026-10-11T12:00:00Z");

    UUID v1Id = UUID.randomUUID();
    ConfirmedSportsResultForOdds v1 =
        new ConfirmedSportsResultForOdds(
            v1Id,
            historicalEventId,
            TEST_COMPETITION,
            TEST_EDITION,
            1,
            ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
            SportsResultKindForOdds.SPORTING_RESULT,
            List.of(new Score(1, 1, teamA, "Team A"), new Score(2, 1, teamB, "Team B")),
            t1Known,
            t1Known,
            sportTime,
            null);

    UUID v2Id = UUID.randomUUID();
    ConfirmedSportsResultForOdds v2 =
        new ConfirmedSportsResultForOdds(
            v2Id,
            historicalEventId,
            TEST_COMPETITION,
            TEST_EDITION,
            2,
            ConfirmedSportsResultForOdds.VersionStatus.CORRIGIDO,
            SportsResultKindForOdds.SPORTING_RESULT,
            List.of(new Score(1, 2, teamA, "Team A"), new Score(2, 1, teamB, "Team B")),
            t2Known,
            t2Known,
            sportTime,
            v1Id);

    List<ConfirmedSportsResultForOdds> fullHistory =
        List.of(baseline.get(0), baseline.get(1), baseline.get(2), baseline.get(3), v1, v2);

    // Target match allows draw (3-way market).
    OddsPricingOutput outputT1 =
        replayService.replay(
            targetEventId,
            targetMarketVersionId,
            teamA,
            teamB,
            fullHistory,
            t1Cutoff,
            targetScheduledAt,
            true);

    assertThat(outputT1.sampleSize()).isEqualTo(5);
    assertThat(outputT1.fairProbabilities()).hasSize(3);
    assertThat(outputT1.publishedOdds()).hasSize(3);
    // After 5 draws between equal ratings, ratings remain 1500 each.
    assertThat(outputT1.homeRating()).isEqualByComparingTo("1500");
    assertThat(outputT1.awayRating()).isEqualByComparingTo("1500");
    assertThat(outputT1.fairProbabilities().get(1)).isEqualByComparingTo("0.2000000000");

    // At T2, corrected to Team A win.
    OddsPricingOutput outputT2 =
        replayService.replay(
            targetEventId,
            targetMarketVersionId,
            teamA,
            teamB,
            fullHistory,
            t2Cutoff,
            targetScheduledAt,
            true);

    assertThat(outputT2.sampleSize()).isEqualTo(5);
    assertThat(outputT2.fairProbabilities()).hasSize(3);
    assertThat(outputT2.homeRating()).isGreaterThan(new BigDecimal("1500"));
    assertThat(outputT2.awayRating()).isLessThan(new BigDecimal("1500"));
    assertThat(outputT2.fairProbabilities().get(0))
        .isGreaterThan(outputT1.fairProbabilities().get(0));
  }

  @Test
  void multiVersionSupersessionChainResolvesProperActiveVersionAtEachCutoff() {
    Instant tb0 = Instant.parse("2026-09-30T00:00:00Z");
    Instant tb1 = Instant.parse("2026-10-01T14:00:00Z");
    Instant tb2 = Instant.parse("2026-10-02T14:00:00Z");
    Instant tb3 = Instant.parse("2026-10-03T14:00:00Z");
    Instant tb4 = Instant.parse("2026-10-04T14:00:00Z");

    UUID b4Id = UUID.randomUUID();
    List<ConfirmedSportsResultForOdds> baseline =
        List.of(
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 1, teamA, "Team A"), new Score(2, 1, teamC, "Team C")),
                tb1,
                tb1,
                tb1,
                null),
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 0, teamA, "Team A"), new Score(2, 0, teamC, "Team C")),
                tb2,
                tb2,
                tb2,
                null),
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 2, teamA, "Team A"), new Score(2, 2, teamC, "Team C")),
                tb3,
                tb3,
                tb3,
                null),
            new ConfirmedSportsResultForOdds(
                b4Id,
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 1, teamA, "Team A"), new Score(2, 1, teamC, "Team C")),
                tb4,
                tb4,
                tb4,
                null));

    UUID historicalEventId = UUID.randomUUID();
    Instant sportTime = Instant.parse("2026-10-10T14:00:00Z");
    Instant t0 = Instant.parse("2026-10-10T13:00:00Z");
    Instant t1 = Instant.parse("2026-10-10T16:00:00Z");
    Instant t2 = Instant.parse("2026-10-11T10:00:00Z");
    Instant t3 = Instant.parse("2026-10-12T10:00:00Z");

    UUID v1Id = UUID.randomUUID();
    ConfirmedSportsResultForOdds v1 =
        new ConfirmedSportsResultForOdds(
            v1Id,
            historicalEventId,
            TEST_COMPETITION,
            TEST_EDITION,
            1,
            ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
            SportsResultKindForOdds.SPORTING_RESULT,
            List.of(new Score(1, 3, teamA, "Team A"), new Score(2, 0, teamC, "Team C")),
            t1,
            t1,
            sportTime,
            null);

    UUID v2Id = UUID.randomUUID();
    ConfirmedSportsResultForOdds v2 =
        new ConfirmedSportsResultForOdds(
            v2Id,
            historicalEventId,
            TEST_COMPETITION,
            TEST_EDITION,
            2,
            ConfirmedSportsResultForOdds.VersionStatus.CORRIGIDO,
            SportsResultKindForOdds.SPORTING_RESULT,
            List.of(new Score(1, 2, teamA, "Team A"), new Score(2, 2, teamC, "Team C")),
            t2,
            t2,
            sportTime,
            v1Id);

    UUID v3Id = UUID.randomUUID();
    ConfirmedSportsResultForOdds v3 =
        new ConfirmedSportsResultForOdds(
            v3Id,
            historicalEventId,
            TEST_COMPETITION,
            TEST_EDITION,
            3,
            ConfirmedSportsResultForOdds.VersionStatus.CORRIGIDO,
            SportsResultKindForOdds.SPORTING_RESULT,
            List.of(new Score(1, 0, teamA, "Team A"), new Score(2, 2, teamC, "Team C")),
            t3,
            t3,
            sportTime,
            v2Id);

    List<ConfirmedSportsResultForOdds> allFacts =
        List.of(baseline.get(0), baseline.get(1), baseline.get(2), baseline.get(3), v1, v2, v3);

    // Before tb1 (no results yet): 0 results
    ReplaySnapshot snapPrior =
        replayService.replayWithLineage(
            targetEventId,
            targetMarketVersionId,
            teamA,
            teamC,
            allFacts,
            tb0,
            targetScheduledAt,
            false,
            OddsPricingKernel.DEFAULT_MARGIN);
    assertThat(snapPrior.admissibleResultsCount()).isZero();
    assertThat(snapPrior.latestConfirmedVersionId()).isNull();

    // Before T1 (after baseline matches): 4 results, neutral estimate for sample < 5
    ReplaySnapshot snap0 =
        replayService.replayWithLineage(
            targetEventId,
            targetMarketVersionId,
            teamA,
            teamC,
            allFacts,
            t0,
            targetScheduledAt,
            false,
            OddsPricingKernel.DEFAULT_MARGIN);
    assertThat(snap0.admissibleResultsCount()).isEqualTo(4);
    assertThat(snap0.latestConfirmedVersionId()).isEqualTo(b4Id);
    assertThat(snap0.output().sampleSize()).isEqualTo(4);
    assertThat(snap0.output().homeRating()).isEqualByComparingTo("1500");

    // At T1 (before T2): V1 active (win -> 5th match, sample size 5, rating increases)
    ReplaySnapshot snap1 =
        replayService.replayWithLineage(
            targetEventId,
            targetMarketVersionId,
            teamA,
            teamC,
            allFacts,
            t1.plusSeconds(60),
            targetScheduledAt,
            false,
            OddsPricingKernel.DEFAULT_MARGIN);
    assertThat(snap1.admissibleResultsCount()).isEqualTo(5);
    assertThat(snap1.latestConfirmedVersionId()).isEqualTo(v1Id);
    assertThat(snap1.output().sampleSize()).isEqualTo(5);
    assertThat(snap1.output().homeRating()).isGreaterThan(new BigDecimal("1500"));

    // At T2 (before T3): V2 active (draw -> 5 draws, ratings remain 1500)
    ReplaySnapshot snap2 =
        replayService.replayWithLineage(
            targetEventId,
            targetMarketVersionId,
            teamA,
            teamC,
            allFacts,
            t2.plusSeconds(60),
            targetScheduledAt,
            false,
            OddsPricingKernel.DEFAULT_MARGIN);
    assertThat(snap2.admissibleResultsCount()).isEqualTo(5);
    assertThat(snap2.latestConfirmedVersionId()).isEqualTo(v2Id);
    assertThat(snap2.output().sampleSize()).isEqualTo(5);
    assertThat(snap2.output().homeRating()).isEqualByComparingTo("1500");

    // At T3: V3 active (loss -> 5th match loss, rating decreases)
    ReplaySnapshot snap3 =
        replayService.replayWithLineage(
            targetEventId,
            targetMarketVersionId,
            teamA,
            teamC,
            allFacts,
            t3.plusSeconds(60),
            targetScheduledAt,
            false,
            OddsPricingKernel.DEFAULT_MARGIN);
    assertThat(snap3.admissibleResultsCount()).isEqualTo(5);
    assertThat(snap3.latestConfirmedVersionId()).isEqualTo(v3Id);
    assertThat(snap3.output().sampleSize()).isEqualTo(5);
    assertThat(snap3.output().homeRating()).isLessThan(new BigDecimal("1500"));
  }

  @Test
  void supersessionValidationRejectsCyclesAndCrossEventSupersession() {
    UUID event1 = UUID.randomUUID();
    UUID event2 = UUID.randomUUID();
    Instant now = Instant.parse("2026-10-10T12:00:00Z");

    UUID v1 = UUID.randomUUID();
    UUID v2 = UUID.randomUUID();

    ConfirmedSportsResultForOdds crossEventFact =
        new ConfirmedSportsResultForOdds(
            v2,
            event2,
            TEST_COMPETITION,
            TEST_EDITION,
            2,
            ConfirmedSportsResultForOdds.VersionStatus.CORRIGIDO,
            SportsResultKindForOdds.SPORTING_RESULT,
            List.of(new Score(1, 1, teamA, "A"), new Score(2, 0, teamB, "B")),
            now,
            now,
            now,
            v1);

    ConfirmedSportsResultForOdds event1Fact =
        new ConfirmedSportsResultForOdds(
            v1,
            event1,
            TEST_COMPETITION,
            TEST_EDITION,
            1,
            ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
            SportsResultKindForOdds.SPORTING_RESULT,
            List.of(new Score(1, 1, teamA, "A"), new Score(2, 0, teamB, "B")),
            now,
            now,
            now,
            null);

    OddsInputFilter filter = new OddsInputFilter();

    assertThatThrownBy(
            () ->
                filter.filterAdmissibleResults(
                    List.of(event1Fact, crossEventFact), now.plusSeconds(60)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("outcome supersession crosses event boundaries");

    // Forked chain: two facts supersede the same v1 at the same cutoff.
    UUID v3 = UUID.randomUUID();
    ConfirmedSportsResultForOdds v3Fact =
        new ConfirmedSportsResultForOdds(
            v3,
            event1,
            TEST_COMPETITION,
            TEST_EDITION,
            3,
            ConfirmedSportsResultForOdds.VersionStatus.CORRIGIDO,
            SportsResultKindForOdds.SPORTING_RESULT,
            List.of(new Score(1, 2, teamA, "A"), new Score(2, 0, teamB, "B")),
            now,
            now,
            now,
            v1);

    ConfirmedSportsResultForOdds v2SameEvent =
        new ConfirmedSportsResultForOdds(
            v2,
            event1,
            TEST_COMPETITION,
            TEST_EDITION,
            2,
            ConfirmedSportsResultForOdds.VersionStatus.CORRIGIDO,
            SportsResultKindForOdds.SPORTING_RESULT,
            List.of(new Score(1, 1, teamA, "A"), new Score(2, 0, teamB, "B")),
            now,
            now,
            now,
            v1);

    assertThatThrownBy(
            () ->
                filter.filterAdmissibleResults(
                    List.of(event1Fact, v2SameEvent, v3Fact), now.plusSeconds(60)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("outcome replay contains a forked supersession chain");

    // Supersession cycle: v1 -> v2 -> v1
    ConfirmedSportsResultForOdds cycleFact1 =
        new ConfirmedSportsResultForOdds(
            v1,
            event1,
            TEST_COMPETITION,
            TEST_EDITION,
            1,
            ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
            SportsResultKindForOdds.SPORTING_RESULT,
            List.of(new Score(1, 1, teamA, "A"), new Score(2, 0, teamB, "B")),
            now,
            now,
            now,
            v2);

    ConfirmedSportsResultForOdds cycleFact2 =
        new ConfirmedSportsResultForOdds(
            v2,
            event1,
            TEST_COMPETITION,
            TEST_EDITION,
            2,
            ConfirmedSportsResultForOdds.VersionStatus.CORRIGIDO,
            SportsResultKindForOdds.SPORTING_RESULT,
            List.of(new Score(1, 1, teamA, "A"), new Score(2, 0, teamB, "B")),
            now,
            now,
            now,
            v1);

    assertThatThrownBy(
            () ->
                filter.filterAdmissibleResults(
                    List.of(cycleFact1, cycleFact2), now.plusSeconds(60)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("outcome replay contains a supersession cycle");
  }

  @Test
  void postponedAndCancelledEventsNeverContaminateLineage() {
    UUID eventId = UUID.randomUUID();
    Instant now = Instant.parse("2026-10-10T12:00:00Z");

    ConfirmedSportsResultForOdds cancelled =
        new ConfirmedSportsResultForOdds(
            UUID.randomUUID(),
            eventId,
            TEST_COMPETITION,
            TEST_EDITION,
            1,
            ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
            SportsResultKindForOdds.CANCELLED,
            List.of(),
            now,
            now,
            now,
            null);

    ConfirmedSportsResultForOdds postponed =
        new ConfirmedSportsResultForOdds(
            UUID.randomUUID(),
            UUID.randomUUID(),
            TEST_COMPETITION,
            TEST_EDITION,
            1,
            ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
            SportsResultKindForOdds.POSTPONED,
            List.of(),
            now,
            now,
            now,
            null);

    OddsPricingOutput output =
        replayService.replay(
            targetEventId,
            targetMarketVersionId,
            teamA,
            teamB,
            List.of(cancelled, postponed),
            now.plusSeconds(60),
            targetScheduledAt,
            false);

    assertThat(output.sampleSize()).isZero();
    assertThat(output.homeRating()).isEqualByComparingTo("1500");
    assertThat(output.awayRating()).isEqualByComparingTo("1500");
  }

  @Test
  void replayFromPortDelegatesSeamlessly() {
    Instant now = Instant.parse("2026-10-10T12:00:00Z");
    Instant t1 = Instant.parse("2026-10-01T14:00:00Z");
    Instant t2 = Instant.parse("2026-10-02T14:00:00Z");
    Instant t3 = Instant.parse("2026-10-03T14:00:00Z");
    Instant t4 = Instant.parse("2026-10-04T14:00:00Z");
    Instant t5 = Instant.parse("2026-10-05T14:00:00Z");

    List<ConfirmedSportsResultForOdds> history =
        List.of(
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                TEST_EDITION,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 2, teamA, "Team A"), new Score(2, 0, teamB, "Team B")),
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
                List.of(new Score(1, 1, teamA, "Team A"), new Score(2, 0, teamB, "Team B")),
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
                List.of(new Score(1, 3, teamA, "Team A"), new Score(2, 1, teamB, "Team B")),
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
                List.of(new Score(1, 2, teamA, "Team A"), new Score(2, 0, teamB, "Team B")),
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
                List.of(new Score(1, 2, teamA, "Team A"), new Score(2, 1, teamB, "Team B")),
                t5,
                t5,
                t5,
                null));

    SportsEventForOdds targetEvent =
        new SportsEventForOdds(
            targetEventId,
            TEST_COMPETITION,
            TEST_EDITION,
            UUID.randomUUID(),
            UUID.randomUUID(),
            targetScheduledAt,
            "UTC",
            SportsEventForOdds.EventFormat.HEAD_TO_HEAD,
            List.of(
                new SportsEventForOdds.Slot(1, "A", teamA, "Team A"),
                new SportsEventForOdds.Slot(2, "B", teamB, "Team B")));

    SportsResultForOddsPort port =
        new SportsResultForOddsPort() {
          @Override
          public Optional<SportsEventForOdds> getEventForOdds(UUID eventId) {
            return eventId.equals(targetEventId) ? Optional.of(targetEvent) : Optional.empty();
          }

          @Override
          public Optional<ConfirmedSportsResultForOdds> getConfirmedResultForOdds(
              UUID eventId, UUID confirmedVersionId) {
            return Optional.empty();
          }

          @Override
          public List<ConfirmedSportsResultForOdds> findAdmissibleResultsAsOf(
              UUID competitionId, UUID editionId, Instant asOfCutoff) {
            return history;
          }
        };

    OddsPricingOutput output =
        replayService.replayFromPort(
            port, targetEventId, targetMarketVersionId, false, now.plusSeconds(60));

    assertThat(output.sampleSize()).isEqualTo(5);
    assertThat(output.confidenceBand()).isEqualTo(ConfidenceEstimator.Band.MEDIUM);
    assertThat(output.homeRating()).isGreaterThan(new BigDecimal("1500"));
    assertThat(output.awayRating()).isLessThan(new BigDecimal("1500"));
    assertThat(output.publishedOdds().get(0)).isLessThan(output.publishedOdds().get(1));
  }

  @Test
  void editionRegressionAppliesFiftyPercentMeanReversionAcrossEditions() {
    UUID edition1 = UUID.randomUUID();
    UUID edition2 = UUID.randomUUID();
    Instant t1 = Instant.parse("2026-10-01T14:00:00Z");
    Instant t2 = Instant.parse("2026-10-02T14:00:00Z");
    Instant t3 = Instant.parse("2026-10-03T14:00:00Z");
    Instant t4 = Instant.parse("2026-10-04T14:00:00Z");
    Instant t5 = Instant.parse("2026-10-05T14:00:00Z");
    Instant t6 = Instant.parse("2026-10-10T14:00:00Z");

    List<ConfirmedSportsResultForOdds> edition1Results =
        List.of(
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                edition1,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 2, teamA, "Team A"), new Score(2, 0, teamC, "Team C")),
                t1,
                t1,
                t1,
                null),
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                edition1,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 2, teamA, "Team A"), new Score(2, 0, teamC, "Team C")),
                t2,
                t2,
                t2,
                null),
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                edition1,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 2, teamA, "Team A"), new Score(2, 0, teamC, "Team C")),
                t3,
                t3,
                t3,
                null),
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                edition1,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 2, teamA, "Team A"), new Score(2, 0, teamC, "Team C")),
                t4,
                t4,
                t4,
                null),
            new ConfirmedSportsResultForOdds(
                UUID.randomUUID(),
                UUID.randomUUID(),
                TEST_COMPETITION,
                edition1,
                1,
                ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                SportsResultKindForOdds.SPORTING_RESULT,
                List.of(new Score(1, 2, teamA, "Team A"), new Score(2, 0, teamC, "Team C")),
                t5,
                t5,
                t5,
                null));

    ConfirmedSportsResultForOdds ed2Game1 =
        new ConfirmedSportsResultForOdds(
            UUID.randomUUID(),
            UUID.randomUUID(),
            TEST_COMPETITION,
            edition2,
            1,
            ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
            SportsResultKindForOdds.SPORTING_RESULT,
            List.of(new Score(1, 1, teamA, "Team A"), new Score(2, 1, teamB, "Team B")),
            t6,
            t6,
            t6,
            null);

    List<ConfirmedSportsResultForOdds> allEditions = new java.util.ArrayList<>(edition1Results);
    allEditions.add(ed2Game1);

    EloRatingEngine elo = new EloRatingEngine();
    EloRatingEngine.RatingSnapshot snapshot = elo.rate(allEditions, teamA, teamB);
    assertThat(snapshot.homeRating()).isBetween(new BigDecimal("1525"), new BigDecimal("1540"));
    assertThat(elo.regressForEdition(new BigDecimal("1700"))).isEqualByComparingTo("1600.0");
  }
}
