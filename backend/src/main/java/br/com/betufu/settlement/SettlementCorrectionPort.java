package br.com.betufu.settlement;

import java.math.BigDecimal;
import java.util.UUID;

/** Consumer-owned recovery boundary for a superseded financial settlement. */
public interface SettlementCorrectionPort {
  void recoverPreviousCredit(
      UUID identityId, UUID previousSettlementId, UUID replacementSettlementId, BigDecimal amount);
}
