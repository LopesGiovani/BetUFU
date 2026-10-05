package br.com.betufu.chat.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ChatModerationModels {
  private ChatModerationModels() {}

  public record CaseSummary(
      UUID caseId,
      UUID messageId,
      UUID eventId,
      UUID competitionId,
      String status,
      Instant openedAt) {}

  public record CaseReceipt(UUID caseId, String status, Instant recordedAt) {}

  public record ModerationEvidence(
      UUID caseId, UUID messageId, String originalBody, List<ReportEvidence> reports) {
    public ModerationEvidence {
      reports = List.copyOf(reports);
    }
  }

  public record ReportEvidence(UUID reportId, String reason, Instant reportedAt) {}

  public record Command(UUID clientCommandId, String reason) {}

  public record ForwardReceipt(UUID caseId, Instant forwardedAt) {}

  static UUID uuid(String value) {
    try {
      UUID parsed = UUID.fromString(value);
      if (!parsed.toString().equals(value)) throw invalid();
      return parsed;
    } catch (IllegalArgumentException | NullPointerException error) {
      throw invalid();
    }
  }

  static String reason(String value) {
    if (value == null || value.isBlank() || value.length() > 280 || value.trim().length() == 0)
      throw invalid();
    return value;
  }

  static IllegalArgumentException invalid() {
    return new IllegalArgumentException("invalid moderation command");
  }
}
