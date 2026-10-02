package br.com.betufu.ledger.internal;

import br.com.betufu.settlement.SettlementCorrectionPort;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("runtime | integrated")
public class SettlementCorrectionAdapter implements SettlementCorrectionPort {
  private final CreditCorrectionService corrections;

  public SettlementCorrectionAdapter(CreditCorrectionService corrections) {
    this.corrections = corrections;
  }

  @Override
  public void recoverPreviousCredit(
      UUID identityId, UUID previousSettlementId, UUID replacementSettlementId, BigDecimal amount) {
    corrections.recoverSettlement(
        identityId, previousSettlementId, replacementSettlementId, amount);
  }
}
