package br.com.betufu.platform.session.passwordreset;

import br.com.betufu.audit.AuditEntry;
import br.com.betufu.audit.AuditRecorder;
import br.com.betufu.platform.session.IdentitySessionTerminationService;
import br.com.betufu.platform.session.SessionTerminationReason;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Durable inbox for password-reset events.
 *
 * <p>Database state transitions are committed before and after the external Redis/IdP effect.
 * PostgreSQL cannot participate in the same atomic transaction as those systems, so recovery is
 * at-least-once and the termination operation must remain idempotent.
 */
@Service
@Profile("runtime | integrated")
public class PasswordResetEventInboxService {

  public static final String STATUS_PENDING = PasswordResetEventInboxEntity.STATUS_PENDING;
  public static final String STATUS_CLAIMED = PasswordResetEventInboxEntity.STATUS_CLAIMED;
  public static final String STATUS_DELIVERED = PasswordResetEventInboxEntity.STATUS_DELIVERED;
  public static final String STATUS_FAILED = PasswordResetEventInboxEntity.STATUS_FAILED;
  public static final String STATUS_PROCESSED = PasswordResetEventInboxEntity.STATUS_PROCESSED;

  public static final Duration DEFAULT_RETENTION = Duration.ofDays(30);
  public static final Duration DEFAULT_LEASE_DURATION = Duration.ofMinutes(5);
  public static final int DEFAULT_RECOVERY_BATCH_SIZE = 100;
  public static final int MAX_RECOVERY_BATCH_SIZE = 100;

  public static final String ERROR_CODE_TERMINATION_FAILED = "TERMINATION_FAILED";
  public static final String ERROR_CODE_AUDIT_FAILED = "AUDIT_FAILED";

  private final PasswordResetEventInboxRepository repository;
  private final IdentitySessionTerminationService sessionTerminationService;
  private final AuditRecorder auditRecorder;
  private final TransactionOperations transactions;

  /** Test-only compatibility constructor for focused tests that do not exercise replay auditing. */
  public PasswordResetEventInboxService(
      PasswordResetEventInboxRepository repository,
      IdentitySessionTerminationService sessionTerminationService) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.sessionTerminationService =
        Objects.requireNonNull(
            sessionTerminationService, "sessionTerminationService must not be null");
    this.auditRecorder = null;
    this.transactions = TransactionOperations.withoutTransaction();
  }

  @Autowired
  public PasswordResetEventInboxService(
      PasswordResetEventInboxRepository repository,
      IdentitySessionTerminationService sessionTerminationService,
      AuditRecorder auditRecorder,
      @Nullable PlatformTransactionManager transactionManager) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.sessionTerminationService =
        Objects.requireNonNull(
            sessionTerminationService, "sessionTerminationService must not be null");
    this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder must not be null");
    if (transactionManager == null) {
      this.transactions = TransactionOperations.withoutTransaction();
    } else {
      TransactionTemplate template = new TransactionTemplate(transactionManager);
      template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
      this.transactions = template;
    }
  }

  public record InboxProcessingResult(
      String sourceEventId, String status, boolean effectExecuted, int terminatedSessionCount) {}

  private record ReplayClaim(
      UUID inboxId, String sourceEventId, String subject, int attemptCount, UUID actorIdentityId) {}

  public InboxProcessingResult processEvent(
      String sourceEventId, String subject, String realmId, String eventType) {
    requireText(sourceEventId, "sourceEventId");
    requireText(subject, "subject");
    requireText(realmId, "realmId");

    String effectiveEventType =
        eventType != null && !eventType.isBlank() ? eventType : "UPDATE_CREDENTIAL";
    int inserted =
        inTransaction(
            () ->
                repository.insertIfAbsent(
                    sourceEventId, subject, realmId, effectiveEventType, STATUS_PENDING));

    if (inserted == 0) {
      String currentStatus =
          repository
              .findBySourceEventId(sourceEventId)
              .map(PasswordResetEventInboxEntity::getStatus)
              .orElse(STATUS_PENDING);
      return new InboxProcessingResult(sourceEventId, currentStatus, false, 0);
    }

    try {
      IdentitySessionTerminationService.TerminationSummary summary = terminate(subject);
      if (!summary.isFullyRevoked()) {
        markFailed(sourceEventId, ERROR_CODE_TERMINATION_FAILED);
        return failedResult(sourceEventId, true, summary.totalTerminated());
      }
      markDelivered(sourceEventId);
      return deliveredResult(sourceEventId, summary.totalTerminated());
    } catch (RuntimeException ex) {
      markFailed(sourceEventId, ERROR_CODE_TERMINATION_FAILED);
      throw ex;
    }
  }

  public List<InboxProcessingResult> recoverPendingEvents(
      String instanceId, Duration leaseDuration, Instant now) {
    return recoverPendingEvents(instanceId, leaseDuration, now, DEFAULT_RECOVERY_BATCH_SIZE);
  }

  public List<InboxProcessingResult> recoverPendingEvents(
      String instanceId, Duration leaseDuration, Instant now, int batchSize) {
    requireText(instanceId, "instanceId");
    Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");
    Objects.requireNonNull(now, "now must not be null");
    if (leaseDuration.isNegative() || leaseDuration.isZero()) {
      throw new IllegalArgumentException("leaseDuration must be positive");
    }
    if (batchSize < 1 || batchSize > MAX_RECOVERY_BATCH_SIZE) {
      throw new IllegalArgumentException(
          "batchSize must be between 1 and " + MAX_RECOVERY_BATCH_SIZE);
    }

    Instant leaseCutoff = now.minus(leaseDuration);
    List<PasswordResetEventInboxEntity> eligible =
        repository.findEligibleForClaim(leaseCutoff, PageRequest.of(0, batchSize));
    List<InboxProcessingResult> results = new ArrayList<>();

    for (PasswordResetEventInboxEntity entity : eligible) {
      int claimed =
          inTransaction(
              () -> repository.claim(entity.getSourceEventId(), instanceId, now, leaseCutoff));
      if (claimed == 0) {
        continue;
      }

      afterClaimCommitted(entity.getSourceEventId());
      try {
        IdentitySessionTerminationService.TerminationSummary summary =
            terminate(entity.getSubject());
        // A recovered claim may run in a new process after the volatile OIDC retry state
        // disappeared. An empty retry cannot prove the remote grant was revoked.
        if (!summary.isFullyRevoked() || !summary.oidcGrantRevocationConfirmed()) {
          markFailed(entity.getSourceEventId(), ERROR_CODE_TERMINATION_FAILED);
          results.add(failedResult(entity.getSourceEventId(), true, summary.totalTerminated()));
        } else {
          markDelivered(entity.getSourceEventId());
          results.add(deliveredResult(entity.getSourceEventId(), summary.totalTerminated()));
        }
      } catch (RuntimeException ex) {
        markFailed(entity.getSourceEventId(), ERROR_CODE_TERMINATION_FAILED);
        results.add(failedResult(entity.getSourceEventId(), false, 0));
      }
    }
    return List.copyOf(results);
  }

  /** Seam used only to prove that a committed claim survives a crash before the external effect. */
  protected void afterClaimCommitted(String sourceEventId) {
    // Production intentionally does nothing.
  }

  public Optional<InboxProcessingResult> reprocessFailedEvent(
      String sourceEventId, UUID actorIdentityId, Instant now) {
    requireText(sourceEventId, "sourceEventId");
    Objects.requireNonNull(actorIdentityId, "actorIdentityId must not be null");
    Objects.requireNonNull(now, "now must not be null");
    requireAuditRecorder();

    Optional<PasswordResetEventInboxEntity> existing =
        repository.findBySourceEventId(sourceEventId);
    if (existing.isEmpty()) {
      return Optional.empty();
    }
    if (!STATUS_FAILED.equals(existing.get().getStatus())) {
      PasswordResetEventInboxEntity entity = existing.get();
      return Optional.of(new InboxProcessingResult(sourceEventId, entity.getStatus(), false, 0));
    }

    ReplayClaim claim = inTransaction(() -> claimReplay(sourceEventId, actorIdentityId, now));
    if (claim == null) {
      String status =
          repository
              .findBySourceEventId(sourceEventId)
              .map(PasswordResetEventInboxEntity::getStatus)
              .orElse(STATUS_FAILED);
      return Optional.of(new InboxProcessingResult(sourceEventId, status, false, 0));
    }

    afterClaimCommitted(sourceEventId);
    IdentitySessionTerminationService.TerminationSummary summary;
    try {
      summary = terminate(claim.subject());
    } catch (RuntimeException terminationFailure) {
      finishReplay(claim, STATUS_FAILED, ERROR_CODE_TERMINATION_FAILED);
      return Optional.of(failedResult(sourceEventId, false, 0));
    }
    // A FAILED replay is different from a first delivery: after a backend restart, an empty
    // in-memory OIDC state cannot prove that the earlier remote grant was revoked. Only an
    // execution that observes REVOKED may close the durable FAILED record as DELIVERED.
    if (!summary.isFullyRevoked() || !summary.oidcGrantRevocationConfirmed()) {
      finishReplay(claim, STATUS_FAILED, ERROR_CODE_TERMINATION_FAILED);
      return Optional.of(failedResult(sourceEventId, true, summary.totalTerminated()));
    }
    finishReplay(claim, STATUS_DELIVERED, null);
    return Optional.of(deliveredResult(sourceEventId, summary.totalTerminated()));
  }

  public int cleanupDelivered(Instant cutoff) {
    Objects.requireNonNull(cutoff, "cutoff must not be null");
    return inTransaction(() -> repository.deleteDeliveredOlderThan(cutoff));
  }

  public int cleanupDeliveredOlderThan(Duration retention, Instant now) {
    Objects.requireNonNull(retention, "retention must not be null");
    Objects.requireNonNull(now, "now must not be null");
    if (retention.isNegative() || retention.isZero()) {
      throw new IllegalArgumentException("retention must be positive");
    }
    return cleanupDelivered(now.minus(retention));
  }

  private ReplayClaim claimReplay(String sourceEventId, UUID actorIdentityId, Instant now) {
    int claimed = repository.claimFailed(sourceEventId, "admin:" + actorIdentityId, now);
    if (claimed == 0) {
      return null;
    }
    PasswordResetEventInboxEntity entity =
        repository.findBySourceEventId(sourceEventId).orElseThrow();
    ReplayClaim replayClaim =
        new ReplayClaim(
            entity.getInboxId(),
            entity.getSourceEventId(),
            entity.getSubject(),
            entity.getAttemptCount(),
            actorIdentityId);
    recordReplayAudit(
        replayClaim, "INTENT", STATUS_FAILED, STATUS_CLAIMED, AuditEntry.OUTCOME_PERMITTED);
    return replayClaim;
  }

  private void finishReplay(ReplayClaim claim, String status, @Nullable String errorCode) {
    try {
      inTransaction(
          () -> {
            if (STATUS_DELIVERED.equals(status)) {
              repository.markDelivered(claim.sourceEventId());
            } else {
              repository.markFailed(claim.sourceEventId(), Objects.requireNonNull(errorCode));
            }
            recordReplayAudit(
                claim,
                "RESULT",
                STATUS_CLAIMED,
                status,
                STATUS_DELIVERED.equals(status)
                    ? AuditEntry.OUTCOME_PERMITTED
                    : AuditEntry.OUTCOME_BLOCKED);
            return null;
          });
    } catch (RuntimeException auditOrPersistenceFailure) {
      markFailed(claim.sourceEventId(), ERROR_CODE_AUDIT_FAILED);
      throw auditOrPersistenceFailure;
    }
  }

  private void recordReplayAudit(
      ReplayClaim claim, String phase, String previousStatus, String newStatus, String outcome) {
    requireAuditRecorder();
    auditRecorder.record(
        AuditEntry.builder()
            .actorIdentityId(claim.actorIdentityId())
            .actorRole("ADMINISTRADOR_GERAL")
            .action(AuditEntry.ACTION_REPROCESSAMENTO_RESET_SENHA)
            .outcome(outcome)
            .previousValue(previousStatus)
            .newValue(newStatus)
            .reason("Password reset replay " + phase + ": sourceEventId=" + claim.sourceEventId())
            .idempotencyKey(
                "password-reset-replay:"
                    + claim.inboxId()
                    + ":"
                    + claim.attemptCount()
                    + ":"
                    + phase)
            .build());
  }

  private void requireAuditRecorder() {
    if (auditRecorder == null) {
      throw new IllegalStateException("AuditRecorder is required for password reset replay");
    }
  }

  private IdentitySessionTerminationService.TerminationSummary terminate(String subject) {
    return sessionTerminationService.terminateAllSessionsFor(
        subject, SessionTerminationReason.SECURITY);
  }

  private void markDelivered(String sourceEventId) {
    inTransaction(() -> repository.markDelivered(sourceEventId));
  }

  private void markFailed(String sourceEventId, String errorCode) {
    inTransaction(() -> repository.markFailed(sourceEventId, errorCode));
  }

  private static InboxProcessingResult deliveredResult(
      String sourceEventId, int terminatedSessionCount) {
    return new InboxProcessingResult(sourceEventId, STATUS_DELIVERED, true, terminatedSessionCount);
  }

  private static InboxProcessingResult failedResult(
      String sourceEventId, boolean effectExecuted, int terminatedSessionCount) {
    return new InboxProcessingResult(
        sourceEventId, STATUS_FAILED, effectExecuted, terminatedSessionCount);
  }

  private <T> T inTransaction(Supplier<T> work) {
    return transactions.execute(status -> work.get());
  }

  private static void requireText(String value, String field) {
    Objects.requireNonNull(value, field + " must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException(field + " must not be blank");
    }
  }
}
