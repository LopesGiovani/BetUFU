package br.com.betufu.phase5;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.betufu.ledger.CreditLedgerPort;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class Phase5CertificationIT {
  @Test
  void creditRulesArePinned() {
    assertThat(CreditLedgerPort.INITIAL_GRANT).isEqualByComparingTo(new BigDecimal("1000.00"));
    assertThat(CreditLedgerPort.MINIMUM_STAKE).isEqualByComparingTo(new BigDecimal("10.00"));
  }
}
