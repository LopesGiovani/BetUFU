package br.com.betufu.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class AchievementRevocationIT extends AchievementFixture {
  @Test
  void correctionRevokesWithoutDeletingOriginalGrantOrRepeatingRevocation() {
    UUID person = identity("corrected", true);
    decision(person, "WON");
    decision(person, "WON");
    DecisionFixture third = decision(person, "WON");
    UUID originalAward = achievements.findByIdentity(person).getFirst().awardId();
    UUID previousSettlement =
        jdbc.queryForObject(
            "SELECT settlement_id FROM public.prediction_settlement WHERE case_id = ?",
            UUID.class,
            third.caseId());
    UUID replacementCase = correctedCase(third.event(), 2, true);
    insertUnit(replacementCase, third.predictionId(), person, "LOST", previousSettlement);

    assertThat(achievements.findByIdentity(person))
        .singleElement()
        .satisfies(
            award -> {
              assertThat(award.awardId()).isEqualTo(originalAward);
              assertThat(award.state()).isEqualTo(AchievementQueryPort.AwardState.REVOKED);
              assertThat(award.revokedAt()).isNotNull();
              assertThat(award.revocationReason()).isNotBlank();
            });
    achievements.findByIdentity(person);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.achievement_award", Long.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM public.achievement_revocation", Long.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.prediction_settlement", Long.class))
        .isEqualTo(4);
  }

  @Test
  void laterCorrectedWinCreatesNewLineageWithoutReactivatingRevokedAward() {
    UUID person = identity("recovered", true);
    decision(person, "WON");
    decision(person, "WON");
    DecisionFixture third = decision(person, "WON");
    UUID originalAward = achievements.findByIdentity(person).getFirst().awardId();
    UUID firstUnit =
        jdbc.queryForObject(
            "SELECT settlement_id FROM public.prediction_settlement WHERE case_id = ?",
            UUID.class,
            third.caseId());
    UUID lostCase = correctedCase(third.event(), 2, true);
    insertUnit(lostCase, third.predictionId(), person, "LOST", firstUnit);
    achievements.findByIdentity(person);
    UUID lostUnit =
        jdbc.queryForObject(
            "SELECT settlement_id FROM public.prediction_settlement WHERE case_id = ?",
            UUID.class,
            lostCase);
    UUID wonCase = correctedCase(third.event(), 3, true);
    insertUnit(wonCase, third.predictionId(), person, "WON", lostUnit);

    assertThat(achievements.findByIdentity(person))
        .hasSize(2)
        .extracting(AchievementQueryPort.Achievement::state)
        .containsExactlyInAnyOrder(
            AchievementQueryPort.AwardState.AWARDED, AchievementQueryPort.AwardState.REVOKED);
    assertThat(
            jdbc.queryForObject(
                "SELECT state FROM public.achievement_award WHERE award_id = ?",
                String.class,
                originalAward))
        .isEqualTo("REVOKED");
  }
}
