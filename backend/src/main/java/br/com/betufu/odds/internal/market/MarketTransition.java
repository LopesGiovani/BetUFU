package br.com.betufu.odds.internal.market;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Pure transition table for the odds market state machine. */
public record MarketTransition(
    MarketState from, MarketState to, Action action, String requiredCapability) {

  public static final String MANAGE_ODDS_MARKET = "MANAGE_ODDS_MARKET";

  private static final Map<Key, MarketTransition> TRANSITIONS =
      Map.ofEntries(
          entry(MarketState.DRAFT, MarketState.OPEN, Action.PUBLISH_MARKET),
          entry(MarketState.DRAFT, MarketState.CANCELLED, Action.CANCEL_DRAFT),
          entry(MarketState.OPEN, MarketState.SUSPENDED, Action.SUSPEND_MARKET),
          entry(MarketState.OPEN, MarketState.CLOSED, Action.CLOSE_DEADLINE),
          entry(MarketState.OPEN, MarketState.CANCELLED, Action.CANCEL_MARKET),
          entry(MarketState.SUSPENDED, MarketState.OPEN, Action.RESUME_MARKET),
          entry(MarketState.SUSPENDED, MarketState.CLOSED, Action.CLOSE_SUSPENDED),
          entry(MarketState.SUSPENDED, MarketState.CANCELLED, Action.CANCEL_SUSPENDED),
          entry(MarketState.CLOSED, MarketState.LOCKED, Action.LOCK_MARKET),
          entry(MarketState.CLOSED, MarketState.SUSPENDED, Action.REOPEN_SUSPENDED),
          entry(MarketState.CLOSED, MarketState.CANCELLED, Action.CANCEL_CLOSED),
          entry(MarketState.LOCKED, MarketState.CANCELLED, Action.VOID_LOCKED));

  public MarketTransition {
    Objects.requireNonNull(from, "from must not be null");
    Objects.requireNonNull(to, "to must not be null");
    Objects.requireNonNull(action, "action must not be null");
    if (!MANAGE_ODDS_MARKET.equals(requiredCapability)) {
      throw new IllegalArgumentException("requiredCapability must be MANAGE_ODDS_MARKET");
    }
  }

  private static Map.Entry<Key, MarketTransition> entry(
      MarketState from, MarketState to, Action action) {
    return Map.entry(
        new Key(from, action), new MarketTransition(from, to, action, MANAGE_ODDS_MARKET));
  }

  public static Optional<MarketTransition> find(MarketState from, Action action) {
    return Optional.ofNullable(TRANSITIONS.get(new Key(from, action)));
  }

  public static boolean isAllowed(MarketState from, Action action) {
    return find(from, action).isPresent();
  }

  public static MarketState apply(MarketState from, Action action) {
    return find(from, action)
        .map(MarketTransition::to)
        .orElseThrow(() -> new IllegalArgumentException("FORBIDDEN_TRANSITION"));
  }

  public static MarketState apply(
      MarketState from, Action action, Instant now, Instant closeDeadline) {
    Objects.requireNonNull(now, "now must not be null");
    Objects.requireNonNull(closeDeadline, "closeDeadline must not be null");
    if ((action == Action.CLOSE_DEADLINE || action == Action.CLOSE_SUSPENDED)
        && now.isBefore(closeDeadline)) {
      throw new IllegalArgumentException("CLOSE_DEADLINE_NOT_REACHED");
    }
    return apply(from, action);
  }

  private record Key(MarketState from, Action action) {}

  public enum Action {
    PUBLISH_MARKET,
    CANCEL_DRAFT,
    SUSPEND_MARKET,
    CLOSE_DEADLINE,
    CANCEL_MARKET,
    RESUME_MARKET,
    CLOSE_SUSPENDED,
    CANCEL_SUSPENDED,
    LOCK_MARKET,
    REOPEN_SUSPENDED,
    CANCEL_CLOSED,
    VOID_LOCKED
  }
}
