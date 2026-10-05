package br.com.betufu.chat.internal;

import br.com.betufu.chat.ChatModerationAuthorityPort;
import br.com.betufu.chat.ChatWriteGuard;
import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("runtime | integrated")
public class ChatSanctionService implements ChatWriteGuard {
  private final ChatSanctionRepository repository;
  private final ChatModerationAuthorityPort authorities;
  private final ChatCommunityModels.Access access;
  private final Clock clock;

  public ChatSanctionService(
      ChatSanctionRepository repository,
      ChatModerationAuthorityPort authorities,
      ChatCommunityModels.Access access,
      Clock clock) {
    this.repository = repository;
    this.authorities = authorities;
    this.access = access;
    this.clock = clock;
  }

  @Transactional
  public ChatModerationModels.SanctionSuggestion suggest(
      String issuer, String subject, String sessionId, UUID caseId) {
    administrator(issuer, subject, sessionId);
    return new ChatModerationModels.SanctionSuggestion(caseId, next(repository.target(caseId)));
  }

  @Transactional
  public ChatModerationModels.SanctionSummary confirm(
      String issuer,
      String subject,
      String sessionId,
      UUID caseId,
      ChatModerationModels.SanctionCommand command) {
    if (command == null || command.clientCommandId() == null || command.kind() == null)
      throw ChatCommunityModels.invalid();
    validateReason(command.reason());
    UUID admin = administrator(issuer, subject, sessionId);
    repository.lockCommandActor(admin);
    UUID target = repository.target(caseId);
    repository.lockTarget(target);
    repository.lockCase(caseId);
    administrator(issuer, subject, sessionId);
    var replay = repository.command(admin, command.clientCommandId());
    if (replay.isPresent()) {
      var old = replay.get();
      if (!old.caseId().equals(caseId)
          || old.kind() != command.kind()
          || !old.reason().equals(command.reason())) throw ChatCommunityModels.conflict();
      return summary(old);
    }
    if (repository.forCase(caseId).isPresent() || next(target) != command.kind())
      throw ChatCommunityModels.conflict();
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    Instant until =
        switch (command.kind()) {
          case CHAT_SUSPENSION_24H -> now.plusSeconds(86400);
          case CHAT_SUSPENSION_7D -> now.plusSeconds(7 * 86400);
          case CHAT_BAN -> null;
        };
    return summary(repository.insert(caseId, admin, target, command, now, until));
  }

  @Transactional(readOnly = true)
  public List<ChatModerationModels.SanctionSummary> own(
      String issuer, String subject, String sessionId) {
    return repository.forOwner(access.actor(issuer, subject, sessionId)).stream()
        .map(this::summary)
        .toList();
  }

  @Override
  public void requireAllowed(UUID actorId, UUID channelId, UUID eventId, Instant now) {
    if (repository.forOwner(actorId).stream().anyMatch(s -> s.active(now))) throw forbidden();
  }

  UUID administrator(String issuer, String subject, String sessionId) {
    UUID actor = access.actor(issuer, subject, sessionId);
    var authority =
        authorities.resolve(issuer, subject).orElseThrow(ChatSanctionService::forbidden);
    if (!actor.equals(authority.identityId()) || !authority.generalAdministrator())
      throw forbidden();
    return actor;
  }

  private ChatModerationModels.SanctionKind next(UUID target) {
    long confirmed = repository.forOwner(target).stream().filter(s -> !s.annulled()).count();
    return confirmed == 0
        ? ChatModerationModels.SanctionKind.CHAT_SUSPENSION_24H
        : confirmed == 1
            ? ChatModerationModels.SanctionKind.CHAT_SUSPENSION_7D
            : ChatModerationModels.SanctionKind.CHAT_BAN;
  }

  private ChatModerationModels.SanctionSummary summary(ChatSanctionRepository.Sanction fact) {
    return new ChatModerationModels.SanctionSummary(
        fact.id(),
        fact.kind(),
        fact.reason(),
        fact.from(),
        fact.until(),
        fact.active(clock.instant()),
        repository.reviewStatus(fact.caseId()));
  }

  static void validateReason(String reason) {
    try {
      ChatModerationModels.reason(reason);
    } catch (IllegalArgumentException error) {
      throw ChatCommunityModels.invalid();
    }
  }

  static ApiProblemException forbidden() {
    return new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
  }
}
