package br.com.betufu.betting.internal;

import br.com.betufu.betting.PredictionAcceptancePort;
import br.com.betufu.betting.PredictionTicket;
import br.com.betufu.ledger.CreditBalance;
import br.com.betufu.ledger.CreditLedgerPort;
import br.com.betufu.odds.OddsForPredictionPort;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("runtime | integrated")
public class PredictionService implements PredictionAcceptancePort {
  private static final Set<String> SELECTION_KEYS = Set.of("HOME", "DRAW", "AWAY");

  private final PredictionRepository repository;
  private final OddsForPredictionPort odds;
  private final PredictionEligibilityPolicy eligibility;
  private final CreditLedgerPort ledger;
  private final TransactionTemplate transactions;
  private final Clock clock;

  public PredictionService(
      PredictionRepository repository,
      OddsForPredictionPort odds,
      PredictionEligibilityPolicy eligibility,
      CreditLedgerPort ledger,
      PlatformTransactionManager transactionManager,
      Clock clock) {
    this.repository = Objects.requireNonNull(repository);
    this.odds = Objects.requireNonNull(odds);
    this.eligibility = Objects.requireNonNull(eligibility);
    this.ledger = Objects.requireNonNull(ledger);
    this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager));
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public PredictionTicket accept(UUID identityId, SubmitPrediction command) {
    Objects.requireNonNull(identityId, "identityId must not be null");
    PreparedCommand prepared = prepare(command);
    String digest =
        PredictionCommandDigest.sha256(identityId, command, prepared.selection(), prepared.stake());
    return Objects.requireNonNull(
        transactions.execute(
            status -> acceptInTransaction(identityId, command, prepared, digest)));
  }

  private PredictionTicket acceptInTransaction(
      UUID identityId, SubmitPrediction command, PreparedCommand prepared, String digest) {
    repository.lockIdempotency(identityId, command.idempotencyKey());
    var existing = repository.findReceiptByKey(identityId, command.idempotencyKey());
    if (existing.isPresent()) {
      if (!existing.get().payloadDigest().equals(digest)) {
        throw new IllegalStateException("idempotency key conflict");
      }
      return existing.get().ticket();
    }

    OddsForPredictionPort.MarketSnapshot snapshot = odds.getSnapshot(command.marketId());
    Instant now = clock.instant();
    validateSnapshot(command, prepared, snapshot, now);
    if (!eligibility.isEligible(identityId, snapshot.competitionId())) {
      throw new IllegalStateException("prediction not eligible");
    }
    if (repository.findByMarket(identityId, command.marketId()).isPresent()) {
      throw new IllegalStateException("prediction conflict");
    }

    CreditBalance balance = ledger.getBalance(identityId);
    if (balance.balance().compareTo(prepared.stake()) < 0) {
      throw new IllegalStateException("insufficient credit balance");
    }

    BigDecimal acceptedOdds =
        snapshot.selections().stream()
            .filter(selection -> selection.key().equals(prepared.selection()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("selection unavailable"))
            .odds();
    if (acceptedOdds == null || acceptedOdds.signum() <= 0) {
      throw new IllegalStateException("odds snapshot is unavailable");
    }
    BigDecimal potential =
        prepared.stake().multiply(acceptedOdds).setScale(2, RoundingMode.HALF_UP);
    BigDecimal profit = potential.subtract(prepared.stake()).setScale(2, RoundingMode.HALF_UP);
    UUID predictionId = UUID.randomUUID();
    PredictionTicket ticket;
    try {
      ticket =
          repository.insert(
              predictionId,
              identityId,
              command.marketId(),
              command.marketVersionId(),
              command.publicationId(),
              command.ruleVersion(),
              prepared.selection(),
              prepared.stake(),
              acceptedOdds,
              potential,
              profit,
              snapshot.closeDeadline(),
              command.idempotencyKey(),
              digest);
    } catch (DuplicateKeyException ex) {
      throw new IllegalStateException("prediction conflict");
    }
    ledger.debit(identityId, prepared.stake(), predictionId);
    return ticket;
  }

  private static PreparedCommand prepare(SubmitPrediction command) {
    Objects.requireNonNull(command, "command must not be null");
    Objects.requireNonNull(command.marketId(), "marketId must not be null");
    Objects.requireNonNull(command.marketVersionId(), "marketVersionId must not be null");
    Objects.requireNonNull(command.publicationId(), "publicationId must not be null");
    if (command.ruleVersion() == null
        || command.ruleVersion().isBlank()
        || command.ruleVersion().length() > 64) {
      throw new IllegalArgumentException("rule version is invalid");
    }
    if (command.idempotencyKey() == null
        || command.idempotencyKey().isBlank()
        || command.idempotencyKey().length() > 255) {
      throw new IllegalArgumentException("idempotency key is invalid");
    }
    if (command.selection() == null) {
      throw new IllegalArgumentException("selection is invalid");
    }
    String selection = command.selection().trim().toUpperCase(Locale.ROOT);
    if (!SELECTION_KEYS.contains(selection)) {
      throw new IllegalArgumentException("selection is invalid");
    }
    Objects.requireNonNull(command.stake(), "stake must not be null");
    BigDecimal stake = command.stake().setScale(2, RoundingMode.HALF_UP);
    if (stake.compareTo(CreditLedgerPort.MINIMUM_STAKE) < 0) {
      throw new IllegalArgumentException("stake below minimum");
    }
    return new PreparedCommand(selection, stake);
  }

  private static void validateSnapshot(
      SubmitPrediction command,
      PreparedCommand prepared,
      OddsForPredictionPort.MarketSnapshot snapshot,
      Instant now) {
    if (snapshot == null
        || !command.marketId().equals(snapshot.marketId())
        || snapshot.competitionId() == null
        || !"OPEN".equals(snapshot.state())) {
      throw new IllegalStateException("market is unavailable");
    }
    if (!snapshot.marketVersionId().equals(command.marketVersionId())
        || !snapshot.publicationId().equals(command.publicationId())
        || !snapshot.ruleVersion().equals(command.ruleVersion())) {
      throw new IllegalStateException("odds snapshot is stale");
    }
    if (snapshot.closeDeadline() == null || !now.isBefore(snapshot.closeDeadline())) {
      throw new IllegalStateException("market is closed");
    }
    if (snapshot.selections().stream()
        .noneMatch(selection -> selection.key().equals(prepared.selection()))) {
      throw new IllegalArgumentException("selection unavailable");
    }
  }

  private record PreparedCommand(String selection, BigDecimal stake) {}
}
