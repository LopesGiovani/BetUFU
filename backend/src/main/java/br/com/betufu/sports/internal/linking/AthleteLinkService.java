package br.com.betufu.sports.internal.linking;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import br.com.betufu.audit.AuditEntry;
import br.com.betufu.audit.AuditRecorder;
import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.identity.OperationalIdentityLookup.OperationalIdentity;
import br.com.betufu.sports.internal.linking.AthleteLinkModels.AthleteLinkDecisionRequest;
import br.com.betufu.sports.internal.linking.AthleteLinkModels.AthleteLinkProposal;
import br.com.betufu.sports.internal.linking.AthleteLinkModels.AthleteLinkProposalEntity;
import br.com.betufu.sports.internal.linking.AthleteLinkModels.AthleteLinkReviewPage;
import br.com.betufu.sports.internal.linking.AthleteLinkModels.AthleteLinkReviewSummary;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("runtime | integrated")
class AthleteLinkService {

    private static final String GENERAL_ADMIN = "ADMINISTRADOR_GERAL";
    private final AthleteLinkRepository repository;
    private final OperationalIdentityLookup identityLookup;
    private final TransactionTemplate transactions;
    private final AuditRecorder auditRecorder;

    AthleteLinkService(
            AthleteLinkRepository repository,
            OperationalIdentityLookup identityLookup,
            PlatformTransactionManager transactionManager,
            AuditRecorder auditRecorder) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.identityLookup = Objects.requireNonNull(identityLookup, "identityLookup must not be null");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager,
                "transactionManager must not be null"));
        this.auditRecorder = Objects.requireNonNull(auditRecorder, "auditRecorder must not be null");
    }

    AthleteLinkProposal getMyPendingProposal(String issuer, String subject) {
        UUID identityId = resolveIdentityId(issuer, subject);
        transactions.executeWithoutResult(status -> repository.expirePendingProposalsForIdentity(identityId));
        return transactions.execute(status -> repository.findPendingProposalByIdentityId(identityId)
                .orElseThrow(AthleteLinkService::notFound));
    }

    AthleteLinkProposal getProposal(String issuer, String subject, UUID proposalId) {
        UUID identityId = resolveIdentityId(issuer, subject);
        transactions.executeWithoutResult(status -> repository.expireProposalIfNeeded(proposalId));
        return transactions.execute(status -> {
            AthleteLinkProposalEntity entity = repository.findProposalById(proposalId)
                    .orElseThrow(AthleteLinkService::notFound);
            if (!entity.identityId().equals(identityId)) {
                throw forbidden();
            }
            return repository.findProposalWithAthleteById(proposalId)
                    .orElseThrow(AthleteLinkService::notFound);
        });
    }

    AthleteLinkProposal decideProposal(
            String issuer,
            String subject,
            UUID proposalId,
            AthleteLinkDecisionRequest request) {
        if (request == null || request.decision() == null || request.decision().isBlank()) {
            throw validation();
        }
        UUID identityId = resolveIdentityId(issuer, subject);

        try {
            return transactions.execute(status -> {
                repository.expireProposalIfNeeded(proposalId);
                AthleteLinkProposalEntity entity = repository.findProposalById(proposalId)
                        .orElseThrow(AthleteLinkService::notFound);

                if (!entity.identityId().equals(identityId)) {
                    throw forbidden();
                }

                return switch (request.decision()) {
                    case "ACCEPT" -> acceptProposal(entity, identityId);
                    case "REJECT" -> rejectProposal(entity, identityId);
                    case "CONTEST" -> contestProposal(entity, identityId);
                    default -> throw validation();
                };
            });
        } catch (DataIntegrityViolationException ex) {
            // A competing proposal may win a partial unique index between the pre-check and UPDATE.
            throw conflict();
        }
    }

    private AthleteLinkProposal acceptProposal(AthleteLinkProposalEntity entity, UUID actorIdentityId) {
        if ("ACEITA".equals(entity.status())) {
            return repository.findProposalWithAthleteById(entity.proposalId()).orElseThrow();
        }
        if (!"PENDENTE".equals(entity.status())) {
            throw conflict();
        }
        if (repository.hasAcceptedLinkForAthlete(entity.athleteId())
                || repository.hasAcceptedLinkForIdentity(entity.identityId())) {
            throw conflict();
        }

        int updated = repository.updateProposalStatus(
                entity.proposalId(), entity.lockVersion(), "ACEITA", actorIdentityId);
        if (updated == 0) {
            AthleteLinkProposalEntity latest = repository.findProposalById(entity.proposalId())
                    .orElseThrow(AthleteLinkService::notFound);
            if ("ACEITA".equals(latest.status())) {
                return repository.findProposalWithAthleteById(entity.proposalId()).orElseThrow();
            }
            throw conflict();
        }
        recordDecisionAudit(entity, actorIdentityId, "ACEITA");
        return repository.findProposalWithAthleteById(entity.proposalId()).orElseThrow();
    }

    private AthleteLinkProposal rejectProposal(AthleteLinkProposalEntity entity, UUID actorIdentityId) {
        if ("REJEITADA".equals(entity.status())) {
            return repository.findProposalWithAthleteById(entity.proposalId()).orElseThrow();
        }
        if (!"PENDENTE".equals(entity.status())) {
            throw conflict();
        }

        int updated = repository.updateProposalStatus(
                entity.proposalId(), entity.lockVersion(), "REJEITADA", actorIdentityId);
        if (updated == 0) {
            AthleteLinkProposalEntity latest = repository.findProposalById(entity.proposalId())
                    .orElseThrow(AthleteLinkService::notFound);
            if ("REJEITADA".equals(latest.status())) {
                return repository.findProposalWithAthleteById(entity.proposalId()).orElseThrow();
            }
            throw conflict();
        }
        recordDecisionAudit(entity, actorIdentityId, "REJEITADA");
        return repository.findProposalWithAthleteById(entity.proposalId()).orElseThrow();
    }

    private AthleteLinkProposal contestProposal(AthleteLinkProposalEntity entity, UUID actorIdentityId) {
        if ("CONTESTADA".equals(entity.status())) {
            return repository.findProposalWithAthleteById(entity.proposalId()).orElseThrow();
        }
        if (!"PENDENTE".equals(entity.status())) {
            throw conflict();
        }

        int updated = repository.updateProposalStatus(
                entity.proposalId(), entity.lockVersion(), "CONTESTADA", actorIdentityId);
        if (updated == 0) {
            AthleteLinkProposalEntity latest = repository.findProposalById(entity.proposalId())
                    .orElseThrow(AthleteLinkService::notFound);
            if ("CONTESTADA".equals(latest.status())) {
                return repository.findProposalWithAthleteById(entity.proposalId()).orElseThrow();
            }
            throw conflict();
        }

        int reviewInserted = repository.insertReview(
                UUID.randomUUID(),
                entity.proposalId(),
                entity.athleteId(),
                entity.identityId(),
                "CONTESTED",
                "PENDENTE");

        if (reviewInserted > 0) {
            recordReviewAudit(entity, actorIdentityId, "CONTESTED");
        }
        recordDecisionAudit(entity, actorIdentityId, "CONTESTADA");
        return repository.findProposalWithAthleteById(entity.proposalId()).orElseThrow();
    }

    AthleteLinkReviewPage listReviews(String issuer, String subject, int page, int size) {
        OperationalIdentity actor = resolveOperationalIdentity(issuer, subject);
        requireReviewAccess(actor);

        int safePage = Math.max(0, page);
        int safeSize = Math.min(50, Math.max(1, size));
        long totalElements = repository.countReviews();
        int offset = safePage * safeSize;
        List<AthleteLinkReviewSummary> items = repository.findReviews(safeSize, offset);
        int totalPages = safeSize == 0 ? 0 : (int) Math.ceil((double) totalElements / safeSize);
        boolean hasNext = (long) (safePage + 1) * safeSize < totalElements;

        return new AthleteLinkReviewPage(items, safePage, safeSize, totalElements, totalPages, hasNext);
    }

    private UUID resolveIdentityId(String issuer, String subject) {
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
            throw unauthorized();
        }
        return identityLookup.findIdentityId(issuer, subject)
                .orElseThrow(AthleteLinkService::unauthorized);
    }

    private OperationalIdentity resolveOperationalIdentity(String issuer, String subject) {
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
            throw unauthorized();
        }
        return identityLookup.lookupIdentity(issuer, subject)
                .orElseThrow(AthleteLinkService::forbidden);
    }

    private static void requireReviewAccess(OperationalIdentity actor) {
        // Until reviews carry a competition scope, only the global administrator can read them.
        boolean allowed = actor.roles().stream().anyMatch(role ->
                GENERAL_ADMIN.equals(role.roleName()) && role.scopeRef() == null);
        if (!allowed) {
            throw forbidden();
        }
    }

    private void recordDecisionAudit(
            AthleteLinkProposalEntity entity,
            UUID actorIdentityId,
            String newStatus) {
        auditRecorder.record(AuditEntry.builder()
                .actorIdentityId(actorIdentityId)
                .actorRole("OWNER")
                .targetIdentityId(entity.identityId())
                .action(AuditEntry.ACTION_VINCULO_ATLETA_DECIDIDO)
                .outcome(AuditEntry.OUTCOME_PERMITTED)
                .previousValue(entity.status())
                .newValue(newStatus)
                .reason("Decisao owner explicita")
                .correlationId(MDC.get("correlationId"))
                .idempotencyKey("athlete-link-decision:" + entity.proposalId() + ":" + newStatus)
                .build());
    }

    private void recordReviewAudit(
            AthleteLinkProposalEntity entity,
            UUID actorIdentityId,
            String reasonCode) {
        auditRecorder.record(AuditEntry.builder()
                .actorIdentityId(actorIdentityId)
                .actorRole("OWNER")
                .targetIdentityId(entity.identityId())
                .action(AuditEntry.ACTION_REVISAO_VINCULO_ATLETA_CRIADA)
                .outcome(AuditEntry.OUTCOME_PERMITTED)
                .newValue(reasonCode)
                .reason("Revisao O8 criada sem dado de matricula")
                .correlationId(MDC.get("correlationId"))
                .idempotencyKey("athlete-link-review:" + entity.proposalId() + ":" + reasonCode)
                .build());
    }

    private static ApiProblemException validation() {
        return new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
    }

    private static ApiProblemException notFound() {
        return new ApiProblemException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND);
    }

    private static ApiProblemException forbidden() {
        return new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
    }

    private static ApiProblemException unauthorized() {
        return new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
    }

    private static ApiProblemException conflict() {
        return new ApiProblemException(HttpStatus.CONFLICT, ApiErrorCode.STALE_VERSION);
    }
}
