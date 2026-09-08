package br.com.betufu.odds.internal.market;

import java.time.Instant;
import java.util.Objects;

/** Central policy wrapper that keeps deadline and transition validation deterministic. */
public final class MarketLifecyclePolicy {
  public MarketState next(
      MarketState current, MarketTransition.Action action, Instant now, Instant closeDeadline) {
    Objects.requireNonNull(current, "current must not be null");
    Objects.requireNonNull(action, "action must not be null");
    return MarketTransition.apply(
        current, action, now, closeDeadline == null ? now : closeDeadline);
  }

  public boolean allows(MarketState current, MarketTransition.Action action) {
    return current != null && action != null && MarketTransition.isAllowed(current, action);
  }
}
