package br.com.betufu.odds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.odds.internal.market.MarketState;
import br.com.betufu.odds.internal.override.OddsCorrectionAttempt;
import br.com.betufu.odds.internal.override.OddsOverridePolicy;
import br.com.betufu.odds.internal.persistence.OddsOverrideRecord;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OddsOverrideIT {
  @Test
  void correctionAttemptCarriesAnExplicitDelta() {
    OddsCorrectionAttempt attempt =
        new OddsCorrectionAttempt(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("2.00"),
            new BigDecimal("2.25"),
            "ticket-42",
            Instant.parse("2026-01-01T00:00:00Z"));
    assertThat(attempt.delta()).isEqualByComparingTo("0.25");
  }

  @Test
  void proposalRequiresSuspendedMarketAndApprovalRequiresDifferentActor() {
    OddsOverridePolicy policy = new OddsOverridePolicy();
    OddsCorrectionAttempt attempt =
        new OddsCorrectionAttempt(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("2.00"),
            new BigDecimal("2.25"),
            "evidence",
            Instant.now());
    assertThatThrownBy(() -> policy.requireProposalAllowed(MarketState.OPEN, attempt))
        .isInstanceOf(OddsOverridePolicy.PolicyViolation.class);
    UUID proposer = UUID.randomUUID();
    OddsOverrideRecord proposal =
        new OddsOverrideRecord(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            proposer,
            null,
            "v1|2|2.25|ZXZpZGVuY2U|cmVhc29u",
            "PROPOSED",
            Instant.now());
    assertThatThrownBy(
            () -> policy.requireApprovalAllowed(MarketState.SUSPENDED, proposal, proposer))
        .isInstanceOf(OddsOverridePolicy.PolicyViolation.class)
        .extracting("violation")
        .isEqualTo(OddsOverridePolicy.Violation.SELF_APPROVAL);
  }
}
