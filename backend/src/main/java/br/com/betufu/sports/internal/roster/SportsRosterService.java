package br.com.betufu.sports.internal.roster;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.identity.OperationalIdentityLookup.OperationalIdentity;
import br.com.betufu.sports.internal.roster.SportsRosterModels.SportsRosterCorrectRequest;
import br.com.betufu.sports.internal.roster.SportsRosterModels.SportsRosterDraft;
import br.com.betufu.sports.internal.roster.SportsRosterModels.SportsRosterMemberItem;
import br.com.betufu.sports.internal.roster.SportsRosterModels.SportsRosterMemberRequest;
import br.com.betufu.sports.internal.roster.SportsRosterModels.SportsRosterPublication;
import br.com.betufu.sports.internal.roster.SportsRosterModels.SportsRosterUpdateRequest;
import br.com.betufu.sports.internal.roster.SportsRosterRepository.AthleteSnapshotInfo;
import br.com.betufu.sports.internal.roster.SportsRosterRepository.PublicationHeader;
import br.com.betufu.sports.internal.roster.SportsRosterRepository.RosterRecord;
import br.com.betufu.sports.internal.roster.SportsRosterRepository.TeamContext;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("runtime | integrated")
class SportsRosterService {

  private static final String GENERAL_ADMIN = "ADMINISTRADOR_GERAL";
  private static final String COMPETITION_ADMIN = "ADMINISTRADOR_COMPETICAO";

  private final SportsRosterRepository repository;
  private final OperationalIdentityLookup identityLookup;
  private final TransactionTemplate transactions;

  @Autowired
  SportsRosterService(
      SportsRosterRepository repository,
      OperationalIdentityLookup identityLookup,
      PlatformTransactionManager transactionManager) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.identityLookup = Objects.requireNonNull(identityLookup, "identityLookup must not be null");
    this.transactions =
        new TransactionTemplate(
            Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
  }

  SportsRosterDraft getRosterDraft(String issuer, String subject, UUID teamId) {
    OperationalIdentity actor = resolve(issuer, subject);
    TeamContext context =
        repository.findTeamContext(teamId).orElseThrow(SportsRosterService::notFound);
    requireWritableCompetition(actor, context.competitionId());

    RosterRecord roster = repository.findOrCreateRoster(teamId);
    List<SportsRosterMemberItem> members = repository.findDraftMembers(roster.rosterId());
    return mapToDraft(roster, context, members);
  }

  SportsRosterDraft updateRosterDraft(
      String issuer,
      String subject,
      UUID teamId,
      Long expectedLockVersion,
      SportsRosterUpdateRequest request) {
    requireExpected(expectedLockVersion);
    validateUpdateRequest(request);
    OperationalIdentity actor = resolve(issuer, subject);
    TeamContext context =
        repository.findTeamContext(teamId).orElseThrow(SportsRosterService::notFound);
    requireWritableCompetition(actor, context.competitionId());

    List<UUID> athleteIds =
        request.members().stream().map(SportsRosterMemberRequest::athleteId).toList();
    if (!repository.athletesExist(athleteIds)) {
      throw notFound();
    }

    return transactions.execute(
        status -> {
          RosterRecord roster = repository.findOrCreateRoster(teamId);
          if (roster.lockVersion() != expectedLockVersion) {
            throw staleVersion();
          }
          if ("PUBLICADO".equals(roster.status())) {
            throw validation();
          }
          int updated = repository.updateRosterLockVersion(roster.rosterId(), expectedLockVersion);
          if (updated == 0) {
            throw staleVersion();
          }
          repository.replaceDraftMembers(roster.rosterId(), request.members());
          List<SportsRosterMemberItem> members = repository.findDraftMembers(roster.rosterId());
          RosterRecord updatedRoster = repository.findRosterByTeamId(teamId).orElseThrow();
          return mapToDraft(updatedRoster, context, members);
        });
  }

  SportsRosterPublication publishRoster(
      String issuer, String subject, UUID teamId, Long expectedLockVersion) {
    requireExpected(expectedLockVersion);
    OperationalIdentity actor = resolve(issuer, subject);
    TeamContext context =
        repository.findTeamContext(teamId).orElseThrow(SportsRosterService::notFound);
    requireWritableCompetition(actor, context.competitionId());

    return transactions.execute(
        status -> {
          RosterRecord roster =
              repository.findRosterByTeamId(teamId).orElseThrow(SportsRosterService::notFound);
          if (roster.lockVersion() != expectedLockVersion) {
            throw staleVersion();
          }
          int updated = repository.markRosterPublished(roster.rosterId(), expectedLockVersion);
          if (updated == 0) {
            throw staleVersion();
          }

          List<SportsRosterMemberItem> draftMembers =
              repository.findDraftMembers(roster.rosterId());
          Optional<PublicationHeader> latestOpt = repository.findLatestPublicationHeader(teamId);
          long versionNo = latestOpt.map(p -> p.versionNo() + 1).orElse(1L);
          UUID supersedesId = latestOpt.map(PublicationHeader::publicationId).orElse(null);

          UUID publicationId = UUID.randomUUID();
          PublicationHeader header =
              new PublicationHeader(
                  publicationId,
                  roster.rosterId(),
                  teamId,
                  context.competitionId(),
                  context.editionId(),
                  context.organizationId(),
                  context.sportId(),
                  context.categoryId(),
                  versionNo,
                  context.teamName(),
                  context.organizationName(),
                  context.editionName(),
                  context.competitionName(),
                  context.sportName(),
                  context.categoryName(),
                  null,
                  null,
                  expectedLockVersion);

          return repository.insertPublication(
              header, draftMembers, actor.identityId(), supersedesId);
        });
  }

  SportsRosterPublication correctRoster(
      String issuer,
      String subject,
      UUID teamId,
      Long expectedLockVersion,
      SportsRosterCorrectRequest request) {
    requireExpected(expectedLockVersion);
    validateCorrectRequest(request);
    OperationalIdentity actor = resolve(issuer, subject);
    TeamContext context =
        repository.findTeamContext(teamId).orElseThrow(SportsRosterService::notFound);
    requireWritableCompetition(actor, context.competitionId());

    List<UUID> athleteIds =
        request.members().stream().map(SportsRosterMemberRequest::athleteId).toList();
    if (!repository.athletesExist(athleteIds)) {
      throw notFound();
    }

    return transactions.execute(
        status -> {
          RosterRecord roster =
              repository.findRosterByTeamId(teamId).orElseThrow(SportsRosterService::notFound);
          if (roster.lockVersion() != expectedLockVersion) {
            throw staleVersion();
          }
          int updated = repository.markRosterPublished(roster.rosterId(), expectedLockVersion);
          if (updated == 0) {
            throw staleVersion();
          }

          PublicationHeader latest =
              repository
                  .findLatestPublicationHeader(teamId)
                  .orElseThrow(SportsRosterService::validation);

          List<SportsRosterMemberItem> snapshotMembers = new ArrayList<>();
          for (SportsRosterMemberRequest memberReq : request.members()) {
            AthleteSnapshotInfo athleteInfo =
                repository
                    .findAthleteSnapshotInfo(memberReq.athleteId())
                    .orElseThrow(SportsRosterService::notFound);
            snapshotMembers.add(
                new SportsRosterMemberItem(
                    athleteInfo.athleteId(),
                    athleteInfo.name(),
                    athleteInfo.slug(),
                    memberReq.jerseyNumber(),
                    memberReq.positionName()));
          }

          long versionNo = latest.versionNo() + 1;
          UUID supersedesId = latest.publicationId();
          UUID publicationId = UUID.randomUUID();

          PublicationHeader header =
              new PublicationHeader(
                  publicationId,
                  roster.rosterId(),
                  teamId,
                  context.competitionId(),
                  context.editionId(),
                  context.organizationId(),
                  context.sportId(),
                  context.categoryId(),
                  versionNo,
                  context.teamName(),
                  context.organizationName(),
                  context.editionName(),
                  context.competitionName(),
                  context.sportName(),
                  context.categoryName(),
                  request.reason().trim(),
                  null,
                  expectedLockVersion);

          SportsRosterPublication publication =
              repository.insertPublication(
                  header, snapshotMembers, actor.identityId(), supersedesId);

          repository.replaceDraftMembers(roster.rosterId(), request.members());

          return publication;
        });
  }

  SportsRosterPublication getLatestPublication(String issuer, String subject, UUID teamId) {
    OperationalIdentity actor = resolve(issuer, subject);
    TeamContext context =
        repository.findTeamContext(teamId).orElseThrow(SportsRosterService::notFound);
    requireCatalogAccess(actor);

    PublicationHeader header =
        repository.findLatestPublicationHeader(teamId).orElseThrow(SportsRosterService::notFound);
    List<SportsRosterMemberItem> members =
        repository.findPublicationMembers(header.publicationId());

    return new SportsRosterPublication(
        header.publicationId(),
        header.rosterId(),
        header.teamId(),
        header.versionNo(),
        header.teamName(),
        header.organizationId(),
        header.organizationName(),
        header.competitionId(),
        header.competitionName(),
        header.editionId(),
        header.editionName(),
        header.sportId(),
        header.sportName(),
        header.categoryId(),
        header.categoryName(),
        header.reason(),
        header.publishedAt(),
        header.baseLockVersion(),
        members);
  }

  private OperationalIdentity resolve(String issuer, String subject) {
    if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
      throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
    }
    return identityLookup
        .lookupIdentity(issuer, subject)
        .orElseThrow(() -> new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN));
  }

  private static void requireCatalogAccess(OperationalIdentity actor) {
    boolean allowed =
        actor.roles().stream()
            .anyMatch(
                role ->
                    (GENERAL_ADMIN.equals(role.roleName()) && role.scopeRef() == null)
                        || (COMPETITION_ADMIN.equals(role.roleName()) && role.scopeRef() != null));
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

  private static void validateUpdateRequest(SportsRosterUpdateRequest request) {
    if (request == null || request.members() == null) {
      throw validation();
    }
    validateMembers(request.members());
  }

  private static void validateCorrectRequest(SportsRosterCorrectRequest request) {
    if (request == null || request.reason() == null || request.reason().isBlank()) {
      throw validation();
    }
    requireLength(request.reason().trim(), 1024);
    if (request.members() == null) {
      throw validation();
    }
    validateMembers(request.members());
  }

  private static void validateMembers(List<SportsRosterMemberRequest> members) {
    Set<UUID> athleteIds = new HashSet<>();
    for (SportsRosterMemberRequest member : members) {
      if (member == null || member.athleteId() == null) {
        throw validation();
      }
      if (!athleteIds.add(member.athleteId())) {
        throw validation();
      }
      if (member.jerseyNumber() != null
          && (member.jerseyNumber() < 1 || member.jerseyNumber() > 99)) {
        throw validation();
      }
      if (member.positionName() != null) {
        requireLength(member.positionName().trim(), 64);
      }
    }
  }

  private static void requireExpected(Long expected) {
    if (expected == null || expected < 0) {
      throw validation();
    }
  }

  private static void requireLength(String value, int maxOctets) {
    if (value == null
        || value.isBlank()
        || value.getBytes(StandardCharsets.UTF_8).length > maxOctets) {
      throw validation();
    }
  }

  private static SportsRosterDraft mapToDraft(
      RosterRecord roster, TeamContext context, List<SportsRosterMemberItem> members) {
    return new SportsRosterDraft(
        roster.rosterId(),
        context.teamId(),
        context.teamName(),
        context.organizationId(),
        context.organizationName(),
        context.competitionId(),
        context.competitionName(),
        context.editionId(),
        context.editionName(),
        context.sportId(),
        context.sportName(),
        context.categoryId(),
        context.categoryName(),
        roster.status(),
        roster.lockVersion(),
        members);
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
