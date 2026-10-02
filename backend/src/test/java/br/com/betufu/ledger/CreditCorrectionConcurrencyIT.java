package br.com.betufu.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.betufu.ledger.CreditCorrectionModels.Kind;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CreditCorrectionConcurrencyIT extends CreditCorrectionFixture {
  @Test
  void concurrentCreditsSettleOnePendingCaseWithoutOverRecovery() throws Exception {
    corrections.approve(checker, propose(Kind.RECOVERY, "60.00", "concurrent").caseId());
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<?> first =
          executor.submit(
              () -> {
                start.await();
                ledger.payout(owner, new BigDecimal("40.00"), UUID.randomUUID());
                return null;
              });
      Future<?> second =
          executor.submit(
              () -> {
                start.await();
                ledger.payout(owner, new BigDecimal("40.00"), UUID.randomUUID());
                return null;
              });
      start.countDown();
      first.get();
      second.get();
    } finally {
      executor.shutdownNow();
    }
    assertThat(ledger.getBalance(owner).balance()).isEqualByComparingTo("20.00");
    assertThat(corrections.hasPending(owner)).isFalse();
    assertThat(recoveryEntries()).isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT coalesce(sum(amount), 0.00) FROM public.credit_correction_allocation",
                BigDecimal.class))
        .isEqualByComparingTo("60.00");
  }
}
