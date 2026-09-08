package br.com.betufu.sports.internal.outcome;

import br.com.betufu.sports.internal.event.SportsEventModels.EventFormat;
import br.com.betufu.sports.internal.outcome.SportsOutcomeModels.OutcomeFact;
import br.com.betufu.sports.internal.outcome.SportsOutcomeModels.OutcomeKind;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("runtime | integrated")
class SportsOutcomeRepository {
  private final JdbcTemplate jdbcTemplate;

  SportsOutcomeRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
  }

  record EventScope(UUID eventId, UUID competitionId, UUID editionId, EventFormat format,
      String status, Instant sportTime, long lockVersion) {}

  Optional<EventScope> findEventForUpdate(UUID eventId) {
    return jdbcTemplate.query(
        "SELECT event_id, competition_id, edition_id, event_format, status, scheduled_at, lock_version FROM public.sports_event WHERE event_id = ? FOR UPDATE",
        rs -> rs.next() ? Optional.of(new EventScope(
            rs.getObject("event_id", UUID.class), rs.getObject("competition_id", UUID.class),
            rs.getObject("edition_id", UUID.class), EventFormat.valueOf(rs.getString("event_format")),
            rs.getString("status"), rs.getTimestamp("scheduled_at").toInstant(), rs.getLong("lock_version")))
            : Optional.empty(), eventId);
  }

  Optional<OutcomeFact> findHead(UUID eventId) {
    return jdbcTemplate.query(
        "SELECT f.fact_id, f.event_id, f.competition_id, f.edition_id, f.version_no, f.outcome_kind, f.source_reference, f.reason, f.recorded_by_identity_id, f.sport_time, f.known_at, f.supersedes_fact_id, p.winner_slot_no FROM public.sports_outcome_head h JOIN public.sports_outcome_fact f ON f.event_id = h.event_id AND f.fact_id = h.fact_id LEFT JOIN public.sports_outcome_administrative_payload p ON p.event_id = f.event_id AND p.fact_id = f.fact_id WHERE h.event_id = ?",
        rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty(), eventId);
  }

  long nextVersion(UUID eventId) {
    Long max = jdbcTemplate.queryForObject("SELECT max(version_no) FROM public.sports_outcome_fact WHERE event_id = ?", Long.class, eventId);
    return max == null ? 1L : max + 1L;
  }

  OutcomeFact insertAdministrativeFact(EventScope event, long versionNo, OutcomeKind kind,
      String sourceReference, String reason, UUID actorId, Instant now, UUID supersedes, Integer winnerSlotNo) {
    UUID factId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_outcome_fact (fact_id,event_id,competition_id,edition_id,version_no,outcome_kind,source_reference,reason,recorded_by_identity_id,sport_time,known_at,supersedes_fact_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
        factId, event.eventId(), event.competitionId(), event.editionId(), versionNo, kind.name(),
        sourceReference.trim(), reason.trim(), actorId, Timestamp.from(event.sportTime()), Timestamp.from(now), supersedes);
    jdbcTemplate.update(
        "INSERT INTO public.sports_outcome_administrative_payload (fact_id,event_id,administrative_reason,winner_slot_no) VALUES (?,?,?,?)",
        factId, event.eventId(), reason.trim(), winnerSlotNo);
    jdbcTemplate.update(
        "INSERT INTO public.sports_outcome_head (event_id,fact_id,lock_version) VALUES (?,?,?) ON CONFLICT (event_id) DO UPDATE SET fact_id = EXCLUDED.fact_id, lock_version = EXCLUDED.lock_version, updated_at = CURRENT_TIMESTAMP(6)",
        event.eventId(), factId, event.lockVersion() + 1);
    return new OutcomeFact(factId, event.eventId(), event.competitionId(), event.editionId(), versionNo,
        kind, sourceReference.trim(), reason.trim(), actorId, event.sportTime(), now, supersedes, winnerSlotNo);
  }

  int advanceEvent(UUID eventId, long expectedLockVersion) {
    return jdbcTemplate.update("UPDATE public.sports_event SET confirmed_version_id = NULL, lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP(6) WHERE event_id = ? AND lock_version = ?", eventId, expectedLockVersion);
  }

  private static OutcomeFact map(java.sql.ResultSet rs) throws java.sql.SQLException {
    return new OutcomeFact(rs.getObject("fact_id", UUID.class), rs.getObject("event_id", UUID.class),
        rs.getObject("competition_id", UUID.class), rs.getObject("edition_id", UUID.class), rs.getLong("version_no"),
        OutcomeKind.valueOf(rs.getString("outcome_kind")), rs.getString("source_reference"), rs.getString("reason"),
        rs.getObject("recorded_by_identity_id", UUID.class), rs.getTimestamp("sport_time").toInstant(),
        rs.getTimestamp("known_at").toInstant(), rs.getObject("supersedes_fact_id", UUID.class),
        (Integer) rs.getObject("winner_slot_no"));
  }
}
