package br.com.betufu.sports.internal.bracket;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.identity.OperationalIdentityLookup.OperationalIdentity;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketCorrectRequest;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketCreateRequest;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketMatchConfigureRequest;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketMatchView;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketPublishRequest;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketRound;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketSlotConfigureRequest;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketSlotView;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketSourceType;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketSummary;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketUpdateRequest;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketView;
import br.com.betufu.sports.internal.bracket.SportsBracketRepository.BracketRecord;
import br.com.betufu.sports.internal.bracket.SportsBracketRepository.EditionContext;
import br.com.betufu.sports.internal.bracket.SportsBracketRepository.EventProjectionInfo;
import br.com.betufu.sports.internal.bracket.SportsBracketRepository.EventSlotProjectionInfo;
import br.com.betufu.sports.internal.bracket.SportsBracketRepository.MatchEntity;
import br.com.betufu.sports.internal.bracket.SportsBracketRepository.PublicationRecord;
import br.com.betufu.sports.internal.bracket.SportsBracketRepository.SlotEntity;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("runtime | integrated")
public class SportsBracketService {

  private static final String GENERAL_ADMIN = "ADMINISTRADOR_GERAL";
  private static final String COMPETITION_ADMIN = "ADMINISTRADOR_COMPETICAO";

  private final SportsBracketRepository repository;
  private final OperationalIdentityLookup identityLookup;
  private final TransactionTemplate transactions;

  SportsBracketService(
      SportsBracketRepository repository,
      OperationalIdentityLookup identityLookup,
      PlatformTransactionManager transactionManager) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.identityLookup = Objects.requireNonNull(identityLookup, "identityLookup must not be null");
    this.transactions =
        new TransactionTemplate(
            Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
  }

  public SportsBracketView getPublicBracket(UUID bracketId) {
    BracketRecord bracket =
        repository.findBracketRecord(bracketId).orElseThrow(SportsBracketService::notFound);

    if (!"PUBLICADO".equals(bracket.status())) {
      throw notFound();
    }

    Optional<PublicationRecord> publication = repository.findLatestPublication(bracketId);
    List<MatchEntity> matches = repository.findMatchesWithSlots(bracketId);
    return buildBracketView(bracket, matches, publication, true);
  }

  public SportsBracketView getBracketPreview(String issuer, String subject, UUID bracketId) {
    OperationalIdentity actor = resolveIdentity(issuer, subject);
    BracketRecord bracket =
        repository.findBracketRecord(bracketId).orElseThrow(SportsBracketService::notFound);
    requireScopeAccess(actor, bracket.competitionId());

    Optional<PublicationRecord> publication = repository.findLatestPublication(bracketId);
    List<MatchEntity> matches = repository.findMatchesWithSlots(bracketId);
    return buildBracketView(bracket, matches, publication, false);
  }

  public List<SportsBracketSummary> listBracketsByEdition(
      String issuer, String subject, UUID editionId) {
    OperationalIdentity actor = resolveIdentity(issuer, subject);
    EditionContext edition =
        repository.findEditionContext(editionId).orElseThrow(SportsBracketService::notFound);
    requireScopeAccess(actor, edition.competitionId());

    return repository.listBracketsByEdition(editionId);
  }

  public SportsBracketSummary createBracket(
      String issuer, String subject, UUID editionId, SportsBracketCreateRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    validateCreateRequest(request);

    OperationalIdentity actor = resolveIdentity(issuer, subject);
    EditionContext edition =
        repository.findEditionContext(editionId).orElseThrow(SportsBracketService::notFound);
    requireWritableCompetition(actor, edition.competitionId());

    if (!repository.catalogEntitiesExist(
        editionId, request.sportId(), request.categoryId(), request.phaseId())) {
      throw notFound();
    }

    return repository.createBracket(edition.competitionId(), editionId, request);
  }

  public SportsBracketView updateBracket(
      String issuer,
      String subject,
      UUID bracketId,
      Long expectedLockVersion,
      SportsBracketUpdateRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    requireExpected(expectedLockVersion);
    validateUpdateRequest(request);

    OperationalIdentity actor = resolveIdentity(issuer, subject);
    BracketRecord bracket =
        repository.findBracketRecord(bracketId).orElseThrow(SportsBracketService::notFound);
    requireWritableCompetition(actor, bracket.competitionId());

    // Published snapshots are immutable; corrections create the next version.
    if (!"RASCUNHO".equals(bracket.status())) {
      throw validation();
    }

    if (!repository.catalogEntitiesExist(
        bracket.editionId(), request.sportId(), request.categoryId(), request.phaseId())) {
      throw notFound();
    }

    validateMatchesGraph(bracket.competitionId(), bracket.editionId(), request.matches());

    return transactions.execute(
        status -> {
          int updated = repository.updateBracketMetadata(bracketId, request, expectedLockVersion);
          if (updated == 0) {
            throw staleVersion();
          }
          repository.replaceMatchesAndSlots(bracketId, request.matches());
          BracketRecord reloaded = repository.findBracketRecord(bracketId).orElseThrow();
          List<MatchEntity> matches = repository.findMatchesWithSlots(bracketId);
          Optional<PublicationRecord> publication = repository.findLatestPublication(bracketId);
          return buildBracketView(reloaded, matches, publication, false);
        });
  }

  public SportsBracketView publishBracket(
      String issuer,
      String subject,
      UUID bracketId,
      Long expectedLockVersion,
      SportsBracketPublishRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    requireExpected(expectedLockVersion);
    if (request.sourceReference() == null || request.sourceReference().isBlank()) {
      throw validation();
    }

    OperationalIdentity actor = resolveIdentity(issuer, subject);
    BracketRecord bracket =
        repository.findBracketRecord(bracketId).orElseThrow(SportsBracketService::notFound);
    requireWritableCompetition(actor, bracket.competitionId());

    if (!"RASCUNHO".equals(bracket.status())) {
      throw validation();
    }

    List<MatchEntity> matches = repository.findMatchesWithSlots(bracketId);
    if (matches.isEmpty()) {
      throw validation();
    }

    return transactions.execute(
        status -> {
          int updated = repository.setBracketPublished(bracketId, expectedLockVersion);
          if (updated == 0) {
            throw staleVersion();
          }
          repository.createPublication(
              bracket,
              actor.identityId(),
              request.sourceReference(),
              null,
              expectedLockVersion,
              matches);

          BracketRecord reloaded = repository.findBracketRecord(bracketId).orElseThrow();
          Optional<PublicationRecord> publication = repository.findLatestPublication(bracketId);
          return buildBracketView(reloaded, matches, publication, false);
        });
  }

  public SportsBracketView correctBracket(
      String issuer,
      String subject,
      UUID bracketId,
      Long expectedLockVersion,
      SportsBracketCorrectRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    requireExpected(expectedLockVersion);
    if (request.reason() == null
        || request.reason().isBlank()
        || request.sourceReference() == null
        || request.sourceReference().isBlank()) {
      throw validation();
    }

    OperationalIdentity actor = resolveIdentity(issuer, subject);
    BracketRecord bracket =
        repository.findBracketRecord(bracketId).orElseThrow(SportsBracketService::notFound);
    requireWritableCompetition(actor, bracket.competitionId());

    if (!"PUBLICADO".equals(bracket.status())) {
      throw validation();
    }

    if (request.matches() != null && !request.matches().isEmpty()) {
      validateMatchesGraph(bracket.competitionId(), bracket.editionId(), request.matches());
    }

    return transactions.execute(
        status -> {
          if (request.matches() != null && !request.matches().isEmpty()) {
            repository.replaceMatchesAndSlots(bracketId, request.matches());
          }
          int updated = repository.updateBracketLockVersion(bracketId, expectedLockVersion);
          if (updated == 0) {
            throw staleVersion();
          }

          List<MatchEntity> matches = repository.findMatchesWithSlots(bracketId);
          repository.createPublication(
              bracket,
              actor.identityId(),
              request.sourceReference(),
              request.reason(),
              expectedLockVersion,
              matches);

          BracketRecord reloaded = repository.findBracketRecord(bracketId).orElseThrow();
          Optional<PublicationRecord> publication = repository.findLatestPublication(bracketId);
          return buildBracketView(reloaded, matches, publication, false);
        });
  }

  void validateMatchesGraph(
      UUID competitionId, UUID editionId, List<SportsBracketMatchConfigureRequest> matches) {
    if (matches == null || matches.isEmpty()) {
      throw validation();
    }

    Set<UUID> matchIds = new HashSet<>();
    Set<String> positions = new HashSet<>();
    List<UUID> eventIds = new ArrayList<>();
    Set<UUID> uniqueEventIds = new HashSet<>();

    for (SportsBracketMatchConfigureRequest match : matches) {
      if (match.matchId() == null) {
        throw validation();
      }
      if (!matchIds.add(match.matchId())) {
        throw validation();
      }
      String pos = match.roundNo() + ":" + match.matchNo();
      if (!positions.add(pos)) {
        throw validation();
      }
      if (match.slots() == null || match.slots().size() != 2) {
        throw validation();
      }

      Set<Integer> slotNumbers = new HashSet<>();
      int byeCount = 0;
      for (SportsBracketSlotConfigureRequest slot : match.slots()) {
        if (slot.slotNo() < 1 || slot.slotNo() > 2) {
          throw validation();
        }
        if (!slotNumbers.add(slot.slotNo())) {
          throw validation();
        }
        if (slot.sourceType() == SportsBracketSourceType.PREDECESSOR_WINNER
            || slot.sourceType() == SportsBracketSourceType.PREDECESSOR_LOSER) {
          if (slot.sourceMatchId() == null) {
            throw validation();
          }
          if (slot.sourceMatchId().equals(match.matchId())) {
            throw validation();
          }
        } else {
          if (slot.sourceMatchId() != null) {
            throw validation();
          }
        }

        if (slot.sourceType() == SportsBracketSourceType.BYE) {
          byeCount++;
        }
        if (match.eventId() != null
            && slot.sourceType() == SportsBracketSourceType.DIRECT
            && slot.seedLabel() != null
            && !slot.seedLabel().isBlank()) {
          throw validation();
        }
      }

      if (byeCount > 1) {
        throw validation();
      }

      if (match.eventId() != null) {
        if (!uniqueEventIds.add(match.eventId())) {
          throw validation();
        }
        eventIds.add(match.eventId());
      }
    }

    Map<UUID, SportsBracketMatchConfigureRequest> matchById = new HashMap<>();
    for (SportsBracketMatchConfigureRequest match : matches) {
      matchById.put(match.matchId(), match);
    }

    // Check predecessor existence and round ordering
    for (SportsBracketMatchConfigureRequest match : matches) {
      for (SportsBracketSlotConfigureRequest slot : match.slots()) {
        if (slot.sourceMatchId() != null) {
          SportsBracketMatchConfigureRequest pred = matchById.get(slot.sourceMatchId());
          if (pred == null) {
            throw validation();
          }
          if (pred.roundNo() >= match.roundNo()) {
            throw validation();
          }
        }
      }
    }

    // Cycle detection using Kahn's algorithm
    Map<UUID, Set<UUID>> adj = new HashMap<>();
    Map<UUID, Integer> inDegree = new HashMap<>();
    for (UUID id : matchIds) {
      adj.put(id, new HashSet<>());
      inDegree.put(id, 0);
    }

    for (SportsBracketMatchConfigureRequest match : matches) {
      for (SportsBracketSlotConfigureRequest slot : match.slots()) {
        if (slot.sourceMatchId() != null) {
          adj.get(slot.sourceMatchId()).add(match.matchId());
        }
      }
    }

    for (Set<UUID> targets : adj.values()) {
      for (UUID target : targets) {
        inDegree.put(target, inDegree.get(target) + 1);
      }
    }

    Queue<UUID> queue = new ArrayDeque<>();
    for (Map.Entry<UUID, Integer> entry : inDegree.entrySet()) {
      if (entry.getValue() == 0) {
        queue.add(entry.getKey());
      }
    }

    int visitedCount = 0;
    while (!queue.isEmpty()) {
      UUID current = queue.poll();
      visitedCount++;
      for (UUID neighbor : adj.get(current)) {
        int newDegree = inDegree.get(neighbor) - 1;
        inDegree.put(neighbor, newDegree);
        if (newDegree == 0) {
          queue.add(neighbor);
        }
      }
    }

    if (visitedCount != matchIds.size()) {
      throw validation();
    }

    // Scope validation for linked events
    if (!eventIds.isEmpty()) {
      if (!repository.eventsBelongToEdition(competitionId, editionId, eventIds)) {
        throw validation();
      }
    }
  }

  SportsBracketView buildBracketView(
      BracketRecord bracket,
      List<MatchEntity> matches,
      Optional<PublicationRecord> publication,
      boolean publicOnly) {

    List<UUID> eventIds =
        matches.stream().map(MatchEntity::eventId).filter(Objects::nonNull).toList();
    Map<UUID, EventProjectionInfo> linkedEvents = repository.findLinkedEvents(eventIds, publicOnly);

    // Sort matches by round_no ascending, match_no ascending
    List<MatchEntity> sortedMatches = new ArrayList<>(matches);
    sortedMatches.sort(
        (a, b) -> {
          int r = Integer.compare(a.roundNo(), b.roundNo());
          return r != 0 ? r : Integer.compare(a.matchNo(), b.matchNo());
        });

    Map<UUID, MatchEvaluation> evaluatedMatches = new HashMap<>();

    for (MatchEntity match : sortedMatches) {
      EventProjectionInfo eventInfo =
          match.eventId() != null ? linkedEvents.get(match.eventId()) : null;

      Integer winnerSlotNo = null;
      Integer loserSlotNo = null;
      if (eventInfo != null && eventInfo.scores().size() == 2) {
        int s1 = eventInfo.scores().getOrDefault(1, 0);
        int s2 = eventInfo.scores().getOrDefault(2, 0);
        if (s1 > s2) {
          winnerSlotNo = 1;
          loserSlotNo = 2;
        } else if (s2 > s1) {
          winnerSlotNo = 2;
          loserSlotNo = 1;
        }
      }

      List<SportsBracketSlotView> slotViews = new ArrayList<>();
      for (int slotIndex = 1; slotIndex <= 2; slotIndex++) {
        final int slotNo = slotIndex;
        SlotEntity slotEntity =
            match.slots().stream().filter(s -> s.slotNo() == slotNo).findFirst().orElse(null);

        SportsBracketSourceType sourceType =
            slotEntity != null ? slotEntity.sourceType() : SportsBracketSourceType.DIRECT;
        UUID sourceMatchId = slotEntity != null ? slotEntity.sourceMatchId() : null;

        String displayLabel = "A definir";
        UUID teamId = null;
        String teamName = null;
        Integer score = eventInfo != null ? eventInfo.scores().get(slotNo) : null;
        Boolean isWinner = winnerSlotNo != null ? (winnerSlotNo == slotNo) : null;

        if (sourceType == SportsBracketSourceType.BYE) {
          displayLabel = "Folga na rodada";
          score = null;
          isWinner = null;
        } else if (sourceType == SportsBracketSourceType.DIRECT) {
          if (eventInfo != null && eventInfo.slots().size() >= slotNo) {
            EventSlotProjectionInfo es = eventInfo.slots().get(slotNo - 1);
            displayLabel = es.displayLabel();
            teamId = es.teamId();
            teamName = es.teamName();
          } else if (slotEntity != null
              && slotEntity.seedLabel() != null
              && !slotEntity.seedLabel().isBlank()) {
            displayLabel = slotEntity.seedLabel();
          }
        } else if (sourceType == SportsBracketSourceType.PREDECESSOR_WINNER) {
          MatchEvaluation predEval =
              sourceMatchId != null ? evaluatedMatches.get(sourceMatchId) : null;
          if (predEval != null && predEval.winnerSlot() != null) {
            displayLabel = predEval.winnerSlot().displayLabel();
            teamId = predEval.winnerSlot().teamId();
            teamName = predEval.winnerSlot().teamName();
          } else {
            String predName =
                predEval != null && predEval.title() != null
                    ? predEval.title()
                    : (predEval != null
                        ? predEval.roundName() + " (Jogo " + predEval.matchNo() + ")"
                        : "confronto anterior");
            displayLabel = "Vencedor de " + predName;
          }
          if (eventInfo != null && eventInfo.slots().size() >= slotNo) {
            EventSlotProjectionInfo es = eventInfo.slots().get(slotNo - 1);
            if (es.teamId() != null) {
              teamId = es.teamId();
              teamName = es.teamName();
              displayLabel = es.displayLabel();
            }
          }
        } else if (sourceType == SportsBracketSourceType.PREDECESSOR_LOSER) {
          MatchEvaluation predEval =
              sourceMatchId != null ? evaluatedMatches.get(sourceMatchId) : null;
          if (predEval != null && predEval.loserSlot() != null) {
            displayLabel = predEval.loserSlot().displayLabel();
            teamId = predEval.loserSlot().teamId();
            teamName = predEval.loserSlot().teamName();
          } else {
            String predName =
                predEval != null && predEval.title() != null
                    ? predEval.title()
                    : (predEval != null
                        ? predEval.roundName() + " (Jogo " + predEval.matchNo() + ")"
                        : "confronto anterior");
            displayLabel = "Perdedor de " + predName;
          }
          if (eventInfo != null && eventInfo.slots().size() >= slotNo) {
            EventSlotProjectionInfo es = eventInfo.slots().get(slotNo - 1);
            if (es.teamId() != null) {
              teamId = es.teamId();
              teamName = es.teamName();
              displayLabel = es.displayLabel();
            }
          }
        }

        slotViews.add(
            new SportsBracketSlotView(
                slotNo,
                sourceType,
                sourceMatchId,
                displayLabel,
                teamId,
                teamName,
                score,
                isWinner));
      }

      SportsBracketSlotView evaluatedWinnerSlot = null;
      SportsBracketSlotView evaluatedLoserSlot = null;

      // If one slot is a bye, other slot is the winner
      if (slotViews.size() == 2) {
        if (slotViews.get(0).sourceType() == SportsBracketSourceType.BYE) {
          evaluatedWinnerSlot = slotViews.get(1);
          evaluatedLoserSlot = slotViews.get(0);
        } else if (slotViews.get(1).sourceType() == SportsBracketSourceType.BYE) {
          evaluatedWinnerSlot = slotViews.get(0);
          evaluatedLoserSlot = slotViews.get(1);
        } else if (winnerSlotNo != null) {
          evaluatedWinnerSlot = slotViews.get(winnerSlotNo - 1);
          evaluatedLoserSlot = slotViews.get(loserSlotNo - 1);
        }
      }

      String eventTitle = eventInfo != null ? eventInfo.title() : null;
      Instant scheduledAt = eventInfo != null ? eventInfo.scheduledAt() : null;
      String zoneId = eventInfo != null ? eventInfo.zoneId() : null;
      String venueName = eventInfo != null ? eventInfo.venueName() : null;
      String venueSpaceName = eventInfo != null ? eventInfo.venueSpaceName() : null;
      String status =
          eventInfo == null
              ? "AGENDADO"
              : (eventInfo.confirmedVersionId() != null && "PUBLICADO".equals(eventInfo.status())
                  ? "CONFIRMADO"
                  : eventInfo.status());

      SportsBracketMatchView matchView =
          new SportsBracketMatchView(
              match.matchId(),
              match.roundNo(),
              match.matchNo(),
              match.roundName(),
              match.eventId(),
              eventTitle,
              scheduledAt,
              zoneId,
              venueName,
              venueSpaceName,
              match.isThirdPlace(),
              status,
              slotViews,
              winnerSlotNo);

      evaluatedMatches.put(
          match.matchId(),
          new MatchEvaluation(
              match.matchId(),
              match.roundNo(),
              match.matchNo(),
              match.roundName(),
              eventTitle,
              evaluatedWinnerSlot,
              evaluatedLoserSlot,
              matchView));
    }

    Map<Integer, List<SportsBracketMatchView>> matchesByRound = new LinkedHashMap<>();
    Map<Integer, String> roundNames = new HashMap<>();

    for (MatchEntity match : sortedMatches) {
      MatchEvaluation eval = evaluatedMatches.get(match.matchId());
      if (eval != null) {
        matchesByRound.computeIfAbsent(match.roundNo(), k -> new ArrayList<>()).add(eval.view());
        roundNames.putIfAbsent(match.roundNo(), match.roundName());
      }
    }

    List<SportsBracketRound> rounds = new ArrayList<>();
    for (Map.Entry<Integer, List<SportsBracketMatchView>> entry : matchesByRound.entrySet()) {
      rounds.add(
          new SportsBracketRound(entry.getKey(), roundNames.get(entry.getKey()), entry.getValue()));
    }

    Long versionNo = publication.map(PublicationRecord::versionNo).orElse(null);
    String sourceReference = publication.map(PublicationRecord::sourceReference).orElse(null);
    Instant publishedAt = publication.map(PublicationRecord::publishedAt).orElse(null);

    return new SportsBracketView(
        bracket.bracketId(),
        bracket.competitionId(),
        bracket.editionId(),
        bracket.sportId(),
        bracket.categoryId(),
        bracket.phaseId(),
        bracket.name(),
        bracket.slug(),
        bracket.status(),
        versionNo,
        sourceReference,
        bracket.competitionName(),
        bracket.editionName(),
        bracket.sportName(),
        bracket.categoryName(),
        bracket.phaseName(),
        publishedAt,
        bracket.lockVersion(),
        rounds);
  }

  private void validateCreateRequest(SportsBracketCreateRequest request) {
    if (request.name() == null
        || request.name().isBlank()
        || request.slug() == null
        || request.slug().isBlank()) {
      throw validation();
    }
  }

  private void validateUpdateRequest(SportsBracketUpdateRequest request) {
    if (request.name() == null
        || request.name().isBlank()
        || request.slug() == null
        || request.slug().isBlank()
        || request.matches() == null) {
      throw validation();
    }
  }

  private OperationalIdentity resolveIdentity(String issuer, String subject) {
    if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
      throw unauthorized();
    }
    return identityLookup
        .lookupIdentity(issuer, subject)
        .orElseThrow(SportsBracketService::unauthorized);
  }

  private void requireScopeAccess(OperationalIdentity actor, UUID competitionId) {
    if (hasGeneralAdmin(actor) || hasCompetitionAdmin(actor, competitionId)) {
      return;
    }
    throw forbidden();
  }

  private void requireWritableCompetition(OperationalIdentity actor, UUID competitionId) {
    if (hasGeneralAdmin(actor) || hasCompetitionAdmin(actor, competitionId)) {
      return;
    }
    throw forbidden();
  }

  private boolean hasGeneralAdmin(OperationalIdentity actor) {
    return actor.roles().stream()
        .anyMatch(r -> GENERAL_ADMIN.equals(r.roleName()) && r.scopeRef() == null);
  }

  private boolean hasCompetitionAdmin(OperationalIdentity actor, UUID competitionId) {
    return actor.roles().stream()
        .anyMatch(
            r -> COMPETITION_ADMIN.equals(r.roleName()) && competitionId.equals(r.scopeRef()));
  }

  private static void requireExpected(Long expectedLockVersion) {
    if (expectedLockVersion == null || expectedLockVersion < 0) {
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

  private static ApiProblemException forbidden() {
    return new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
  }

  private static ApiProblemException unauthorized() {
    return new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
  }

  private record MatchEvaluation(
      UUID matchId,
      int roundNo,
      int matchNo,
      String roundName,
      String title,
      SportsBracketSlotView winnerSlot,
      SportsBracketSlotView loserSlot,
      SportsBracketMatchView view) {}
}
