package br.com.betufu.odds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.odds.internal.model.ConfidenceEstimator;
import br.com.betufu.odds.internal.model.OddsInputFilter;
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
 * Tests verifying explicit result taxonomy and exclusion of non-sporting outcomes from rating
 * (ODDS-04, ODDS-08, D-109, D-110, D-111, T-04-TAXONOMY).
 */
class OddsResultTaxonomyTest {

  private static final UUID TEST_COMPETITION =
      UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID TEST_EDITION = UUID.fromString("00000000-0000-0000-0000-000000000002");

  private OddsInputFilter filter;
  private OddsReplayService replayService;
  private UUID teamA;
  private UUID teamB;
  private UUID eventId;
  private UUID marketVersionId;

  @BeforeEach
  void setUp() {
    filter = new OddsInputFilter();
    replayService = new OddsReplayService(filter, new OddsPricingKernel());
    teamA = UUID.randomUUID();
    teamB = UUID.randomUUID();
    eventId = UUID.randomUUID();
    marketVersionId = UUID.randomUUID();
  }

  @Test
  void administrativeWalkoverNeverFeedsRatingAndCannotCarryScore() {
    Instant now = Instant.parse("2026-10-10T12:00:00Z");

    // Administrative outcome cannot carry scores (enforced by ConfirmedSportsResultForOdds)
    assertThatThrownBy(
            () ->
                new ConfirmedSportsResultForOdds(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    TEST_COMPETITION,
                    TEST_EDITION,
                    1,
                    ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                    SportsResultKindForOdds.ADMINISTRATIVE_WALKOVER,
                    List.of(new Score(1, 20, teamA, "A"), new Score(2, 0, teamB, "B")),
                    now,
                    now,
                    now,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("administrative and lifecycle facts cannot carry scores");

    // Valid administrative walkover without scores
    ConfirmedSportsResultForOdds walkover =
        new ConfirmedSportsResultForOdds(
            UUID.randomUUID(),
            UUID.randomUUID(),
            TEST_COMPETITION,
            TEST_EDITION,
            1,
            ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
            SportsResultKindForOdds.ADMINISTRATIVE_WALKOVER,
            List.of(),
            now,
            now,
            now,
            null);

    assertThat(SportsResultKindForOdds.ADMINISTRATIVE_WALKOVER.feedsRating()).isFalse();
    assertThat(SportsResultKindForOdds.ADMINISTRATIVE_WALKOVER.hasSportsScore()).isFalse();
    assertThat(OddsInputFilter.isAdmissibleSportingResult(walkover)).isFalse();

    List<ConfirmedSportsResultForOdds> filtered =
        filter.filterAdmissibleResults(List.of(walkover), now.plusSeconds(60));
    assertThat(filtered).isEmpty();

    OddsPricingOutput output =
        replayService.replay(
            eventId,
            marketVersionId,
            teamA,
            teamB,
            List.of(walkover),
            now.plusSeconds(60),
            now.plusSeconds(3600),
            false);

    assertThat(output.sampleSize()).isZero();
    assertThat(output.homeRating()).isEqualByComparingTo("1500");
    assertThat(output.awayRating()).isEqualByComparingTo("1500");
    assertThat(output.confidenceBand()).isEqualTo(ConfidenceEstimator.Band.LOW);
  }

  @Test
  void abandonedNoValidResultIsExcludedFromRatingEngine() {
    Instant now = Instant.parse("2026-10-10T12:00:00Z");

    ConfirmedSportsResultForOdds abandoned =
        new ConfirmedSportsResultForOdds(
            UUID.randomUUID(),
            UUID.randomUUID(),
            TEST_COMPETITION,
            TEST_EDITION,
            1,
            ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
            SportsResultKindForOdds.ABANDONED_NO_VALID_RESULT,
            List.of(),
            now,
            now,
            now,
            null);

    assertThat(SportsResultKindForOdds.ABANDONED_NO_VALID_RESULT.feedsRating()).isFalse();
    assertThat(SportsResultKindForOdds.ABANDONED_NO_VALID_RESULT.hasSportsScore()).isFalse();
    assertThat(OddsInputFilter.isAdmissibleSportingResult(abandoned)).isFalse();

    List<ConfirmedSportsResultForOdds> filtered =
        filter.filterAdmissibleResults(List.of(abandoned), now.plusSeconds(60));
    assertThat(filtered).isEmpty();
  }

  @Test
  void cancelledAndPostponedLifecycleEventsAreExcludedFromRatingEngine() {
    Instant now = Instant.parse("2026-10-10T12:00:00Z");

    ConfirmedSportsResultForOdds cancelled =
        new ConfirmedSportsResultForOdds(
            UUID.randomUUID(),
            UUID.randomUUID(),
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

    assertThat(cancelled.resultKind().isLifecycle()).isTrue();
    assertThat(cancelled.resultKind().feedsRating()).isFalse();
    assertThat(postponed.resultKind().isLifecycle()).isTrue();
    assertThat(postponed.resultKind().feedsRating()).isFalse();

    assertThat(OddsInputFilter.isAdmissibleSportingResult(cancelled)).isFalse();
    assertThat(OddsInputFilter.isAdmissibleSportingResult(postponed)).isFalse();

    List<ConfirmedSportsResultForOdds> filtered =
        filter.filterAdmissibleResults(List.of(cancelled, postponed), now.plusSeconds(60));
    assertThat(filtered).isEmpty();
  }

  @Test
  void sportingResultWithValidScoresFeedsRatingCorrectly() {
    Instant now = Instant.parse("2026-10-10T12:00:00Z");

    ConfirmedSportsResultForOdds confirmado =
        new ConfirmedSportsResultForOdds(
            UUID.randomUUID(),
            UUID.randomUUID(),
            TEST_COMPETITION,
            TEST_EDITION,
            1,
            ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
            SportsResultKindForOdds.SPORTING_RESULT,
            List.of(new Score(1, 2, teamA, "Team A"), new Score(2, 1, teamB, "Team B")),
            now,
            now,
            now,
            null);

    ConfirmedSportsResultForOdds corrigido =
        new ConfirmedSportsResultForOdds(
            UUID.randomUUID(),
            UUID.randomUUID(),
            TEST_COMPETITION,
            TEST_EDITION,
            2,
            ConfirmedSportsResultForOdds.VersionStatus.CORRIGIDO,
            SportsResultKindForOdds.SPORTING_RESULT,
            List.of(new Score(1, 3, teamA, "Team A"), new Score(2, 0, teamB, "Team B")),
            now.plusSeconds(60),
            now.plusSeconds(60),
            now,
            null);

    assertThat(confirmado.resultKind().feedsRating()).isTrue();
    assertThat(confirmado.resultKind().hasSportsScore()).isTrue();
    assertThat(OddsInputFilter.isAdmissibleSportingResult(confirmado)).isTrue();
    assertThat(OddsInputFilter.isAdmissibleSportingResult(corrigido)).isTrue();

    List<ConfirmedSportsResultForOdds> filtered =
        filter.filterAdmissibleResults(List.of(confirmado, corrigido), now.plusSeconds(120));
    assertThat(filtered).hasSize(2);
  }

  @Test
  void mixedTournamentTaxonomyFiltersToExactSportingSubset() {
    Instant t1 = Instant.parse("2026-10-01T12:00:00Z");
    Instant t2 = Instant.parse("2026-10-02T12:00:00Z");
    Instant t3 = Instant.parse("2026-10-03T12:00:00Z");
    Instant t4 = Instant.parse("2026-10-04T12:00:00Z");
    Instant t5 = Instant.parse("2026-10-05T12:00:00Z");
    Instant t6 = Instant.parse("2026-10-06T12:00:00Z");
    Instant t7 = Instant.parse("2026-10-07T12:00:00Z");
    Instant t8 = Instant.parse("2026-10-08T12:00:00Z");
    Instant t9 = Instant.parse("2026-10-09T12:00:00Z");
    Instant t10 = Instant.parse("2026-10-09T18:00:00Z");
    Instant t11 = Instant.parse("2026-10-09T20:00:00Z");

    UUID event1 = UUID.randomUUID();
    UUID event2 = UUID.randomUUID();
    UUID event3 = UUID.randomUUID();
    UUID event4 = UUID.randomUUID();
    UUID event5 = UUID.randomUUID();
    UUID event6 = UUID.randomUUID();
    UUID event7 = UUID.randomUUID();
    UUID event8 = UUID.randomUUID();
    UUID event9 = UUID.randomUUID();
    UUID event10 = UUID.randomUUID();
    UUID event11 = UUID.randomUUID();

    ConfirmedSportsResultForOdds sport1 =
        new ConfirmedSportsResultForOdds(
            UUID.randomUUID(),
            event1,
            TEST_COMPETITION,
            TEST_EDITION,
            1,
            ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
            SportsResultKindForOdds.SPORTING_RESULT,
            List.of(new Score(1, 1, teamA, "A"), new Score(2, 0, teamB, "B")),
            t1,
            t1,
            t1,
            null);

    ConfirmedSportsResultForOdds admin1 =
        new ConfirmedSportsResultForOdds(
            UUID.randomUUID(),
            event2,
            TEST_COMPETITION,
            TEST_EDITION,
            1,
            ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
            SportsResultKindForOdds.ADMINISTRATIVE_WALKOVER,
            List.of(),
            t2,
            t2,
            t2,
            null);

    ConfirmedSportsResultForOdds sport2 =
        new ConfirmedSportsResultForOdds(
            UUID.randomUUID(),
            event3,
            TEST_COMPETITION,
            TEST_EDITION,
            1,
            ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
            SportsResultKindForOdds.SPORTING_RESULT,
            List.of(new Score(1, 2, teamA, "A"), new Score(2, 2, teamB, "B")),
            t3,
            t3,
            t3,
            null);

    ConfirmedSportsResultForOdds abandoned1 =
        new ConfirmedSportsResultForOdds(
            UUID.randomUUID(),
            event4,
            TEST_COMPETITION,
            TEST_EDITION,
            1,
            ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
            SportsResultKindForOdds.ABANDONED_NO_VALID_RESULT,
            List.of(),
            t4,
            t4,
            t4,
            null);

    ConfirmedSportsResultForOdds cancelled1 =
        new ConfirmedSportsResultForOdds(
            UUID.randomUUID(),
            event5,
            TEST_COMPETITION,
            TEST_EDITION,
            1,
            ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
            SportsResultKindForOdds.CANCELLED,
            List.of(),
            t5,
            t5,
            t5,
            null);

    ConfirmedSportsResultForOdds sport3 =
        new ConfirmedSportsResultForOdds(
            UUID.randomUUID(),
            event6,
            TEST_COMPETITION,
            TEST_EDITION,
            1,
            ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
            SportsResultKindForOdds.SPORTING_RESULT,
            List.of(new Score(1, 0, teamA, "A"), new Score(2, 1, teamB, "B")),
            t6,
            t6,
            t6,
            null);

    ConfirmedSportsResultForOdds postponed1 =
        new ConfirmedSportsResultForOdds(
            UUID.randomUUID(),
            event7,
            TEST_COMPETITION,
            TEST_EDITION,
            1,
            ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
            SportsResultKindForOdds.POSTPONED,
            List.of(),
            t7,
            t7,
            t7,
            null);

    ConfirmedSportsResultForOdds admin2 =
        new ConfirmedSportsResultForOdds(
            UUID.randomUUID(),
            event8,
            TEST_COMPETITION,
            TEST_EDITION,
            1,
            ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
            SportsResultKindForOdds.ADMINISTRATIVE_WALKOVER,
            List.of(),
            t8,
            t8,
            t8,
            null);

    ConfirmedSportsResultForOdds sport4 =
        new ConfirmedSportsResultForOdds(
            UUID.randomUUID(),
            event9,
            TEST_COMPETITION,
            TEST_EDITION,
            1,
            ConfirmedSportsResultForOdds.VersionStatus.CORRIGIDO,
            SportsResultKindForOdds.SPORTING_RESULT,
            List.of(new Score(1, 3, teamA, "A"), new Score(2, 0, teamB, "B")),
            t9,
            t9,
            t9,
            null);

    ConfirmedSportsResultForOdds admin3 =
        new ConfirmedSportsResultForOdds(
            UUID.randomUUID(),
            event10,
            TEST_COMPETITION,
            TEST_EDITION,
            1,
            ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
            SportsResultKindForOdds.ADMINISTRATIVE_WALKOVER,
            List.of(),
            t10,
            t10,
            t10,
            null);

    ConfirmedSportsResultForOdds sport5 =
        new ConfirmedSportsResultForOdds(
            UUID.randomUUID(),
            event11,
            TEST_COMPETITION,
            TEST_EDITION,
            1,
            ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
            SportsResultKindForOdds.SPORTING_RESULT,
            List.of(new Score(1, 2, teamA, "A"), new Score(2, 0, teamB, "B")),
            t11,
            t11,
            t11,
            null);

    List<ConfirmedSportsResultForOdds> allMixed =
        List.of(
            sport1,
            admin1,
            sport2,
            abandoned1,
            cancelled1,
            sport3,
            postponed1,
            admin2,
            sport4,
            admin3,
            sport5);

    List<ConfirmedSportsResultForOdds> filtered =
        filter.filterAdmissibleResults(allMixed, Instant.parse("2026-10-10T00:00:00Z"));

    // Exactly the 5 sporting results remain, in canonical chronological order
    assertThat(filtered).hasSize(5);
    assertThat(filtered.get(0).versionId()).isEqualTo(sport1.versionId());
    assertThat(filtered.get(1).versionId()).isEqualTo(sport2.versionId());
    assertThat(filtered.get(2).versionId()).isEqualTo(sport3.versionId());
    assertThat(filtered.get(3).versionId()).isEqualTo(sport4.versionId());
    assertThat(filtered.get(4).versionId()).isEqualTo(sport5.versionId());

    OddsPricingOutput output =
        replayService.replay(
            eventId,
            marketVersionId,
            teamA,
            teamB,
            allMixed,
            Instant.parse("2026-10-10T00:00:00Z"),
            Instant.parse("2026-10-15T00:00:00Z"),
            false);

    assertThat(output.sampleSize()).isEqualTo(5);
    assertThat(output.confidenceBand()).isEqualTo(ConfidenceEstimator.Band.MEDIUM);
    assertThat(output.homeRating()).isGreaterThan(new BigDecimal("1500"));
    assertThat(output.awayRating()).isLessThan(new BigDecimal("1500"));
  }

  @Test
  void scoresValidationEnforcesOrderedSlotsAndNonNegativeScores() {
    UUID team = UUID.randomUUID();
    assertThatThrownBy(() -> new Score(0, 1, team, "A"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("slotNo must be positive");

    assertThatThrownBy(() -> new Score(1, -1, team, "A"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("score must not be negative");

    assertThatThrownBy(() -> new Score(1, 1, team, "  "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("displayLabel must not be blank");
  }

  @Test
  void taxonomyEnumHelperMethodsExhaustiveCheck() {
    assertThat(SportsResultKindForOdds.SPORTING_RESULT.feedsRating()).isTrue();
    assertThat(SportsResultKindForOdds.SPORTING_RESULT.hasSportsScore()).isTrue();
    assertThat(SportsResultKindForOdds.SPORTING_RESULT.isLifecycle()).isFalse();

    assertThat(SportsResultKindForOdds.ADMINISTRATIVE_WALKOVER.feedsRating()).isFalse();
    assertThat(SportsResultKindForOdds.ADMINISTRATIVE_WALKOVER.hasSportsScore()).isFalse();
    assertThat(SportsResultKindForOdds.ADMINISTRATIVE_WALKOVER.isLifecycle()).isFalse();

    assertThat(SportsResultKindForOdds.ABANDONED_NO_VALID_RESULT.feedsRating()).isFalse();
    assertThat(SportsResultKindForOdds.ABANDONED_NO_VALID_RESULT.hasSportsScore()).isFalse();
    assertThat(SportsResultKindForOdds.ABANDONED_NO_VALID_RESULT.isLifecycle()).isFalse();

    assertThat(SportsResultKindForOdds.CANCELLED.feedsRating()).isFalse();
    assertThat(SportsResultKindForOdds.CANCELLED.hasSportsScore()).isFalse();
    assertThat(SportsResultKindForOdds.CANCELLED.isLifecycle()).isTrue();

    assertThat(SportsResultKindForOdds.POSTPONED.feedsRating()).isFalse();
    assertThat(SportsResultKindForOdds.POSTPONED.hasSportsScore()).isFalse();
    assertThat(SportsResultKindForOdds.POSTPONED.isLifecycle()).isTrue();
  }
}
