package br.com.betufu.sports.internal.settlement;

import br.com.betufu.settlement.SportsResultForSettlementPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("runtime | integrated")
public class SportsResultForSettlementAdapter implements SportsResultForSettlementPort {
  private final JdbcTemplate jdbc;

  public SportsResultForSettlementAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<ResultSnapshot> findCurrent(UUID eventId) {
    if (eventId == null) return Optional.empty();
    return jdbc.query(
        """
            SELECT f.fact_id, f.outcome_kind, f.known_at, sp.confirmed_version_id,
                   sp.confirmed_at, ap.winner_slot_no
            FROM public.sports_outcome_head h
            JOIN public.sports_outcome_fact f ON f.event_id = h.event_id AND f.fact_id = h.fact_id
            JOIN public.sports_event e ON e.event_id = f.event_id
            LEFT JOIN public.sports_outcome_sporting_payload sp
              ON sp.event_id = f.event_id AND sp.fact_id = f.fact_id
            LEFT JOIN public.sports_result_version rv
              ON rv.event_id = sp.event_id AND rv.version_id = sp.confirmed_version_id
            LEFT JOIN public.sports_outcome_administrative_payload ap
              ON ap.event_id = f.event_id AND ap.fact_id = f.fact_id
            WHERE h.event_id = ?
              AND ((e.status = 'CANCELADO' AND f.outcome_kind = 'EVENT_CANCELLED'
                    AND ap.fact_id IS NOT NULL AND ap.winner_slot_no IS NULL)
                OR (e.status = 'PUBLICADO' AND f.outcome_kind = 'SPORTING_RESULT'
                    AND sp.confirmed_version_id = e.confirmed_version_id
                    AND rv.status IN ('CONFIRMADO', 'CORRIGIDO'))
                OR (e.status = 'PUBLICADO'
                    AND f.outcome_kind IN ('ADMINISTRATIVE_WALKOVER', 'ABANDONED_NO_VALID_RESULT')
                    AND ap.fact_id IS NOT NULL))
            """,
        rs -> rs.next() ? Optional.of(map(eventId, rs)) : Optional.empty(),
        eventId);
  }

  private ResultSnapshot map(UUID eventId, ResultSet rs) throws SQLException {
    UUID versionId = rs.getObject("confirmed_version_id", UUID.class);
    Integer homeScore = null;
    Integer awayScore = null;
    if (versionId != null) {
      List<Integer> scores =
          jdbc.query(
              "SELECT score FROM public.sports_result_version_slot WHERE version_id = ? ORDER BY slot_no",
              (scoreRs, rowNo) -> scoreRs.getInt("score"),
              versionId);
      if (scores.size() != 2)
        throw new IllegalStateException("confirmed result requires two scores");
      homeScore = scores.get(0);
      awayScore = scores.get(1);
    }
    return new ResultSnapshot(
        eventId,
        rs.getObject("fact_id", UUID.class),
        versionId,
        OutcomeKind.valueOf(rs.getString("outcome_kind")),
        homeScore,
        awayScore,
        rs.getObject("winner_slot_no", Integer.class),
        rs.getTimestamp("confirmed_at") == null
            ? null
            : rs.getTimestamp("confirmed_at").toInstant(),
        rs.getTimestamp("known_at").toInstant());
  }

  @Override
  public List<EventResult> findCandidates() {
    return jdbc
        .query(
            """
            SELECT e.event_id, e.title, e.scheduled_at FROM public.sports_event e
            JOIN public.sports_outcome_head h ON h.event_id = e.event_id
            WHERE e.status IN ('PUBLICADO', 'CANCELADO')
            ORDER BY e.scheduled_at DESC, e.event_id
            """,
            (rs, row) ->
                new EventMetadata(
                    rs.getObject("event_id", UUID.class),
                    rs.getString("title"),
                    rs.getTimestamp("scheduled_at").toInstant()))
        .stream()
        .flatMap(
            event ->
                findCurrent(event.id()).stream()
                    .map(result -> new EventResult(event.title(), event.scheduledAt(), result)))
        .limit(100)
        .toList();
  }

  private record EventMetadata(UUID id, String title, java.time.Instant scheduledAt) {}
}
