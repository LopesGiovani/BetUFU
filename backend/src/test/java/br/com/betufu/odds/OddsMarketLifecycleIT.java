package br.com.betufu.odds;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.odds.internal.market.MarketLifecyclePolicy;
import br.com.betufu.odds.internal.market.MarketState;
import br.com.betufu.odds.internal.market.MarketTransition;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OddsMarketLifecycleIT {
  @Test
  void publishWithoutDeadlineIsRejected() {
    assertThatThrownBy(
            () ->
                new MarketLifecyclePolicy()
                    .next(
                        MarketState.DRAFT,
                        MarketTransition.Action.PUBLISH_MARKET,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("CLOSE_DEADLINE_REQUIRED");
  }

  @Test
  void closeBeforeDeadlineIsRejectedWithoutAStateTransition() {
    assertThatThrownBy(
            () ->
                new MarketLifecyclePolicy()
                    .next(
                        MarketState.OPEN,
                        MarketTransition.Action.CLOSE_DEADLINE,
                        Instant.parse("2026-01-01T00:00:00Z"),
                        Instant.parse("2026-01-02T00:00:00Z")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
