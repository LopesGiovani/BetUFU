package br.com.betufu.sports.internal.roster;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("runtime | integrated")
public class SportsEventRosterBindingRepository {

  private final JdbcTemplate jdbc;

  public SportsEventRosterBindingRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public record EventSlot(
      UUID eventId, UUID competitionId, UUID editionId, UUID teamId, int slotNo) {}

  public record Binding(
      UUID marketVersionId,
      UUID eventId,
      int slotNo,
      UUID teamId,
      UUID competitionId,
      UUID editionId,
      UUID publicationId,
      long versionNo,
      Instant knownAt) {}

  public List<EventSlot> lockHeadToHeadEvent(UUID eventId) {
    return jdbc.query(
        """
            SELECT e.event_id, e.competition_id, e.edition_id, s.team_id, s.slot_no
            FROM public.sports_event e
            JOIN public.sports_event_slot s ON s.event_id = e.event_id
            WHERE e.event_id = ? AND e.event_format = 'HEAD_TO_HEAD' AND e.status = 'PUBLICADO'
            ORDER BY s.slot_no
            FOR UPDATE OF e
            """,
        (rs, rowNum) ->
            new EventSlot(
                rs.getObject("event_id", UUID.class),
                rs.getObject("competition_id", UUID.class),
                rs.getObject("edition_id", UUID.class),
                rs.getObject("team_id", UUID.class),
                rs.getInt("slot_no")),
        eventId);
  }

  public int insertBinding(
      UUID marketVersionId, EventSlot slot, UUID publicationId, long versionNo, Instant knownAt) {
    return jdbc.update(
        """
            INSERT INTO public.odds_market_roster_binding
                (market_version_id, event_id, slot_no, team_id, competition_id, edition_id,
                 roster_publication_id, roster_version_no, roster_known_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
        marketVersionId,
        slot.eventId(),
        slot.slotNo(),
        slot.teamId(),
        slot.competitionId(),
        slot.editionId(),
        publicationId,
        versionNo,
        Timestamp.from(knownAt));
  }

  public List<Binding> findBindings(UUID marketVersionId) {
    return jdbc.query(
        """
            SELECT market_version_id, event_id, slot_no, team_id, competition_id, edition_id,
                   roster_publication_id, roster_version_no, roster_known_at
            FROM public.odds_market_roster_binding
            WHERE market_version_id = ? ORDER BY slot_no
            """,
        (rs, rowNum) ->
            new Binding(
                rs.getObject("market_version_id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getInt("slot_no"),
                rs.getObject("team_id", UUID.class),
                rs.getObject("competition_id", UUID.class),
                rs.getObject("edition_id", UUID.class),
                rs.getObject("roster_publication_id", UUID.class),
                rs.getLong("roster_version_no"),
                rs.getTimestamp("roster_known_at").toInstant()),
        marketVersionId);
  }
}
