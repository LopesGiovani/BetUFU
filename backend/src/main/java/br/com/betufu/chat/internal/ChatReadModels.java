package br.com.betufu.chat.internal;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ChatReadModels {
  private ChatReadModels() {}

  public record ChatMessage(
      UUID messageId,
      UUID participantId,
      String nickname,
      String text,
      Instant postedAt,
      boolean removed,
      long changeCursor) {}

  public record ChatReadResponse(
      String state,
      String kind,
      UUID eventId,
      List<ChatMessage> messages,
      String historyCursor,
      long changeCursor,
      Instant deadline) {
    public ChatReadResponse {
      messages = List.copyOf(messages);
    }
  }

  record Channel(UUID channelId, String kind, UUID eventId) {}

  record Fact(
      UUID messageId,
      UUID authorId,
      String text,
      Instant postedAt,
      boolean removed,
      long changeCursor) {}

  record HistoryPosition(UUID channelId, Instant postedAt, UUID messageId) {}
}
