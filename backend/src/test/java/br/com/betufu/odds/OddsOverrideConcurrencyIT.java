package br.com.betufu.odds;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.betufu.odds.internal.market.MarketState;
import br.com.betufu.odds.internal.market.MarketTransition;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class OddsOverrideConcurrencyIT {
  @Test
  void onlyOneCompareAndSetWinnerCanAdvanceAStaleLock() throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger winner = new AtomicInteger();
    Runnable attempt =
        () -> {
          try {
            start.await();
            if (winner.compareAndSet(0, 1)) {
              assertThat(
                      MarketTransition.apply(
                          MarketState.SUSPENDED, MarketTransition.Action.RESUME_MARKET))
                  .isEqualTo(MarketState.OPEN);
            }
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
          }
        };
    Thread first = new Thread(attempt);
    Thread second = new Thread(attempt);
    first.start();
    second.start();
    start.countDown();
    first.join();
    second.join();
    assertThat(winner).hasValue(1);
  }
}
