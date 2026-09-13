package br.com.betufu.ledger;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/** Public ledger boundary consumed by other modules without exposing ledger internals. */
public interface CreditLedgerPort {

  BigDecimal INITIAL_GRANT = new BigDecimal("1000.00");
  BigDecimal MINIMUM_STAKE = new BigDecimal("10.00");

  enum EntryType {
    GRANT_INITIAL,
    DEBIT,
    COMPENSATION
  }

  /** Opaque source categories accepted by the ledger. */
  enum SourceType {
    SYSTEM,
    PREDICTION,
    COMPENSATION
  }

  record LedgerOperation(UUID entryId, CreditBalance balance, boolean replayed) {
    public LedgerOperation {
      Objects.requireNonNull(entryId, "entryId must not be null");
      Objects.requireNonNull(balance, "balance must not be null");
    }
  }

  CreditBalance getBalance(UUID identityId);

  CreditBalance ensureInitialGrant(UUID identityId);

  LedgerOperation debit(UUID identityId, BigDecimal amount, UUID sourceId);

  LedgerOperation debit(UUID identityId, BigDecimal amount, String sourceType, UUID sourceId);

  LedgerOperation compensate(UUID identityId, BigDecimal amount, UUID sourceId);

  LedgerOperation compensate(
      UUID identityId, BigDecimal amount, String sourceType, UUID sourceId);
}
