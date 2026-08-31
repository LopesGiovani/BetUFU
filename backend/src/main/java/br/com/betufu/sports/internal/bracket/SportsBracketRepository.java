package br.com.betufu.sports.internal.bracket;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketCreateRequest;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketMatchConfigureRequest;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketRound;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketSlotConfigureRequest;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketSourceType;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketSummary;
import br.com.betufu.sports.internal.bracket.SportsBracketModels.SportsBracketUpdateRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("runtime | integrated")
class SportsBracketRepository {

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;

    SportsBracketRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
    }

    record EditionContext(
            UUID competitionId,
            UUID editionId,
            String competitionName,
            String editionName) {
    }

    record BracketRecord(
            UUID bracketId,
            UUID competitionId,
            UUID editionId,
            UUID sportId,
            UUID categoryId,
            UUID phaseId,
            String name,
            String slug,
            String status,
            long lockVersion,
            Instant createdAt,
            Instant updatedAt,
            String competitionName,
            String editionName,
            String sportName,
            String categoryName,
            String phaseName) {
    }

    record SlotEntity(
            UUID matchId,
            int slotNo,
            SportsBracketSourceType sourceType,
            UUID sourceMatchId,
            String seedLabel) {
    }

    record MatchEntity(
            UUID matchId,
            UUID bracketId,
            int roundNo,
            int matchNo,
            String roundName,
            UUID eventId,
            boolean isThirdPlace,
            List<SlotEntity> slots) {
    }

    record EventSlotProjectionInfo(
            int slotNo,
            String displayLabel,
            UUID teamId,
            String teamName) {
    }

    record EventProjectionInfo(
            UUID eventId,
            String title,
            Instant scheduledAt,
            String zoneId,
            String status,
            String venueName,
            String venueSpaceName,
            UUID confirmedVersionId,
            List<EventSlotProjectionInfo> slots,
            Map<Integer, Integer> scores) {
    }

    record PublicationRecord(
            UUID publicationId,
            UUID bracketId,
            UUID competitionId,
            UUID editionId,
            long versionNo,
            String bracketName,
            String competitionName,
            String editionName,
            String sportName,
            String categoryName,
            String phaseName,
            String sourceReference,
            String reason,
            Instant publishedAt,
            long baseLockVersion) {
    }

    record PublicationNodeRecord(
            UUID publicationId,
            UUID matchId,
            int roundNo,
            int matchNo,
            String roundName,
            UUID eventId,
            boolean isThirdPlace,
            SportsBracketSourceType slot1SourceType,
            UUID slot1SourceMatchId,
            String slot1SeedLabel,
            SportsBracketSourceType slot2SourceType,
            UUID slot2SourceMatchId,
            String slot2SeedLabel) {
    }

    Optional<EditionContext> findEditionContext(UUID editionId) {
        return jdbc.query(
                """
                SELECT e.competition_id, e.edition_id, c.name AS competition_name, e.name AS edition_name
                FROM public.sports_edition e
                JOIN public.sports_competition c ON c.competition_id = e.competition_id
                WHERE e.edition_id = ?
                """,
                (rs, rowNum) -> new EditionContext(
                        rs.getObject("competition_id", UUID.class),
                        rs.getObject("edition_id", UUID.class),
                        rs.getString("competition_name"),
                        rs.getString("edition_name")),
                editionId).stream().findFirst();
    }

    Optional<BracketRecord> findBracketRecord(UUID bracketId) {
        return jdbc.query(
                """
                SELECT b.bracket_id, b.competition_id, b.edition_id, b.sport_id, b.category_id, b.phase_id,
                       b.name, b.slug, b.status, b.lock_version, b.created_at, b.updated_at,
                       c.name AS competition_name, e.name AS edition_name,
                       s.name AS sport_name, cat.name AS category_name, p.name AS phase_name
                FROM public.sports_bracket b
                JOIN public.sports_competition c ON c.competition_id = b.competition_id
                JOIN public.sports_edition e ON e.edition_id = b.edition_id
                LEFT JOIN public.sports_sport s ON s.sport_id = b.sport_id
                LEFT JOIN public.sports_category cat ON cat.category_id = b.category_id
                LEFT JOIN public.sports_phase p ON p.phase_id = b.phase_id
                WHERE b.bracket_id = ?
                """,
                this::mapBracketRecord,
                bracketId).stream().findFirst();
    }

    List<SportsBracketSummary> listBracketsByEdition(UUID editionId) {
        return jdbc.query(
                """
                SELECT bracket_id, competition_id, edition_id, sport_id, category_id, phase_id,
                       name, slug, status, lock_version, created_at, updated_at
                FROM public.sports_bracket
                WHERE edition_id = ?
                ORDER BY name ASC
                """,
                (rs, rowNum) -> new SportsBracketSummary(
                        rs.getObject("bracket_id", UUID.class),
                        rs.getObject("competition_id", UUID.class),
                        rs.getObject("edition_id", UUID.class),
                        rs.getObject("sport_id", UUID.class),
                        rs.getObject("category_id", UUID.class),
                        rs.getObject("phase_id", UUID.class),
                        rs.getString("name"),
                        rs.getString("slug"),
                        rs.getString("status"),
                        rs.getLong("lock_version"),
                        toInstant(rs.getTimestamp("created_at")),
                        toInstant(rs.getTimestamp("updated_at"))),
                editionId);
    }

    SportsBracketSummary createBracket(UUID competitionId, UUID editionId, SportsBracketCreateRequest request) {
        UUID bracketId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO public.sports_bracket (
                    bracket_id, competition_id, edition_id, sport_id, category_id, phase_id,
                    name, slug, status, lock_version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'RASCUNHO', 0)
                """,
                bracketId, competitionId, editionId, request.sportId(), request.categoryId(), request.phaseId(),
                request.name().trim(), request.slug().trim());

        return findBracketRecord(bracketId)
                .map(r -> new SportsBracketSummary(
                        r.bracketId(), r.competitionId(), r.editionId(), r.sportId(), r.categoryId(), r.phaseId(),
                        r.name(), r.slug(), r.status(), r.lockVersion(), r.createdAt(), r.updatedAt()))
                .orElseThrow();
    }

    int updateBracketMetadata(UUID bracketId, SportsBracketUpdateRequest request, long expectedLockVersion) {
        return jdbc.update(
                """
                UPDATE public.sports_bracket
                SET name = ?, slug = ?, sport_id = ?, category_id = ?, phase_id = ?,
                    lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP(6)
                WHERE bracket_id = ? AND lock_version = ?
                """,
                request.name().trim(), request.slug().trim(), request.sportId(), request.categoryId(), request.phaseId(),
                bracketId, expectedLockVersion);
    }

    int updateBracketLockVersion(UUID bracketId, long expectedLockVersion) {
        return jdbc.update(
                """
                UPDATE public.sports_bracket
                SET lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP(6)
                WHERE bracket_id = ? AND lock_version = ?
                """,
                bracketId, expectedLockVersion);
    }

    int setBracketPublished(UUID bracketId, long expectedLockVersion) {
        return jdbc.update(
                """
                UPDATE public.sports_bracket
                SET status = 'PUBLICADO', lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP(6)
                WHERE bracket_id = ? AND lock_version = ?
                """,
                bracketId, expectedLockVersion);
    }

    List<MatchEntity> findMatchesWithSlots(UUID bracketId) {
        List<MatchEntity> rawMatches = jdbc.query(
                """
                SELECT match_id, bracket_id, round_no, match_no, round_name, event_id, is_third_place
                FROM public.sports_bracket_match
                WHERE bracket_id = ?
                ORDER BY round_no ASC, match_no ASC
                """,
                (rs, rowNum) -> new MatchEntity(
                        rs.getObject("match_id", UUID.class),
                        rs.getObject("bracket_id", UUID.class),
                        rs.getInt("round_no"),
                        rs.getInt("match_no"),
                        rs.getString("round_name"),
                        rs.getObject("event_id", UUID.class),
                        rs.getBoolean("is_third_place"),
                        new ArrayList<>()),
                bracketId);

        if (rawMatches.isEmpty()) {
            return List.of();
        }

        List<SlotEntity> slots = jdbc.query(
                """
                SELECT s.match_id, s.slot_no, s.source_type, s.source_match_id, s.seed_label
                FROM public.sports_bracket_match_slot s
                JOIN public.sports_bracket_match m ON m.match_id = s.match_id
                WHERE m.bracket_id = ?
                ORDER BY s.slot_no ASC
                """,
                (rs, rowNum) -> new SlotEntity(
                        rs.getObject("match_id", UUID.class),
                        rs.getInt("slot_no"),
                        SportsBracketSourceType.valueOf(rs.getString("source_type")),
                        rs.getObject("source_match_id", UUID.class),
                        rs.getString("seed_label")),
                bracketId);

        Map<UUID, List<SlotEntity>> slotsByMatch = new HashMap<>();
        for (SlotEntity slot : slots) {
            slotsByMatch.computeIfAbsent(slot.matchId(), k -> new ArrayList<>()).add(slot);
        }

        List<MatchEntity> result = new ArrayList<>();
        for (MatchEntity m : rawMatches) {
            List<SlotEntity> matchSlots = slotsByMatch.getOrDefault(m.matchId(), List.of());
            result.add(new MatchEntity(
                    m.matchId(), m.bracketId(), m.roundNo(), m.matchNo(), m.roundName(),
                    m.eventId(), m.isThirdPlace(), matchSlots));
        }
        return result;
    }

    void replaceMatchesAndSlots(UUID bracketId, List<SportsBracketMatchConfigureRequest> matches) {
        jdbc.update(
                """
                DELETE FROM public.sports_bracket_match_slot
                WHERE match_id IN (SELECT match_id FROM public.sports_bracket_match WHERE bracket_id = ?)
                """,
                bracketId);
        jdbc.update("DELETE FROM public.sports_bracket_match WHERE bracket_id = ?", bracketId);

        for (SportsBracketMatchConfigureRequest match : matches) {
            UUID matchId = match.matchId() != null ? match.matchId() : UUID.randomUUID();
            jdbc.update(
                    """
                    INSERT INTO public.sports_bracket_match (
                        match_id, bracket_id, round_no, match_no, round_name, event_id, is_third_place
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    matchId, bracketId, match.roundNo(), match.matchNo(), match.roundName().trim(),
                    match.eventId(), match.isThirdPlace());

            for (SportsBracketSlotConfigureRequest slot : match.slots()) {
                jdbc.update(
                        """
                        INSERT INTO public.sports_bracket_match_slot (
                            match_id, slot_no, source_type, source_match_id, seed_label
                        ) VALUES (?, ?, ?, ?, ?)
                        """,
                        matchId, slot.slotNo(), slot.sourceType().name(), slot.sourceMatchId(),
                        slot.seedLabel() != null && !slot.seedLabel().isBlank() ? slot.seedLabel().trim() : null);
            }
        }
    }

    boolean catalogEntitiesExist(UUID editionId, UUID sportId, UUID categoryId, UUID phaseId) {
        if (sportId != null) {
            Integer count = jdbc.queryForObject("SELECT count(*) FROM public.sports_sport WHERE sport_id = ?", Integer.class, sportId);
            if (count == null || count == 0) return false;
        }
        if (categoryId != null) {
            Integer count = jdbc.queryForObject("SELECT count(*) FROM public.sports_category WHERE category_id = ? AND edition_id = ?", Integer.class, categoryId, editionId);
            if (count == null || count == 0) return false;
        }
        if (phaseId != null) {
            Integer count = jdbc.queryForObject("SELECT count(*) FROM public.sports_phase WHERE phase_id = ? AND edition_id = ?", Integer.class, phaseId, editionId);
            if (count == null || count == 0) return false;
        }
        return true;
    }

    boolean eventsBelongToEdition(UUID competitionId, UUID editionId, List<UUID> eventIds) {
        List<UUID> nonNullIds = eventIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (nonNullIds.isEmpty()) {
            return true;
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("competitionId", competitionId)
                .addValue("editionId", editionId)
                .addValue("eventIds", nonNullIds);

        Integer count = namedJdbc.queryForObject(
                """
                SELECT count(*)
                FROM public.sports_event
                WHERE competition_id = :competitionId AND edition_id = :editionId AND event_id IN (:eventIds)
                """,
                params,
                Integer.class);
        return count != null && count == nonNullIds.size();
    }

    Map<UUID, EventProjectionInfo> findLinkedEvents(List<UUID> eventIds, boolean publicOnly) {
        List<UUID> nonNullIds = eventIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (nonNullIds.isEmpty()) {
            return Map.of();
        }

        MapSqlParameterSource params = new MapSqlParameterSource().addValue("eventIds", nonNullIds);

        String visibilityPredicate = publicOnly ? " AND e.status <> 'RASCUNHO'" : "";
        List<EventProjectionInfo> events = namedJdbc.query(
                ("""
                SELECT e.event_id, e.title, e.scheduled_at, e.zone_id, e.status, e.confirmed_version_id,
                       v.name AS venue_name, vs.name AS venue_space_name
                FROM public.sports_event e
                LEFT JOIN public.sports_venue v ON v.venue_id = e.venue_id
                LEFT JOIN public.sports_venue_space vs ON vs.venue_space_id = e.venue_space_id
                WHERE e.event_id IN (:eventIds)
                """ + visibilityPredicate),
                params,
                (rs, rowNum) -> new EventProjectionInfo(
                        rs.getObject("event_id", UUID.class),
                        rs.getString("title"),
                        toInstant(rs.getTimestamp("scheduled_at")),
                        rs.getString("zone_id"),
                        rs.getString("status"),
                        rs.getString("venue_name"),
                        rs.getString("venue_space_name"),
                        rs.getObject("confirmed_version_id", UUID.class),
                        new ArrayList<>(),
                        new HashMap<>()));

        List<Map<String, Object>> slotRows = namedJdbc.queryForList(
                """
                SELECT es.event_id, es.slot_no, es.display_label, es.team_id, t.name AS team_name
                FROM public.sports_event_slot es
                LEFT JOIN public.sports_team t ON t.team_id = es.team_id
                WHERE es.event_id IN (:eventIds)
                ORDER BY es.slot_no ASC
                """,
                params);

        Map<UUID, List<EventSlotProjectionInfo>> slotsByEvent = new HashMap<>();
        for (Map<String, Object> row : slotRows) {
            UUID eventId = (UUID) row.get("event_id");
            int slotNo = ((Number) row.get("slot_no")).intValue();
            String displayLabel = (String) row.get("display_label");
            UUID teamId = (UUID) row.get("team_id");
            String teamName = (String) row.get("team_name");
            slotsByEvent.computeIfAbsent(eventId, k -> new ArrayList<>())
                    .add(new EventSlotProjectionInfo(slotNo, displayLabel, teamId, teamName));
        }

        List<Map<String, Object>> scoreRows = namedJdbc.queryForList(
                """
                SELECT rvs.version_id, e.event_id, rvs.slot_no, rvs.score
                FROM public.sports_result_version_slot rvs
                JOIN public.sports_event e ON e.confirmed_version_id = rvs.version_id
                WHERE e.event_id IN (:eventIds)
                """,
                params);

        Map<UUID, Map<Integer, Integer>> scoresByEvent = new HashMap<>();
        for (Map<String, Object> row : scoreRows) {
            UUID eventId = (UUID) row.get("event_id");
            int slotNo = ((Number) row.get("slot_no")).intValue();
            int score = ((Number) row.get("score")).intValue();
            scoresByEvent.computeIfAbsent(eventId, k -> new HashMap<>()).put(slotNo, score);
        }

        Map<UUID, EventProjectionInfo> resultMap = new HashMap<>();
        for (EventProjectionInfo ev : events) {
            List<EventSlotProjectionInfo> sl = slotsByEvent.getOrDefault(ev.eventId(), List.of());
            Map<Integer, Integer> sc = scoresByEvent.getOrDefault(ev.eventId(), Map.of());
            resultMap.put(ev.eventId(), new EventProjectionInfo(
                    ev.eventId(), ev.title(), ev.scheduledAt(), ev.zoneId(), ev.status(),
                    ev.venueName(), ev.venueSpaceName(), ev.confirmedVersionId(), sl, sc));
        }
        return resultMap;
    }

    UUID createPublication(
            BracketRecord bracket,
            UUID publishedByIdentityId,
            String sourceReference,
            String reason,
            long baseLockVersion,
            List<MatchEntity> matches) {

        Long maxVersion = jdbc.queryForObject(
                "SELECT max(version_no) FROM public.sports_bracket_publication WHERE bracket_id = ?",
                Long.class,
                bracket.bracketId());
        long versionNo = (maxVersion == null ? 0L : maxVersion) + 1L;

        UUID supersedesId = null;
        if (versionNo > 1) {
            supersedesId = jdbc.query(
                    "SELECT publication_id FROM public.sports_bracket_publication WHERE bracket_id = ? ORDER BY version_no DESC LIMIT 1",
                    (rs, rowNum) -> rs.getObject("publication_id", UUID.class),
                    bracket.bracketId()).stream().findFirst().orElse(null);
        }

        UUID publicationId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO public.sports_bracket_publication (
                    publication_id, bracket_id, competition_id, edition_id, sport_id, category_id, phase_id,
                    version_no, bracket_name, competition_name, edition_name, sport_name, category_name, phase_name,
                    source_reference, reason, published_by_identity_id, supersedes_publication_id, base_lock_version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                publicationId, bracket.bracketId(), bracket.competitionId(), bracket.editionId(),
                bracket.sportId(), bracket.categoryId(), bracket.phaseId(),
                versionNo, bracket.name(), bracket.competitionName(), bracket.editionName(),
                bracket.sportName(), bracket.categoryName(), bracket.phaseName(),
                sourceReference.trim(), reason != null && !reason.isBlank() ? reason.trim() : null,
                publishedByIdentityId, supersedesId, baseLockVersion);

        for (MatchEntity match : matches) {
            SlotEntity slot1 = match.slots().stream().filter(s -> s.slotNo() == 1).findFirst().orElse(null);
            SlotEntity slot2 = match.slots().stream().filter(s -> s.slotNo() == 2).findFirst().orElse(null);

            jdbc.update(
                    """
                    INSERT INTO public.sports_bracket_publication_node (
                        publication_id, match_id, round_no, match_no, round_name, event_id, is_third_place,
                        slot1_source_type, slot1_source_match_id, slot1_seed_label,
                        slot2_source_type, slot2_source_match_id, slot2_seed_label
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    publicationId, match.matchId(), match.roundNo(), match.matchNo(), match.roundName(),
                    match.eventId(), match.isThirdPlace(),
                    slot1 != null ? slot1.sourceType().name() : "DIRECT",
                    slot1 != null ? slot1.sourceMatchId() : null,
                    slot1 != null ? slot1.seedLabel() : null,
                    slot2 != null ? slot2.sourceType().name() : "DIRECT",
                    slot2 != null ? slot2.sourceMatchId() : null,
                    slot2 != null ? slot2.seedLabel() : null);
        }

        return publicationId;
    }

    Optional<PublicationRecord> findLatestPublication(UUID bracketId) {
        return jdbc.query(
                """
                SELECT publication_id, bracket_id, competition_id, edition_id, version_no,
                       bracket_name, competition_name, edition_name, sport_name, category_name, phase_name,
                       source_reference, reason, published_at, base_lock_version
                FROM public.sports_bracket_publication
                WHERE bracket_id = ?
                ORDER BY version_no DESC
                LIMIT 1
                """,
                (rs, rowNum) -> new PublicationRecord(
                        rs.getObject("publication_id", UUID.class),
                        rs.getObject("bracket_id", UUID.class),
                        rs.getObject("competition_id", UUID.class),
                        rs.getObject("edition_id", UUID.class),
                        rs.getLong("version_no"),
                        rs.getString("bracket_name"),
                        rs.getString("competition_name"),
                        rs.getString("edition_name"),
                        rs.getString("sport_name"),
                        rs.getString("category_name"),
                        rs.getString("phase_name"),
                        rs.getString("source_reference"),
                        rs.getString("reason"),
                        toInstant(rs.getTimestamp("published_at")),
                        rs.getLong("base_lock_version")),
                bracketId).stream().findFirst();
    }

    List<PublicationNodeRecord> findPublicationNodes(UUID publicationId) {
        return jdbc.query(
                """
                SELECT publication_id, match_id, round_no, match_no, round_name, event_id, is_third_place,
                       slot1_source_type, slot1_source_match_id, slot1_seed_label,
                       slot2_source_type, slot2_source_match_id, slot2_seed_label
                FROM public.sports_bracket_publication_node
                WHERE publication_id = ?
                ORDER BY round_no ASC, match_no ASC
                """,
                (rs, rowNum) -> new PublicationNodeRecord(
                        rs.getObject("publication_id", UUID.class),
                        rs.getObject("match_id", UUID.class),
                        rs.getInt("round_no"),
                        rs.getInt("match_no"),
                        rs.getString("round_name"),
                        rs.getObject("event_id", UUID.class),
                        rs.getBoolean("is_third_place"),
                        SportsBracketSourceType.valueOf(rs.getString("slot1_source_type")),
                        rs.getObject("slot1_source_match_id", UUID.class),
                        rs.getString("slot1_seed_label"),
                        SportsBracketSourceType.valueOf(rs.getString("slot2_source_type")),
                        rs.getObject("slot2_source_match_id", UUID.class),
                        rs.getString("slot2_seed_label")),
                publicationId);
    }

    private BracketRecord mapBracketRecord(ResultSet rs, int rowNum) throws SQLException {
        return new BracketRecord(
                rs.getObject("bracket_id", UUID.class),
                rs.getObject("competition_id", UUID.class),
                rs.getObject("edition_id", UUID.class),
                rs.getObject("sport_id", UUID.class),
                rs.getObject("category_id", UUID.class),
                rs.getObject("phase_id", UUID.class),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getString("status"),
                rs.getLong("lock_version"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")),
                rs.getString("competition_name"),
                rs.getString("edition_name"),
                rs.getString("sport_name"),
                rs.getString("category_name"),
                rs.getString("phase_name"));
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
