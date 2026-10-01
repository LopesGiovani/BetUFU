package br.com.betufu.ledger.internal;

import br.com.betufu.ledger.CreditLedgerPort;
import br.com.betufu.settlement.SettlementCreditPort;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("runtime | integrated")
public class SettlementCreditAdapter implements SettlementCreditPort {
  private final CreditLedgerPort ledger;

  public SettlementCreditAdapter(CreditLedgerPort ledger) {
    this.ledger = ledger;
  }

  @Override
  public UUID payout(UUID identityId, BigDecimal amount, UUID settlementId) {
    return ledger.payout(identityId, amount, settlementId).entryId();
  }

  @Override
  public UUID refund(UUID identityId, BigDecimal amount, UUID settlementId) {
    return ledger.refund(identityId, amount, settlementId).entryId();
  }
}
