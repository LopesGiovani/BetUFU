package br.com.betufu.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class RankingCalculationIT extends RankingFixture {
  @Test
  void ordersWinsThenRateAndSharesCompetitionPositionForRemainingTies() {
    UUID leader = identity("leader", true);
    UUID tiedA = identity("tied-a", true);
    UUID tiedB = identity("tied-b", true);
    UUID fourth = identity("fourth", true);
    decision(leader, "WON");
    decision(leader, "WON");
    decision(tiedA, "WON");
    decision(tiedA, "WON");
    decision(tiedA, "LOST");
    decision(tiedB, "WON");
    decision(tiedB, "WON");
    decision(tiedB, "LOST");
    decision(fourth, "WON");
    decision(fourth, "REFUNDED");

    List<RankingModels.Entry> entries = service.findLifetimeRanking();
    assertThat(entries).extracting(RankingModels.Entry::position).containsExactly(1, 2, 2, 4);
    assertThat(entries.getFirst().nickname()).isEqualTo("leader");
    assertThat(entries.subList(1, 3))
        .extracting(RankingModels.Entry::nickname)
        .containsExactlyInAnyOrder("tied-a", "tied-b");
    assertThat(entries.get(3).nickname()).isEqualTo("fourth");
    assertThat(entries.getFirst().hitRate()).isEqualByComparingTo("1.000000");
    assertThat(entries.get(1).hitRate()).isEqualByComparingTo("0.666667");
    assertThat(entries.get(3).losses()).isZero();
    assertThat(service.findMemberMetrics(tiedB).orElseThrow().position()).isEqualTo(2);
  }

  @Test
  void rebuildFollowsCorrectionAndHidesOldResultWhileNewCaseIsIncomplete() {
    UUID person = identity("corrected", true);
    DecisionFixture first = decision(person, "WON");
    assertThat(service.findLifetimeRanking().getFirst().wins()).isEqualTo(1);
    UUID replacement = correctedCase(first.event(), 2, true);
    UUID previousId =
        jdbc.queryForObject(
            "SELECT settlement_id FROM public.prediction_settlement WHERE case_id = ?",
            UUID.class,
            first.caseId());
    insertUnit(replacement, first.predictionId(), person, "LOST", previousId);

    assertThat(service.findLifetimeRanking().getFirst().wins()).isZero();
    assertThat(service.findLifetimeRanking().getFirst().losses()).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.ranking_projection", Long.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.prediction_settlement", Long.class))
        .isEqualTo(2);

    correctedCase(first.event(), 3, false);
    assertThat(service.findLifetimeRanking()).isEmpty();
  }

  @Test
  void excludesPredictionsAcceptedBeforeAnyValidEnrollment() {
    UUID person = identity("late-enroll", false);
    decision(person, "WON");
    enroll(person, java.time.Instant.now());
    assertThat(service.findLifetimeRanking()).isEmpty();
  }
}
