package br.com.betufu.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class AchievementIdempotencyIT extends AchievementFixture {
  @Test
  void repeatedEvaluationDoesNotDuplicateAnAwardOrFinancialEntry() {
    UUID person = identity("retry", true);
    decision(person, "WON");
    decision(person, "WON");
    decision(person, "WON");

    achievements.findByIdentity(person);
    achievements.findByIdentity(person);
    achievements.reconcile();

    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.achievement_award", Long.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM public.achievement_revocation", Long.class))
        .isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.credit_ledger_entry", Long.class))
        .isZero();
  }
}
