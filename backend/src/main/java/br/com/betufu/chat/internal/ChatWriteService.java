package br.com.betufu.chat.internal;

import br.com.betufu.chat.ChatEligibilityPort;
import br.com.betufu.chat.ChatEventPort;
import br.com.betufu.chat.ChatWriteGuard;
import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("runtime | integrated")
public class ChatWriteService {
  private final ChatWriteRepository writes;
  private final ChatRepository channels;
  private final ChatEligibilityPort eligibility;
  private final ChatEventPort events;
  private final List<ChatWriteGuard> guards;
  private final ChatPushService push;
  private final ChatLifecycleService lifecycle;
  private final Clock clock;
  private final ObjectProvider<SessionRepository<?>> sessions;

  public ChatWriteService(
      ChatWriteRepository writes,
      ChatRepository channels,
      ChatEligibilityPort eligibility,
      ChatEventPort events,
      List<ChatWriteGuard> guards,
      ChatPushService push,
      ChatLifecycleService lifecycle,
      Clock clock,
      ObjectProvider<SessionRepository<?>> sessions) {
    this.writes = writes;
    this.channels = channels;
    this.eligibility = eligibility;
    this.events = events;
    this.guards = List.copyOf(guards);
    this.push = push;
    this.lifecycle = lifecycle;
    this.clock = clock;
    this.sessions = sessions;
  }

  // Expected business rejection may commit lifecycle facts, but never an accepted message.
  @Transactional(noRollbackFor = ApiProblemException.class)
  public ChatWriteController.Ack write(
      String issuer, String subject, String sessionId, UUID eventId, UUID key, String text) {
    if (key == null) throw invalid();
    UUID actor =
        eligibility
            .findActor(issuer, subject)
            .orElseThrow(
                () -> new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN));
    // This row serializes all actor keys/channels. Accepted replay precedes current write
    // eligibility.
    Instant last = writes.lockActor(actor);
    requireCurrentSession(sessionId, issuer, subject);
    var receipt = writes.findReceipt(actor, key);
    if (receipt.isPresent()) {
      var old = receipt.get();
      if (!Objects.equals(old.eventId(), eventId) || !old.text().equals(text))
        throw new ApiProblemException(HttpStatus.CONFLICT, ApiErrorCode.STALE_VERSION);
      return old.ack();
    }
    validateText(text);
    if (!eligibility.lockActiveEnrollment(actor))
      throw new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
    ChatReadModels.Channel channel;
    if (eventId == null) channel = channels.global();
    else {
      if (channels.isGlobalAlias(eventId) || events.findPublished(eventId).isEmpty())
        throw new ApiProblemException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND);
      channel = channels.recoverEvent(eventId);
    }
    Instant guardTime = clock.instant().truncatedTo(ChronoUnit.MICROS);
    guards.forEach(guard -> guard.requireAllowed(actor, channel.channelId(), eventId, guardTime));
    lifecycle.requireWritable(eventId, guardTime);
    // Event locking may wait; acceptance and the limiter use the time after that wait.
    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
    if (last != null && now.isBefore(last.plusSeconds(3)))
      throw new RateLimited(last.plusSeconds(3), now);
    requireCurrentSession(sessionId, issuer, subject);
    var ack = writes.accept(actor, key, channel.channelId(), text, now);
    push.changed(eventId);
    return ack;
  }

  private void validateText(String text) {
    if (text == null
        || text.length() > 560
        || text.codePointCount(0, text.length()) > 280
        || text.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c))
        || text.codePoints()
            .anyMatch(c -> Character.isISOControl(c) || (c >= 0xd800 && c <= 0xdfff)))
      throw invalid();
  }

  private ApiProblemException invalid() {
    return new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
  }

  private void requireCurrentSession(String id, String issuer, String subject) {
    var repository = sessions.getIfAvailable();
    var session = repository == null || id == null ? null : repository.findById(id);
    SecurityContext context =
        session == null ? null : session.getAttribute("SPRING_SECURITY_CONTEXT");
    var auth = context == null ? null : context.getAuthentication();
    if (session == null
        || session.isExpired()
        || !clock
            .instant()
            .isBefore(session.getLastAccessedTime().plus(session.getMaxInactiveInterval()))
        || auth == null
        || !auth.isAuthenticated()
        || !(auth.getPrincipal() instanceof OidcUser user)
        || !issuer.equals(user.getIssuer().toString())
        || !subject.equals(user.getSubject()))
      throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
  }

  static final class RateLimited extends RuntimeException {
    final Instant retryAt;
    final long retryAfter;

    RateLimited(Instant retryAt, Instant now) {
      super("BETUFU-429-CHAT_RATE_LIMIT");
      this.retryAt = retryAt;
      var remaining = java.time.Duration.between(now, retryAt);
      this.retryAfter = Math.max(1, remaining.getSeconds() + (remaining.getNano() > 0 ? 1 : 0));
    }
  }

  @Repository
  @Profile("runtime | integrated")
  public static class ChatWriteRepository {
    private final JdbcTemplate jdbc;

    public ChatWriteRepository(JdbcTemplate jdbc) {
      this.jdbc = jdbc;
    }

    Instant lockActor(UUID actorId) {
      jdbc.update(
          "INSERT INTO chat_write_limiter(author_identity_id) VALUES (?) ON CONFLICT DO NOTHING",
          actorId);
      Instant last =
          jdbc.queryForObject(
              "SELECT last_accepted_at FROM chat_write_limiter WHERE author_identity_id=? FOR UPDATE",
              (rs, row) -> {
                Timestamp time = rs.getTimestamp(1);
                return time == null ? null : time.toInstant();
              },
              actorId);
      if (last != null) return last;
      return jdbc.queryForObject(
          "SELECT max(m.posted_at) FROM chat_write_receipt r JOIN chat_message m ON m.message_id=r.message_id WHERE r.author_identity_id=?",
          (rs, row) -> {
            Timestamp time = rs.getTimestamp(1);
            return time == null ? null : time.toInstant();
          },
          actorId);
    }

    Optional<Receipt> findReceipt(UUID actorId, UUID key) {
      return jdbc
          .query(
              """
          SELECT m.message_id,m.posted_at,m.change_cursor,m.body,c.event_id
          FROM chat_write_receipt r
          JOIN chat_message m ON m.message_id=r.message_id
          JOIN chat_channel c ON c.channel_id=m.channel_id
          WHERE r.author_identity_id=? AND r.idempotency_key=?
          """,
              (rs, row) ->
                  new Receipt(
                      new ChatWriteController.Ack(
                          rs.getObject("message_id", UUID.class),
                          rs.getTimestamp("posted_at").toInstant(),
                          rs.getLong("change_cursor")),
                      rs.getObject("event_id", UUID.class),
                      rs.getString("body")),
              actorId,
              key)
          .stream()
          .findFirst();
    }

    ChatWriteController.Ack accept(
        UUID actorId, UUID key, UUID channelId, String text, Instant now) {
      ChatWriteController.Ack ack =
          jdbc.queryForObject(
              """
          INSERT INTO chat_message(channel_id,author_identity_id,body,posted_at)
          VALUES (?,?,?,?) RETURNING message_id,posted_at,change_cursor
          """,
              (rs, row) ->
                  new ChatWriteController.Ack(
                      rs.getObject("message_id", UUID.class),
                      rs.getTimestamp("posted_at").toInstant(),
                      rs.getLong("change_cursor")),
              channelId,
              actorId,
              text,
              Timestamp.from(now));
      jdbc.update(
          "INSERT INTO chat_write_receipt(author_identity_id,idempotency_key,message_id) VALUES (?,?,?)",
          actorId,
          key,
          ack.messageId());
      jdbc.update(
          "UPDATE chat_write_limiter SET last_accepted_at=? WHERE author_identity_id=?",
          Timestamp.from(now),
          actorId);
      return ack;
    }

    record Receipt(ChatWriteController.Ack ack, UUID eventId, String text) {}
  }
}
