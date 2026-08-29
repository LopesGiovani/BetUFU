package br.com.betufu.sports.internal.tracer;

import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.identity.OperationalIdentityLookup.GrantedRole;
import br.com.betufu.platform.identity.OperationalIdentityLookup.OperationalIdentity;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.CompetitionCreateRequest;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.CompetitionResponse;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.EditionCreateRequest;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.EditionResponse;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.EventCreateRequest;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.EventResponse;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.EventSlotRequest;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.PublicEventResponse;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.PublicSportsEventPage;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("runtime | integrated")
class SportsTracerService {

    private static final String GENERAL_ADMIN = "ADMINISTRADOR_GERAL";
    private static final String COMPETITION_ADMIN = "ADMINISTRADOR_COMPETICAO";
    static final int MAX_PUBLIC_PAGE_SIZE = 50;

    private final SportsTracerRepository repository;
    private final OperationalIdentityLookup identityLookup;
    private final TransactionOperations transactions;

    SportsTracerService(
            SportsTracerRepository repository,
            OperationalIdentityLookup identityLookup,
            PlatformTransactionManager transactionManager) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.identityLookup = Objects.requireNonNull(identityLookup, "identityLookup must not be null");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(
                transactionManager, "transactionManager must not be null"));
    }

    CompetitionResponse createCompetition(
            String issuer,
            String subject,
            CompetitionCreateRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        requireGlobalAdministrator(resolveIdentity(issuer, subject));
        requireUtf8Length(request.name().trim(), 160);
        return repository.createCompetition(request);
    }

    EditionResponse createEdition(
            String issuer,
            String subject,
            UUID competitionId,
            EditionCreateRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        requireGlobalAdministrator(resolveIdentity(issuer, subject));
        requireCompetition(competitionId);
        validateEditionDates(request);
        requireUtf8Length(request.name().trim(), 160);
        return repository.createEdition(competitionId, request);
    }

    EventResponse createEvent(
            String issuer,
            String subject,
            UUID editionId,
            EventCreateRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        SportsTracerRepository.EditionScope edition = repository.findEditionScope(editionId)
                .orElseThrow(SportsTracerService::notFound);
        OperationalIdentity actor = resolveIdentity(issuer, subject);
        requireCompetitionAdministrator(actor, edition.competitionId());
        validateEvent(request);

        EventResponse response = transactions.execute(status -> repository.createEvent(edition, request));
        return Objects.requireNonNull(response, "event transaction returned null");
    }

    PublicEventResponse publishEvent(
            String issuer,
            String subject,
            UUID eventId,
            Long expectedLockVersion) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(expectedLockVersion, "expectedLockVersion must not be null");

        PublicEventResponse response = transactions.execute(status -> {
            SportsTracerRepository.EventWork event = repository.findEventForUpdate(eventId)
                    .orElseThrow(SportsTracerService::notFound);
            OperationalIdentity actor = resolveIdentity(issuer, subject);
            requireCompetitionAdministrator(actor, event.competitionId());

            if (!"RASCUNHO".equals(event.status()) || event.lockVersion() != expectedLockVersion) {
                throw staleVersion();
            }

            List<EventSlotRequest> slots = repository.findEventSlots(eventId);
            if (slots.size() != 2) {
                throw new IllegalStateException("HEAD_TO_HEAD event must have exactly two persisted slots");
            }

            long versionNo = repository.nextPublicationVersion(eventId);
            UUID supersedesPublicationId = repository.latestPublicationId(eventId).orElse(null);
            PublicEventResponse publication = repository.insertPublication(
                    event,
                    slots,
                    actor.identityId(),
                    versionNo,
                    supersedesPublicationId);

            if (repository.markEventPublished(eventId, expectedLockVersion) != 1) {
                throw staleVersion();
            }
            return publication;
        });

        return Objects.requireNonNull(response, "publication transaction returned null");
    }

    PublicEventResponse getPublishedEvent(UUID eventId) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        return repository.findLatestPublication(eventId)
                .orElseThrow(SportsTracerService::notFound);
    }

    PublicSportsEventPage getPublishedEvents(
            UUID competitionId,
            UUID editionId,
            Instant from,
            Instant to,
            int page,
            int size) {
        if (page < 0 || size < 1 || size > MAX_PUBLIC_PAGE_SIZE) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
        }
        if (from != null && to != null && to.isBefore(from)) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
        }

        try {
            Math.multiplyExact((long) page, size);
        } catch (ArithmeticException ex) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
        }

        PublicSportsEventPage pageResponse = transactions.execute(status -> {
            SportsTracerRepository.PublishedEventsPageData result = repository.findPublishedEvents(
                    competitionId,
                    editionId,
                    from,
                    to,
                    page,
                    size);
            long totalElements = result.totalElements();
            long totalPagesLong = totalElements == 0
                    ? 0
                    : ((totalElements - 1) / size) + 1;
            int totalPages;
            try {
                totalPages = Math.toIntExact(totalPagesLong);
            } catch (ArithmeticException ex) {
                throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
            }
            boolean hasNext = ((long) page + 1) * size < totalElements;
            return new PublicSportsEventPage(
                    result.items(),
                    page,
                    size,
                    totalElements,
                    totalPages,
                    hasNext);
        });
        return Objects.requireNonNull(pageResponse, "public agenda transaction returned null");
    }

    private OperationalIdentity resolveIdentity(String issuer, String subject) {
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
            throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
        }
        return identityLookup.lookupIdentity(issuer, subject)
                .orElseThrow(() -> new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN));
    }

    private static void requireGlobalAdministrator(OperationalIdentity identity) {
        boolean permitted = identity.roles().stream()
                .anyMatch(role -> GENERAL_ADMIN.equals(role.roleName()) && role.scopeRef() == null);
        if (!permitted) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
        }
    }

    private static void requireCompetitionAdministrator(
            OperationalIdentity identity,
            UUID competitionId) {
        GrantedRole requiredRole = new GrantedRole(COMPETITION_ADMIN, competitionId);
        if (!identity.roles().contains(requiredRole)) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
        }
    }

    private void requireCompetition(UUID competitionId) {
        if (!repository.existsCompetition(competitionId)) {
            throw notFound();
        }
    }

    private static void validateEditionDates(EditionCreateRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.startDate() != null
                && request.endDate() != null
                && request.endDate().isBefore(request.startDate())) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
        }
    }

    private static void validateEvent(EventCreateRequest request) {
        requireUtf8Length(request.title().trim(), 200);
        requireUtf8Length(request.zoneId(), 64);
        requireUtf8Length(request.sourceReference().trim(), 2048);

        try {
            ZoneId.of(request.zoneId());
        } catch (DateTimeException ex) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
        }

        Set<Integer> slotNumbers = new HashSet<>();
        for (EventSlotRequest slot : request.slots()) {
            requireUtf8Length(slot.displayLabel().trim(), 160);
            if (!slotNumbers.add(slot.slotNo())) {
                throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
            }
        }
    }

    private static void requireUtf8Length(String value, int maximumOctets) {
        if (value.getBytes(StandardCharsets.UTF_8).length > maximumOctets) {
            throw new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
        }
    }

    private static ApiProblemException notFound() {
        return new ApiProblemException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND);
    }

    private static ApiProblemException staleVersion() {
        return new ApiProblemException(HttpStatus.CONFLICT, ApiErrorCode.STALE_VERSION);
    }
}
