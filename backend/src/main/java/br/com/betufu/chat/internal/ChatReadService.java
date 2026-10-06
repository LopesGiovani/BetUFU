package br.com.betufu.chat.internal;

import br.com.betufu.chat.ChatEmergencyPausePort;
import br.com.betufu.chat.ChatEventPort;
import br.com.betufu.chat.ChatIdentityPort;
import br.com.betufu.chat.internal.ChatReadModels.*;
import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("runtime | integrated")
public class ChatReadService {
  private final ChatRepository repository;
  private final ChatEventPort events;
  private final ChatIdentityPort identities;
  private final ChatEmergencyPausePort pauses;
  private final ChatCommunityModels.Access access;

  public ChatReadService(
      ChatRepository repository,
      ChatEventPort events,
      ChatIdentityPort identities,
      ChatEmergencyPausePort pauses,
      ChatCommunityModels.Access access) {
    this.repository = repository;
    this.events = events;
    this.identities = identities;
    this.pauses = pauses;
    this.access = access;
  }

  @Transactional(readOnly = true)
  public ChatReadModels.Availability availability(
      UUID eventId, boolean authenticated, String issuer, String subject, String sessionId) {
    if (eventId == null && !authenticated)
      throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
    Channel channel = resolveChannel(eventId, authenticated);
    var channelState =
        pauses.current(ChatEmergencyPausePort.TargetKind.CHANNEL, channel.channelId());
    var eventState =
        eventId == null
            ? new ChatEmergencyPausePort.State(false, null)
            : pauses.current(ChatEmergencyPausePort.TargetKind.EVENT, eventId);
    var accountState =
        authenticated
            ? pauses.current(
                ChatEmergencyPausePort.TargetKind.ACCOUNT, access.actor(issuer, subject, sessionId))
            : new ChatEmergencyPausePort.State(false, null);
    boolean paused = channelState.paused() || eventState.paused() || accountState.paused();
    UUID revision =
        channelState.revision() != null
            ? channelState.revision()
            : eventState.revision() != null ? eventState.revision() : accountState.revision();
    return new ChatReadModels.Availability(paused ? "PAUSED" : "AVAILABLE", paused, revision);
  }

  @Transactional
  public ChatReadResponse read(
      UUID eventId, boolean authenticated, String historyCursor, Long after, int limit) {
    requireLimit(limit);
    Channel channel = resolveChannel(eventId, authenticated);
    if (after != null) {
      if (after < 0 || historyCursor != null) throw invalid();
      List<Fact> facts = repository.changes(channel, after, limit);
      long next = facts.isEmpty() ? after : facts.getLast().changeCursor();
      return response(channel, facts, null, next);
    }
    HistoryPosition before = decode(historyCursor, channel);
    List<Fact> result = repository.history(channel, before, limit);
    boolean more = result.size() > limit;
    List<Fact> page = result.subList(0, Math.min(limit, result.size()));
    String next = more ? encode(channel, page.getLast()) : null;
    return response(channel, page, next, repository.latestCursor(channel));
  }

  private Channel resolveChannel(UUID eventId, boolean authenticated) {
    if (eventId == null) {
      requireSession(authenticated);
      return repository.global();
    }
    if (repository.isGlobalAlias(eventId)) {
      requireSession(authenticated);
      throw new ApiProblemException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND);
    }
    if (events.findPublished(eventId).isEmpty())
      throw new ApiProblemException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND);
    return repository.recoverEvent(eventId);
  }

  private ChatReadResponse response(
      Channel channel, List<Fact> facts, String historyCursor, long changeCursor) {
    List<ChatMessage> messages = facts.stream().map(this::message).toList();
    // Read availability is not write permission. Lifecycle/deadlines belong to 07-10.
    return new ChatReadResponse(
        "AVAILABLE",
        channel.kind(),
        channel.eventId(),
        messages,
        historyCursor,
        changeCursor,
        null);
  }

  private ChatMessage message(Fact fact) {
    ChatIdentityPort.Participant participant =
        fact.removed() ? null : identities.findParticipant(fact.authorId()).orElse(null);
    return new ChatMessage(
        fact.messageId(),
        participant == null ? null : participant.participantId(),
        participant == null ? null : participant.nickname(),
        fact.text(),
        fact.postedAt(),
        fact.removed(),
        fact.changeCursor());
  }

  private String encode(Channel channel, Fact fact) {
    String value = channel.channelId() + "|" + fact.postedAt() + "|" + fact.messageId();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(value.getBytes(StandardCharsets.US_ASCII));
  }

  private HistoryPosition decode(String cursor, Channel channel) {
    if (cursor == null) return null;
    if (cursor.length() > 256 || !cursor.matches("[A-Za-z0-9_-]+")) throw invalid();
    try {
      byte[] bytes = Base64.getUrlDecoder().decode(cursor);
      if (!Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(cursor))
        throw invalid();
      String[] parts = new String(bytes, StandardCharsets.US_ASCII).split("\\|", -1);
      if (parts.length != 3) throw invalid();
      UUID channelId = UUID.fromString(parts[0]);
      Instant time = Instant.parse(parts[1]);
      UUID messageId = UUID.fromString(parts[2]);
      if (time.isBefore(Instant.parse("0001-01-01T00:00:00Z"))
          || time.isAfter(Instant.parse("9999-12-31T23:59:59.999999Z"))) throw invalid();
      if (!channel.channelId().equals(channelId)
          || !channelId.toString().equals(parts[0])
          || !messageId.toString().equals(parts[2])) throw invalid();
      return new HistoryPosition(channelId, time, messageId);
    } catch (IllegalArgumentException | java.time.DateTimeException error) {
      throw invalid();
    }
  }

  private void requireLimit(int limit) {
    if (limit < 1 || limit > 100) throw invalid();
  }

  private void requireSession(boolean authenticated) {
    if (!authenticated)
      throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
  }

  private ApiProblemException invalid() {
    return new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
  }
}
