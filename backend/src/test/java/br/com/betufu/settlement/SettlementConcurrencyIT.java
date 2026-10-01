package br.com.betufu.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class SettlementConcurrencyIT extends SettlementFixture {
  @Test
  void concurrentProcessorsCannotDuplicatePayoutOrUnit() throws Exception {
    UUID authorization = service.authorize(eventId, administratorId, "auth-concurrent");
    CountDownLatch start = new CountDownLatch(1);
    try (var workers = Executors.newFixedThreadPool(2)) {
      Future<?> first = workers.submit(() -> processWhenReleased(authorization, start));
      Future<?> second = workers.submit(() -> processWhenReleased(authorization, start));
      start.countDown();
      first.get(30, TimeUnit.SECONDS);
      second.get(30, TimeUnit.SECONDS);
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM public.settlement_case", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM public.prediction_settlement", Integer.class))
        .isEqualTo(1);
    assertThat(ledgerEntries("PAYOUT")).isEqualTo(1);
    assertThat(ledger.getBalance(bettorId).balance()).isEqualByComparingTo("1056.00");
  }

  private void processWhenReleased(UUID authorization, CountDownLatch start) {
    try {
      start.await();
      service.process(authorization);
    } catch (IllegalStateException busy) {
      if (!busy.getMessage().contains("already being processed")) throw busy;
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }
}
