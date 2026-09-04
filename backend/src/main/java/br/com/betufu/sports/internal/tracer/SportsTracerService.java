package br.com.betufu.sports.internal.tracer;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.identity.OperationalIdentityLookup.GrantedRole;
import br.com.betufu.platform.identity.OperationalIdentityLookup.OperationalIdentity;
import br.com.betufu.sports.internal.event.SportsEventModels;
import br.com.betufu.sports.internal.event.SportsEventModels.SportsEventDetail;
import br.com.betufu.sports.internal.event.SportsEventService;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.CompetitionCreateRequest;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.CompetitionResponse;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.EditionCreateRequest;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.EditionResponse;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.EventFormat;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.EventCreateRequest;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.EventResponse;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.EventSlotRequest;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.PublicEventResponse;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.PublicSportsEventPage;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.VenueState;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
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
  private final SportsEventService eventService;
  private final OperationalIdentityLookup identityLookup;
  private final TransactionOperations transactions;

  SportsTracerService(
      SportsTracerRepository repository,
      SportsEventService eventService,
      OperationalIdentityLookup identityLookup,
      PlatformTransactionManager transactionManager) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.eventService = Objects.requireNonNull(eventService, "eventService must not be null");
    this.identityLookup = Objects.requireNonNull(identityLookup, "identityLookup must not be null");
    this.transactions =
        new TransactionTemplate(
            Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
  }

  CompetitionResponse createCompetition(
      String issuer, String subject, CompetitionCreateRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    requireGlobalAdministrator(resolveIdentity(issuer, subject));
    requireUtf8Length(request.name().trim(), 160);
    return repository.createCompetition(request);
  }

  EditionResponse createEdition(
      String issuer, String subject, UUID competitionId, EditionCreateRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    requireGlobalAdministrator(resolveIdentity(issuer, subject));
    requireCompetition(competitionId);
    validateEditionDates(request);
    requireUtf8Length(request.name().trim(), 160);
    return repository.createEdition(competitionId, request);
  }

  EventResponse createEvent(
      String issuer, String subject, UUID editionId, EventCreateRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    SportsTracerRepository.EditionScope edition =
        repository.findEditionScope(editionId).orElseThrow(SportsTracerService::notFound);
    OperationalIdentity actor = resolveIdentity(issuer, subject);
    requireCompetitionAdministrator(actor, edition.competitionId());
    validateEvent(request);
    SportsTracerRepository.EventCatalogScope catalog =
        repository
            .findEventCatalogScope(editionId, request)
            .orElseThrow(SportsTracerService::notFound);
    validateCatalogReferences(request, catalog);

    SportsEventDetail detail =
        eventService.createEvent(
            issuer,
            subject,
            editionId,
            new SportsEventModels.SportsEventCreateRequest(
                request.title(),
                SportsEventModels.EventFormat.valueOf(request.format().name()),
                request.scheduledAt(),
                request.zoneId(),
                SportsEventModels.VenueState.valueOf(request.venueState().name()),
                request.sourceReference(),
                request.slots().stream()
                    .map(
                        slot ->
                            new SportsEventModels.SportsEventSlotRequest(
                                slot.slotNo(), slot.displayLabel(), null))
                    .toList(),
                request.sportId(),
                request.categoryId(),
                request.phaseId(),
                request.venueId(),
                request.venueSpaceId()));
    return toLegacyEventResponse(detail);
  }

  PublicEventResponse publishEvent(
      String issuer, String subject, UUID eventId, Long expectedLockVersion) {
    Objects.requireNonNull(eventId, "eventId must not be null");
    Objects.requireNonNull(expectedLockVersion, "expectedLockVersion must not be null");
    UUID competitionId =
        repository.findEventCompetitionId(eventId).orElseThrow(SportsTracerService::notFound);
    OperationalIdentity actor = resolveIdentity(issuer, subject);
    requireCompetitionAdministrator(actor, competitionId);
    if (!"RASCUNHO".equals(repository.findEventStatus(eventId).orElseThrow(SportsTracerService::notFound))) {
      throw validation();
    }

    eventService.publishEvent(issuer, subject, eventId, expectedLockVersion);
    return repository.findLatestPublication(eventId).orElseThrow(SportsTracerService::notFound);
  }

  private static EventResponse toLegacyEventResponse(SportsEventDetail detail) {
    return new EventResponse(
        detail.eventId(),
        detail.competitionId(),
        detail.editionId(),
        detail.title(),
        EventFormat.valueOf(detail.format().name()),
        detail.status(),
        detail.scheduledAt(),
        detail.zoneId(),
        VenueState.valueOf(detail.venueState().name()),
        detail.lockVersion(),
        detail.sportId(),
        detail.sportName(),
        detail.categoryId(),
        detail.categoryName(),
        detail.phaseId(),
        detail.phaseName(),
        detail.venueId(),
        detail.venueName(),
        detail.venueSpaceId(),
        detail.venueSpaceName());
  }

  PublicEventResponse getPublishedEvent(UUID eventId) {
    Objects.requireNonNull(eventId, "eventId must not be null");
    return repository.findLatestPublication(eventId).orElseThrow(SportsTracerService::notFound);
  }

  PublicSportsEventPage getPublishedEvents(
      UUID competitionId,
      UUID editionId,
      UUID sportId,
      UUID categoryId,
      UUID phaseId,
      VenueState state,
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

    PublicSportsEventPage pageResponse =
        transactions.execute(
            status -> {
              SportsTracerRepository.PublishedEventsPageData result =
                  repository.findPublishedEvents(
                      competitionId,
                      editionId,
                      sportId,
                      categoryId,
                      phaseId,
                      state,
                      from,
                      to,
                      page,
                      size);
              long totalElements = result.totalElements();
              long totalPagesLong = totalElements == 0 ? 0 : ((totalElements - 1) / size) + 1;
              int totalPages;
              try {
                totalPages = Math.toIntExact(totalPagesLong);
              } catch (ArithmeticException ex) {
                throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
              }
              boolean hasNext = ((long) page + 1) * size < totalElements;
              return new PublicSportsEventPage(
                  result.items(), page, size, totalElements, totalPages, hasNext);
            });
    return Objects.requireNonNull(pageResponse, "public agenda transaction returned null");
  }

  private OperationalIdentity resolveIdentity(String issuer, String subject) {
    if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
      throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
    }
    return identityLookup
        .lookupIdentity(issuer, subject)
        .orElseThrow(() -> new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN));
  }

  private static void requireGlobalAdministrator(OperationalIdentity identity) {
    boolean permitted =
        identity.roles().stream()
            .anyMatch(role -> GENERAL_ADMIN.equals(role.roleName()) && role.scopeRef() == null);
    if (!permitted) {
      throw new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
    }
  }

  private static void requireCompetitionAdministrator(
      OperationalIdentity identity, UUID competitionId) {
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

  private static void validateCatalogReferences(
      EventCreateRequest request, SportsTracerRepository.EventCatalogScope catalog) {
    if (request.sportId() != null && !request.sportId().equals(catalog.sportId())) {
      throw validation();
    }
    if (request.categoryId() != null && !request.categoryId().equals(catalog.categoryId())) {
      throw validation();
    }
    if (request.phaseId() != null && !request.phaseId().equals(catalog.phaseId())) {
      throw validation();
    }
    if (request.venueState() == VenueState.LOCAL_A_DEFINIR
        && (request.venueId() != null || request.venueSpaceId() != null)) {
      throw validation();
    }
    if (request.venueState() == VenueState.LOCAL_DEFINIDO
        && (request.venueId() == null || !request.venueId().equals(catalog.venueId()))) {
      throw validation();
    }
    if (request.venueSpaceId() != null && !request.venueSpaceId().equals(catalog.venueSpaceId())) {
      throw validation();
    }
  }

  private static ApiProblemException validation() {
    return new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
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
