package br.com.betufu.odds.internal.override;

import br.com.betufu.odds.internal.market.MarketState;
import br.com.betufu.odds.internal.persistence.OddsOverrideRecord;
import java.util.Objects;
import java.util.UUID;

/** Pure maker-checker policy, independent from HTTP and persistence details. */
public final class OddsOverridePolicy {

  public void requireProposalAllowed(MarketState state, OddsCorrectionAttempt attempt) {
    Objects.requireNonNull(attempt, "correction attempt must not be null");
    if (state != MarketState.SUSPENDED) {
      throw new PolicyViolation(
          terminal(state) ? Violation.FINAL_STATE : Violation.MARKET_NOT_SUSPENDED);
    }
  }

  public void requireApprovalAllowed(
      MarketState state, OddsOverrideRecord proposal, UUID approverId) {
    Objects.requireNonNull(proposal, "proposal must not be null");
    Objects.requireNonNull(approverId, "approverId must not be null");
    if (state != MarketState.SUSPENDED) {
      throw new PolicyViolation(
          terminal(state) ? Violation.FINAL_STATE : Violation.MARKET_NOT_SUSPENDED);
    }
    if (!"PROPOSED".equals(proposal.decision())) {
      throw new PolicyViolation(Violation.NOT_PENDING);
    }
    if (approverId.equals(proposal.proposerId())) {
      throw new PolicyViolation(Violation.SELF_APPROVAL);
    }
  }

  private static boolean terminal(MarketState state) {
    return state == MarketState.CLOSED
        || state == MarketState.LOCKED
        || state == MarketState.CANCELLED;
  }

  public enum Violation {
    MARKET_NOT_SUSPENDED,
    FINAL_STATE,
    NOT_PENDING,
    SELF_APPROVAL
  }

  public static final class PolicyViolation extends RuntimeException {
    private final Violation violation;

    public PolicyViolation(Violation violation) {
      super(Objects.requireNonNull(violation).name());
      this.violation = violation;
    }

    public Violation violation() {
      return violation;
    }
  }
}
