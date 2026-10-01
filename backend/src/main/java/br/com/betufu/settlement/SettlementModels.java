package br.com.betufu.settlement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable, minimal settlement facts shared with the next processing plan. */
public final class SettlementModels {
  private SettlementModels() {}

  public enum AuthorizationDecision {
    APPROVED,
    REJECTED
  }

  public enum SettlementReason {
    CONFIRMED_RESULT,
    CORRECTED_RESULT,
    ADMINISTRATIVE_WALKOVER,
    NO_VALID_RESULT,
    EVENT_CANCELLED
  }

  public record Authorization(
      UUID authorizationId,
      UUID eventId,
      UUID outcomeFactId,
      UUID confirmedVersionId,
      UUID authorizedByIdentityId,
      AuthorizationDecision decision,
      Instant authorizedAt,
      String idempotencyKey) {
    public Authorization {
      Objects.requireNonNull(authorizationId);
      Objects.requireNonNull(eventId);
      Objects.requireNonNull(outcomeFactId);
      Objects.requireNonNull(authorizedByIdentityId);
      Objects.requireNonNull(decision);
      Objects.requireNonNull(authorizedAt);
      requireText(idempotencyKey);
    }
  }

  public record SettlementCase(
      UUID caseId,
      UUID authorizationId,
      UUID eventId,
      UUID outcomeFactId,
      int processingVersion,
      String ruleVersion,
      Instant createdAt) {
    public SettlementCase {
      Objects.requireNonNull(caseId);
      Objects.requireNonNull(authorizationId);
      Objects.requireNonNull(eventId);
      Objects.requireNonNull(outcomeFactId);
      if (processingVersion < 1)
        throw new IllegalArgumentException("processingVersion must be positive");
      requireText(ruleVersion);
      Objects.requireNonNull(createdAt);
    }
  }

  public record Unit(
      UUID settlementId,
      UUID caseId,
      UUID predictionId,
      UUID identityId,
      SettlementState state,
      SettlementReason reason,
      BigDecimal amount,
      UUID supersedesSettlementId,
      String idempotencyKey,
      Instant createdAt) {
    public Unit {
      Objects.requireNonNull(settlementId);
      Objects.requireNonNull(caseId);
      Objects.requireNonNull(predictionId);
      Objects.requireNonNull(identityId);
      Objects.requireNonNull(state);
      Objects.requireNonNull(reason);
      Objects.requireNonNull(amount);
      if (amount.signum() < 0 || amount.scale() != 2) {
        throw new IllegalArgumentException("amount must be non-negative numeric(20,2)");
      }
      requireText(idempotencyKey);
      Objects.requireNonNull(createdAt);
    }
  }

  public static BigDecimal monetaryAmount(BigDecimal value) {
    return Objects.requireNonNull(value).setScale(2, RoundingMode.HALF_UP);
  }

  private static void requireText(String value) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("text is required");
  }
}
