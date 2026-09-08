package br.com.betufu.odds.internal.override;

import br.com.betufu.audit.AuditEntry;
import br.com.betufu.audit.AuditRecorder;
import br.com.betufu.odds.internal.governance.OddsAuthorizationService;
import br.com.betufu.odds.internal.governance.OddsAuthorizationService.AuthorizationDecision;
import br.com.betufu.odds.internal.market.MarketState;
import br.com.betufu.odds.internal.market.OddsMarketModels.OddsMarketVersion;
import br.com.betufu.odds.internal.override.OddsOverrideModels.ApproveOverrideRequest;
import br.com.betufu.odds.internal.override.OddsOverrideModels.OverrideView;
import br.com.betufu.odds.internal.override.OddsOverrideModels.ProposeOverrideRequest;
import br.com.betufu.odds.internal.persistence.OddsCalculationRecord;
import br.com.betufu.odds.internal.persistence.OddsMarketRecord;
import br.com.betufu.odds.internal.persistence.OddsMarketRepository;
import br.com.betufu.odds.internal.persistence.OddsOverrideRecord;
import br.com.betufu.odds.internal.persistence.OddsPublicationRecord;
import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.sports.SportsEventForOdds;
import br.com.betufu.sports.SportsResultForOddsPort;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates scoped maker-checker overrides without rewriting accepted price history. */
@Service
@Profile("runtime | integrated")
public class OddsOverrideService {
  private static final BigDecimal DEFAULT_MARGIN = new BigDecimal("0.05");

  private final OddsMarketRepository repository;
  private final SportsResultForOddsPort sports;
  private final OddsAuthorizationService authorization;
  private final AuditRecorder audit;
  private final OddsOverridePolicy policy;

  public OddsOverrideService(
      OddsMarketRepository repository,
      SportsResultForOddsPort sports,
      OddsAuthorizationService authorization,
      AuditRecorder audit) {
    this.repository = Objects.requireNonNull(repository);
    this.sports = Objects.requireNonNull(sports);
    this.authorization = Objects.requireNonNull(authorization);
    this.audit = Objects.requireNonNull(audit);
    this.policy = new OddsOverridePolicy();
  }

  @Transactional
  public OverrideView propose(
      String issuer,
      String subject,
      UUID marketId,
      String idempotencyKey,
      ProposeOverrideRequest request) {
    requireRequest(request, idempotencyKey);
    Context context =
        context(issuer, subject, marketId, OddsAuthorizationService.PROPOSE_ODDS_OVERRIDE);
    requireLock(context.market(), request.expectedLockVersion());
    OddsCalculationRecord calculation =
        repository
            .findCalculation(request.correctionCalculationId())
            .orElseThrow(OddsOverrideService::correctionRequired);
    OddsMarketVersion current = context.current();
    if (!calculation.marketVersionId().equals(current.marketVersionId())) {
      throw correctionRequired();
    }
    OddsCorrectionAttempt attempt =
        new OddsCorrectionAttempt(
            calculation.calculationId(),
            calculation.marketVersionId(),
            request.previousOdds(),
            request.proposedOdds(),
            request.evidenceReference(),
            calculation.createdAt() == null ? Instant.now() : calculation.createdAt());
    requireProposalAllowed(current.state(), attempt);

    UUID overrideId = UUID.randomUUID();
    String evidence = encode(attempt, request.reason());
    OddsOverrideRecord proposal =
        repository.saveOverride(
            new OddsOverrideRecord(
                overrideId,
                marketId,
                calculation.calculationId(),
                context.actor().identityId(),
                null,
                evidence,
                "PROPOSED",
                null),
            context.actor().identityId(),
            idempotencyKey,
            digest(marketId + "|" + calculation.calculationId() + "|" + evidence));
    recordAudit(
        context.actor(),
        marketId,
        AuditEntry.ACTION_ODDS_OVERRIDE_PROPOSED,
        current.state().name(),
        "PROPOSED",
        request.reason(),
        idempotencyKey);
    return view(proposal, attempt);
  }

  @Transactional
  public OverrideView approve(
      String issuer,
      String subject,
      UUID marketId,
      UUID proposalId,
      String idempotencyKey,
      ApproveOverrideRequest request) {
    if (request == null
        || request.expectedLockVersion() == null
        || request.reason() == null
        || request.reason().isBlank()) {
      throw validation();
    }
    requireIdempotency(idempotencyKey);
    Context context =
        context(issuer, subject, marketId, OddsAuthorizationService.APPROVE_ODDS_OVERRIDE);
    requireLock(context.market(), request.expectedLockVersion());
    OddsOverrideRecord proposal =
        repository.findOverride(proposalId).orElseThrow(OddsOverrideService::notFound);
    if (!marketId.equals(proposal.marketId())) throw notFound();
    requireApprovalAllowed(context.current().state(), proposal, context.actor().identityId());
    AttemptEnvelope envelope =
        decode(proposal.reason(), proposal.calculationId(), context.current().marketVersionId());
    OddsCalculationRecord prior =
        repository
            .findCalculation(proposal.calculationId())
            .orElseThrow(OddsOverrideService::correctionRequired);

    UUID versionId = UUID.randomUUID();
    repository.saveVersion(
        new OddsMarketVersion(
            versionId,
            marketId,
            repository.nextVersionNo(marketId),
            MarketState.SUSPENDED,
            context.current().closeDeadline(),
            context.current().marketVersionId(),
            null));
    OddsCalculationRecord approvedCalculation =
        repository.saveCalculation(
            new OddsCalculationRecord(
                UUID.randomUUID(),
                versionId,
                prior.algorithmVersion(),
                prior.configurationDigest(),
                prior.inputSnapshot(),
                prior.confirmedVersionId(),
                prior.fairProbability(),
                prior.confidence(),
                null,
                prior.knownAt()));
    repository.savePublication(
        new OddsPublicationRecord(
            UUID.randomUUID(),
            approvedCalculation.calculationId(),
            DEFAULT_MARGIN,
            "HALF_UP",
            envelope.attempt().proposedOdds(),
            null,
            request.expectedLockVersion(),
            null));
    UUID approvalId = UUID.randomUUID();
    OddsOverrideRecord approval =
        repository.saveOverride(
            new OddsOverrideRecord(
                approvalId,
                marketId,
                approvedCalculation.calculationId(),
                proposal.proposerId(),
                context.actor().identityId(),
                encode(envelope.attempt(), request.reason()),
                "APPROVED",
                null),
            context.actor().identityId(),
            idempotencyKey,
            digest(marketId + "|" + proposalId + "|APPROVED"));
    if (!repository.advanceMarketHead(marketId, versionId, request.expectedLockVersion())) {
      throw stale();
    }
    recordAudit(
        context.actor(),
        marketId,
        AuditEntry.ACTION_ODDS_OVERRIDE_APPROVED,
        "PROPOSED",
        "APPROVED",
        request.reason(),
        idempotencyKey);
    return view(approval, envelope.attempt());
  }

  private Context context(String issuer, String subject, UUID marketId, String capability) {
    OddsMarketRecord market =
        repository.findMarket(marketId).orElseThrow(OddsOverrideService::notFound);
    SportsEventForOdds event =
        sports.getEventForOdds(market.eventId()).orElseThrow(OddsOverrideService::notFound);
    AuthorizationDecision decision =
        authorization.authorize(issuer, subject, capability, event.competitionId());
    if (!decision.permitted()) throw forbidden();
    OddsMarketVersion current =
        repository.findCurrentVersion(marketId).orElseThrow(OddsOverrideService::notFound);
    return new Context(market, current, decision);
  }

  private void requireProposalAllowed(MarketState state, OddsCorrectionAttempt attempt) {
    try {
      policy.requireProposalAllowed(state, attempt);
    } catch (OddsOverridePolicy.PolicyViolation violation) {
      throw conflict();
    }
  }

  private void requireApprovalAllowed(
      MarketState state, OddsOverrideRecord proposal, UUID approverId) {
    try {
      policy.requireApprovalAllowed(state, proposal, approverId);
    } catch (OddsOverridePolicy.PolicyViolation violation) {
      if (violation.violation() == OddsOverridePolicy.Violation.SELF_APPROVAL) throw forbidden();
      throw conflict();
    }
  }

  private void recordAudit(
      AuthorizationDecision actor,
      UUID marketId,
      String action,
      String previous,
      String next,
      String reason,
      String idempotencyKey) {
    audit.record(
        AuditEntry.builder()
            .actorIdentityId(actor.identityId())
            .actorRole(actor.role())
            .targetIdentityId(marketId)
            .action(action)
            .outcome(AuditEntry.OUTCOME_PERMITTED)
            .previousValue(previous)
            .newValue(next)
            .reason(reason)
            .idempotencyKey(idempotencyKey)
            .build());
  }

  private static OverrideView view(OddsOverrideRecord record, OddsCorrectionAttempt attempt) {
    return new OverrideView(
        record.overrideId(),
        record.marketId(),
        record.calculationId(),
        record.proposerId(),
        record.approverId(),
        record.decision(),
        attempt.previousOdds(),
        attempt.proposedOdds(),
        attempt.delta(),
        record.createdAt());
  }

  private static String encode(OddsCorrectionAttempt attempt, String reason) {
    Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
    return "v1|"
        + attempt.previousOdds().toPlainString()
        + "|"
        + attempt.proposedOdds().toPlainString()
        + "|"
        + encoder.encodeToString(attempt.evidenceReference().getBytes(StandardCharsets.UTF_8))
        + "|"
        + encoder.encodeToString(reason.trim().getBytes(StandardCharsets.UTF_8));
  }

  private static AttemptEnvelope decode(String value, UUID calculationId, UUID marketVersionId) {
    try {
      String[] fields = value.split("\\|", 5);
      if (fields.length != 5 || !"v1".equals(fields[0])) throw new IllegalArgumentException();
      String evidence =
          new String(Base64.getUrlDecoder().decode(fields[3]), StandardCharsets.UTF_8);
      String reason = new String(Base64.getUrlDecoder().decode(fields[4]), StandardCharsets.UTF_8);
      return new AttemptEnvelope(
          new OddsCorrectionAttempt(
              calculationId,
              marketVersionId,
              new BigDecimal(fields[1]),
              new BigDecimal(fields[2]),
              evidence,
              Instant.now()),
          reason);
    } catch (RuntimeException exception) {
      throw correctionRequired();
    }
  }

  private static String digest(String value) {
    try {
      byte[] bytes =
          MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
      return "sha256:" + java.util.HexFormat.of().formatHex(bytes);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static void requireRequest(ProposeOverrideRequest request, String idempotencyKey) {
    if (request == null
        || request.expectedLockVersion() == null
        || request.reason() == null
        || request.reason().isBlank()) throw validation();
    requireIdempotency(idempotencyKey);
  }

  private static void requireIdempotency(String key) {
    if (key == null || key.isBlank()) throw validation();
  }

  private static void requireLock(OddsMarketRecord market, long expected) {
    if (market.lockVersion() != expected) throw stale();
  }

  private static ApiProblemException validation() {
    return new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
  }

  private static ApiProblemException correctionRequired() {
    return new ApiProblemException(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.VALIDATION_FAILED);
  }

  private static ApiProblemException forbidden() {
    return new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
  }

  private static ApiProblemException notFound() {
    return new ApiProblemException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND);
  }

  private static ApiProblemException stale() {
    return new ApiProblemException(HttpStatus.CONFLICT, ApiErrorCode.STALE_VERSION);
  }

  private static ApiProblemException conflict() {
    return new ApiProblemException(HttpStatus.CONFLICT, ApiErrorCode.STALE_VERSION);
  }

  private record Context(
      OddsMarketRecord market, OddsMarketVersion current, AuthorizationDecision actor) {}

  private record AttemptEnvelope(OddsCorrectionAttempt attempt, String reason) {}
}
