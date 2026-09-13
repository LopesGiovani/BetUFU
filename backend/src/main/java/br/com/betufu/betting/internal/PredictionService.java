package br.com.betufu.betting.internal;

import br.com.betufu.betting.PredictionAcceptancePort;
import br.com.betufu.betting.PredictionTicket;
import br.com.betufu.ledger.CreditLedgerPort;
import br.com.betufu.odds.OddsForPredictionPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("runtime | integrated")
public class PredictionService implements PredictionAcceptancePort {
  private final PredictionRepository repository;
  private final OddsForPredictionPort odds;
  private final PredictionEligibilityPolicy eligibility;
  private final CreditLedgerPort ledger;
  private final TransactionTemplate transactions;

  public PredictionService(PredictionRepository repository, OddsForPredictionPort odds, PredictionEligibilityPolicy eligibility, CreditLedgerPort ledger, PlatformTransactionManager transactionManager) {
    this.repository = repository; this.odds = odds; this.eligibility = eligibility; this.ledger = ledger; this.transactions = new TransactionTemplate(transactionManager);
  }

  @Override
  public PredictionTicket accept(UUID identityId, SubmitPrediction command) {
    Objects.requireNonNull(identityId); Objects.requireNonNull(command);
    if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()) throw new IllegalArgumentException("idempotency key required");
    return transactions.execute(status -> {
      var existing = repository.findByKey(identityId, command.idempotencyKey());
      if (existing.isPresent()) return existing.get();
      var snapshot = odds.getSnapshot(command.marketId());
      if (!eligibility.isEligible(identityId, snapshot.competitionId())) throw new IllegalStateException("prediction not eligible");
      if (!snapshot.marketVersionId().equals(command.marketVersionId()) || !snapshot.publicationId().equals(command.publicationId()) || !snapshot.ruleVersion().equals(command.ruleVersion())) throw new IllegalStateException("odds snapshot is stale");
      if (snapshot.closeDeadline() != null && !Instant.now().isBefore(snapshot.closeDeadline())) throw new IllegalStateException("market is closed");
      BigDecimal stake = command.stake().setScale(2, RoundingMode.UNNECESSARY);
      if (stake.compareTo(CreditLedgerPort.MINIMUM_STAKE) < 0) throw new IllegalArgumentException("stake below minimum");
      BigDecimal acceptedOdds = snapshot.selections().stream().filter(s -> s.key().equals(command.selection())).findFirst().orElseThrow(() -> new IllegalArgumentException("selection unavailable")).odds();
      BigDecimal potential = stake.multiply(acceptedOdds).setScale(2, RoundingMode.HALF_UP);
      BigDecimal profit = potential.subtract(stake).setScale(2, RoundingMode.HALF_UP);
      UUID predictionId = UUID.randomUUID();
      ledger.debit(identityId, stake, "PREDICTION", predictionId);
      try { return repository.insert(identityId, command.marketId(), command.marketVersionId(), command.publicationId(), command.ruleVersion(), command.selection(), stake, acceptedOdds, potential, profit, snapshot.closeDeadline(), command.idempotencyKey(), command.idempotencyKey()); }
      catch (DuplicateKeyException ex) { return repository.findByKey(identityId, command.idempotencyKey()).orElseThrow(() -> ex); }
    });
  }
}
