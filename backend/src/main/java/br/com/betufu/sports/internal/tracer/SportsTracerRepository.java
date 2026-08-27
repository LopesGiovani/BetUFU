package br.com.betufu.sports.internal.tracer;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.betufu.sports.CompetitionScopeCatalog;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.CompetitionCreateRequest;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.CompetitionResponse;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.EditionCreateRequest;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.EditionResponse;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.EventCreateRequest;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.EventFormat;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.EventResponse;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.EventSlotRequest;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.PublicEventResponse;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.PublicSlotResponse;
import br.com.betufu.sports.internal.tracer.SportsTracerModels.VenueState;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("runtime | integrated")
class SportsTracerRepository implements CompetitionScopeCatalog {

    private final JdbcTemplate jdbcTemplate;

    SportsTracerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean existsCompetition(UUID competitionId) {
        if (competitionId == null) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.sports_competition WHERE competition_id = ?",
                Integer.class,
                competitionId);
        return count != null && count == 1;
    }

    CompetitionResponse createCompetition(CompetitionCreateRequest request) {
        UUID competitionId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO public.sports_competition (competition_id, name, slug) VALUES (?, ?, ?)",
                competitionId,
                request.name().trim(),
                request.slug());
        return new CompetitionResponse(
                competitionId,
                request.name().trim(),
                request.slug(),
                "ATIVA",
                0);
    }

    EditionResponse createEdition(UUID competitionId, EditionCreateRequest request) {
        UUID editionId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO public.sports_edition "
                        + "(edition_id, competition_id, name, slug, start_date, end_date) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                editionId,
                competitionId,
                request.name().trim(),
                request.slug(),
                sqlDate(request.startDate()),
                sqlDate(request.endDate()));
        return new EditionResponse(
                editionId,
                competitionId,
                request.name().trim(),
                request.slug(),
                request.startDate(),
                request.endDate(),
                "ATIVA",
                0);
    }

    Optional<EditionScope> findEditionScope(UUID editionId) {
        return jdbcTemplate.query(
                        "SELECT e.edition_id, e.competition_id, e.name AS edition_name, "
                                + "c.name AS competition_name "
                                + "FROM public.sports_edition e "
                                + "JOIN public.sports_competition c ON c.competition_id = e.competition_id "
                                + "WHERE e.edition_id = ?",
                        (rs, rowNum) -> new EditionScope(
                                rs.getObject("edition_id", UUID.class),
                                rs.getObject("competition_id", UUID.class),
                                rs.getString("competition_name"),
                                rs.getString("edition_name")),
                        editionId)
                .stream()
                .findFirst();
    }

    EventResponse createEvent(EditionScope edition, EventCreateRequest request) {
        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO public.sports_event "
                        + "(event_id, competition_id, edition_id, title, event_format, scheduled_at, "
                        + "zone_id, venue_state, source_reference) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                eventId,
                edition.competitionId(),
                edition.editionId(),
                request.title().trim(),
                request.format().name(),
                Timestamp.from(request.scheduledAt()),
                request.zoneId(),
                request.venueState().name(),
                request.sourceReference().trim());

        for (EventSlotRequest slot : request.slots()) {
            jdbcTemplate.update(
                    "INSERT INTO public.sports_event_slot (event_id, slot_no, display_label) VALUES (?, ?, ?)",
                    eventId,
                    slot.slotNo(),
                    slot.displayLabel().trim());
        }

        return new EventResponse(
                eventId,
                edition.competitionId(),
                edition.editionId(),
                request.title().trim(),
                request.format(),
                "RASCUNHO",
                request.scheduledAt(),
                request.zoneId(),
                request.venueState(),
                0);
    }

    Optional<EventWork> findEventForUpdate(UUID eventId) {
        return jdbcTemplate.query(
                        "SELECT ev.event_id, ev.competition_id, ev.edition_id, "
                                + "c.name AS competition_name, ed.name AS edition_name, ev.title, "
                                + "ev.event_format, ev.status, ev.scheduled_at, ev.zone_id, "
                                + "ev.venue_state, ev.source_reference, ev.lock_version "
                                + "FROM public.sports_event ev "
                                + "JOIN public.sports_competition c ON c.competition_id = ev.competition_id "
                                + "JOIN public.sports_edition ed ON ed.edition_id = ev.edition_id "
                                + "WHERE ev.event_id = ? FOR UPDATE OF ev",
                        (rs, rowNum) -> new EventWork(
                                rs.getObject("event_id", UUID.class),
                                rs.getObject("competition_id", UUID.class),
                                rs.getObject("edition_id", UUID.class),
                                rs.getString("competition_name"),
                                rs.getString("edition_name"),
                                rs.getString("title"),
                                EventFormat.valueOf(rs.getString("event_format")),
                                rs.getString("status"),
                                rs.getTimestamp("scheduled_at").toInstant(),
                                rs.getString("zone_id"),
                                VenueState.valueOf(rs.getString("venue_state")),
                                rs.getString("source_reference"),
                                rs.getLong("lock_version")),
                        eventId)
                .stream()
                .findFirst();
    }

    List<EventSlotRequest> findEventSlots(UUID eventId) {
        return jdbcTemplate.query(
                "SELECT slot_no, display_label FROM public.sports_event_slot "
                        + "WHERE event_id = ? ORDER BY slot_no",
                (rs, rowNum) -> new EventSlotRequest(
                        rs.getInt("slot_no"),
                        rs.getString("display_label")),
                eventId);
    }

    int markEventPublished(UUID eventId, long expectedLockVersion) {
        return jdbcTemplate.update(
                "UPDATE public.sports_event SET status = 'PUBLICADO', "
                        + "lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP(6) "
                        + "WHERE event_id = ? AND status = 'RASCUNHO' AND lock_version = ?",
                eventId,
                expectedLockVersion);
    }

    long nextPublicationVersion(UUID eventId) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT COALESCE(max(version_no), 0) + 1 "
                        + "FROM public.sports_event_publication WHERE event_id = ?",
                Long.class,
                eventId);
        return value == null ? 1 : value;
    }

    Optional<UUID> latestPublicationId(UUID eventId) {
        return jdbcTemplate.query(
                        "SELECT publication_id FROM public.sports_event_publication "
                                + "WHERE event_id = ? ORDER BY version_no DESC LIMIT 1",
                        (rs, rowNum) -> rs.getObject("publication_id", UUID.class),
                        eventId)
                .stream()
                .findFirst();
    }

    PublicEventResponse insertPublication(
            EventWork event,
            List<EventSlotRequest> slots,
            UUID publisherIdentityId,
            long versionNo,
            UUID supersedesPublicationId) {
        UUID publicationId = UUID.randomUUID();
        Instant publishedAt = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO public.sports_event_publication "
                        + "(publication_id, event_id, competition_id, edition_id, version_no, "
                        + "competition_name, edition_name, title, event_format, scheduled_at, zone_id, "
                        + "venue_state, source_reference, published_by_identity_id, published_at, "
                        + "supersedes_publication_id, base_lock_version) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
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
                publisherIdentityId,
                Timestamp.from(publishedAt),
                supersedesPublicationId,
                event.lockVersion());

        for (EventSlotRequest slot : slots) {
            jdbcTemplate.update(
                    "INSERT INTO public.sports_event_publication_slot "
                            + "(publication_id, slot_no, display_label) VALUES (?, ?, ?)",
                    publicationId,
                    slot.slotNo(),
                    slot.displayLabel());
        }

        return new PublicEventResponse(
                publicationId,
                event.eventId(),
                event.competitionId(),
                event.editionId(),
                versionNo,
                event.competitionName(),
                event.editionName(),
                event.title(),
                event.format(),
                event.scheduledAt(),
                event.zoneId(),
                event.venueState(),
                event.sourceReference(),
                publishedAt,
                slots.stream()
                        .map(slot -> new PublicSlotResponse(slot.slotNo(), slot.displayLabel()))
                        .toList());
    }

    Optional<PublicEventResponse> findLatestPublication(UUID eventId) {
        Optional<PublicationProjection> publication = jdbcTemplate.query(
                        "SELECT publication_id, event_id, competition_id, edition_id, version_no, "
                                + "competition_name, edition_name, title, event_format, scheduled_at, "
                                + "zone_id, venue_state, source_reference, published_at "
                                + "FROM public.sports_event_publication WHERE event_id = ? "
                                + "ORDER BY version_no DESC LIMIT 1",
                        (rs, rowNum) -> new PublicationProjection(
                                rs.getObject("publication_id", UUID.class),
                                rs.getObject("event_id", UUID.class),
                                rs.getObject("competition_id", UUID.class),
                                rs.getObject("edition_id", UUID.class),
                                rs.getLong("version_no"),
                                rs.getString("competition_name"),
                                rs.getString("edition_name"),
                                rs.getString("title"),
                                EventFormat.valueOf(rs.getString("event_format")),
                                rs.getTimestamp("scheduled_at").toInstant(),
                                rs.getString("zone_id"),
                                VenueState.valueOf(rs.getString("venue_state")),
                                rs.getString("source_reference"),
                                rs.getTimestamp("published_at").toInstant()),
                        eventId)
                .stream()
                .findFirst();
        if (publication.isEmpty()) {
            return Optional.empty();
        }

        PublicationProjection row = publication.get();
        List<PublicSlotResponse> slots = jdbcTemplate.query(
                "SELECT slot_no, display_label FROM public.sports_event_publication_slot "
                        + "WHERE publication_id = ? ORDER BY slot_no",
                (rs, rowNum) -> new PublicSlotResponse(
                        rs.getInt("slot_no"),
                        rs.getString("display_label")),
                row.publicationId());

        return Optional.of(new PublicEventResponse(
                row.publicationId(),
                row.eventId(),
                row.competitionId(),
                row.editionId(),
                row.versionNo(),
                row.competitionName(),
                row.editionName(),
                row.title(),
                row.format(),
                row.scheduledAt(),
                row.zoneId(),
                row.venueState(),
                row.sourceReference(),
                row.publishedAt(),
                slots));
    }

    private static Date sqlDate(LocalDate value) {
        return value == null ? null : Date.valueOf(value);
    }

    record EditionScope(
            UUID editionId,
            UUID competitionId,
            String competitionName,
            String editionName) {
    }

    record EventWork(
            UUID eventId,
            UUID competitionId,
            UUID editionId,
            String competitionName,
            String editionName,
            String title,
            EventFormat format,
            String status,
            Instant scheduledAt,
            String zoneId,
            VenueState venueState,
            String sourceReference,
            long lockVersion) {
    }

    private record PublicationProjection(
            UUID publicationId,
            UUID eventId,
            UUID competitionId,
            UUID editionId,
            long versionNo,
            String competitionName,
            String editionName,
            String title,
            EventFormat format,
            Instant scheduledAt,
            String zoneId,
            VenueState venueState,
            String sourceReference,
            Instant publishedAt) {
    }
}
