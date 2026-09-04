package br.com.betufu.sports.internal.event;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.identity.OperationalIdentityLookup.OperationalIdentity;
import br.com.betufu.sports.internal.event.SportsEventModels.EventFormat;
import br.com.betufu.sports.internal.event.SportsEventModels.SportsEventCancelRequest;
import br.com.betufu.sports.internal.event.SportsEventModels.SportsEventCreateRequest;
import br.com.betufu.sports.internal.event.SportsEventModels.SportsEventDetail;
import br.com.betufu.sports.internal.event.SportsEventModels.SportsEventLifecycleEntry;
import br.com.betufu.sports.internal.event.SportsEventModels.SportsEventPostponeRequest;
import br.com.betufu.sports.internal.event.SportsEventModels.SportsEventSlotItem;
import br.com.betufu.sports.internal.event.SportsEventModels.SportsEventSlotRequest;
import br.com.betufu.sports.internal.event.SportsEventModels.SportsEventSuspendRequest;
import br.com.betufu.sports.internal.event.SportsEventModels.SportsEventUpdateRequest;
import br.com.betufu.sports.internal.event.SportsEventModels.VenueState;
import br.com.betufu.sports.internal.event.SportsEventRepository.CatalogScope;
import br.com.betufu.sports.internal.event.SportsEventRepository.EditionScope;
import br.com.betufu.sports.internal.event.SportsEventRepository.EventScope;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
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
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("runtime | integrated")
public class SportsEventService {

  private static final String GENERAL_ADMIN = "ADMINISTRADOR_GERAL";
  private static final String COMPETITION_ADMIN = "ADMINISTRADOR_COMPETICAO";

  private final SportsEventRepository repository;
  private final OperationalIdentityLookup identityLookup;
  private final TransactionTemplate transactions;

  SportsEventService(
      SportsEventRepository repository,
      OperationalIdentityLookup identityLookup,
      PlatformTransactionManager transactionManager) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.identityLookup = Objects.requireNonNull(identityLookup, "identityLookup must not be null");
    this.transactions =
        new TransactionTemplate(
            Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
  }

  public SportsEventDetail createEvent(
      String issuer, String subject, UUID editionId, SportsEventCreateRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    validateCreateRequest(request);

    EditionScope edition =
        repository.findEditionScope(editionId).orElseThrow(SportsEventService::notFound);
    OperationalIdentity actor = resolveIdentity(issuer, subject);
    requireWritableCompetition(actor, edition.competitionId());

    CatalogScope catalog =
        repository
            .findCatalogScope(
                editionId,
                request.sportId(),
                request.categoryId(),
                request.phaseId(),
                request.venueId(),
                request.venueSpaceId())
            .orElseThrow(SportsEventService::notFound);

    validateSlotTeams(request.slots());

    SportsEventDetail created =
        transactions.execute(
            status ->
                repository.createEvent(
                    edition.competitionId(), editionId, request, catalog, actor.identityId()));
    return Objects.requireNonNull(created, "created event must not be null");
  }

  public SportsEventDetail getEventDetail(String issuer, String subject, UUID eventId) {
    OperationalIdentity actor = resolveIdentity(issuer, subject);
    EventScope scope = repository.findEventScope(eventId).orElseThrow(SportsEventService::notFound);
    requireScopeAccess(actor, scope.competitionId());

    return repository.findEventDetail(eventId).orElseThrow(SportsEventService::notFound);
  }

  public List<SportsEventLifecycleEntry> getLifecycle(
      String issuer, String subject, UUID eventId, Integer requestedLimit) {
    int limit = requestedLimit == null ? 100 : requestedLimit;
    if (limit < 1 || limit > 500) {
      throw validation();
    }

    OperationalIdentity actor = resolveIdentity(issuer, subject);
    EventScope scope = repository.findEventScope(eventId).orElseThrow(SportsEventService::notFound);
    requireScopeAccess(actor, scope.competitionId());
    return repository.findLifecycle(eventId, limit);
  }

  public List<SportsEventDetail> listEventsByEdition(
      String issuer, String subject, UUID editionId) {
    OperationalIdentity actor = resolveIdentity(issuer, subject);
    EditionScope edition =
        repository.findEditionScope(editionId).orElseThrow(SportsEventService::notFound);
    requireScopeAccess(actor, edition.competitionId());

    return repository.listEventsByEdition(editionId);
  }

  public SportsEventDetail updateEventDraft(
      String issuer,
      String subject,
      UUID eventId,
      Long expectedLockVersion,
      SportsEventUpdateRequest request) {
    requireExpectedLockVersion(expectedLockVersion);
    Objects.requireNonNull(request, "request must not be null");
    validateUpdateRequest(request);

    EventScope event = repository.findEventScope(eventId).orElseThrow(SportsEventService::notFound);
    OperationalIdentity actor = resolveIdentity(issuer, subject);
    requireWritableCompetition(actor, event.competitionId());

    if (!"RASCUNHO".equals(event.status())) {
      throw validation();
    }

    CatalogScope catalog =
        repository
            .findCatalogScope(
                event.editionId(),
                request.sportId(),
                request.categoryId(),
                request.phaseId(),
                request.venueId(),
                request.venueSpaceId())
            .orElseThrow(SportsEventService::notFound);

    validateSlotTeams(request.slots());

    return transactions.execute(
        status -> {
          EventScope locked =
              repository.findEventScopeForUpdate(eventId).orElseThrow(SportsEventService::notFound);
          if (locked.lockVersion() != expectedLockVersion) {
            throw staleVersion();
          }
          if (!"RASCUNHO".equals(locked.status())) {
            throw validation();
          }

          int updated = repository.updateEventDraft(eventId, request, expectedLockVersion);
          if (updated == 0) {
            throw staleVersion();
          }
          return repository.findEventDetail(eventId).orElseThrow();
        });
  }

  public SportsEventDetail publishEvent(
      String issuer, String subject, UUID eventId, Long expectedLockVersion) {
    requireExpectedLockVersion(expectedLockVersion);
    EventScope event = repository.findEventScope(eventId).orElseThrow(SportsEventService::notFound);
    OperationalIdentity actor = resolveIdentity(issuer, subject);
    requireWritableCompetition(actor, event.competitionId());

    if (!"RASCUNHO".equals(event.status())
        && !"SUSPENSO".equals(event.status())
        && !"ADIADO".equals(event.status())) {
      throw validation();
    }

    return transactions.execute(
        status -> {
          EventScope locked =
              repository.findEventScopeForUpdate(eventId).orElseThrow(SportsEventService::notFound);
          if (locked.lockVersion() != expectedLockVersion) {
            throw staleVersion();
          }
          if (!"RASCUNHO".equals(locked.status())
              && !"SUSPENSO".equals(locked.status())
              && !"ADIADO".equals(locked.status())) {
            throw validation();
          }

          SportsEventDetail detail =
              repository.findEventDetail(eventId).orElseThrow(SportsEventService::notFound);
          List<SportsEventSlotItem> slots = repository.findEventSlots(eventId);
          if (slots.size() != 2) {
            throw validation();
          }

          long versionNo = repository.nextPublicationVersion(eventId);
          UUID supersedesId = repository.latestPublicationId(eventId).orElse(null);

          repository.insertEventPublication(
              detail, slots, actor.identityId(), versionNo, supersedesId, expectedLockVersion);

          int updated =
              repository.updateEventStatus(eventId, "PUBLICADO", null, expectedLockVersion);
          if (updated == 0) {
            throw staleVersion();
          }
          repository.insertLifecycle(
              eventId,
              locked.competitionId(),
              locked.editionId(),
              locked.status(),
              "PUBLICADO",
              actor.identityId(),
              locked.sourceReference(),
              null,
              expectedLockVersion,
              expectedLockVersion + 1);
          return repository.findEventDetail(eventId).orElseThrow();
        });
  }

  public SportsEventDetail suspendEvent(
      String issuer,
      String subject,
      UUID eventId,
      Long expectedLockVersion,
      SportsEventSuspendRequest request) {
    requireExpectedLockVersion(expectedLockVersion);
    EventScope event = repository.findEventScope(eventId).orElseThrow(SportsEventService::notFound);
    OperationalIdentity actor = resolveIdentity(issuer, subject);
    requireWritableCompetition(actor, event.competitionId());

    if (!"PUBLICADO".equals(event.status())) {
      throw validation();
    }

    String reason = request != null && request.reason() != null ? request.reason().trim() : null;
    if (reason != null && !reason.isBlank()) {
      requireUtf8Length(reason, 1024);
    }

    return transactions.execute(
        status -> {
          EventScope locked =
              repository.findEventScopeForUpdate(eventId).orElseThrow(SportsEventService::notFound);
          if (locked.lockVersion() != expectedLockVersion) {
            throw staleVersion();
          }
          if (!"PUBLICADO".equals(locked.status())) {
            throw validation();
          }

          int updated =
              repository.updateEventStatus(eventId, "SUSPENSO", reason, expectedLockVersion);
          if (updated == 0) {
            throw staleVersion();
          }
          repository.insertLifecycle(
              eventId,
              locked.competitionId(),
              locked.editionId(),
              locked.status(),
              "SUSPENSO",
              actor.identityId(),
              locked.sourceReference(),
              reason,
              expectedLockVersion,
              expectedLockVersion + 1);
          return repository.findEventDetail(eventId).orElseThrow();
        });
  }

  public SportsEventDetail postponeEvent(
      String issuer,
      String subject,
      UUID eventId,
      Long expectedLockVersion,
      SportsEventPostponeRequest request) {
    requireExpectedLockVersion(expectedLockVersion);
    if (request == null
        || request.scheduledAt() == null
        || request.zoneId() == null
        || request.zoneId().isBlank()) {
      throw validation();
    }
    validateZoneId(request.zoneId());

    EventScope event = repository.findEventScope(eventId).orElseThrow(SportsEventService::notFound);
    OperationalIdentity actor = resolveIdentity(issuer, subject);
    requireWritableCompetition(actor, event.competitionId());

    if (!"PUBLICADO".equals(event.status()) && !"SUSPENSO".equals(event.status())) {
      throw validation();
    }

    String reason = request.reason() != null ? request.reason().trim() : null;
    if (reason != null && !reason.isBlank()) {
      requireUtf8Length(reason, 1024);
    }

    return transactions.execute(
        status -> {
          EventScope locked =
              repository.findEventScopeForUpdate(eventId).orElseThrow(SportsEventService::notFound);
          if (locked.lockVersion() != expectedLockVersion) {
            throw staleVersion();
          }
          if (!"PUBLICADO".equals(locked.status()) && !"SUSPENSO".equals(locked.status())) {
            throw validation();
          }

          int updated =
              repository.postponeEvent(
                  eventId,
                  request.scheduledAt(),
                  request.zoneId().trim(),
                  reason,
                  expectedLockVersion);
          if (updated == 0) {
            throw staleVersion();
          }
          repository.insertLifecycle(
              eventId,
              locked.competitionId(),
              locked.editionId(),
              locked.status(),
              "ADIADO",
              actor.identityId(),
              locked.sourceReference(),
              reason,
              expectedLockVersion,
              expectedLockVersion + 1);
          return repository.findEventDetail(eventId).orElseThrow();
        });
  }

  public SportsEventDetail cancelEvent(
      String issuer,
      String subject,
      UUID eventId,
      Long expectedLockVersion,
      SportsEventCancelRequest request) {
    requireExpectedLockVersion(expectedLockVersion);
    if (request == null || request.reason() == null || request.reason().isBlank()) {
      throw validation();
    }
    requireUtf8Length(request.reason().trim(), 1024);

    EventScope event = repository.findEventScope(eventId).orElseThrow(SportsEventService::notFound);
    OperationalIdentity actor = resolveIdentity(issuer, subject);
    requireWritableCompetition(actor, event.competitionId());

    if ("CANCELADO".equals(event.status())) {
      throw validation();
    }

    return transactions.execute(
        status -> {
          EventScope locked =
              repository.findEventScopeForUpdate(eventId).orElseThrow(SportsEventService::notFound);
          if (locked.lockVersion() != expectedLockVersion) {
            throw staleVersion();
          }
          if ("CANCELADO".equals(locked.status())) {
            throw validation();
          }

          int updated =
              repository.updateEventStatus(
                  eventId, "CANCELADO", request.reason().trim(), expectedLockVersion);
          if (updated == 0) {
            throw staleVersion();
          }
          repository.insertLifecycle(
              eventId,
              locked.competitionId(),
              locked.editionId(),
              locked.status(),
              "CANCELADO",
              actor.identityId(),
              locked.sourceReference(),
              request.reason().trim(),
              expectedLockVersion,
              expectedLockVersion + 1);
          return repository.findEventDetail(eventId).orElseThrow();
        });
  }

  private void validateCreateRequest(SportsEventCreateRequest request) {
    if (request.title() == null || request.title().isBlank()) {
      throw validation();
    }
    requireUtf8Length(request.title().trim(), 200);
    if (request.format() != EventFormat.HEAD_TO_HEAD && request.format() != EventFormat.GENERIC) {
      throw validation();
    }
    if (request.scheduledAt() == null) {
      throw validation();
    }
    if (request.zoneId() == null || request.zoneId().isBlank()) {
      throw validation();
    }
    validateZoneId(request.zoneId());
    if (request.venueState() == null) {
      throw validation();
    }
    if (request.venueState() == VenueState.LOCAL_A_DEFINIR
        && (request.venueId() != null || request.venueSpaceId() != null)) {
      throw validation();
    }
    if (request.venueState() == VenueState.LOCAL_DEFINIDO && request.venueId() == null) {
      throw validation();
    }
    if (request.sourceReference() == null || request.sourceReference().isBlank()) {
      throw validation();
    }
    requireUtf8Length(request.sourceReference().trim(), 2048);
    validateSlots(request.slots());
  }

  private void validateUpdateRequest(SportsEventUpdateRequest request) {
    if (request.title() == null || request.title().isBlank()) {
      throw validation();
    }
    requireUtf8Length(request.title().trim(), 200);
    if (request.format() != EventFormat.HEAD_TO_HEAD && request.format() != EventFormat.GENERIC) {
      throw validation();
    }
    if (request.scheduledAt() == null) {
      throw validation();
    }
    if (request.zoneId() == null || request.zoneId().isBlank()) {
      throw validation();
    }
    validateZoneId(request.zoneId());
    if (request.venueState() == null) {
      throw validation();
    }
    if (request.venueState() == VenueState.LOCAL_A_DEFINIR
        && (request.venueId() != null || request.venueSpaceId() != null)) {
      throw validation();
    }
    if (request.venueState() == VenueState.LOCAL_DEFINIDO && request.venueId() == null) {
      throw validation();
    }
    if (request.sourceReference() == null || request.sourceReference().isBlank()) {
      throw validation();
    }
    requireUtf8Length(request.sourceReference().trim(), 2048);
    validateSlots(request.slots());
  }

  private void validateSlots(List<SportsEventSlotRequest> slots) {
    if (slots == null || slots.size() != 2) {
      throw validation();
    }
    Set<Integer> slotNos = new HashSet<>();
    for (SportsEventSlotRequest slot : slots) {
      if (slot == null || slot.displayLabel() == null || slot.displayLabel().isBlank()) {
        throw validation();
      }
      requireUtf8Length(slot.displayLabel().trim(), 160);
      if (!slotNos.add(slot.slotNo()) || slot.slotNo() < 1 || slot.slotNo() > 2) {
        throw validation();
      }
    }
  }

  private void validateSlotTeams(List<SportsEventSlotRequest> slots) {
    List<UUID> teamIds =
        slots.stream().map(SportsEventSlotRequest::teamId).filter(Objects::nonNull).toList();
    if (!repository.teamsExist(teamIds)) {
      throw notFound();
    }
  }

  private static void validateZoneId(String zoneId) {
    try {
      ZoneId.of(zoneId.trim());
    } catch (DateTimeException ex) {
      throw validation();
    }
  }

  private OperationalIdentity resolveIdentity(String issuer, String subject) {
    if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
      throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
    }
    return identityLookup
        .lookupIdentity(issuer, subject)
        .orElseThrow(() -> new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN));
  }

  private static void requireScopeAccess(OperationalIdentity actor, UUID competitionId) {
    boolean allowed =
        actor.roles().stream()
            .anyMatch(
                role ->
                    (GENERAL_ADMIN.equals(role.roleName()) && role.scopeRef() == null)
                        || (COMPETITION_ADMIN.equals(role.roleName())
                            && competitionId.equals(role.scopeRef())));
    if (!allowed) {
      throw new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
    }
  }

  private static void requireWritableCompetition(OperationalIdentity actor, UUID competitionId) {
    if (competitionId == null) {
      throw notFound();
    }
    boolean permitted =
        isGlobal(actor)
            || actor.roles().stream()
                .anyMatch(
                    role ->
                        COMPETITION_ADMIN.equals(role.roleName())
                            && competitionId.equals(role.scopeRef()));
    if (!permitted) {
      throw new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
    }
  }

  private static boolean isGlobal(OperationalIdentity actor) {
    return actor.roles().stream()
        .anyMatch(role -> GENERAL_ADMIN.equals(role.roleName()) && role.scopeRef() == null);
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
