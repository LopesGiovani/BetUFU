package br.com.betufu.chat.internal;

import br.com.betufu.chat.ChatEventPort;
import br.com.betufu.chat.ChatModerationAuthorityPort;
import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("runtime | integrated")
public class ChatModerationService {
  private final ChatModerationRepository repository;
  private final ChatModerationAuthorityPort authorities;
  private final ChatEventPort events;
  private final ChatCommunityModels.Access access;
  private final ChatPushService push;

  public ChatModerationService(
      ChatModerationRepository repository,
      ChatModerationAuthorityPort authorities,
      ChatEventPort events,
      ChatCommunityModels.Access access,
      ChatPushService push) {
    this.repository = repository;
    this.authorities = authorities;
    this.events = events;
    this.access = access;
    this.push = push;
  }

  @Transactional(readOnly = true)
  public List<ChatModerationModels.CaseSummary> list(
      String issuer, String subject, String sessionId) {
    var authority = currentAuthority(issuer, subject, sessionId);
    return repository.cases().stream()
        .filter(row -> allowed(authority, row.eventId()))
        .map(this::summary)
        .toList();
  }

  @Transactional
  public ChatModerationModels.CaseReceipt open(
      String issuer, String subject, String sessionId, UUID reportId) {
    if (reportId == null) throw invalid();
    var authority = currentAuthority(issuer, subject, sessionId);
    var target = repository.targetForReport(reportId).orElseThrow(ChatCommunityModels::missing);
    var eventId = target.eventId();
    if (!allowed(authority, eventId)) throw forbidden();
    UUID caseId = repository.ensureCase(reportId);
    var row = repository.findCase(caseId).orElseThrow(ChatCommunityModels::missing);
    return new ChatModerationModels.CaseReceipt(caseId, row.status(), row.openedAt());
  }

  @Transactional
  public ChatModerationModels.CaseReceipt remove(
      String issuer,
      String subject,
      String sessionId,
      UUID caseId,
      ChatModerationModels.Command command) {
    requireCommand(command);
    var authority = currentAuthority(issuer, subject, sessionId);
    var row = repository.lockCase(caseId).orElseThrow(ChatCommunityModels::missing);
    if (!allowed(authority, row.eventId())) throw forbidden();
    var prior =
        repository.command(
            "chat_moderation_decision_fact", authority.identityId(), command.clientCommandId());
    if (prior.isPresent()
        && (!prior.get().caseId().equals(caseId) || !prior.get().reason().equals(command.reason())))
      throw ChatCommunityModels.conflict();
    var decision = repository.decision(caseId, authority.identityId(), command.clientCommandId());
    if (decision.isPresent()
        && (!"REMOVE".equals(decision.get().kind())
            || !command.reason().equals(decision.get().reason())))
      throw ChatCommunityModels.conflict();
    if (decision.isEmpty())
      repository.insertDecision(
          caseId, authority.identityId(), command.clientCommandId(), "REMOVE", command.reason());
    var removal =
        repository.remove(caseId, row.messageId(), authority.identityId(), command.reason());
    push.changed(row.eventId());
    return new ChatModerationModels.CaseReceipt(caseId, "RESOLVED", removal.removedAt());
  }

  @Transactional
  public ChatModerationModels.ForwardReceipt forward(
      String issuer,
      String subject,
      String sessionId,
      UUID caseId,
      ChatModerationModels.Command command) {
    requireCommand(command);
    var authority = currentAuthority(issuer, subject, sessionId);
    var row = repository.lockCase(caseId).orElseThrow(ChatCommunityModels::missing);
    if (!allowed(authority, row.eventId())) throw forbidden();
    var prior =
        repository.command(
            "chat_moderation_forward_fact", authority.identityId(), command.clientCommandId());
    if (prior.isPresent()
        && (!prior.get().caseId().equals(caseId) || !prior.get().reason().equals(command.reason())))
      throw ChatCommunityModels.conflict();
    var fact =
        repository
            .forward(caseId, authority.identityId(), command.clientCommandId(), command.reason())
            .orElseThrow();
    push.changed(row.eventId());
    return new ChatModerationModels.ForwardReceipt(caseId, fact.forwardedAt());
  }

  @Transactional
  public ChatModerationModels.ModerationEvidence evidence(
      String issuer, String subject, String sessionId, UUID caseId, String reason) {
    String accessReason = ChatModerationModels.reason(reason);
    var authority = currentAuthority(issuer, subject, sessionId);
    var row = repository.lockCase(caseId).orElseThrow(ChatCommunityModels::missing);
    if (!allowed(authority, row.eventId())) throw forbidden();
    repository.recordEvidenceAccess(caseId, authority.identityId(), accessReason);
    var evidence = repository.evidence(caseId);
    return new ChatModerationModels.ModerationEvidence(
        evidence.header().caseId(),
        evidence.header().messageId(),
        evidence.header().body(),
        evidence.reports().stream()
            .map(
                r ->
                    new ChatModerationModels.ReportEvidence(
                        r.reportId(), r.reason(), r.reportedAt()))
            .toList());
  }

  private ChatModerationAuthorityPort.Authority currentAuthority(
      String issuer, String subject, String sessionId) {
    UUID actor = access.actor(issuer, subject, sessionId);
    var authority = authorities.resolve(issuer, subject).orElseThrow(this::forbidden);
    if (!actor.equals(authority.identityId())) throw forbidden();
    if (!authority.generalAdministrator() && authority.moderatorScopes().isEmpty())
      throw forbidden();
    return authority;
  }

  private boolean allowed(ChatModerationAuthorityPort.Authority authority, UUID eventId) {
    if (eventId == null) return authority.generalAdministrator();
    UUID competition =
        events.findPublished(eventId).map(ChatEventPort.PublishedEvent::competitionId).orElse(null);
    return authority.canModerate(competition);
  }

  private ChatModerationModels.CaseSummary summary(ChatModerationRepository.CaseRow row) {
    UUID competition =
        row.eventId() == null
            ? null
            : events
                .findPublished(row.eventId())
                .map(ChatEventPort.PublishedEvent::competitionId)
                .orElse(null);
    return new ChatModerationModels.CaseSummary(
        row.caseId(), row.messageId(), row.eventId(), competition, row.status(), row.openedAt());
  }

  private void requireCommand(ChatModerationModels.Command command) {
    if (command == null || command.clientCommandId() == null) throw invalid();
    ChatModerationModels.reason(command.reason());
  }

  private ApiProblemException invalid() {
    return new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
  }

  private ApiProblemException forbidden() {
    return new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
  }
}
