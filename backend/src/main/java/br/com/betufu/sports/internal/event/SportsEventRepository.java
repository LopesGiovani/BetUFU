package br.com.betufu.sports.internal.event;

import br.com.betufu.sports.internal.event.SportsEventModels.EventFormat;
import br.com.betufu.sports.internal.event.SportsEventModels.SportsEventCreateRequest;
import br.com.betufu.sports.internal.event.SportsEventModels.SportsEventDetail;
import br.com.betufu.sports.internal.event.SportsEventModels.SportsEventLifecycleEntry;
import br.com.betufu.sports.internal.event.SportsEventModels.SportsEventSlotItem;
import br.com.betufu.sports.internal.event.SportsEventModels.SportsEventSlotRequest;
import br.com.betufu.sports.internal.event.SportsEventModels.SportsEventUpdateRequest;
import br.com.betufu.sports.internal.event.SportsEventModels.VenueState;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
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
class SportsEventRepository {

  private final JdbcTemplate jdbcTemplate;

  SportsEventRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
  }

  record EditionScope(
      UUID competitionId, UUID editionId, String competitionName, String editionName) {}

  record EventScope(
      UUID eventId,
      UUID competitionId,
      UUID editionId,
      String status,
      String sourceReference,
      long lockVersion,
      UUID confirmedVersionId) {}

  record CatalogScope(
      UUID competitionId,
      UUID editionId,
      String competitionName,
      String editionName,
      String sportName,
      String categoryName,
      String phaseName,
      String venueName,
      String venueSpaceName) {}

  Optional<EditionScope> findEditionScope(UUID editionId) {
    if (editionId == null) {
      return Optional.empty();
    }
    return jdbcTemplate.query(
        """
                SELECT e.competition_id, e.edition_id, c.name AS competition_name, e.name AS edition_name
                FROM public.sports_edition e
                JOIN public.sports_competition c ON c.competition_id = e.competition_id
                WHERE e.edition_id = ?
                """,
        rs ->
            rs.next()
                ? Optional.of(
                    new EditionScope(
                        rs.getObject("competition_id", UUID.class),
                        rs.getObject("edition_id", UUID.class),
                        rs.getString("competition_name"),
                        rs.getString("edition_name")))
                : Optional.empty(),
        editionId);
  }

  Optional<CatalogScope> findCatalogScope(
      UUID editionId,
      UUID sportId,
      UUID categoryId,
      UUID phaseId,
      UUID venueId,
      UUID venueSpaceId) {
    return jdbcTemplate.query(
        """
                SELECT
                    e.competition_id,
                    e.edition_id,
                    c.name AS competition_name,
                    e.name AS edition_name,
                    s.name AS sport_name,
                    cat.name AS category_name,
                    p.name AS phase_name,
                    v.name AS venue_name,
                    vs.name AS venue_space_name
                FROM public.sports_edition e
                JOIN public.sports_competition c ON c.competition_id = e.competition_id
                LEFT JOIN public.sports_sport s ON s.sport_id = ?
                LEFT JOIN public.sports_category cat ON cat.category_id = ? AND cat.edition_id = e.edition_id
                LEFT JOIN public.sports_phase p ON p.phase_id = ? AND p.edition_id = e.edition_id
                LEFT JOIN public.sports_venue v ON v.venue_id = ? AND v.competition_id = e.competition_id
                LEFT JOIN public.sports_venue_space vs ON vs.venue_space_id = ? AND vs.venue_id = v.venue_id
                WHERE e.edition_id = ?
                """,
        rs -> {
          if (!rs.next()) {
            return Optional.empty();
          }
          if (sportId != null && rs.getString("sport_name") == null) {
            return Optional.empty();
          }
          if (categoryId != null && rs.getString("category_name") == null) {
            return Optional.empty();
          }
          if (phaseId != null && rs.getString("phase_name") == null) {
            return Optional.empty();
          }
          if (venueId != null && rs.getString("venue_name") == null) {
            return Optional.empty();
          }
          if (venueSpaceId != null && rs.getString("venue_space_name") == null) {
            return Optional.empty();
          }
          return Optional.of(
              new CatalogScope(
                  rs.getObject("competition_id", UUID.class),
                  rs.getObject("edition_id", UUID.class),
                  rs.getString("competition_name"),
                  rs.getString("edition_name"),
                  rs.getString("sport_name"),
                  rs.getString("category_name"),
                  rs.getString("phase_name"),
                  rs.getString("venue_name"),
                  rs.getString("venue_space_name")));
        },
        sportId,
        categoryId,
        phaseId,
        venueId,
        venueSpaceId,
        editionId);
  }

  Optional<EventScope> findEventScope(UUID eventId) {
    if (eventId == null) {
      return Optional.empty();
    }
    return jdbcTemplate.query(
        """
                SELECT event_id, competition_id, edition_id, status, source_reference, lock_version, confirmed_version_id
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
                        rs.getString("status"),
                        rs.getString("source_reference"),
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
                SELECT event_id, competition_id, edition_id, status, source_reference, lock_version, confirmed_version_id
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
                        rs.getString("status"),
                        rs.getString("source_reference"),
                        rs.getLong("lock_version"),
                        rs.getObject("confirmed_version_id", UUID.class)))
                : Optional.empty(),
        eventId);
  }

  Optional<SportsEventDetail> findEventDetail(UUID eventId) {
    if (eventId == null) {
      return Optional.empty();
    }
    List<SportsEventSlotItem> slots = findEventSlots(eventId);
    return jdbcTemplate.query(
        """
                SELECT
                    ev.event_id,
                    ev.competition_id,
                    c.name AS competition_name,
                    ev.edition_id,
                    ed.name AS edition_name,
                    ev.title,
                    ev.event_format,
                    ev.status,
                    ev.scheduled_at,
                    ev.zone_id,
                    ev.venue_state,
                    ev.source_reference,
                    ev.lock_version,
                    ev.sport_id,
                    s.name AS sport_name,
                    ev.category_id,
                    cat.name AS category_name,
                    ev.phase_id,
                    p.name AS phase_name,
                    ev.venue_id,
                    v.name AS venue_name,
                    ev.venue_space_id,
                    vs.name AS venue_space_name,
                    ev.confirmed_version_id,
                    ev.status_reason,
                    ev.created_at,
                    ev.updated_at
                FROM public.sports_event ev
                JOIN public.sports_competition c ON c.competition_id = ev.competition_id
                JOIN public.sports_edition ed ON ed.edition_id = ev.edition_id
                LEFT JOIN public.sports_sport s ON s.sport_id = ev.sport_id
                LEFT JOIN public.sports_category cat ON cat.category_id = ev.category_id
                LEFT JOIN public.sports_phase p ON p.phase_id = ev.phase_id
                LEFT JOIN public.sports_venue v ON v.venue_id = ev.venue_id
                LEFT JOIN public.sports_venue_space vs ON vs.venue_space_id = ev.venue_space_id
                WHERE ev.event_id = ?
                """,
        rs -> rs.next() ? Optional.of(mapEventDetail(rs, slots)) : Optional.empty(),
        eventId);
  }

  List<SportsEventLifecycleEntry> findLifecycle(UUID eventId, int limit) {
    if (eventId == null || limit < 1) {
      return List.of();
    }
    return jdbcTemplate.query(
        """
                SELECT lifecycle_id, event_id, previous_status, new_status, actor_identity_id,
                       source_reference, reason, base_lock_version, lock_version, sequence_no,
                       occurred_at
                FROM public.sports_event_lifecycle_history
                WHERE event_id = ?
                ORDER BY sequence_no ASC, occurred_at ASC, lifecycle_id ASC
                LIMIT ?
                """,
        (rs, rowNum) ->
            new SportsEventLifecycleEntry(
                rs.getObject("lifecycle_id", UUID.class),
                rs.getObject("event_id", UUID.class),
                rs.getString("previous_status"),
                rs.getString("new_status"),
                rs.getObject("actor_identity_id", UUID.class),
                rs.getString("source_reference"),
                rs.getString("reason"),
                rs.getLong("base_lock_version"),
                rs.getLong("lock_version"),
                rs.getLong("sequence_no"),
                rs.getTimestamp("occurred_at").toInstant()),
        eventId,
        limit);
  }

  List<SportsEventDetail> listEventsByEdition(UUID editionId) {
    if (editionId == null) {
      return List.of();
    }
    Map<UUID, List<SportsEventSlotItem>> slotsByEvent = findAllSlotsForEdition(editionId);
    return jdbcTemplate.query(
        """
                SELECT
                    ev.event_id,
                    ev.competition_id,
                    c.name AS competition_name,
                    ev.edition_id,
                    ed.name AS edition_name,
                    ev.title,
                    ev.event_format,
                    ev.status,
                    ev.scheduled_at,
                    ev.zone_id,
                    ev.venue_state,
                    ev.source_reference,
                    ev.lock_version,
                    ev.sport_id,
                    s.name AS sport_name,
                    ev.category_id,
                    cat.name AS category_name,
                    ev.phase_id,
                    p.name AS phase_name,
                    ev.venue_id,
                    v.name AS venue_name,
                    ev.venue_space_id,
                    vs.name AS venue_space_name,
                    ev.confirmed_version_id,
                    ev.status_reason,
                    ev.created_at,
                    ev.updated_at
                FROM public.sports_event ev
                JOIN public.sports_competition c ON c.competition_id = ev.competition_id
                JOIN public.sports_edition ed ON ed.edition_id = ev.edition_id
                LEFT JOIN public.sports_sport s ON s.sport_id = ev.sport_id
                LEFT JOIN public.sports_category cat ON cat.category_id = ev.category_id
                LEFT JOIN public.sports_phase p ON p.phase_id = ev.phase_id
                LEFT JOIN public.sports_venue v ON v.venue_id = ev.venue_id
                LEFT JOIN public.sports_venue_space vs ON vs.venue_space_id = ev.venue_space_id
                WHERE ev.edition_id = ?
                ORDER BY ev.scheduled_at ASC, ev.event_id ASC
                """,
        (rs, rowNum) -> {
          UUID eventId = rs.getObject("event_id", UUID.class);
          List<SportsEventSlotItem> slots = slotsByEvent.getOrDefault(eventId, List.of());
          return mapEventDetail(rs, slots);
        },
        editionId);
  }

  List<SportsEventSlotItem> findEventSlots(UUID eventId) {
    return jdbcTemplate.query(
        """
                SELECT s.slot_no, s.display_label, s.team_id, t.name AS team_name
                FROM public.sports_event_slot s
                LEFT JOIN public.sports_team t ON t.team_id = s.team_id
                WHERE s.event_id = ?
                ORDER BY s.slot_no ASC
                """,
        (rs, rowNum) ->
            new SportsEventSlotItem(
                rs.getInt("slot_no"),
                rs.getString("display_label"),
                rs.getObject("team_id", UUID.class),
                rs.getString("team_name")),
        eventId);
  }

  private Map<UUID, List<SportsEventSlotItem>> findAllSlotsForEdition(UUID editionId) {
    return jdbcTemplate.query(
        """
                SELECT s.event_id, s.slot_no, s.display_label, s.team_id, t.name AS team_name
                FROM public.sports_event_slot s
                JOIN public.sports_event ev ON ev.event_id = s.event_id
                LEFT JOIN public.sports_team t ON t.team_id = s.team_id
                WHERE ev.edition_id = ?
                ORDER BY s.event_id, s.slot_no ASC
                """,
        rs -> {
          Map<UUID, List<SportsEventSlotItem>> map = new java.util.HashMap<>();
          while (rs.next()) {
            UUID evId = rs.getObject("event_id", UUID.class);
            SportsEventSlotItem item =
                new SportsEventSlotItem(
                    rs.getInt("slot_no"),
                    rs.getString("display_label"),
                    rs.getObject("team_id", UUID.class),
                    rs.getString("team_name"));
            map.computeIfAbsent(evId, k -> new ArrayList<>()).add(item);
          }
          return map;
        },
        editionId);
  }

  SportsEventDetail createEvent(
      UUID competitionId,
      UUID editionId,
      SportsEventCreateRequest request,
      CatalogScope catalog,
      UUID actorIdentityId) {
    UUID eventId = UUID.randomUUID();
    jdbcTemplate.update(
        """
                INSERT INTO public.sports_event (
                    event_id, competition_id, edition_id, title, event_format, status,
                    scheduled_at, zone_id, venue_state, source_reference, lock_version,
                    sport_id, category_id, phase_id, venue_id, venue_space_id
                ) VALUES (?, ?, ?, ?, ?, 'RASCUNHO', ?, ?, ?, ?, 0, ?, ?, ?, ?, ?)
                """,
        eventId,
        competitionId,
        editionId,
        request.title().trim(),
        request.format().name(),
        Timestamp.from(request.scheduledAt()),
        request.zoneId().trim(),
        request.venueState().name(),
        request.sourceReference().trim(),
        request.sportId(),
        request.categoryId(),
        request.phaseId(),
        request.venueId(),
        request.venueSpaceId());

    for (SportsEventSlotRequest slot : request.slots()) {
      jdbcTemplate.update(
          """
                    INSERT INTO public.sports_event_slot (event_id, slot_no, display_label, team_id)
                    VALUES (?, ?, ?, ?)
                    """,
          eventId,
          slot.slotNo(),
          slot.displayLabel().trim(),
          slot.teamId());
    }

    insertLifecycle(
        eventId,
        competitionId,
        editionId,
        null,
        "RASCUNHO",
        actorIdentityId,
        request.sourceReference().trim(),
        null,
        0,
        0);

    return findEventDetail(eventId).orElseThrow();
  }

  void insertLifecycle(
      UUID eventId,
      UUID competitionId,
      UUID editionId,
      String previousStatus,
      String newStatus,
      UUID actorIdentityId,
      String sourceReference,
      String reason,
      long baseLockVersion,
      long lockVersion) {
    Long maxSequence =
        jdbcTemplate.queryForObject(
            "SELECT max(sequence_no) FROM public.sports_event_lifecycle_history WHERE event_id = ?",
            Long.class,
            eventId);
    long sequenceNo = maxSequence == null ? 1L : maxSequence + 1L;
    jdbcTemplate.update(
        """
                INSERT INTO public.sports_event_lifecycle_history (
                    lifecycle_id, event_id, competition_id, edition_id, sequence_no,
                    previous_status, new_status, actor_identity_id, source_reference, reason,
                    base_lock_version, lock_version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
        UUID.randomUUID(),
        eventId,
        competitionId,
        editionId,
        sequenceNo,
        previousStatus,
        newStatus,
        actorIdentityId,
        sourceReference.trim(),
        reason == null ? null : reason.trim(),
        baseLockVersion,
        lockVersion);
  }

  int updateEventDraft(UUID eventId, SportsEventUpdateRequest request, long expectedLockVersion) {
    int updated =
        jdbcTemplate.update(
            """
                UPDATE public.sports_event
                SET
                    title = ?,
                    event_format = ?,
                    scheduled_at = ?,
                    zone_id = ?,
                    venue_state = ?,
                    source_reference = ?,
                    sport_id = ?,
                    category_id = ?,
                    phase_id = ?,
                    venue_id = ?,
                    venue_space_id = ?,
                    lock_version = lock_version + 1,
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE event_id = ?
                  AND lock_version = ?
                  AND status = 'RASCUNHO'
                """,
            request.title().trim(),
            request.format().name(),
            Timestamp.from(request.scheduledAt()),
            request.zoneId().trim(),
            request.venueState().name(),
            request.sourceReference().trim(),
            request.sportId(),
            request.categoryId(),
            request.phaseId(),
            request.venueId(),
            request.venueSpaceId(),
            eventId,
            expectedLockVersion);

    if (updated > 0) {
      jdbcTemplate.update("DELETE FROM public.sports_event_slot WHERE event_id = ?", eventId);
      for (SportsEventSlotRequest slot : request.slots()) {
        jdbcTemplate.update(
            """
                        INSERT INTO public.sports_event_slot (event_id, slot_no, display_label, team_id)
                        VALUES (?, ?, ?, ?)
                        """,
            eventId,
            slot.slotNo(),
            slot.displayLabel().trim(),
            slot.teamId());
      }
    }
    return updated;
  }

  int updateEventStatus(
      UUID eventId, String newStatus, String statusReason, long expectedLockVersion) {
    return jdbcTemplate.update(
        """
                UPDATE public.sports_event
                SET
                    status = ?,
                    status_reason = ?,
                    lock_version = lock_version + 1,
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE event_id = ?
                  AND lock_version = ?
                """,
        newStatus,
        statusReason == null ? null : statusReason.trim(),
        eventId,
        expectedLockVersion);
  }

  int postponeEvent(
      UUID eventId,
      Instant scheduledAt,
      String zoneId,
      String statusReason,
      long expectedLockVersion) {
    return jdbcTemplate.update(
        """
                UPDATE public.sports_event
                SET
                    status = 'ADIADO',
                    scheduled_at = ?,
                    zone_id = ?,
                    status_reason = ?,
                    lock_version = lock_version + 1,
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE event_id = ?
                  AND lock_version = ?
                """,
        Timestamp.from(scheduledAt),
        zoneId.trim(),
        statusReason == null ? null : statusReason.trim(),
        eventId,
        expectedLockVersion);
  }

  long nextPublicationVersion(UUID eventId) {
    Long max =
        jdbcTemplate.queryForObject(
            "SELECT max(version_no) FROM public.sports_event_publication WHERE event_id = ?",
            Long.class,
            eventId);
    return max == null ? 1L : max + 1L;
  }

  Optional<UUID> latestPublicationId(UUID eventId) {
    return jdbcTemplate.query(
        """
                SELECT publication_id FROM public.sports_event_publication
                WHERE event_id = ?
                ORDER BY version_no DESC
                LIMIT 1
                """,
        rs ->
            rs.next() ? Optional.of(rs.getObject("publication_id", UUID.class)) : Optional.empty(),
        eventId);
  }

  void insertEventPublication(
      SportsEventDetail event,
      List<SportsEventSlotItem> slots,
      UUID publishedByIdentityId,
      long versionNo,
      UUID supersedesId,
      long baseLockVersion) {
    UUID publicationId = UUID.randomUUID();
    jdbcTemplate.update(
        """
                INSERT INTO public.sports_event_publication (
                    publication_id, event_id, competition_id, edition_id, version_no,
                    competition_name, edition_name, title, event_format, scheduled_at,
                    zone_id, venue_state, source_reference, published_by_identity_id,
                    published_at, supersedes_publication_id, base_lock_version,
                    sport_id, sport_name, category_id, category_name, phase_id, phase_name,
                    venue_id, venue_name, venue_space_id, venue_space_name
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP(6), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
        publicationId,
        event.eventId(),
        event.competitionId(),
        event.editionId(),
        versionNo,
        event.competitionName(),
        event.editionName(),
        event.title(),
        event.format().name(),
        Timestamp.from(event.scheduledAt()),
        event.zoneId(),
        event.venueState().name(),
        event.sourceReference(),
        publishedByIdentityId,
        supersedesId,
        baseLockVersion,
        event.sportId(),
        event.sportName(),
        event.categoryId(),
        event.categoryName(),
        event.phaseId(),
        event.phaseName(),
        event.venueId(),
        event.venueName(),
        event.venueSpaceId(),
        event.venueSpaceName());

    for (SportsEventSlotItem slot : slots) {
      jdbcTemplate.update(
          """
                    INSERT INTO public.sports_event_publication_slot (
                        publication_id, slot_no, display_label, team_id, team_name
                    ) VALUES (?, ?, ?, ?, ?)
                    """,
          publicationId,
          slot.slotNo(),
          slot.displayLabel(),
          slot.teamId(),
          slot.teamName());
    }
  }

  boolean teamsExist(List<UUID> teamIds) {
    if (teamIds == null || teamIds.isEmpty()) {
      return true;
    }
    String inClause = String.join(",", Collections.nCopies(teamIds.size(), "?"));
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.sports_team WHERE team_id IN (" + inClause + ")",
            Integer.class,
            teamIds.toArray());
    return count != null && count == teamIds.size();
  }

  private static SportsEventDetail mapEventDetail(ResultSet rs, List<SportsEventSlotItem> slots)
      throws SQLException {
    return new SportsEventDetail(
        rs.getObject("event_id", UUID.class),
        rs.getObject("competition_id", UUID.class),
        rs.getString("competition_name"),
        rs.getObject("edition_id", UUID.class),
        rs.getString("edition_name"),
        rs.getString("title"),
        EventFormat.valueOf(rs.getString("event_format")),
        rs.getString("status"),
        rs.getTimestamp("scheduled_at").toInstant(),
        rs.getString("zone_id"),
        VenueState.valueOf(rs.getString("venue_state")),
        rs.getString("source_reference"),
        rs.getLong("lock_version"),
        rs.getObject("sport_id", UUID.class),
        rs.getString("sport_name"),
        rs.getObject("category_id", UUID.class),
        rs.getString("category_name"),
        rs.getObject("phase_id", UUID.class),
        rs.getString("phase_name"),
        rs.getObject("venue_id", UUID.class),
        rs.getString("venue_name"),
        rs.getObject("venue_space_id", UUID.class),
        rs.getString("venue_space_name"),
        slots,
        rs.getObject("confirmed_version_id", UUID.class),
        rs.getString("status_reason"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }
}
