package br.com.betufu.settlement.internal;

import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.settlement.PredictionForSettlementPort;
import br.com.betufu.settlement.PredictionForSettlementPort.PredictionSnapshot;
import br.com.betufu.settlement.PredictionForSettlementPort.PredictionState;
import br.com.betufu.settlement.SettlementCorrectionPort;
import br.com.betufu.settlement.SettlementCreditPort;
import br.com.betufu.settlement.SettlementModels.Authorization;
import br.com.betufu.settlement.SettlementModels.AuthorizationDecision;
import br.com.betufu.settlement.SettlementModels.SettlementCase;
import br.com.betufu.settlement.SettlementModels.SettlementReason;
import br.com.betufu.settlement.SettlementModels.Unit;
import br.com.betufu.settlement.SettlementProcessPort;
import br.com.betufu.settlement.SettlementReadPort;
import br.com.betufu.settlement.SettlementReadPort.AuthorizationCandidate;
import br.com.betufu.settlement.SettlementReadPort.HistoryItem;
import br.com.betufu.settlement.SettlementState;
import br.com.betufu.settlement.SportsResultForSettlementPort;
import br.com.betufu.settlement.SportsResultForSettlementPort.OutcomeKind;
import br.com.betufu.settlement.SportsResultForSettlementPort.ResultSnapshot;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("runtime | integrated")
public class SettlementService implements SettlementProcessPort, SettlementReadPort {
  private static final String RULE_VERSION = "h2h-settlement-v1";
  private final SettlementRepository repository;
  private final SettlementCreditPort credits;
  private final SettlementCorrectionPort corrections;
  private final SportsResultForSettlementPort sports;
  private final PredictionForSettlementPort predictions;
  private final OperationalIdentityLookup identities;
  private final TransactionTemplate transactions;
  private final Clock clock;
  private final Duration leaseDuration;

  @Autowired
  public SettlementService(
      SettlementRepository repository,
      SettlementCreditPort credits,
      SportsResultForSettlementPort sports,
      PredictionForSettlementPort predictions,
      OperationalIdentityLookup identities,
      PlatformTransactionManager transactionManager,
      @Value("${betufu.settlement.lease-duration:PT2M}") Duration leaseDuration,
      SettlementCorrectionPort corrections) {
    this(
        repository,
        credits,
        sports,
        predictions,
        identities,
        transactionManager,
        Clock.systemUTC(),
        leaseDuration,
        corrections);
  }

  public SettlementService(
      SettlementRepository repository,
      SettlementCreditPort credits,
      SportsResultForSettlementPort sports,
      PredictionForSettlementPort predictions,
      OperationalIdentityLookup identities,
      PlatformTransactionManager transactionManager,
      Clock clock,
      Duration leaseDuration) {
    this(
        repository,
        credits,
        sports,
        predictions,
        identities,
        transactionManager,
        clock,
        leaseDuration,
        (identityId, previousId, replacementId, amount) -> {
          throw new IllegalStateException(
              "prior financial settlement requires correction processing");
        });
  }

  public SettlementService(
      SettlementRepository repository,
      SettlementCreditPort credits,
      SportsResultForSettlementPort sports,
      PredictionForSettlementPort predictions,
      OperationalIdentityLookup identities,
      PlatformTransactionManager transactionManager,
      Clock clock,
      Duration leaseDuration,
      SettlementCorrectionPort corrections) {
    this.repository = Objects.requireNonNull(repository);
    this.credits = Objects.requireNonNull(credits);
    this.corrections = Objects.requireNonNull(corrections);
    this.sports = Objects.requireNonNull(sports);
    this.predictions = Objects.requireNonNull(predictions);
    this.identities = Objects.requireNonNull(identities);
    this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager));
    this.clock = Objects.requireNonNull(clock);
    this.leaseDuration = Objects.requireNonNull(leaseDuration);
    if (leaseDuration.isZero() || leaseDuration.isNegative()) {
      throw new IllegalArgumentException("settlement lease duration must be positive");
    }
  }

  @Override
  public UUID authorize(UUID eventId, UUID administratorIdentityId, String idempotencyKey) {
    return authorizeInternal(eventId, null, null, administratorIdentityId, idempotencyKey);
  }

  @Override
  public UUID authorize(
      UUID eventId,
      UUID expectedOutcomeFactId,
      UUID expectedConfirmedVersionId,
      UUID administratorIdentityId,
      String idempotencyKey) {
    Objects.requireNonNull(expectedOutcomeFactId);
    return authorizeInternal(
        eventId,
        expectedOutcomeFactId,
        expectedConfirmedVersionId,
        administratorIdentityId,
        idempotencyKey);
  }

  private UUID authorizeInternal(
      UUID eventId,
      UUID expectedOutcomeFactId,
      UUID expectedConfirmedVersionId,
      UUID administratorIdentityId,
      String idempotencyKey) {
    Objects.requireNonNull(eventId);
    Objects.requireNonNull(administratorIdentityId);
    if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 255) {
      throw new IllegalArgumentException("invalid settlement authorization key");
    }
    return transactions.execute(
        ignored -> {
          requireAdministrator(administratorIdentityId);
          repository.lockEvent(eventId);
          ResultSnapshot result = currentResult(eventId);
          if (expectedOutcomeFactId != null
              && (!expectedOutcomeFactId.equals(result.outcomeFactId())
                  || !Objects.equals(expectedConfirmedVersionId, result.confirmedVersionId()))) {
            throw new IllegalStateException(
                "settlement authorization refers to a stale result version");
          }
          Authorization requested =
              new Authorization(
                  UUID.randomUUID(),
                  eventId,
                  result.outcomeFactId(),
                  result.confirmedVersionId(),
                  administratorIdentityId,
                  AuthorizationDecision.APPROVED,
                  clock.instant(),
                  idempotencyKey);
          Authorization stored = repository.insertAuthorizationIfAbsent(requested);
          if (!stored.eventId().equals(eventId)
              || !stored.outcomeFactId().equals(result.outcomeFactId())
              || !Objects.equals(stored.confirmedVersionId(), result.confirmedVersionId())
              || !stored.authorizedByIdentityId().equals(administratorIdentityId)
              || stored.decision() != AuthorizationDecision.APPROVED) {
            throw new IllegalStateException(
                "settlement authorization key was reused with a different payload");
          }
          return stored.authorizationId();
        });
  }

  @Override
  public UUID process(UUID authorizationId) {
    Objects.requireNonNull(authorizationId);
    SettlementCase settlementCase = transactions.execute(ignored -> prepare(authorizationId));
    UUID owner = UUID.randomUUID();
    Instant now = clock.instant();
    boolean claimed =
        transactions.execute(
            ignored ->
                repository.claim(settlementCase.caseId(), owner, now, now.plus(leaseDuration)));
    if (!claimed) {
      if (findCase(settlementCase.caseId())
          .map(progress -> progress.status() == ProcessingStatus.COMPLETE)
          .orElse(false)) return settlementCase.caseId();
      throw new IllegalStateException("settlement case is already being processed");
    }
    try {
      List<PredictionSnapshot> accepted = predictions.findAcceptedByEvent(settlementCase.eventId());
      for (PredictionSnapshot prediction : accepted) {
        transactions.executeWithoutResult(
            ignored -> processUnit(settlementCase, owner, prediction));
      }
      transactions.executeWithoutResult(
          ignored -> repository.complete(settlementCase.caseId(), owner, clock.instant()));
      return settlementCase.caseId();
    } catch (RuntimeException failure) {
      transactions.executeWithoutResult(
          ignored -> repository.fail(settlementCase.caseId(), owner, clock.instant()));
      throw failure;
    }
  }

  private SettlementCase prepare(UUID authorizationId) {
    Authorization authorization = repository.findAuthorization(authorizationId).orElseThrow();
    if (authorization.decision() != AuthorizationDecision.APPROVED) {
      throw new IllegalStateException("settlement authorization is not approved");
    }
    requireAdministrator(authorization.authorizedByIdentityId());
    repository.lockEvent(authorization.eventId());
    requireAuthorizedResult(authorization);
    Optional<SettlementCase> existing =
        repository.findCaseByOutcome(authorization.eventId(), authorization.outcomeFactId());
    if (existing.isPresent()) return existing.get();
    int version = repository.nextProcessingVersion(authorization.eventId());
    SettlementCase settlementCase =
        new SettlementCase(
            UUID.randomUUID(),
            authorizationId,
            authorization.eventId(),
            authorization.outcomeFactId(),
            version,
            RULE_VERSION,
            clock.instant());
    repository.insertCase(settlementCase);
    repository.insertProcessing(
        settlementCase.caseId(), predictions.findAcceptedByEvent(authorization.eventId()).size());
    return settlementCase;
  }

  private void processUnit(
      SettlementCase settlementCase, UUID owner, PredictionSnapshot prediction) {
    Authorization authorization =
        repository.findAuthorization(settlementCase.authorizationId()).orElseThrow();
    repository.lockEvent(settlementCase.eventId());
    requireAdministrator(authorization.authorizedByIdentityId());
    ResultSnapshot result = requireAuthorizedResult(authorization);
    Instant now = clock.instant();
    if (!repository.hasLeaseForUpdate(settlementCase.caseId(), owner, now)) {
      throw new IllegalStateException("settlement processing lease expired");
    }
    if (!prediction.eventId().equals(settlementCase.eventId())) {
      throw new IllegalStateException("prediction does not belong to settlement event");
    }
    String key = "settlement:" + settlementCase.caseId() + ":" + prediction.predictionId();
    if (repository.findUnitByKey(key).isPresent()) return;
    Unit previous = repository.findLatestUnit(prediction.predictionId()).orElse(null);
    Decision decision = decide(prediction, result, settlementCase.processingVersion());
    UUID unitId = UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    Unit unit =
        new Unit(
            unitId,
            settlementCase.caseId(),
            prediction.predictionId(),
            prediction.identityId(),
            decision.state(),
            decision.reason(),
            decision.amount(),
            previous == null ? null : previous.settlementId(),
            key,
            now);
    repository.insertUnit(unit);
    if (previous != null && previous.amount().signum() > 0) {
      corrections.recoverPreviousCredit(
          prediction.identityId(), previous.settlementId(), unitId, previous.amount());
    }
    UUID ledgerEntryId = null;
    if (decision.state() == SettlementState.WON) {
      ledgerEntryId = credits.payout(prediction.identityId(), decision.amount(), unitId);
    } else if (decision.state() == SettlementState.REFUNDED
        || decision.state() == SettlementState.ANNULLED) {
      ledgerEntryId = credits.refund(prediction.identityId(), decision.amount(), unitId);
    }
    repository.insertCalculation(unit, prediction, result, ledgerEntryId);
    repository.advance(settlementCase.caseId(), owner, now, now.plus(leaseDuration));
  }

  private ResultSnapshot requireAuthorizedResult(Authorization authorization) {
    ResultSnapshot current = currentResult(authorization.eventId());
    if (!current.outcomeFactId().equals(authorization.outcomeFactId())
        || !Objects.equals(current.confirmedVersionId(), authorization.confirmedVersionId())) {
      throw new IllegalStateException("settlement authorization refers to a stale result");
    }
    return current;
  }

  private ResultSnapshot currentResult(UUID eventId) {
    return sports
        .findCurrent(eventId)
        .orElseThrow(() -> new IllegalStateException("event has no current settleable result"));
  }

  private void requireAdministrator(UUID actorId) {
    if (!identities.hasGlobalRole(actorId, "ADMINISTRADOR_GERAL")) {
      throw new SecurityException("general administrator role is required for settlement");
    }
  }

  private static Decision decide(
      PredictionSnapshot prediction, ResultSnapshot result, int version) {
    if (prediction.state() == PredictionState.ANNULLED) {
      return new Decision(
          SettlementState.ANNULLED,
          SettlementReason.CONFIRMED_RESULT,
          prediction.stake().setScale(2));
    }
    if (result.kind() == OutcomeKind.EVENT_CANCELLED
        || result.kind() == OutcomeKind.ABANDONED_NO_VALID_RESULT) {
      return new Decision(
          SettlementState.REFUNDED,
          result.kind() == OutcomeKind.EVENT_CANCELLED
              ? SettlementReason.EVENT_CANCELLED
              : SettlementReason.NO_VALID_RESULT,
          prediction.stake().setScale(2));
    }
    String winner;
    if (result.kind() == OutcomeKind.ADMINISTRATIVE_WALKOVER) {
      winner = result.winnerSlotNo() == 1 ? "HOME" : result.winnerSlotNo() == 2 ? "AWAY" : null;
    } else {
      if (result.homeScore() == null || result.awayScore() == null)
        throw new IllegalStateException("sporting result has no scores");
      winner =
          result.homeScore() > result.awayScore()
              ? "HOME"
              : result.homeScore() < result.awayScore() ? "AWAY" : "DRAW";
    }
    if (winner == null) throw new IllegalStateException("administrative walkover has no winner");
    SettlementReason reason =
        result.kind() == OutcomeKind.ADMINISTRATIVE_WALKOVER
            ? SettlementReason.ADMINISTRATIVE_WALKOVER
            : version > 1 ? SettlementReason.CORRECTED_RESULT : SettlementReason.CONFIRMED_RESULT;
    boolean won = winner.equals(prediction.selection());
    return new Decision(
        won ? SettlementState.WON : SettlementState.LOST,
        reason,
        won ? prediction.potentialReturn().setScale(2) : BigDecimal.ZERO.setScale(2));
  }

  @Override
  public Optional<CaseProgress> findCase(UUID caseId) {
    return repository.findProgress(caseId);
  }

  @Override
  public Optional<PredictionOutcome> findLatestByPrediction(UUID predictionId) {
    return repository.findLatestOutcome(predictionId);
  }

  @Override
  public List<HistoryItem> findHistoryByIdentity(UUID identityId) {
    return predictions.findAcceptedByIdentity(identityId).stream()
        .flatMap(
            prediction -> {
              var facts =
                  repository.findHistoryFacts(prediction.predictionId(), prediction.eventId());
              var items = new java.util.ArrayList<HistoryItem>();
              if (facts.stream().noneMatch(SettlementRepository.HistoryFact::current)) {
                items.add(
                    historyItem(
                        prediction,
                        null,
                        true,
                        repository
                            .findLatestProgressByEvent(prediction.eventId())
                            .map(p -> p.status().name())
                            .orElse("PENDING")));
              }
              facts.forEach(
                  fact ->
                      items.add(
                          historyItem(prediction, fact.outcome(), fact.current(), fact.status())));
              return items.stream();
            })
        .toList();
  }

  private HistoryItem historyItem(
      PredictionSnapshot p, PredictionOutcome o, boolean current, String processingStatus) {
    return new HistoryItem(
        p.predictionId(),
        p.eventId(),
        p.marketId(),
        p.acceptedAt(),
        p.selection(),
        p.stake(),
        p.acceptedOdds(),
        p.potentialReturn(),
        o == null ? null : o.settlementId(),
        o == null ? SettlementState.PENDING : o.state(),
        o == null ? null : o.reason(),
        o == null ? p.ruleVersion() : o.ruleVersion(),
        o == null ? null : o.amount(),
        o == null ? null : o.outcomeKind(),
        o == null ? null : o.homeScore(),
        o == null ? null : o.awayScore(),
        o == null ? null : o.winnerSlotNo(),
        o == null ? null : o.ledgerEntryId(),
        current,
        processingStatus);
  }

  @Override
  public List<AuthorizationCandidate> findAuthorizationCandidates() {
    return sports.findCandidates().stream()
        .map(
            event -> {
              var r = event.result();
              return new AuthorizationCandidate(
                  r.eventId(),
                  event.title(),
                  event.scheduledAt(),
                  r.outcomeFactId(),
                  r.confirmedVersionId(),
                  r.kind().name(),
                  r.homeScore(),
                  r.awayScore(),
                  r.winnerSlotNo(),
                  repository.isAuthorized(r.outcomeFactId()),
                  repository
                      .findCaseByOutcome(r.eventId(), r.outcomeFactId())
                      .flatMap(c -> repository.findProgress(c.caseId()))
                      .orElse(null));
            })
        .toList();
  }

  @Override
  public List<CurrentFact> findCurrentFacts() {
    return repository.findCurrentFacts();
  }

  private record Decision(SettlementState state, SettlementReason reason, BigDecimal amount) {}
}
