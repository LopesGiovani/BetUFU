package br.com.betufu.odds;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.betufu.odds.internal.market.MarketState;
import br.com.betufu.odds.internal.market.MarketTransition;
import org.junit.jupiter.api.Test;

class OddsMarketIT {
  @Test
  void draftCanBePublishedOnlyThroughTheApprovedTransition() {
    assertThat(MarketTransition.apply(MarketState.DRAFT, MarketTransition.Action.PUBLISH_MARKET))
        .isEqualTo(MarketState.OPEN);
    assertThat(
            MarketTransition.isAllowed(MarketState.DRAFT, MarketTransition.Action.SUSPEND_MARKET))
        .isFalse();
  }
}
