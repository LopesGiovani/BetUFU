package br.com.betufu.ledger.internal;

import br.com.betufu.verification.EnrollmentVerified;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Grants the one-time initial balance after enrollment commit, safely on retries. */
@Component
@Profile("runtime | integrated")
public class EnrollmentCreditGrantListener {

  private final CreditLedgerService ledger;

  public EnrollmentCreditGrantListener(CreditLedgerService ledger) {
    this.ledger = Objects.requireNonNull(ledger, "ledger must not be null");
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onEnrollmentVerified(EnrollmentVerified event) {
    if (event == null || event.identityId() == null) {
      return;
    }
    ledger.ensureInitialGrant(event.identityId());
  }
}
