package br.com.betufu.chat.internal;

import br.com.betufu.chat.internal.ChatReadModels.Channel;
import br.com.betufu.chat.internal.ChatReadModels.Fact;
import br.com.betufu.chat.internal.ChatReadModels.HistoryPosition;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("runtime | integrated")
public class ChatRepository {
  private final JdbcTemplate jdbc;
  private static final String READ =
      """
      SELECT m.message_id, m.posted_at, m.change_cursor,
             COALESCE(p.state = 'REMOVED', false) AS removed,
             CASE WHEN p.state = 'REMOVED' THEN NULL ELSE m.author_identity_id END AS author_id,
             CASE WHEN p.state = 'REMOVED' THEN NULL ELSE m.body END AS visible_text
      FROM public.chat_message m
      LEFT JOIN public.chat_message_projection p ON p.message_id = m.message_id
      WHERE m.channel_id = ?
      """;

  public ChatRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  Channel global() {
    return jdbc.queryForObject(
        "SELECT channel_id, kind, event_id FROM public.chat_channel WHERE kind='GLOBAL'",
        this::channel);
  }

  boolean isGlobalAlias(UUID id) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM public.chat_channel WHERE channel_id=? AND kind='GLOBAL')",
            Boolean.class,
            id));
  }

  Channel recoverEvent(UUID eventId) {
    jdbc.update(
        "INSERT INTO public.chat_channel(kind,event_id) VALUES ('EVENT',?) ON CONFLICT DO NOTHING",
        eventId);
    return jdbc.queryForObject(
        "SELECT channel_id,kind,event_id FROM public.chat_channel WHERE kind='EVENT' AND event_id=?",
        this::channel,
        eventId);
  }

  List<Fact> history(Channel channel, HistoryPosition before, int limit) {
    if (before == null)
      return jdbc.query(
          READ + " ORDER BY m.posted_at DESC,m.message_id DESC LIMIT ?",
          this::fact,
          channel.channelId(),
          limit + 1);
    return jdbc.query(
        READ
            + " AND (m.posted_at,m.message_id) < (?,?) ORDER BY m.posted_at DESC,m.message_id DESC LIMIT ?",
        this::fact,
        channel.channelId(),
        Timestamp.from(before.postedAt()),
        before.messageId(),
        limit + 1);
  }

  List<Fact> changes(Channel channel, long after, int limit) {
    return jdbc.query(
        READ + " AND m.change_cursor > ? ORDER BY m.change_cursor LIMIT ?",
        this::fact,
        channel.channelId(),
        after,
        limit);
  }

  long latestCursor(Channel channel) {
    return jdbc.queryForObject(
        "SELECT COALESCE(max(change_cursor),0) FROM public.chat_message WHERE channel_id=?",
        Long.class,
        channel.channelId());
  }

  private Channel channel(ResultSet rs, int row) throws SQLException {
    return new Channel(
        rs.getObject("channel_id", UUID.class),
        rs.getString("kind"),
        rs.getObject("event_id", UUID.class));
  }

  private Fact fact(ResultSet rs, int row) throws SQLException {
    return new Fact(
        rs.getObject("message_id", UUID.class),
        rs.getObject("author_id", UUID.class),
        rs.getString("visible_text"),
        rs.getTimestamp("posted_at").toInstant(),
        rs.getBoolean("removed"),
        rs.getLong("change_cursor"));
  }
}
