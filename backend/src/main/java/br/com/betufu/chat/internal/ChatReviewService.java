package br.com.betufu.chat.internal;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("runtime | integrated")
public class ChatReviewService {
  private final ChatSanctionRepository repository;
  private final ChatSanctionService sanctions;
  private final ChatCommunityModels.Access access;
  private final Clock clock;

  public ChatReviewService(
      ChatSanctionRepository repository,
      ChatSanctionService sanctions,
      ChatCommunityModels.Access access,
      Clock clock) {
    this.repository = repository;
    this.sanctions = sanctions;
    this.access = access;
    this.clock = clock;
  }

  @Transactional
  public ChatModerationModels.ReviewReceipt request(
      String issuer,
      String subject,
      String sessionId,
      UUID sanctionId,
      ChatModerationModels.Command command) {
    validate(command);
    UUID owner = access.actor(issuer, subject, sessionId);
    repository.lockCommandActor(owner);
    repository.lockTarget(owner);
    var sanction =
        repository
            .sanction(sanctionId)
            .filter(s -> s.target().equals(owner))
            .orElseThrow(ChatCommunityModels::missing);
    repository.lockCase(sanction.caseId());
    access.requireCurrent(issuer, subject, sessionId);
    var prior = repository.reviewCommand(owner, command.clientCommandId());
    if (prior.isPresent()) {
      var old = prior.get();
      if (!old.caseId().equals(sanction.caseId())
          || !old.kind().equals("OPENED")
          || !old.reason().equals(command.reason())) throw ChatCommunityModels.conflict();
      return receipt(old, sanctionId, "PENDING");
    }
    if (repository.opened(sanction.caseId()).isPresent() || sanction.annulled())
      throw ChatCommunityModels.conflict();
    var fact =
        repository.insertReview(
            sanction.caseId(),
            owner,
            command.clientCommandId(),
            "OPENED",
            command.reason(),
            clock.instant().truncatedTo(ChronoUnit.MICROS));
    return receipt(fact, sanctionId, "PENDING");
  }

  @Transactional(readOnly = true)
  public List<ChatModerationModels.ReviewReceipt> pending(
      String issuer, String subject, String sessionId) {
    sanctions.administrator(issuer, subject, sessionId);
    return repository.pendingReviews().stream()
        .map(r -> receipt(r, repository.forCase(r.caseId()).orElseThrow().id(), "PENDING"))
        .toList();
  }

  @Transactional
  public ChatModerationModels.ReviewReceipt decide(
      String issuer,
      String subject,
      String sessionId,
      UUID reviewId,
      ChatModerationModels.ReviewCommand command) {
    if (command == null || command.clientCommandId() == null || command.decision() == null)
      throw ChatCommunityModels.invalid();
    ChatSanctionService.validateReason(command.reason());
    UUID admin = sanctions.administrator(issuer, subject, sessionId);
    repository.lockCommandActor(admin);
    var request =
        repository
            .review(reviewId)
            .filter(r -> r.kind().equals("OPENED"))
            .orElseThrow(ChatCommunityModels::missing);
    var sanction = repository.forCase(request.caseId()).orElseThrow(ChatCommunityModels::missing);
    repository.lockTarget(sanction.target());
    repository.lockCase(request.caseId());
    sanctions.administrator(issuer, subject, sessionId);
    String kind =
        command.decision() == ChatModerationModels.ReviewDecision.ANNUL ? "REVERSED" : "REVIEWED";
    var prior = repository.reviewCommand(admin, command.clientCommandId());
    if (prior.isPresent()) {
      var old = prior.get();
      if (!old.caseId().equals(request.caseId())
          || !old.kind().equals(kind)
          || !old.reason().equals(command.reason())) throw ChatCommunityModels.conflict();
      return receipt(old, sanction.id(), kind.equals("REVERSED") ? "ANNULLED" : "MAINTAINED");
    }
    if (repository.resolution(request.caseId()).isPresent()) throw ChatCommunityModels.conflict();
    var fact =
        repository.insertReview(
            request.caseId(),
            admin,
            command.clientCommandId(),
            kind,
            command.reason(),
            clock.instant().truncatedTo(ChronoUnit.MICROS));
    return receipt(fact, sanction.id(), kind.equals("REVERSED") ? "ANNULLED" : "MAINTAINED");
  }

  private void validate(ChatModerationModels.Command command) {
    if (command == null || command.clientCommandId() == null) throw ChatCommunityModels.invalid();
    ChatSanctionService.validateReason(command.reason());
  }

  private ChatModerationModels.ReviewReceipt receipt(
      ChatSanctionRepository.Review fact, UUID sanctionId, String status) {
    return new ChatModerationModels.ReviewReceipt(
        fact.id(), sanctionId, status, fact.reason(), fact.recordedAt());
  }
}
