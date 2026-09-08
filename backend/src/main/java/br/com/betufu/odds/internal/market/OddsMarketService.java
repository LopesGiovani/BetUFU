package br.com.betufu.odds.internal.market;

import br.com.betufu.audit.AuditEntry;
import br.com.betufu.audit.AuditRecorder;
import br.com.betufu.odds.internal.governance.OddsAuthorizationService;
import br.com.betufu.odds.internal.model.OddsPricingInput;
import br.com.betufu.odds.internal.model.OddsPricingOutput;
import br.com.betufu.odds.internal.persistence.OddsCalculationRecord;
import br.com.betufu.odds.internal.persistence.OddsMarketRecord;
import br.com.betufu.odds.internal.persistence.OddsMarketRepository;
import br.com.betufu.odds.internal.persistence.OddsPublicationRecord;
import br.com.betufu.sports.SportsEventForOdds;
import br.com.betufu.sports.SportsResultForOddsPort;
import br.com.betufu.sports.SportsRosterForOddsPort.SportsRosterBindingForOdds;
import br.com.betufu.sports.SportsRosterSnapshotForOdds;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Application service for H2H market creation, publication and lifecycle commands. */
@Service
@Profile("runtime | integrated")
public class OddsMarketService {
  public static final String MARKET_TYPE_H2H = "H2H";

  private final OddsMarketRepository repository;
  private final SportsResultForOddsPort sports;
  private final OddsAuthorizationService authorization;
  private final RosterMarketEligibilityService rosterEligibility;
  private final OddsCalculationApplicationService calculation;
  private final AuditRecorder audit;
  private final MarketLifecyclePolicy lifecyclePolicy;

  @Autowired
  public OddsMarketService(
      OddsMarketRepository repository,
      SportsResultForOddsPort sports,
      OddsAuthorizationService authorization,
      RosterMarketEligibilityService rosterEligibility,
      OddsCalculationApplicationService calculation,
      AuditRecorder audit) {
    this.repository = Objects.requireNonNull(repository);
    this.sports = Objects.requireNonNull(sports);
    this.authorization = Objects.requireNonNull(authorization);
    this.rosterEligibility = Objects.requireNonNull(rosterEligibility);
    this.calculation = Objects.requireNonNull(calculation);
    this.audit = Objects.requireNonNull(audit);
    this.lifecyclePolicy = new MarketLifecyclePolicy();
  }

  @Transactional
  public OddsMarketModels.MarketView createMarket(
      String issuer,
      String subject,
      String idempotencyKey,
      OddsMarketModels.CreateMarketRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    SportsEventForOdds event =
        sports.getEventForOdds(request.eventId()).orElseThrow(OddsMarketService::notFound);
    if (event.format() != SportsEventForOdds.EventFormat.HEAD_TO_HEAD) throw validation();
    requireAuthorized(issuer, subject, event.competitionId());
    if (repository.findMarketByEvent(event.eventId(), MARKET_TYPE_H2H).isPresent()) {
      return view(repository.findMarketByEvent(event.eventId(), MARKET_TYPE_H2H).orElseThrow());
    }
    UUID marketId = UUID.randomUUID();
    OddsMarketRecord market =
        repository.saveMarket(
            new OddsMarketRecord(marketId, event.eventId(), MARKET_TYPE_H2H, null, 0, null, null));
    UUID versionId = UUID.randomUUID();
    repository.saveVersion(
        new OddsMarketModels.OddsMarketVersion(
            versionId, marketId, 1, MarketState.DRAFT, request.closeDeadline(), null, null));
    repository.advanceMarketHead(marketId, versionId, market.lockVersion());
    audit.record(
        AuditEntry.builder()
            .actorIdentityId(
                authorization
                    .authorize(
                        issuer,
                        subject,
                        OddsAuthorizationService.MANAGE_ODDS_MARKET,
                        event.competitionId())
                    .identityId())
            .action(AuditEntry.ACTION_ODDS_MARKET_CREATED)
            .outcome(AuditEntry.OUTCOME_PERMITTED)
            .targetIdentityId(marketId)
            .build());
    return view(repository.findMarket(marketId).orElseThrow());
  }

  @Transactional
  public OddsMarketModels.MarketView transition(
      String issuer,
      String subject,
      UUID marketId,
      MarketTransition.Action action,
      long expectedLockVersion,
      String reason,
      List<SportsRosterSnapshotForOdds> snapshots,
      boolean regulationAllowsDraw) {
    OddsMarketRecord market =
        repository.findMarket(marketId).orElseThrow(OddsMarketService::notFound);
    SportsEventForOdds event =
        sports.getEventForOdds(market.eventId()).orElseThrow(OddsMarketService::notFound);
    requireAuthorized(issuer, subject, event.competitionId(), event);
    if (market.lockVersion() != expectedLockVersion) throw stale();
    OddsMarketModels.OddsMarketVersion current =
        repository.findCurrentVersion(marketId).orElseThrow(OddsMarketService::notFound);
    MarketState next;
    try {
      next = lifecyclePolicy.next(current.state(), action, Instant.now(), current.closeDeadline());
    } catch (IllegalArgumentException ex) {
      throw validation();
    }
    if (action == MarketTransition.Action.PUBLISH_MARKET) {
      List<SportsRosterBindingForOdds> bindings;
      try {
        bindings =
            rosterEligibility.bindMarketVersion(
                current.marketVersionId(), event.eventId(), snapshots);
      } catch (RuntimeException ex) {
        throw validation();
      }
      if (!rosterEligibility.isEligible(current.marketVersionId(), event.eventId(), bindings))
        throw validation();
      OddsPricingOutput priced =
          calculation.price(
              new OddsPricingInput(
                  event.eventId(),
                  current.marketVersionId(),
                  event.slots().get(0).teamId(),
                  event.slots().get(1).teamId(),
                  List.of(),
                  regulationAllowsDraw));
      OddsCalculationRecord calc =
          repository.saveCalculation(
              new OddsCalculationRecord(
                  UUID.randomUUID(),
                  current.marketVersionId(),
                  priced.algorithmVersion(),
                  priced.configurationDigest(),
                  priced.inputSnapshot(),
                  null,
                  priced.fairProbability(),
                  priced.confidence(),
                  null,
                  null));
      repository.savePublication(
          new OddsPublicationRecord(
              UUID.randomUUID(),
              calc.calculationId(),
              priced.margin(),
              priced.roundingMode().name(),
              priced.publishedOdds().get(0),
              null,
              expectedLockVersion,
              null));
    }
    UUID versionId = UUID.randomUUID();
    repository.saveVersion(
        new OddsMarketModels.OddsMarketVersion(
            versionId,
            marketId,
            repository.nextVersionNo(marketId),
            next,
            current.closeDeadline(),
            current.marketVersionId(),
            null));
    if (!repository.advanceMarketHead(marketId, versionId, expectedLockVersion)) throw stale();
    audit.record(
        AuditEntry.builder()
            .action(AuditEntry.ACTION_ODDS_MARKET_LIFECYCLE_CHANGED)
            .outcome(AuditEntry.OUTCOME_PERMITTED)
            .targetIdentityId(marketId)
            .previousValue(current.state().name())
            .newValue(next.name())
            .reason(reason)
            .build());
    return view(repository.findMarket(marketId).orElseThrow());
  }

  @Transactional(readOnly = true)
  public OddsMarketModels.MarketView getMarket(UUID marketId) {
    return view(repository.findMarket(marketId).orElseThrow(OddsMarketService::notFound));
  }

  private void requireAuthorized(String issuer, String subject, UUID competitionId) {
    requireAuthorized(issuer, subject, competitionId, null);
  }

  private void requireAuthorized(
      String issuer, String subject, UUID competitionId, SportsEventForOdds event) {
    if (event != null && event.format() != SportsEventForOdds.EventFormat.HEAD_TO_HEAD)
      throw validation();
    if (!authorization.isAuthorized(
        issuer, subject, OddsAuthorizationService.MANAGE_ODDS_MARKET, competitionId))
      throw forbidden();
  }

  private OddsMarketModels.MarketView view(OddsMarketRecord market) {
    var current = repository.findCurrentVersion(market.marketId());
    return new OddsMarketModels.MarketView(
        market.marketId(),
        market.eventId(),
        market.marketType(),
        current.map(OddsMarketModels.OddsMarketVersion::state).orElse(MarketState.DRAFT),
        current.map(OddsMarketModels.OddsMarketVersion::marketVersionId).orElse(null),
        current.map(OddsMarketModels.OddsMarketVersion::versionNo).orElse(0L),
        current.map(OddsMarketModels.OddsMarketVersion::closeDeadline).orElse(null),
        market.lockVersion(),
        market.createdAt(),
        market.updatedAt());
  }

  private static RuntimeException notFound() {
    return new br.com.betufu.platform.api.ApiProblemException(
        HttpStatus.NOT_FOUND, br.com.betufu.platform.api.ApiErrorCode.NOT_FOUND);
  }

  private static RuntimeException forbidden() {
    return new br.com.betufu.platform.api.ApiProblemException(
        HttpStatus.FORBIDDEN, br.com.betufu.platform.api.ApiErrorCode.FORBIDDEN);
  }

  private static RuntimeException validation() {
    return new br.com.betufu.platform.api.ApiProblemException(
        HttpStatus.BAD_REQUEST, br.com.betufu.platform.api.ApiErrorCode.VALIDATION_FAILED);
  }

  private static RuntimeException stale() {
    return new br.com.betufu.platform.api.ApiProblemException(
        HttpStatus.CONFLICT, br.com.betufu.platform.api.ApiErrorCode.STALE_VERSION);
  }
}
