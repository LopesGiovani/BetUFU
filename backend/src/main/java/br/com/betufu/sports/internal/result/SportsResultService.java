package br.com.betufu.sports.internal.result;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.identity.OperationalIdentityLookup.OperationalIdentity;
import br.com.betufu.sports.internal.result.SportsResultModels.SportsResultConfirmRequest;
import br.com.betufu.sports.internal.result.SportsResultModels.SportsResultCorrectRequest;
import br.com.betufu.sports.internal.result.SportsResultModels.SportsResultInformRequest;
import br.com.betufu.sports.internal.result.SportsResultModels.SportsResultReviewRequest;
import br.com.betufu.sports.internal.result.SportsResultModels.SportsResultScoreItem;
import br.com.betufu.sports.internal.result.SportsResultModels.SportsResultScoreRequest;
import br.com.betufu.sports.internal.result.SportsResultModels.SportsResultVersion;
import br.com.betufu.sports.internal.result.SportsResultRepository.EventScope;
import br.com.betufu.sports.internal.result.SportsResultRepository.EventSlot;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("runtime | integrated")
public class SportsResultService {

    private static final String GENERAL_ADMIN = "ADMINISTRADOR_GERAL";
    private static final String COMPETITION_ADMIN = "ADMINISTRADOR_COMPETICAO";

    private final SportsResultRepository repository;
    private final OperationalIdentityLookup identityLookup;
    private final TransactionTemplate transactions;

    SportsResultService(
            SportsResultRepository repository,
            OperationalIdentityLookup identityLookup,
            PlatformTransactionManager transactionManager) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.identityLookup = Objects.requireNonNull(identityLookup, "identityLookup must not be null");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(
                transactionManager, "transactionManager must not be null"));
    }

    public List<SportsResultVersion> getResultVersions(String issuer, String subject, UUID eventId) {
        OperationalIdentity actor = resolveIdentity(issuer, subject);
        EventScope event = repository.findEventScope(eventId)
                .orElseThrow(SportsResultService::notFound);
        requireScopeAccess(actor, event.competitionId());

        return repository.findResultVersions(eventId);
    }

    public SportsResultVersion informResult(
            String issuer,
            String subject,
            UUID eventId,
            Long expectedLockVersion,
            SportsResultInformRequest request) {
        requireExpectedLockVersion(expectedLockVersion);
        if (request == null || request.sourceReference() == null || request.sourceReference().isBlank()) {
            throw validation();
        }
        requireUtf8Length(request.sourceReference().trim(), 2048);
        validateScores(request.scores());

        EventScope event = repository.findEventScope(eventId)
                .orElseThrow(SportsResultService::notFound);
        OperationalIdentity actor = resolveIdentity(issuer, subject);
        requireWritableCompetition(actor, event.competitionId());

        if ("RASCUNHO".equals(event.status()) || "CANCELADO".equals(event.status())) {
            throw validation();
        }

        List<EventSlot> slots = repository.findEventSlots(eventId);
        List<SportsResultScoreItem> scoreItems = buildScoreItems(slots, request.scores());

        return transactions.execute(status -> {
            EventScope locked = repository.findEventScopeForUpdate(eventId)
                    .orElseThrow(SportsResultService::notFound);
            if (locked.lockVersion() != expectedLockVersion) {
                throw staleVersion();
            }
            if ("RASCUNHO".equals(locked.status()) || "CANCELADO".equals(locked.status())) {
                throw validation();
            }

            long versionNo = repository.nextResultVersionNo(eventId);
            Optional<SportsResultVersion> latest = repository.findLatestResultVersion(eventId);
            UUID supersedesId = latest.map(SportsResultVersion::versionId).orElse(null);

            SportsResultVersion created = repository.insertResultVersion(
                    eventId,
                    locked.competitionId(),
                    locked.editionId(),
                    versionNo,
                    "INFORMADO",
                    request.sourceReference().trim(),
                    null,
                    actor.identityId(),
                    null,
                    Instant.now(),
                    null,
                    supersedesId,
                    expectedLockVersion,
                    scoreItems);

            int lockUpdated = repository.updateEventLockVersion(eventId, expectedLockVersion);
            if (lockUpdated == 0) {
                throw staleVersion();
            }

            return created;
        });
    }

    public SportsResultVersion submitForReviewResult(
            String issuer,
            String subject,
            UUID eventId,
            Long expectedLockVersion,
            SportsResultReviewRequest request) {
        requireExpectedLockVersion(expectedLockVersion);
        if (request == null || request.sourceReference() == null || request.sourceReference().isBlank()) {
            throw validation();
        }
        requireUtf8Length(request.sourceReference().trim(), 2048);
        validateScores(request.scores());

        EventScope event = repository.findEventScope(eventId)
                .orElseThrow(SportsResultService::notFound);
        OperationalIdentity actor = resolveIdentity(issuer, subject);
        requireWritableCompetition(actor, event.competitionId());

        if ("RASCUNHO".equals(event.status()) || "CANCELADO".equals(event.status())) {
            throw validation();
        }

        List<EventSlot> slots = repository.findEventSlots(eventId);
        List<SportsResultScoreItem> scoreItems = buildScoreItems(slots, request.scores());

        return transactions.execute(status -> {
            EventScope locked = repository.findEventScopeForUpdate(eventId)
                    .orElseThrow(SportsResultService::notFound);
            if (locked.lockVersion() != expectedLockVersion) {
                throw staleVersion();
            }
            if ("RASCUNHO".equals(locked.status()) || "CANCELADO".equals(locked.status())) {
                throw validation();
            }

            long versionNo = repository.nextResultVersionNo(eventId);
            Optional<SportsResultVersion> latest = repository.findLatestResultVersion(eventId);
            UUID supersedesId = latest.map(SportsResultVersion::versionId).orElse(null);

            SportsResultVersion created = repository.insertResultVersion(
                    eventId,
                    locked.competitionId(),
                    locked.editionId(),
                    versionNo,
                    "AGUARDANDO_REVISAO",
                    request.sourceReference().trim(),
                    null,
                    actor.identityId(),
                    null,
                    Instant.now(),
                    null,
                    supersedesId,
                    expectedLockVersion,
                    scoreItems);

            int lockUpdated = repository.updateEventLockVersion(eventId, expectedLockVersion);
            if (lockUpdated == 0) {
                throw staleVersion();
            }

            return created;
        });
    }

    public SportsResultVersion confirmResult(
            String issuer,
            String subject,
            UUID eventId,
            Long expectedLockVersion,
            SportsResultConfirmRequest request) {
        requireExpectedLockVersion(expectedLockVersion);
        if (request == null || request.sourceReference() == null || request.sourceReference().isBlank()) {
            throw validation();
        }
        requireUtf8Length(request.sourceReference().trim(), 2048);
        validateScores(request.scores());

        EventScope event = repository.findEventScope(eventId)
                .orElseThrow(SportsResultService::notFound);
        OperationalIdentity actor = resolveIdentity(issuer, subject);
        requireWritableCompetition(actor, event.competitionId());

        if ("RASCUNHO".equals(event.status()) || "CANCELADO".equals(event.status())) {
            throw validation();
        }

        List<EventSlot> slots = repository.findEventSlots(eventId);
        List<SportsResultScoreItem> scoreItems = buildScoreItems(slots, request.scores());

        return transactions.execute(status -> {
            EventScope locked = repository.findEventScopeForUpdate(eventId)
                    .orElseThrow(SportsResultService::notFound);
            if (locked.lockVersion() != expectedLockVersion) {
                throw staleVersion();
            }
            if ("RASCUNHO".equals(locked.status()) || "CANCELADO".equals(locked.status())) {
                throw validation();
            }

            long versionNo = repository.nextResultVersionNo(eventId);
            Optional<SportsResultVersion> latest = repository.findLatestResultVersion(eventId);
            UUID supersedesId = latest.map(SportsResultVersion::versionId).orElse(null);
            Instant now = Instant.now();

            SportsResultVersion created = repository.insertResultVersion(
                    eventId,
                    locked.competitionId(),
                    locked.editionId(),
                    versionNo,
                    "CONFIRMADO",
                    request.sourceReference().trim(),
                    null,
                    actor.identityId(),
                    actor.identityId(),
                    now,
                    now,
                    supersedesId,
                    expectedLockVersion,
                    scoreItems);

            int eventUpdated = repository.updateEventConfirmedVersion(
                    eventId,
                    created.versionId(),
                    expectedLockVersion);
            if (eventUpdated == 0) {
                throw staleVersion();
            }

            return created;
        });
    }

    public SportsResultVersion correctResult(
            String issuer,
            String subject,
            UUID eventId,
            Long expectedLockVersion,
            SportsResultCorrectRequest request) {
        requireExpectedLockVersion(expectedLockVersion);
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw validation();
        }
        requireUtf8Length(request.reason().trim(), 1024);
        if (request.sourceReference() == null || request.sourceReference().isBlank()) {
            throw validation();
        }
        requireUtf8Length(request.sourceReference().trim(), 2048);
        validateScores(request.scores());

        EventScope event = repository.findEventScope(eventId)
                .orElseThrow(SportsResultService::notFound);
        OperationalIdentity actor = resolveIdentity(issuer, subject);
        requireWritableCompetition(actor, event.competitionId());

        if ("RASCUNHO".equals(event.status()) || "CANCELADO".equals(event.status())) {
            throw validation();
        }

        if (event.confirmedVersionId() == null) {
            throw validation();
        }

        List<EventSlot> slots = repository.findEventSlots(eventId);
        List<SportsResultScoreItem> scoreItems = buildScoreItems(slots, request.scores());

        return transactions.execute(status -> {
            EventScope locked = repository.findEventScopeForUpdate(eventId)
                    .orElseThrow(SportsResultService::notFound);
            if (locked.lockVersion() != expectedLockVersion) {
                throw staleVersion();
            }
            if ("RASCUNHO".equals(locked.status()) || "CANCELADO".equals(locked.status())) {
                throw validation();
            }
            if (locked.confirmedVersionId() == null) {
                throw validation();
            }

            long versionNo = repository.nextResultVersionNo(eventId);
            UUID supersedesId = locked.confirmedVersionId();
            Instant now = Instant.now();

            SportsResultVersion created = repository.insertResultVersion(
                    eventId,
                    locked.competitionId(),
                    locked.editionId(),
                    versionNo,
                    "CORRIGIDO",
                    request.sourceReference().trim(),
                    request.reason().trim(),
                    actor.identityId(),
                    actor.identityId(),
                    now,
                    now,
                    supersedesId,
                    expectedLockVersion,
                    scoreItems);

            int eventUpdated = repository.updateEventConfirmedVersion(
                    eventId,
                    created.versionId(),
                    expectedLockVersion);
            if (eventUpdated == 0) {
                throw staleVersion();
            }

            return created;
        });
    }

    private void validateScores(List<SportsResultScoreRequest> scores) {
        if (scores == null || scores.size() != 2) {
            throw validation();
        }
        Set<Integer> slotNos = new HashSet<>();
        for (SportsResultScoreRequest score : scores) {
            if (score == null || score.score() < 0 || score.slotNo() < 1 || score.slotNo() > 2) {
                throw validation();
            }
            if (!slotNos.add(score.slotNo())) {
                throw validation();
            }
        }
    }

    private List<SportsResultScoreItem> buildScoreItems(
            List<EventSlot> slots,
            List<SportsResultScoreRequest> scores) {
        if (slots.size() != 2) {
            throw validation();
        }
        Map<Integer, SportsResultScoreRequest> requestBySlot = scores.stream()
                .collect(Collectors.toMap(SportsResultScoreRequest::slotNo, Function.identity()));

        List<SportsResultScoreItem> items = new ArrayList<>();
        for (EventSlot slot : slots) {
            SportsResultScoreRequest req = requestBySlot.get(slot.slotNo());
            if (req == null) {
                throw validation();
            }
            items.add(new SportsResultScoreItem(
                    slot.slotNo(),
                    slot.displayLabel(),
                    req.score(),
                    slot.teamId(),
                    slot.teamName()));
        }
        return items;
    }

    private OperationalIdentity resolveIdentity(String issuer, String subject) {
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
            throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
        }
        return identityLookup.lookupIdentity(issuer, subject)
                .orElseThrow(() -> new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN));
    }

    private static void requireScopeAccess(OperationalIdentity actor, UUID competitionId) {
        boolean allowed = actor.roles().stream().anyMatch(role ->
                (GENERAL_ADMIN.equals(role.roleName()) && role.scopeRef() == null)
                        || (COMPETITION_ADMIN.equals(role.roleName()) && competitionId.equals(role.scopeRef())));
        if (!allowed) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
        }
    }

    private static void requireWritableCompetition(OperationalIdentity actor, UUID competitionId) {
        if (competitionId == null) {
            throw notFound();
        }
        boolean permitted = isGlobal(actor) || actor.roles().stream().anyMatch(role ->
                COMPETITION_ADMIN.equals(role.roleName()) && competitionId.equals(role.scopeRef()));
        if (!permitted) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
        }
    }

    private static boolean isGlobal(OperationalIdentity actor) {
        return actor.roles().stream().anyMatch(role -> GENERAL_ADMIN.equals(role.roleName()) && role.scopeRef() == null);
    }

    private static void requireExpectedLockVersion(Long expected) {
        if (expected == null || expected < 0) {
            throw validation();
        }
    }

    private static void requireUtf8Length(String value, int maxOctets) {
        if (value.getBytes(StandardCharsets.UTF_8).length > maxOctets) {
            throw validation();
        }
    }

    private static ApiProblemException validation() {
        return new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
    }

    private static ApiProblemException notFound() {
        return new ApiProblemException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND);
    }

    private static ApiProblemException staleVersion() {
        return new ApiProblemException(HttpStatus.CONFLICT, ApiErrorCode.STALE_VERSION);
    }
}
