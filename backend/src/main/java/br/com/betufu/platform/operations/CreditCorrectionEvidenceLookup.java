package br.com.betufu.platform.operations;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Neutral read contract for a monetary fact supporting an administrative correction. */
public interface CreditCorrectionEvidenceLookup {
  enum Type {
    LEDGER_ENTRY,
    PREDICTION,
    SETTLEMENT
  }

  enum InverseEffect {
    CREDIT,
    RECOVERY
  }

  record Fact(
      UUID identityId, BigDecimal amount, InverseEffect inverseEffect, UUID originalEntryId) {
    public Fact {
      Objects.requireNonNull(identityId);
      Objects.requireNonNull(amount);
      Objects.requireNonNull(inverseEffect);
    }
  }

  Type type();

  Optional<Fact> find(UUID evidenceId);
}
