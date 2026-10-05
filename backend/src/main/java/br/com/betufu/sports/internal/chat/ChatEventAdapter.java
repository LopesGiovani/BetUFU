package br.com.betufu.sports.internal.chat;

import br.com.betufu.chat.ChatEventPort;
import java.sql.Timestamp;
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

  @Override
  public Optional<DefinitiveMilestone> findDefinitiveMilestone(UUID eventId) {
    var history = findDefinitiveMilestones(eventId);
    return history.isEmpty() ? Optional.empty() : Optional.of(history.getLast());
  }

  @Override
  public java.util.List<DefinitiveMilestone> findDefinitiveMilestones(UUID eventId) {
    if (eventId == null) return java.util.List.of();
    // Serializes chat acceptance with producer confirmations and completion.
    jdbc.query(
        "SELECT event_id FROM public.sports_event WHERE event_id=? FOR UPDATE",
        (rs, row) -> rs.getObject(1, UUID.class),
        eventId);
    return jdbc.query(
        """
        SELECT f.fact_id,f.outcome_kind,f.known_at,sp.confirmed_version_id,sp.confirmed_at
        FROM public.sports_outcome_fact f
        LEFT JOIN public.sports_outcome_sporting_payload sp
          ON sp.event_id=f.event_id AND sp.fact_id=f.fact_id
        LEFT JOIN public.sports_outcome_administrative_payload ap
          ON ap.event_id=f.event_id AND ap.fact_id=f.fact_id
        WHERE f.event_id=? AND (sp.fact_id IS NOT NULL OR ap.fact_id IS NOT NULL)
        ORDER BY f.version_no
        """,
        (rs, row) -> {
          Timestamp confirmed = rs.getTimestamp("confirmed_at");
          return new DefinitiveMilestone(
              eventId,
              rs.getObject("fact_id", UUID.class),
              rs.getObject("confirmed_version_id", UUID.class),
              rs.getString("outcome_kind"),
              confirmed == null ? rs.getTimestamp("known_at").toInstant() : confirmed.toInstant());
        },
        eventId);
  }
}
