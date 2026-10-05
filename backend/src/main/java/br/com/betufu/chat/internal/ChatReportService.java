package br.com.betufu.chat.internal;

import br.com.betufu.chat.ChatEventPort;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("runtime | integrated")
public class ChatReportService {
  private final ChatReportRepository repository;
  private final ChatMuteRepository accounts;
  private final ChatCommunityModels.Access access;
  private final ChatEventPort events;

  public ChatReportService(
      ChatReportRepository repository,
      ChatMuteRepository accounts,
      ChatCommunityModels.Access access,
      ChatEventPort events) {
    this.repository = repository;
    this.accounts = accounts;
    this.access = access;
    this.events = events;
  }

  @Transactional
  public ChatCommunityModels.ReportReceipt report(
      String issuer,
      String subject,
      String sessionId,
      UUID message,
      UUID key,
      ChatCommunityModels.ReportReason reason) {
    if (message == null || key == null || reason == null) throw ChatCommunityModels.invalid();
    UUID actor = access.actor(issuer, subject, sessionId);
    accounts.lockAccount(actor);
    access.requireCurrent(issuer, subject, sessionId);
    var target = repository.target(message).orElseThrow(ChatCommunityModels::missing);
    if (target.eventId() != null && events.findPublished(target.eventId()).isEmpty())
      throw ChatCommunityModels.missing();
    var replay = repository.receipt(actor, key);
    if (replay.isPresent()) return requireSame(replay.get(), message, reason).receipt();
    var report =
        repository
            .occurrence(actor, message)
            .map(old -> requireSame(old, message, reason))
            .orElseGet(() -> repository.insert(actor, message, reason));
    repository.receipt(actor, key, report.receipt().reportId());
    access.requireCurrent(issuer, subject, sessionId);
    return report.receipt();
  }

  private ChatReportRepository.Report requireSame(
      ChatReportRepository.Report report, UUID message, ChatCommunityModels.ReportReason reason) {
    if (!report.messageId().equals(message) || report.reason() != reason)
      throw ChatCommunityModels.conflict();
    return report;
  }
}
