package br.com.betufu.sports.internal.linking;

import br.com.betufu.audit.AuditEntry;
import br.com.betufu.audit.AuditRecorder;
import br.com.betufu.sports.EnrollmentFingerprintPort;
import br.com.betufu.sports.EnrollmentFingerprintPort.EnrollmentVerifiedEvent;
import br.com.betufu.sports.EnrollmentFingerprintPort.Fingerprint;
import br.com.betufu.sports.internal.athlete.SportsAthleteCreated;
import br.com.betufu.sports.internal.linking.AthleteLinkModels.AthleteLinkProposalEntity;
import br.com.betufu.sports.internal.linking.AthleteLinkModels.AthleteMatch;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Profile("runtime | integrated")
public class AthleteLinkReconciler {

  private final AthleteLinkRepository repository;
  private final EnrollmentFingerprintPort fingerprintPort;
  private final TransactionTemplate transactions;
  private final AuditRecorder auditRecorder;

  public AthleteLinkReconciler(
      AthleteLinkRepository repository,
      EnrollmentFingerprintPort fingerprintPort,
      PlatformTransactionManager transactionManager,
      AuditRecorder auditRecorder) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.fingerprintPort =
        Objects.requireNonNull(fingerprintPort, "fingerprintPort must not be null");
    this.transactions =
        new TransactionTemplate(
            Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
    this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder must not be null");
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onEnrollmentVerified(EnrollmentVerifiedEvent event) {
    if (event == null || event.identityId() == null) {
      return;
    }
    reconcileAccountFirst(event.identityId());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onSportsAthleteCreated(SportsAthleteCreated event) {
    if (event == null || event.athleteId() == null) {
      return;
    }
    reconcileAthleteFirst(event.athleteId());
  }

  public void reconcileAccountFirst(UUID identityId) {
    Optional<Fingerprint> fingerprintOpt =
        fingerprintPort.findActiveFingerprintByIdentityId(identityId);
    if (fingerprintOpt.isEmpty()) {
      return;
    }
    Fingerprint fingerprint = fingerprintOpt.get();
    List<AthleteMatch> matchingAthletes = repository.findAthletesByHmac(fingerprint.hash());

    if (matchingAthletes.isEmpty()) {
      return;
    }

    if (matchingAthletes.size() > 1) {
      transactions.execute(
          status -> {
            int inserted =
                repository.insertReview(
                    UUID.randomUUID(), null, null, identityId, "MULTIPLE_CANDIDATES", "PENDENTE");
            recordReviewAuditIfInserted(inserted, identityId, null, "MULTIPLE_CANDIDATES");
            return null;
          });
      return;
    }

    AthleteMatch athlete = matchingAthletes.getFirst();
    reconcilePair(athlete, identityId, fingerprint);
  }

  public void reconcileAthleteFirst(UUID athleteId) {
    Optional<AthleteMatch> athleteOpt = repository.findAthleteById(athleteId);
    if (athleteOpt.isEmpty()) {
      return;
    }
    AthleteMatch athlete = athleteOpt.get();
    if (athlete.matriculaHmac() == null) {
      return;
    }

    List<UUID> matchingIdentities =
        fingerprintPort.findActiveIdentitiesByFingerprint(athlete.matriculaHmac());
    if (matchingIdentities.isEmpty()) {
      return;
    }

    if (matchingIdentities.size() > 1) {
      transactions.execute(
          status -> {
            int inserted =
                repository.insertReview(
                    UUID.randomUUID(), null, athleteId, null, "MULTIPLE_CANDIDATES", "PENDENTE");
            recordReviewAuditIfInserted(inserted, null, athleteId, "MULTIPLE_CANDIDATES");
            return null;
          });
      return;
    }

    UUID identityId = matchingIdentities.getFirst();
    Optional<Fingerprint> fingerprintOpt =
        fingerprintPort.findActiveFingerprintByIdentityId(identityId);
    if (fingerprintOpt.isEmpty()) {
      return;
    }

    reconcilePair(athlete, identityId, fingerprintOpt.get());
  }

  private void reconcilePair(AthleteMatch athlete, UUID identityId, Fingerprint fingerprint) {
    transactions.execute(
        status -> {
          boolean hashDivergent = !Arrays.equals(athlete.matriculaHmac(), fingerprint.hash());
          boolean versionDivergent =
              athlete.hmacVersion() != null && athlete.hmacVersion() != fingerprint.version();

          if (hashDivergent || versionDivergent) {
            int inserted =
                repository.insertReview(
                    UUID.randomUUID(),
                    null,
                    athlete.athleteId(),
                    identityId,
                    "DIVERGENT_DATA",
                    "PENDENTE");
            recordReviewAuditIfInserted(
                inserted, identityId, athlete.athleteId(), "DIVERGENT_DATA");
            return null;
          }

          if (repository.hasAcceptedLinkForAthlete(athlete.athleteId())
              || repository.hasAcceptedLinkForIdentity(identityId)) {
            int inserted =
                repository.insertReview(
                    UUID.randomUUID(),
                    null,
                    athlete.athleteId(),
                    identityId,
                    "ALREADY_LINKED",
                    "PENDENTE");
            recordReviewAuditIfInserted(
                inserted, identityId, athlete.athleteId(), "ALREADY_LINKED");
            return null;
          }

          Optional<AthleteLinkProposalEntity> existing =
              repository.findProposalByPair(athlete.athleteId(), identityId);
          if (existing.isPresent()) {
            // Proposal already exists (PENDENTE, ACEITA, REJEITADA, CONTESTADA, EXPIRADA) ->
            // idempotent
            return null;
          }

          repository.insertProposal(UUID.randomUUID(), athlete.athleteId(), identityId, "PENDENTE");
          return null;
        });
  }

  private void recordReviewAuditIfInserted(
      int inserted, UUID identityId, UUID athleteId, String reasonCode) {
    if (inserted == 0) {
      return;
    }
    String scopeKey = identityId != null ? identityId.toString() : athleteId.toString();
    auditRecorder.record(
        AuditEntry.builder()
            .targetIdentityId(identityId)
            .action(AuditEntry.ACTION_REVISAO_VINCULO_ATLETA_CRIADA)
            .outcome(AuditEntry.OUTCOME_PERMITTED)
            .newValue(reasonCode)
            .reason("Reconciliacao automatica sem exposicao de matricula")
            .idempotencyKey("athlete-link-review:" + reasonCode + ":" + scopeKey)
            .build());
  }
}
