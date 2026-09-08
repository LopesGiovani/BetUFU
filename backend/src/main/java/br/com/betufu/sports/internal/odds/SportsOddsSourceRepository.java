package br.com.betufu.sports.internal.odds;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("runtime | integrated")
class SportsOddsSourceRepository {

  private final JdbcTemplate jdbcTemplate;

  SportsOddsSourceRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
  }

  record EventRow(
      UUID eventId,
      UUID competitionId,
      UUID editionId,
      UUID sportId,
      UUID categoryId,
      Instant scheduledAt,
      String zoneId,
      String format,
      List<EventSlotRow> slots) {}

  record EventSlotRow(int slotNo, String displayLabel, UUID teamId, String teamName) {}

  record OutcomeRow(
      UUID factId,
      UUID eventId,
      UUID competitionId,
      UUID editionId,
      long versionNo,
      String outcomeKind,
      UUID confirmedVersionId,
      String confirmedVersionStatus,
      Instant confirmedAt,
      Instant knownAt,
      Instant sportTime,
      UUID supersedesFactId,
      UUID supersedesVersionId,
      List<ScoreRow> scores,
      Integer winnerSlotNo) {}

  record ScoreRow(int slotNo, int score, UUID teamId, String displayLabel) {}

  Optional<EventRow> findEvent(UUID eventId) {
    if (eventId == null) {
      return Optional.empty();
    }
    return jdbcTemplate.query(
        """
            SELECT ev.event_id, ev.competition_id, ev.edition_id, ev.sport_id,
                   ev.category_id, ev.scheduled_at, ev.zone_id, ev.event_format,
                   s1.slot_no AS slot1_no, s1.display_label AS slot1_label,
                   s1.team_id AS slot1_team_id, t1.name AS slot1_team_name,
                   s2.slot_no AS slot2_no, s2.display_label AS slot2_label,
                   s2.team_id AS slot2_team_id, t2.name AS slot2_team_name
            FROM public.sports_event ev
            JOIN public.sports_sport sport ON sport.sport_id = ev.sport_id
            JOIN public.sports_category category
              ON category.category_id = ev.category_id AND category.edition_id = ev.edition_id
            JOIN public.sports_event_slot s1
              ON s1.event_id = ev.event_id AND s1.slot_no = 1
            JOIN public.sports_team t1 ON t1.team_id = s1.team_id
            JOIN public.sports_event_slot s2
              ON s2.event_id = ev.event_id AND s2.slot_no = 2
            JOIN public.sports_team t2 ON t2.team_id = s2.team_id
            WHERE ev.event_id = ?
              AND ev.event_format = 'HEAD_TO_HEAD'
              AND ev.status = 'PUBLICADO'
            """,
        rs -> rs.next() ? Optional.of(mapEvent(rs)) : Optional.empty(),
        eventId);
  }

  Optional<OutcomeRow> findCurrentSportingOutcome(UUID eventId, UUID confirmedVersionId) {
    if (eventId == null || confirmedVersionId == null) {
      return Optional.empty();
    }
    List<OutcomeRow> rows =
        queryOutcomeRows(
            """
                AND ev.confirmed_version_id = ?
                AND f.outcome_kind = 'SPORTING_RESULT'
                AND sp.confirmed_version_id = ?
                AND h.fact_id = f.fact_id
                """,
            eventId,
            confirmedVersionId,
            confirmedVersionId);
    return firstOutcome(rows);
  }

  List<OutcomeRow> findOutcomeFactsAsOf(UUID competitionId, UUID editionId, Instant cutoff) {
    if (competitionId == null || editionId == null || cutoff == null) {
      return List.of();
    }
    return queryOutcomeRows(
        """
            AND f.competition_id = ?
            AND f.edition_id = ?
            AND f.known_at <= ?
            """,
        null,
        competitionId,
        editionId,
        Timestamp.from(cutoff));
  }

  private List<OutcomeRow> queryOutcomeRows(String predicate, UUID eventId, Object... arguments) {
    String eventPredicate = eventId == null ? "" : " AND ev.event_id = ? ";
    String sql =
        """
            SELECT f.fact_id, f.event_id, f.competition_id, f.edition_id, f.version_no,
                   f.outcome_kind, f.known_at, f.sport_time, f.supersedes_fact_id,
                   sp.confirmed_version_id, sp.confirmed_version_status, sp.confirmed_at,
                   rv.supersedes_version_id,
                   predecessor_sp.confirmed_version_id AS predecessor_version_id,
                   ap.winner_slot_no,
                   rvs.slot_no, rvs.score, rvs.team_id, rvs.display_label
            FROM public.sports_outcome_fact f
            JOIN public.sports_event ev ON ev.event_id = f.event_id
            LEFT JOIN public.sports_outcome_head h
              ON h.event_id = f.event_id AND h.fact_id = f.fact_id
            LEFT JOIN public.sports_outcome_sporting_payload sp
              ON sp.event_id = f.event_id AND sp.fact_id = f.fact_id
            LEFT JOIN public.sports_result_version rv
              ON rv.event_id = f.event_id AND rv.version_id = sp.confirmed_version_id
            LEFT JOIN public.sports_outcome_fact predecessor
              ON predecessor.event_id = f.event_id AND predecessor.fact_id = f.supersedes_fact_id
            LEFT JOIN public.sports_outcome_sporting_payload predecessor_sp
              ON predecessor_sp.event_id = predecessor.event_id
             AND predecessor_sp.fact_id = predecessor.fact_id
            LEFT JOIN public.sports_outcome_administrative_payload ap
              ON ap.event_id = f.event_id AND ap.fact_id = f.fact_id
            LEFT JOIN public.sports_result_version_slot rvs
              ON rvs.version_id = sp.confirmed_version_id
            WHERE 1 = 1
            """
            + eventPredicate
            + predicate
            + " ORDER BY f.event_id, f.sport_time ASC, f.known_at ASC, f.fact_id ASC, rvs.slot_no ASC";
    Object[] params;
    if (eventId == null) {
      params = arguments;
    } else {
      params = new Object[arguments.length + 1];
      params[0] = eventId;
      System.arraycopy(arguments, 0, params, 1, arguments.length);
    }
    return jdbcTemplate.query(sql, this::mapOutcomeRows, params);
  }

  private List<OutcomeRow> mapOutcomeRows(ResultSet rs) throws SQLException {
    java.util.Map<UUID, OutcomeAccumulator> grouped = new java.util.LinkedHashMap<>();
    while (rs.next()) {
      UUID factId = rs.getObject("fact_id", UUID.class);
      OutcomeAccumulator accumulator = grouped.get(factId);
      if (accumulator == null) {
        accumulator = newAccumulator(rs);
        grouped.put(factId, accumulator);
      }
      Integer slotNo = rs.getObject("slot_no", Integer.class);
      if (slotNo != null) {
        accumulator.scores.add(
            new ScoreRow(
                slotNo,
                rs.getInt("score"),
                rs.getObject("team_id", UUID.class),
                rs.getString("display_label")));
      }
    }
    return grouped.values().stream().map(OutcomeAccumulator::toRow).toList();
  }

  private static OutcomeAccumulator newAccumulator(ResultSet rs) throws SQLException {
    UUID factId = rs.getObject("fact_id", UUID.class);
    return new OutcomeAccumulator(
        factId,
        rs.getObject("event_id", UUID.class),
        rs.getObject("competition_id", UUID.class),
        rs.getObject("edition_id", UUID.class),
        rs.getLong("version_no"),
        rs.getString("outcome_kind"),
        rs.getObject("confirmed_version_id", UUID.class),
        rs.getString("confirmed_version_status"),
        instant(rs, "confirmed_at"),
        instant(rs, "known_at"),
        instant(rs, "sport_time"),
        rs.getObject("supersedes_fact_id", UUID.class),
        firstNonNull(
            rs.getObject("predecessor_version_id", UUID.class),
            rs.getObject("supersedes_version_id", UUID.class)),
        rs.getObject("winner_slot_no", Integer.class));
  }

  private static EventRow mapEvent(ResultSet rs) throws SQLException {
    return new EventRow(
        rs.getObject("event_id", UUID.class),
        rs.getObject("competition_id", UUID.class),
        rs.getObject("edition_id", UUID.class),
        rs.getObject("sport_id", UUID.class),
        rs.getObject("category_id", UUID.class),
        rs.getTimestamp("scheduled_at").toInstant(),
        rs.getString("zone_id"),
        rs.getString("event_format"),
        List.of(
            new EventSlotRow(
                rs.getInt("slot1_no"),
                rs.getString("slot1_label"),
                rs.getObject("slot1_team_id", UUID.class),
                rs.getString("slot1_team_name")),
            new EventSlotRow(
                rs.getInt("slot2_no"),
                rs.getString("slot2_label"),
                rs.getObject("slot2_team_id", UUID.class),
                rs.getString("slot2_team_name"))));
  }

  private static Optional<OutcomeRow> firstOutcome(List<OutcomeRow> rows) {
    return rows.stream().findFirst();
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp timestamp = rs.getTimestamp(column);
    return timestamp == null ? null : timestamp.toInstant();
  }

  private static <T> T firstNonNull(T first, T second) {
    return first != null ? first : second;
  }

  private static final class OutcomeAccumulator {
    private final UUID factId;
    private final UUID eventId;
    private final UUID competitionId;
    private final UUID editionId;
    private final long versionNo;
    private final String outcomeKind;
    private final UUID confirmedVersionId;
    private final String confirmedVersionStatus;
    private final Instant confirmedAt;
    private final Instant knownAt;
    private final Instant sportTime;
    private final UUID supersedesFactId;
    private final UUID supersedesVersionId;
    private final Integer winnerSlotNo;
    private final java.util.List<ScoreRow> scores = new java.util.ArrayList<>();

    private OutcomeAccumulator(
        UUID factId,
        UUID eventId,
        UUID competitionId,
        UUID editionId,
        long versionNo,
        String outcomeKind,
        UUID confirmedVersionId,
        String confirmedVersionStatus,
        Instant confirmedAt,
        Instant knownAt,
        Instant sportTime,
        UUID supersedesFactId,
        UUID supersedesVersionId,
        Integer winnerSlotNo) {
      this.factId = factId;
      this.eventId = eventId;
      this.competitionId = competitionId;
      this.editionId = editionId;
      this.versionNo = versionNo;
      this.outcomeKind = outcomeKind;
      this.confirmedVersionId = confirmedVersionId;
      this.confirmedVersionStatus = confirmedVersionStatus;
      this.confirmedAt = confirmedAt;
      this.knownAt = knownAt;
      this.sportTime = sportTime;
      this.supersedesFactId = supersedesFactId;
      this.supersedesVersionId = supersedesVersionId;
      this.winnerSlotNo = winnerSlotNo;
    }

    private OutcomeRow toRow() {
      return new OutcomeRow(
          factId,
          eventId,
          competitionId,
          editionId,
          versionNo,
          outcomeKind,
          confirmedVersionId,
          confirmedVersionStatus,
          confirmedAt,
          knownAt,
          sportTime,
          supersedesFactId,
          supersedesVersionId,
          List.copyOf(scores),
          winnerSlotNo);
    }
  }
}
