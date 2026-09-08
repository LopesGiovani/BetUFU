package br.com.betufu.sports.internal.result;

import br.com.betufu.sports.internal.event.SportsEventModels.EventFormat;
import br.com.betufu.sports.internal.result.SportsResultModels.SportsResultScoreItem;
import br.com.betufu.sports.internal.result.SportsResultModels.SportsResultVersion;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("runtime | integrated")
class SportsResultRepository {

  private final JdbcTemplate jdbcTemplate;

  SportsResultRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
  }

  record EventScope(
      UUID eventId,
      UUID competitionId,
      UUID editionId,
      EventFormat format,
      String status,
      long lockVersion,
      UUID confirmedVersionId) {}

  record EventSlot(int slotNo, String displayLabel, UUID teamId, String teamName) {}

  Optional<EventScope> findEventScope(UUID eventId) {
    if (eventId == null) {
      return Optional.empty();
    }
    return jdbcTemplate.query(
        """
                SELECT event_id, competition_id, edition_id, event_format, status, lock_version, confirmed_version_id
                FROM public.sports_event
                WHERE event_id = ?
                """,
        rs ->
            rs.next()
                ? Optional.of(
                    new EventScope(
                        rs.getObject("event_id", UUID.class),
                        rs.getObject("competition_id", UUID.class),
                        rs.getObject("edition_id", UUID.class),
                        EventFormat.valueOf(rs.getString("event_format")),
                        rs.getString("status"),
                        rs.getLong("lock_version"),
                        rs.getObject("confirmed_version_id", UUID.class)))
                : Optional.empty(),
        eventId);
  }

  Optional<EventScope> findEventScopeForUpdate(UUID eventId) {
    if (eventId == null) {
      return Optional.empty();
    }
    return jdbcTemplate.query(
        """
                SELECT event_id, competition_id, edition_id, event_format, status, lock_version, confirmed_version_id
                FROM public.sports_event
                WHERE event_id = ?
                FOR UPDATE
                """,
        rs ->
            rs.next()
                ? Optional.of(
                    new EventScope(
                        rs.getObject("event_id", UUID.class),
                        rs.getObject("competition_id", UUID.class),
                        rs.getObject("edition_id", UUID.class),
                        EventFormat.valueOf(rs.getString("event_format")),
                        rs.getString("status"),
                        rs.getLong("lock_version"),
                        rs.getObject("confirmed_version_id", UUID.class)))
                : Optional.empty(),
        eventId);
  }

  List<EventSlot> findEventSlots(UUID eventId) {
    return jdbcTemplate.query(
        """
                SELECT s.slot_no, s.display_label, s.team_id, t.name AS team_name
                FROM public.sports_event_slot s
                LEFT JOIN public.sports_team t ON t.team_id = s.team_id
                WHERE s.event_id = ?
                ORDER BY s.slot_no ASC
                """,
        (rs, rowNum) ->
            new EventSlot(
                rs.getInt("slot_no"),
                rs.getString("display_label"),
                rs.getObject("team_id", UUID.class),
                rs.getString("team_name")),
        eventId);
  }

  List<SportsResultVersion> findResultVersions(UUID eventId) {
    if (eventId == null) {
      return List.of();
    }
    Map<UUID, List<SportsResultScoreItem>> scoresByVersion = findScoresForEvent(eventId);
    return jdbcTemplate.query(
        """
                SELECT
                    version_id,
                    event_id,
                    competition_id,
                    edition_id,
                    version_no,
                    status,
                    source_reference,
                    reason,
                    recorded_by_identity_id,
                    confirmed_by_identity_id,
                    recorded_at,
                    confirmed_at,
                    supersedes_version_id,
                    base_lock_version
                FROM public.sports_result_version
                WHERE event_id = ?
                ORDER BY version_no ASC
                """,
        (rs, rowNum) -> {
          UUID versionId = rs.getObject("version_id", UUID.class);
          List<SportsResultScoreItem> scores = scoresByVersion.getOrDefault(versionId, List.of());
          return mapResultVersion(rs, scores);
        },
        eventId);
  }

  Optional<SportsResultVersion> findLatestResultVersion(UUID eventId) {
    if (eventId == null) {
      return Optional.empty();
    }
    return jdbcTemplate.query(
        """
                SELECT
                    version_id,
                    event_id,
                    competition_id,
                    edition_id,
                    version_no,
                    status,
                    source_reference,
                    reason,
                    recorded_by_identity_id,
                    confirmed_by_identity_id,
                    recorded_at,
                    confirmed_at,
                    supersedes_version_id,
                    base_lock_version
                FROM public.sports_result_version
                WHERE event_id = ?
                ORDER BY version_no DESC
                LIMIT 1
                """,
        rs -> {
          if (!rs.next()) {
            return Optional.empty();
          }
          UUID versionId = rs.getObject("version_id", UUID.class);
          List<SportsResultScoreItem> scores = findScoresForVersion(versionId);
          return Optional.of(mapResultVersion(rs, scores));
        },
        eventId);
  }

  Optional<SportsResultVersion> findConfirmedResultVersion(UUID eventId) {
    if (eventId == null) {
      return Optional.empty();
    }
    return jdbcTemplate.query(
        """
                SELECT
                    rv.version_id,
                    rv.event_id,
                    rv.competition_id,
                    rv.edition_id,
                    rv.version_no,
                    rv.status,
                    rv.source_reference,
                    rv.reason,
                    rv.recorded_by_identity_id,
                    rv.confirmed_by_identity_id,
                    rv.recorded_at,
                    rv.confirmed_at,
                    rv.supersedes_version_id,
                    rv.base_lock_version
                FROM public.sports_event ev
                JOIN public.sports_result_version rv ON rv.version_id = ev.confirmed_version_id
                WHERE ev.event_id = ?
                """,
        rs -> {
          if (!rs.next()) {
            return Optional.empty();
          }
          UUID versionId = rs.getObject("version_id", UUID.class);
          List<SportsResultScoreItem> scores = findScoresForVersion(versionId);
          return Optional.of(mapResultVersion(rs, scores));
        },
        eventId);
  }

  private List<SportsResultScoreItem> findScoresForVersion(UUID versionId) {
    return jdbcTemplate.query(
        """
                SELECT s.slot_no, s.score, s.display_label, s.team_id, t.name AS team_name
                FROM public.sports_result_version_slot s
                LEFT JOIN public.sports_team t ON t.team_id = s.team_id
                WHERE s.version_id = ?
                ORDER BY s.slot_no ASC
                """,
        (rs, rowNum) ->
            new SportsResultScoreItem(
                rs.getInt("slot_no"),
                rs.getString("display_label"),
                rs.getInt("score"),
                rs.getObject("team_id", UUID.class),
                rs.getString("team_name")),
        versionId);
  }

  private Map<UUID, List<SportsResultScoreItem>> findScoresForEvent(UUID eventId) {
    return jdbcTemplate.query(
        """
                SELECT s.version_id, s.slot_no, s.score, s.display_label, s.team_id, t.name AS team_name
                FROM public.sports_result_version_slot s
                JOIN public.sports_result_version rv ON rv.version_id = s.version_id
                LEFT JOIN public.sports_team t ON t.team_id = s.team_id
                WHERE rv.event_id = ?
                ORDER BY s.version_id, s.slot_no ASC
                """,
        rs -> {
          Map<UUID, List<SportsResultScoreItem>> map = new java.util.HashMap<>();
          while (rs.next()) {
            UUID vId = rs.getObject("version_id", UUID.class);
            SportsResultScoreItem item =
                new SportsResultScoreItem(
                    rs.getInt("slot_no"),
                    rs.getString("display_label"),
                    rs.getInt("score"),
                    rs.getObject("team_id", UUID.class),
                    rs.getString("team_name"));
            map.computeIfAbsent(vId, k -> new ArrayList<>()).add(item);
          }
          return map;
        },
        eventId);
  }

  long nextResultVersionNo(UUID eventId) {
    Long max =
        jdbcTemplate.queryForObject(
            "SELECT max(version_no) FROM public.sports_result_version WHERE event_id = ?",
            Long.class,
            eventId);
    return max == null ? 1L : max + 1L;
  }

  SportsResultVersion insertResultVersion(
      UUID eventId,
      UUID competitionId,
      UUID editionId,
      long versionNo,
      String status,
      String sourceReference,
      String reason,
      UUID recordedByIdentityId,
      UUID confirmedByIdentityId,
      Instant recordedAt,
      Instant confirmedAt,
      UUID supersedesVersionId,
      long baseLockVersion,
      List<SportsResultScoreItem> scores) {
    UUID versionId = UUID.randomUUID();
    jdbcTemplate.update(
        """
                INSERT INTO public.sports_result_version (
                    version_id, event_id, competition_id, edition_id, version_no,
                    status, source_reference, reason, recorded_by_identity_id,
                    confirmed_by_identity_id, recorded_at, confirmed_at,
                    supersedes_version_id, base_lock_version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
        versionId,
        eventId,
        competitionId,
        editionId,
        versionNo,
        status,
        sourceReference.trim(),
        reason == null ? null : reason.trim(),
        recordedByIdentityId,
        confirmedByIdentityId,
        Timestamp.from(recordedAt),
        confirmedAt == null ? null : Timestamp.from(confirmedAt),
        supersedesVersionId,
        baseLockVersion);

    for (SportsResultScoreItem score : scores) {
      jdbcTemplate.update(
          """
                    INSERT INTO public.sports_result_version_slot (
                        version_id, slot_no, score, display_label, team_id
                    ) VALUES (?, ?, ?, ?, ?)
                    """,
          versionId,
          score.slotNo(),
          score.score(),
          score.displayLabel(),
          score.teamId());
    }

    return new SportsResultVersion(
        versionId,
        eventId,
        competitionId,
        editionId,
        versionNo,
        status,
        sourceReference.trim(),
        reason == null ? null : reason.trim(),
        scores,
        recordedByIdentityId,
        confirmedByIdentityId,
        recordedAt,
        confirmedAt,
        supersedesVersionId,
        baseLockVersion);
  }

  int updateEventConfirmedVersion(UUID eventId, UUID versionId, long expectedLockVersion) {
    return jdbcTemplate.update(
        """
                UPDATE public.sports_event
                SET
                    confirmed_version_id = ?,
                    lock_version = lock_version + 1,
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE event_id = ?
                  AND lock_version = ?
                """,
        versionId,
        eventId,
        expectedLockVersion);
  }

  void insertSportingOutcomeAuthority(
      EventScope event, SportsResultVersion version, UUID supersedesFactId, Instant knownAt) {
    UUID factId = UUID.randomUUID();
    UUID currentFact =
        jdbcTemplate.query(
            "SELECT fact_id FROM public.sports_outcome_head WHERE event_id = ?",
            rs -> rs.next() ? rs.getObject("fact_id", UUID.class) : null,
            event.eventId());
    supersedesFactId = supersedesFactId == null ? currentFact : supersedesFactId;
    Instant sportTime =
        jdbcTemplate.queryForObject(
            "SELECT scheduled_at FROM public.sports_event WHERE event_id = ?",
            (rs, rowNum) -> rs.getTimestamp(1).toInstant(),
            event.eventId());
    jdbcTemplate.update(
        "INSERT INTO public.sports_outcome_fact (fact_id,event_id,competition_id,edition_id,version_no,outcome_kind,source_reference,reason,recorded_by_identity_id,sport_time,known_at,supersedes_fact_id) VALUES (?,?,?,?,?,'SPORTING_RESULT',?,?,?,?,?,?)",
        factId,
        event.eventId(),
        event.competitionId(),
        event.editionId(),
        version.versionNo(),
        version.sourceReference(),
        version.reason(),
        version.recordedByIdentityId(),
        Timestamp.from(sportTime),
        Timestamp.from(knownAt),
        supersedesFactId);
    jdbcTemplate.update(
        "INSERT INTO public.sports_outcome_sporting_payload (fact_id,event_id,confirmed_version_id,confirmed_version_status,confirmed_at) VALUES (?,?,?,?,?)",
        factId,
        event.eventId(),
        version.versionId(),
        version.status(),
        Timestamp.from(version.confirmedAt()));
    jdbcTemplate.update(
        "INSERT INTO public.sports_outcome_head (event_id,fact_id,lock_version) VALUES (?,?,?) ON CONFLICT (event_id) DO UPDATE SET fact_id = EXCLUDED.fact_id, lock_version = EXCLUDED.lock_version, updated_at = CURRENT_TIMESTAMP(6)",
        event.eventId(),
        factId,
        event.lockVersion() + 1);
  }

  int updateEventLockVersion(UUID eventId, long expectedLockVersion) {
    return jdbcTemplate.update(
        """
                UPDATE public.sports_event
                SET
                    lock_version = lock_version + 1,
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE event_id = ?
                  AND lock_version = ?
                """,
        eventId,
        expectedLockVersion);
  }

  private static SportsResultVersion mapResultVersion(
      ResultSet rs, List<SportsResultScoreItem> scores) throws SQLException {
    Timestamp recordedTs = rs.getTimestamp("recorded_at");
    Timestamp confirmedTs = rs.getTimestamp("confirmed_at");
    return new SportsResultVersion(
        rs.getObject("version_id", UUID.class),
        rs.getObject("event_id", UUID.class),
        rs.getObject("competition_id", UUID.class),
        rs.getObject("edition_id", UUID.class),
        rs.getLong("version_no"),
        rs.getString("status"),
        rs.getString("source_reference"),
        rs.getString("reason"),
        scores,
        rs.getObject("recorded_by_identity_id", UUID.class),
        rs.getObject("confirmed_by_identity_id", UUID.class),
        recordedTs == null ? null : recordedTs.toInstant(),
        confirmedTs == null ? null : confirmedTs.toInstant(),
        rs.getObject("supersedes_version_id", UUID.class),
        rs.getLong("base_lock_version"));
  }
}
