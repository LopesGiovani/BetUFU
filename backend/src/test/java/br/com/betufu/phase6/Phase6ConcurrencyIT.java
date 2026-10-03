package br.com.betufu.phase6;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class Phase6ConcurrencyIT extends Phase6Fixture {
  @Test
  void simultaneousAuthorizationsAndProcessorsConvergeToOnePayoutAndRanking() throws Exception {
    List<UUID> authorizations = new java.util.concurrent.CopyOnWriteArrayList<>();
    race(() -> authorizations.add(authorize("shared-authorization")));
    assertThat(authorizations).hasSize(4).containsOnly(authorizations.getFirst());
    UUID authorization = authorizations.getFirst();
    race(() -> processOrBusy(authorization));
    service.process(authorization);
    race(() -> ranking.findLifetimeRanking());
    assertThat(ranking.findLifetimeRanking())
        .singleElement()
        .satisfies(entry -> assertThat(entry.wins()).isEqualTo(1));
    assertThat(ledgerEntries("PAYOUT")).isEqualTo(1);
    assertThat(ledger.getBalance(bettorId).balance()).isEqualByComparingTo("1100.00");
    assertFinancialIntegrity();
  }

  @Test
  void concurrentRefundRetriesAndFutureCreditsRecoverSpentPayoutExactlyOnce() throws Exception {
    service.process(authorize("initial-payout"));
    ledger.debit(bettorId, new BigDecimal("1050.00"), UUID.randomUUID());
    cancelEvent();
    UUID authorization = authorize("cancel-spent-payout");
    race(() -> processOrBusy(authorization));
    service.process(authorization);
    assertThat(ledger.getBalance(bettorId).balance()).isEqualByComparingTo("0.00");
    assertThat(ledgerEntries("REFUND")).isEqualTo(1);
    UUID futureCredit = UUID.randomUUID();
    race(() -> ledger.payout(bettorId, new BigDecimal("100.00"), futureCredit));
    assertThat(ledger.getBalance(bettorId).balance()).isEqualByComparingTo("50.00");
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM public.credit_correction_case", Long.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT sum(amount) FROM public.credit_correction_allocation", BigDecimal.class))
        .isEqualByComparingTo("150.00");
    assertThat(ranking.findLifetimeRanking()).isEmpty();
    assertFinancialIntegrity();
  }

  @Test
  void concurrentAchievementGrantAndRevocationRetainOneOriginalLineage() throws Exception {
    service.process(authorize("achievement-win"));
    BigDecimal before = ledger.getBalance(bettorId).balance();
    race(this::evaluateAchievements);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.achievement_award", Long.class))
        .isEqualTo(1);
    assertThat(ledger.getBalance(bettorId).balance()).isEqualByComparingTo(before);
    UUID originalAward =
        jdbc.queryForObject("SELECT award_id FROM public.achievement_award", UUID.class);
    cancelEvent();
    service.process(authorize("achievement-refund"));
    race(this::evaluateAchievements);
    assertThat(
            jdbc.queryForObject(
                "SELECT state FROM public.achievement_award WHERE award_id = ?",
                String.class,
                originalAward))
        .isEqualTo("REVOKED");
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM public.achievement_revocation", Long.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.achievement_award", Long.class))
        .isEqualTo(1);
    assertFinancialIntegrity();
  }

  private void processOrBusy(UUID authorization) {
    try {
      service.process(authorization);
    } catch (IllegalStateException busy) {
      if (!"settlement case is already being processed".equals(busy.getMessage())) throw busy;
    }
  }

  private void race(Runnable action) throws Exception {
    CountDownLatch ready = new CountDownLatch(4);
    CountDownLatch start = new CountDownLatch(1);
    try (var workers = Executors.newFixedThreadPool(4)) {
      List<Future<?>> futures = new ArrayList<>();
      for (int i = 0; i < 4; i++) {
        futures.add(
            workers.submit(
                () -> {
                  ready.countDown();
                  if (!start.await(10, TimeUnit.SECONDS))
                    throw new IllegalStateException("race start timed out");
                  action.run();
                  return null;
                }));
      }
      try {
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        for (Future<?> future : futures) future.get(30, TimeUnit.SECONDS);
      } finally {
        start.countDown();
        workers.shutdownNow();
      }
    }
  }
}
