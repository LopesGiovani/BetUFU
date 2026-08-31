package br.com.betufu.sports.internal.roster;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.betufu.sports.internal.roster.SportsRosterModels.SportsRosterMemberItem;
import br.com.betufu.sports.internal.roster.SportsRosterModels.SportsRosterMemberRequest;
import br.com.betufu.sports.internal.roster.SportsRosterModels.SportsRosterPublication;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("runtime | integrated")
class SportsRosterRepository {

    private final JdbcTemplate jdbc;

    SportsRosterRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    record TeamContext(
            UUID teamId,
            String teamName,
            UUID organizationId,
            String organizationName,
            UUID competitionId,
            String competitionName,
            UUID editionId,
            String editionName,
            UUID sportId,
            String sportName,
            UUID categoryId,
            String categoryName) {
    }

    record RosterRecord(
            UUID rosterId,
            UUID teamId,
            String status,
            long lockVersion) {
    }

    record PublicationHeader(
            UUID publicationId,
            UUID rosterId,
            UUID teamId,
            UUID competitionId,
            UUID editionId,
            UUID organizationId,
            UUID sportId,
            UUID categoryId,
            long versionNo,
            String teamName,
            String organizationName,
            String editionName,
            String competitionName,
            String sportName,
            String categoryName,
            String reason,
            Instant publishedAt,
            long baseLockVersion) {
    }

    record AthleteSnapshotInfo(
            UUID athleteId,
            String name,
            String slug) {
    }

    Optional<TeamContext> findTeamContext(UUID teamId) {
        return jdbc.query(
                """
                SELECT t.team_id, t.name AS team_name,
                       t.organization_id, o.name AS organization_name,
                       e.competition_id, c.name AS competition_name,
                       t.edition_id, e.name AS edition_name,
                       t.sport_id, s.name AS sport_name,
                       t.category_id, cat.name AS category_name
                FROM public.sports_team t
                JOIN public.sports_organization o ON o.organization_id = t.organization_id
                JOIN public.sports_edition e ON e.edition_id = t.edition_id
                JOIN public.sports_competition c ON c.competition_id = e.competition_id
                JOIN public.sports_sport s ON s.sport_id = t.sport_id
                JOIN public.sports_category cat ON cat.category_id = t.category_id AND cat.edition_id = t.edition_id
                WHERE t.team_id = ?
                """,
                (rs, rowNum) -> new TeamContext(
                        rs.getObject("team_id", UUID.class),
                        rs.getString("team_name"),
                        rs.getObject("organization_id", UUID.class),
                        rs.getString("organization_name"),
                        rs.getObject("competition_id", UUID.class),
                        rs.getString("competition_name"),
                        rs.getObject("edition_id", UUID.class),
                        rs.getString("edition_name"),
                        rs.getObject("sport_id", UUID.class),
                        rs.getString("sport_name"),
                        rs.getObject("category_id", UUID.class),
                        rs.getString("category_name")),
                teamId).stream().findFirst();
    }

    Optional<UUID> findCompetitionForTeam(UUID teamId) {
        return jdbc.query(
                """
                SELECT e.competition_id
                FROM public.sports_team t
                JOIN public.sports_edition e ON e.edition_id = t.edition_id
                WHERE t.team_id = ?
                """,
                (rs, rowNum) -> rs.getObject("competition_id", UUID.class),
                teamId).stream().findFirst();
    }

    RosterRecord findOrCreateRoster(UUID teamId) {
        Optional<RosterRecord> existing = findRosterByTeamId(teamId);
        if (existing.isPresent()) {
            return existing.get();
        }
        UUID newRosterId = UUID.randomUUID();
        jdbc.update(
                """
                INSERT INTO public.sports_roster (roster_id, team_id, status, lock_version)
                VALUES (?, ?, 'RASCUNHO', 0)
                ON CONFLICT (team_id) DO NOTHING
                """,
                newRosterId, teamId);
        return findRosterByTeamId(teamId).orElseThrow();
    }

    Optional<RosterRecord> findRosterByTeamId(UUID teamId) {
        return jdbc.query(
                """
                SELECT roster_id, team_id, status, lock_version
                FROM public.sports_roster
                WHERE team_id = ?
                """,
                this::mapRosterRecord,
                teamId).stream().findFirst();
    }

    Optional<RosterRecord> findRosterByIdForUpdate(UUID rosterId) {
        return jdbc.query(
                """
                SELECT roster_id, team_id, status, lock_version
                FROM public.sports_roster
                WHERE roster_id = ?
                FOR UPDATE
                """,
                this::mapRosterRecord,
                rosterId).stream().findFirst();
    }

    List<SportsRosterMemberItem> findDraftMembers(UUID rosterId) {
        return jdbc.query(
                """
                SELECT m.athlete_id, a.name AS athlete_name, a.slug AS athlete_slug,
                       m.jersey_number, m.position_name
                FROM public.sports_roster_member m
                JOIN public.sports_athlete a ON a.athlete_id = m.athlete_id
                WHERE m.roster_id = ?
                ORDER BY lower(a.name), a.athlete_id
                """,
                this::mapMemberItem,
                rosterId);
    }

    int updateRosterLockVersion(UUID rosterId, long expectedLockVersion) {
        return jdbc.update(
                """
                UPDATE public.sports_roster
                SET lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP(6)
                WHERE roster_id = ? AND lock_version = ?
                """,
                rosterId, expectedLockVersion);
    }

    int markRosterPublished(UUID rosterId, long expectedLockVersion) {
        return jdbc.update(
                """
                UPDATE public.sports_roster
                SET status = 'PUBLICADO', lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP(6)
                WHERE roster_id = ? AND lock_version = ?
                """,
                rosterId, expectedLockVersion);
    }

    void replaceDraftMembers(UUID rosterId, List<SportsRosterMemberRequest> members) {
        jdbc.update("DELETE FROM public.sports_roster_member WHERE roster_id = ?", rosterId);
        for (SportsRosterMemberRequest member : members) {
            jdbc.update(
                    """
                    INSERT INTO public.sports_roster_member (roster_id, athlete_id, jersey_number, position_name)
                    VALUES (?, ?, ?, ?)
                    """,
                    rosterId, member.athleteId(), member.jerseyNumber(), member.positionName());
        }
    }

    boolean athletesExist(List<UUID> athleteIds) {
        if (athleteIds == null || athleteIds.isEmpty()) {
            return true;
        }
        String inSql = String.join(",", Collections.nCopies(athleteIds.size(), "?"));
        Integer count = jdbc.queryForObject(
                "SELECT count(DISTINCT athlete_id) FROM public.sports_athlete WHERE athlete_id IN (" + inSql + ")",
                Integer.class,
                athleteIds.toArray());
        return count != null && count == athleteIds.stream().distinct().count();
    }

    Optional<AthleteSnapshotInfo> findAthleteSnapshotInfo(UUID athleteId) {
        return jdbc.query(
                "SELECT athlete_id, name, slug FROM public.sports_athlete WHERE athlete_id = ?",
                (rs, rowNum) -> new AthleteSnapshotInfo(
                        rs.getObject("athlete_id", UUID.class),
                        rs.getString("name"),
                        rs.getString("slug")),
                athleteId).stream().findFirst();
    }

    Optional<PublicationHeader> findLatestPublicationHeader(UUID teamId) {
        return jdbc.query(
                """
                SELECT publication_id, roster_id, team_id, competition_id, edition_id, organization_id,
                       sport_id, category_id, version_no, team_name, organization_name, edition_name,
                       competition_name, sport_name, category_name, reason, published_at, base_lock_version
                FROM public.sports_roster_publication
                WHERE team_id = ?
                ORDER BY version_no DESC
                LIMIT 1
                """,
                this::mapPublicationHeader,
                teamId).stream().findFirst();
    }

    Optional<PublicationHeader> findLatestPublicationHeaderByRosterId(UUID rosterId) {
        return jdbc.query(
                """
                SELECT publication_id, roster_id, team_id, competition_id, edition_id, organization_id,
                       sport_id, category_id, version_no, team_name, organization_name, edition_name,
                       competition_name, sport_name, category_name, reason, published_at, base_lock_version
                FROM public.sports_roster_publication
                WHERE roster_id = ?
                ORDER BY version_no DESC
                LIMIT 1
                """,
                this::mapPublicationHeader,
                rosterId).stream().findFirst();
    }

    List<SportsRosterMemberItem> findPublicationMembers(UUID publicationId) {
        return jdbc.query(
                """
                SELECT athlete_id, athlete_name, athlete_slug, jersey_number, position_name
                FROM public.sports_roster_publication_member
                WHERE publication_id = ?
                ORDER BY lower(athlete_name), athlete_id
                """,
                this::mapMemberItem,
                publicationId);
    }

    SportsRosterPublication insertPublication(
            PublicationHeader header,
            List<SportsRosterMemberItem> members,
            UUID publishedByIdentityId,
            UUID supersedesPublicationId) {
        jdbc.update(
                """
                INSERT INTO public.sports_roster_publication (
                    publication_id, roster_id, team_id, competition_id, edition_id, organization_id,
                    sport_id, category_id, version_no, team_name, organization_name, edition_name,
                    competition_name, sport_name, category_name, reason, published_by_identity_id,
                    supersedes_publication_id, base_lock_version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                header.publicationId(),
                header.rosterId(),
                header.teamId(),
                header.competitionId(),
                header.editionId(),
                header.organizationId(),
                header.sportId(),
                header.categoryId(),
                header.versionNo(),
                header.teamName(),
                header.organizationName(),
                header.editionName(),
                header.competitionName(),
                header.sportName(),
                header.categoryName(),
                header.reason(),
                publishedByIdentityId,
                supersedesPublicationId,
                header.baseLockVersion());

        for (SportsRosterMemberItem member : members) {
            jdbc.update(
                    """
                    INSERT INTO public.sports_roster_publication_member (
                        publication_id, athlete_id, athlete_name, athlete_slug, jersey_number, position_name
                    ) VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    header.publicationId(),
                    member.athleteId(),
                    member.athleteName(),
                    member.athleteSlug(),
                    member.jerseyNumber(),
                    member.positionName());
        }

        Timestamp publishedAtTimestamp = jdbc.queryForObject(
                "SELECT published_at FROM public.sports_roster_publication WHERE publication_id = ?",
                Timestamp.class,
                header.publicationId());
        Instant publishedAt = publishedAtTimestamp != null ? publishedAtTimestamp.toInstant() : Instant.now();

        return new SportsRosterPublication(
                header.publicationId(),
                header.rosterId(),
                header.teamId(),
                header.versionNo(),
                header.teamName(),
                header.organizationId(),
                header.organizationName(),
                header.competitionId(),
                header.competitionName(),
                header.editionId(),
                header.editionName(),
                header.sportId(),
                header.sportName(),
                header.categoryId(),
                header.categoryName(),
                header.reason(),
                publishedAt,
                header.baseLockVersion(),
                members);
    }

    private RosterRecord mapRosterRecord(ResultSet rs, int rowNum) throws SQLException {
        return new RosterRecord(
                rs.getObject("roster_id", UUID.class),
                rs.getObject("team_id", UUID.class),
                rs.getString("status"),
                rs.getLong("lock_version"));
    }

    private SportsRosterMemberItem mapMemberItem(ResultSet rs, int rowNum) throws SQLException {
        return new SportsRosterMemberItem(
                rs.getObject("athlete_id", UUID.class),
                rs.getString("athlete_name"),
                rs.getString("athlete_slug"),
                rs.getObject("jersey_number") != null ? rs.getInt("jersey_number") : null,
                rs.getString("position_name"));
    }

    private PublicationHeader mapPublicationHeader(ResultSet rs, int rowNum) throws SQLException {
        Timestamp publishedAt = rs.getTimestamp("published_at");
        return new PublicationHeader(
                rs.getObject("publication_id", UUID.class),
                rs.getObject("roster_id", UUID.class),
                rs.getObject("team_id", UUID.class),
                rs.getObject("competition_id", UUID.class),
                rs.getObject("edition_id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getObject("sport_id", UUID.class),
                rs.getObject("category_id", UUID.class),
                rs.getLong("version_no"),
                rs.getString("team_name"),
                rs.getString("organization_name"),
                rs.getString("edition_name"),
                rs.getString("competition_name"),
                rs.getString("sport_name"),
                rs.getString("category_name"),
                rs.getString("reason"),
                publishedAt != null ? publishedAt.toInstant() : null,
                rs.getLong("base_lock_version"));
    }
}
