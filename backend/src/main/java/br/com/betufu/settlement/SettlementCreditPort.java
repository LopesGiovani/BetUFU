package br.com.betufu.settlement;

import java.math.BigDecimal;
import java.util.UUID;

/** Consumer-owned credit effects; the ledger module supplies the implementation. */
public interface SettlementCreditPort {
  UUID payout(UUID identityId, BigDecimal amount, UUID settlementId);

  UUID refund(UUID identityId, BigDecimal amount, UUID settlementId);
}
