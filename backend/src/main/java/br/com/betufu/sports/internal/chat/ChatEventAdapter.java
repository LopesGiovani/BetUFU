package br.com.betufu.sports.internal.chat;

import br.com.betufu.chat.ChatEventPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("runtime | integrated")
public class ChatEventAdapter implements ChatEventPort {
  private final JdbcTemplate jdbc;

  public ChatEventAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<PublishedEvent> findPublished(UUID eventId) {
    return jdbc.query(
        """
        SELECT e.event_id, e.competition_id, e.edition_id
        FROM public.sports_event e
        WHERE e.event_id = ? AND e.status <> 'RASCUNHO'
          AND EXISTS (SELECT 1 FROM public.sports_event_publication p WHERE p.event_id = e.event_id)
        """,
        rs ->
            rs.next()
                ? Optional.of(
                    new PublishedEvent(
                        rs.getObject(1, UUID.class),
                        rs.getObject(2, UUID.class),
                        rs.getObject(3, UUID.class)))
                : Optional.empty(),
        eventId);
  }
}
