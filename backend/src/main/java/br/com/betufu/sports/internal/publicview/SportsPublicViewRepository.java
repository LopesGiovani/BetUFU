package br.com.betufu.sports.internal.publicview;

import br.com.betufu.sports.internal.catalog.SportsCatalogModels.SportsSportSummary;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.EventFormat;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicAthleteDetail;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicAthleteStats;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicAthleteTeamParticipation;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicCategorySummary;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicCompetitionDetail;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicEditionSummary;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicEventDetail;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicEventResultDetail;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicEventSlotItem;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicOrganizationDetail;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicOrganizationRosterMemberItem;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicOrganizationTeamItem;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicPhaseSummary;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicResultScoreItem;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicSlotResponse;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicSourceHistoryItem;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicSportsEventSummary;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicTeamDetail;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicTeamRosterDetail;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicTeamRosterMemberItem;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicTeamSummary;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicVenueSpaceSummary;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.PublicVenueSummary;
import br.com.betufu.sports.internal.publicview.SportsPublicViewModels.VenueState;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("runtime | integrated")
class SportsPublicViewRepository {

  private final JdbcTemplate jdbcTemplate;

  SportsPublicViewRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  // ==========================================
  // P2: Competição e Edição
  // ==========================================

  Optional<PublicCompetitionDetail> findCompetitionDetail(UUID competitionId) {
    if (competitionId == null) {
      return Optional.empty();
    }

    List<CompetitionHeader> headers =
        jdbcTemplate.query(
            "SELECT competition_id, name, slug FROM public.sports_competition "
                + "WHERE competition_id = ? AND status = 'ATIVA'",
            (rs, rowNum) ->
                new CompetitionHeader(
                    rs.getObject("competition_id", UUID.class),
                    rs.getString("name"),
                    rs.getString("slug")),
            competitionId);

    if (headers.isEmpty()) {
      return Optional.empty();
    }

    CompetitionHeader header = headers.get(0);

    List<SportsSportSummary> sports =
        jdbcTemplate.query(
            "SELECT s.sport_id, s.name, s.slug, s.status, s.lock_version "
                + "FROM public.sports_competition_sport cs "
                + "JOIN public.sports_sport s ON s.sport_id = cs.sport_id "
                + "WHERE cs.competition_id = ? AND s.status = 'ATIVA' "
                + "ORDER BY lower(s.name), s.sport_id",
            (rs, rowNum) ->
                new SportsSportSummary(
                    rs.getObject("sport_id", UUID.class),
                    rs.getString("name"),
                    rs.getString("slug"),
                    rs.getString("status"),
                    rs.getLong("lock_version")),
            competitionId);

    List<PublicEditionSummary> editions =
        jdbcTemplate.query(
            "SELECT edition_id, competition_id, name, slug, start_date, end_date "
                + "FROM public.sports_edition "
                + "WHERE competition_id = ? AND status = 'ATIVA' "
                + "ORDER BY start_date ASC NULLS LAST, name ASC",
            (rs, rowNum) ->
                new PublicEditionSummary(
                    rs.getObject("edition_id", UUID.class),
                    rs.getObject("competition_id", UUID.class),
                    rs.getString("name"),
                    rs.getString("slug"),
                    toLocalDate(rs.getDate("start_date")),
                    toLocalDate(rs.getDate("end_date"))),
            competitionId);

    List<PublicCategorySummary> categories =
        jdbcTemplate.query(
            "SELECT c.category_id, c.edition_id, c.name, c.slug "
                + "FROM public.sports_category c "
                + "JOIN public.sports_edition e ON e.edition_id = c.edition_id "
                + "WHERE e.competition_id = ? AND c.status = 'ATIVA' "
                + "ORDER BY c.name ASC",
            (rs, rowNum) ->
                new PublicCategorySummary(
                    rs.getObject("category_id", UUID.class),
                    rs.getObject("edition_id", UUID.class),
                    rs.getString("name"),
                    rs.getString("slug")),
            competitionId);

    List<PublicPhaseSummary> phases =
        jdbcTemplate.query(
            "SELECT p.phase_id, p.category_id, p.edition_id, p.name, p.slug "
                + "FROM public.sports_phase p "
                + "JOIN public.sports_edition e ON e.edition_id = p.edition_id "
                + "WHERE e.competition_id = ? AND p.status = 'ATIVA' "
                + "ORDER BY p.name ASC",
            (rs, rowNum) ->
                new PublicPhaseSummary(
                    rs.getObject("phase_id", UUID.class),
                    rs.getObject("category_id", UUID.class),
                    rs.getObject("edition_id", UUID.class),
                    rs.getString("name"),
                    rs.getString("slug")),
            competitionId);

    List<PublicVenueSummary> venues = findVenuesForCompetition(competitionId);

    List<PublicTeamSummary> teams =
        jdbcTemplate.query(
            "SELECT t.team_id, t.organization_id, o.name AS organization_name, t.edition_id, "
                + "t.sport_id, s.name AS sport_name, t.category_id, c.name AS category_name, "
                + "t.name, t.slug "
                + "FROM public.sports_team t "
                + "JOIN public.sports_organization o ON o.organization_id = t.organization_id "
                + "JOIN public.sports_edition e ON e.edition_id = t.edition_id "
                + "JOIN public.sports_sport s ON s.sport_id = t.sport_id "
                + "JOIN public.sports_category c ON c.category_id = t.category_id "
                + "WHERE e.competition_id = ? AND t.status = 'PUBLICADO' AND o.status = 'PUBLICADO' "
                + "ORDER BY t.name ASC",
            (rs, rowNum) ->
                new PublicTeamSummary(
                    rs.getObject("team_id", UUID.class),
                    rs.getObject("organization_id", UUID.class),
                    rs.getString("organization_name"),
                    rs.getObject("edition_id", UUID.class),
                    rs.getObject("sport_id", UUID.class),
                    rs.getString("sport_name"),
                    rs.getObject("category_id", UUID.class),
                    rs.getString("category_name"),
                    rs.getString("name"),
                    rs.getString("slug")),
            competitionId);

    List<PublicSportsEventSummary> recentEvents = findEventsForCompetition(competitionId, 20);

    return Optional.of(
        new PublicCompetitionDetail(
            header.competitionId(),
            header.name(),
            header.slug(),
            sports,
            editions,
            categories,
            phases,
            venues,
            teams,
            recentEvents));
  }

  private List<PublicVenueSummary> findVenuesForCompetition(UUID competitionId) {
    List<VenueRow> venueRows =
        jdbcTemplate.query(
            "SELECT venue_id, competition_id, name, address, map_url, notes "
                + "FROM public.sports_venue "
                + "WHERE competition_id = ? "
                + "ORDER BY name ASC",
            (rs, rowNum) ->
                new VenueRow(
                    rs.getObject("venue_id", UUID.class),
                    rs.getObject("competition_id", UUID.class),
                    rs.getString("name"),
                    rs.getString("address"),
                    rs.getString("map_url"),
                    rs.getString("notes")),
            competitionId);

    if (venueRows.isEmpty()) {
      return List.of();
    }

    List<PublicVenueSummary> result = new ArrayList<>();
    for (VenueRow vr : venueRows) {
      List<PublicVenueSpaceSummary> spaces =
          jdbcTemplate.query(
              "SELECT venue_space_id, venue_id, name, notes "
                  + "FROM public.sports_venue_space "
                  + "WHERE venue_id = ? "
                  + "ORDER BY name ASC",
              (rs, rowNum) ->
                  new PublicVenueSpaceSummary(
                      rs.getObject("venue_space_id", UUID.class),
                      rs.getObject("venue_id", UUID.class),
                      rs.getString("name"),
                      rs.getString("notes")),
              vr.venueId());

      result.add(
          new PublicVenueSummary(
              vr.venueId(),
              vr.competitionId(),
              vr.name(),
              vr.address(),
              vr.mapUrl(),
              vr.notes(),
              spaces));
    }
    return result;
  }

  // ==========================================
  // P3: Evento
  // ==========================================

  Optional<PublicEventDetail> findEventDetail(UUID eventId) {
    if (eventId == null) {
      return Optional.empty();
    }

    String sql =
        "SELECT p.publication_id, p.event_id, p.competition_id, p.edition_id, "
            + "p.version_no, p.competition_name, p.edition_name, p.title, p.event_format, "
            + "p.scheduled_at, p.zone_id, p.venue_state, p.source_reference, p.published_at, "
            + "p.sport_id, p.sport_name, p.category_id, p.category_name, p.phase_id, p.phase_name, "
            + "p.venue_id, p.venue_name, p.venue_space_id, p.venue_space_name, "
            + "e.status AS current_status, e.status_reason AS current_status_reason, "
            + "e.confirmed_version_id "
            + "FROM public.sports_event_publication p "
            + "JOIN (SELECT event_id, max(version_no) AS max_version "
            + "      FROM public.sports_event_publication "
            + "      GROUP BY event_id) latest "
            + "ON latest.event_id = p.event_id AND latest.max_version = p.version_no "
            + "JOIN public.sports_event e ON e.event_id = p.event_id "
            + "WHERE p.event_id = ? AND e.status <> 'RASCUNHO'";

    List<EventDetailRow> rows =
        jdbcTemplate.query(
            sql,
            (rs, rowNum) ->
                new EventDetailRow(
                    rs.getObject("publication_id", UUID.class),
                    rs.getObject("event_id", UUID.class),
                    rs.getObject("competition_id", UUID.class),
                    rs.getObject("edition_id", UUID.class),
                    rs.getLong("version_no"),
                    rs.getString("competition_name"),
                    rs.getString("edition_name"),
                    rs.getString("title"),
                    EventFormat.valueOf(rs.getString("event_format")),
                    rs.getString("current_status"),
                    rs.getString("current_status_reason"),
                    rs.getTimestamp("scheduled_at").toInstant(),
                    rs.getString("zone_id"),
                    VenueState.valueOf(rs.getString("venue_state")),
                    rs.getString("source_reference"),
                    rs.getTimestamp("published_at").toInstant(),
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
                    rs.getObject("confirmed_version_id", UUID.class)),
            eventId);

    if (rows.isEmpty()) {
      return Optional.empty();
    }

    EventDetailRow row = rows.get(0);

    // Slots
    List<PublicEventSlotItem> slots =
        jdbcTemplate.query(
            "SELECT slot_no, display_label, team_id, team_name "
                + "FROM public.sports_event_publication_slot "
                + "WHERE publication_id = ? "
                + "ORDER BY slot_no ASC",
            (rs, rowNum) ->
                new PublicEventSlotItem(
                    rs.getInt("slot_no"),
                    rs.getString("display_label"),
                    rs.getObject("team_id", UUID.class),
                    rs.getString("team_name")),
            row.publicationId());

    // Venue extra info
    String venueAddress = null;
    String venueMapUrl = null;
    String venueNotes = null;
    if (row.venueId() != null) {
      List<Map<String, Object>> venueData =
          jdbcTemplate.queryForList(
              "SELECT address, map_url, notes FROM public.sports_venue WHERE venue_id = ?",
              row.venueId());
      if (!venueData.isEmpty()) {
        venueAddress = (String) venueData.get(0).get("address");
        venueMapUrl = (String) venueData.get(0).get("map_url");
        venueNotes = (String) venueData.get(0).get("notes");
      }
    }

    String venueSpaceNotes = null;
    if (row.venueSpaceId() != null) {
      List<Map<String, Object>> spaceData =
          jdbcTemplate.queryForList(
              "SELECT notes FROM public.sports_venue_space WHERE venue_space_id = ?",
              row.venueSpaceId());
      if (!spaceData.isEmpty()) {
        venueSpaceNotes = (String) spaceData.get(0).get("notes");
      }
    }

    // Confirmed result
    PublicEventResultDetail resultDetail = null;
    if (row.confirmedVersionId() != null) {
      List<PublicEventResultDetail> results =
          jdbcTemplate.query(
              "SELECT version_id, version_no, status, confirmed_at "
                  + "FROM public.sports_result_version "
                  + "WHERE version_id = ? AND status IN ('CONFIRMADO', 'CORRIGIDO')",
              (rs, rowNum) -> {
                UUID vId = rs.getObject("version_id", UUID.class);
                long vNo = rs.getLong("version_no");
                String st = rs.getString("status");
                Instant confAt = toInstant(rs.getTimestamp("confirmed_at"));
                List<PublicResultScoreItem> scores =
                    jdbcTemplate.query(
                        "SELECT slot_no, score, display_label, team_id "
                            + "FROM public.sports_result_version_slot "
                            + "WHERE version_id = ? ORDER BY slot_no ASC",
                        (srs, sRowNum) ->
                            new PublicResultScoreItem(
                                srs.getInt("slot_no"),
                                srs.getInt("score"),
                                srs.getString("display_label"),
                                srs.getObject("team_id", UUID.class)),
                        vId);
                return new PublicEventResultDetail(vNo, st, confAt, scores);
              },
              row.confirmedVersionId());

      if (!results.isEmpty()) {
        resultDetail = results.get(0);
      }
    }

    // Source history (published/confirmed result versions)
    List<PublicSourceHistoryItem> sourceHistory =
        jdbcTemplate.query(
            "SELECT version_no, source_reference, reason, recorded_at, confirmed_at "
                + "FROM public.sports_result_version "
                + "WHERE event_id = ? AND status IN ('CONFIRMADO', 'CORRIGIDO') "
                + "ORDER BY version_no ASC",
            (rs, rowNum) ->
                new PublicSourceHistoryItem(
                    rs.getLong("version_no"),
                    rs.getString("source_reference"),
                    rs.getString("reason"),
                    toInstant(rs.getTimestamp("recorded_at")),
                    toInstant(rs.getTimestamp("confirmed_at"))),
            eventId);

    return Optional.of(
        new PublicEventDetail(
            row.eventId(),
            row.competitionId(),
            row.editionId(),
            row.versionNo(),
            row.competitionName(),
            row.editionName(),
            row.title(),
            row.format(),
            row.status(),
            row.statusReason(),
            row.scheduledAt(),
            row.zoneId(),
            row.venueState(),
            row.sourceReference(),
            row.publishedAt(),
            slots,
            row.sportId(),
            row.sportName(),
            row.categoryId(),
            row.categoryName(),
            row.phaseId(),
            row.phaseName(),
            row.venueId(),
            row.venueName(),
            venueAddress,
            venueMapUrl,
            venueNotes,
            row.venueSpaceId(),
            row.venueSpaceName(),
            venueSpaceNotes,
            resultDetail,
            sourceHistory));
  }

  // ==========================================
  // P4: Organização Esportiva
  // ==========================================

  Optional<PublicOrganizationDetail> findOrganizationDetail(UUID organizationId) {
    if (organizationId == null) {
      return Optional.empty();
    }

    List<OrganizationHeader> headers =
        jdbcTemplate.query(
            "SELECT organization_id, name, slug FROM public.sports_organization "
                + "WHERE organization_id = ? AND status = 'PUBLICADO'",
            (rs, rowNum) ->
                new OrganizationHeader(
                    rs.getObject("organization_id", UUID.class),
                    rs.getString("name"),
                    rs.getString("slug")),
            organizationId);

    if (headers.isEmpty()) {
      return Optional.empty();
    }

    OrganizationHeader header = headers.get(0);

    List<PublicOrganizationTeamItem> teams =
        jdbcTemplate.query(
            "SELECT t.team_id, t.edition_id, e.name AS edition_name, e.competition_id, "
                + "comp.name AS competition_name, t.sport_id, s.name AS sport_name, "
                + "t.category_id, cat.name AS category_name, t.name, t.slug "
                + "FROM public.sports_team t "
                + "JOIN public.sports_edition e ON e.edition_id = t.edition_id "
                + "JOIN public.sports_competition comp ON comp.competition_id = e.competition_id "
                + "JOIN public.sports_sport s ON s.sport_id = t.sport_id "
                + "JOIN public.sports_category cat ON cat.category_id = t.category_id "
                + "WHERE t.organization_id = ? AND t.status = 'PUBLICADO' "
                + "ORDER BY e.start_date DESC NULLS LAST, t.name ASC",
            (rs, rowNum) ->
                new PublicOrganizationTeamItem(
                    rs.getObject("team_id", UUID.class),
                    rs.getObject("edition_id", UUID.class),
                    rs.getString("edition_name"),
                    rs.getObject("competition_id", UUID.class),
                    rs.getString("competition_name"),
                    rs.getObject("sport_id", UUID.class),
                    rs.getString("sport_name"),
                    rs.getObject("category_id", UUID.class),
                    rs.getString("category_name"),
                    rs.getString("name"),
                    rs.getString("slug")),
            organizationId);

    // Published roster members
    List<PublicOrganizationRosterMemberItem> rosterMembers =
        jdbcTemplate.query(
            "SELECT m.athlete_id, m.athlete_name, m.athlete_slug, p.team_id, p.team_name, "
                + "p.edition_id, p.edition_name, p.sport_name, p.category_name, "
                + "m.jersey_number, m.position_name "
                + "FROM public.sports_roster_publication p "
                + "JOIN (SELECT roster_id, max(version_no) AS max_version "
                + "      FROM public.sports_roster_publication "
                + "      GROUP BY roster_id) latest "
                + "ON latest.roster_id = p.roster_id AND latest.max_version = p.version_no "
                + "JOIN public.sports_roster_publication_member m ON m.publication_id = p.publication_id "
                + "JOIN public.sports_team t ON t.team_id = p.team_id AND t.status = 'PUBLICADO' "
                + "JOIN public.sports_athlete a ON a.athlete_id = m.athlete_id "
                + "WHERE p.organization_id = ? AND a.status = 'PUBLICADO' "
                + "ORDER BY m.athlete_name ASC",
            (rs, rowNum) ->
                new PublicOrganizationRosterMemberItem(
                    rs.getObject("athlete_id", UUID.class),
                    rs.getString("athlete_name"),
                    rs.getString("athlete_slug"),
                    rs.getObject("team_id", UUID.class),
                    rs.getString("team_name"),
                    rs.getObject("edition_id", UUID.class),
                    rs.getString("edition_name"),
                    rs.getString("sport_name"),
                    rs.getString("category_name"),
                    rs.getObject("jersey_number") == null ? null : rs.getInt("jersey_number"),
                    rs.getString("position_name")),
            organizationId);

    List<PublicSportsEventSummary> events = findEventsForOrganization(organizationId, 20);

    return Optional.of(
        new PublicOrganizationDetail(
            header.organizationId(), header.name(), header.slug(), teams, rosterMembers, events));
  }

  // ==========================================
  // P5: Equipe
  // ==========================================

  Optional<PublicTeamDetail> findTeamDetail(UUID teamId) {
    if (teamId == null) {
      return Optional.empty();
    }

    String sql =
        "SELECT t.team_id, t.organization_id, o.name AS organization_name, "
            + "t.edition_id, e.name AS edition_name, e.competition_id, comp.name AS competition_name, "
            + "t.sport_id, s.name AS sport_name, t.category_id, cat.name AS category_name, "
            + "t.name, t.slug "
            + "FROM public.sports_team t "
            + "JOIN public.sports_organization o ON o.organization_id = t.organization_id "
            + "JOIN public.sports_edition e ON e.edition_id = t.edition_id "
            + "JOIN public.sports_competition comp ON comp.competition_id = e.competition_id "
            + "JOIN public.sports_sport s ON s.sport_id = t.sport_id "
            + "JOIN public.sports_category cat ON cat.category_id = t.category_id "
            + "WHERE t.team_id = ? AND t.status = 'PUBLICADO' AND o.status = 'PUBLICADO'";

    List<TeamHeader> headers =
        jdbcTemplate.query(
            sql,
            (rs, rowNum) ->
                new TeamHeader(
                    rs.getObject("team_id", UUID.class),
                    rs.getObject("organization_id", UUID.class),
                    rs.getString("organization_name"),
                    rs.getObject("edition_id", UUID.class),
                    rs.getString("edition_name"),
                    rs.getObject("competition_id", UUID.class),
                    rs.getString("competition_name"),
                    rs.getObject("sport_id", UUID.class),
                    rs.getString("sport_name"),
                    rs.getObject("category_id", UUID.class),
                    rs.getString("category_name"),
                    rs.getString("name"),
                    rs.getString("slug")),
            teamId);

    if (headers.isEmpty()) {
      return Optional.empty();
    }

    TeamHeader header = headers.get(0);

    // Published roster
    PublicTeamRosterDetail rosterDetail = null;
    List<PublicTeamRosterDetail> rosters =
        jdbcTemplate.query(
            "SELECT publication_id, version_no, published_at "
                + "FROM public.sports_roster_publication "
                + "WHERE team_id = ? "
                + "ORDER BY version_no DESC LIMIT 1",
            (rs, rowNum) -> {
              UUID pubId = rs.getObject("publication_id", UUID.class);
              long verNo = rs.getLong("version_no");
              Instant pubAt = toInstant(rs.getTimestamp("published_at"));
              List<PublicTeamRosterMemberItem> members =
                  jdbcTemplate.query(
                      "SELECT m.athlete_id, m.athlete_name, m.athlete_slug, "
                          + "m.jersey_number, m.position_name "
                          + "FROM public.sports_roster_publication_member m "
                          + "JOIN public.sports_athlete a ON a.athlete_id = m.athlete_id "
                          + "WHERE m.publication_id = ? AND a.status = 'PUBLICADO' "
                          + "ORDER BY m.jersey_number ASC NULLS LAST, m.athlete_name ASC",
                      (mrs, mRowNum) ->
                          new PublicTeamRosterMemberItem(
                              mrs.getObject("athlete_id", UUID.class),
                              mrs.getString("athlete_name"),
                              mrs.getString("athlete_slug"),
                              mrs.getObject("jersey_number") == null
                                  ? null
                                  : mrs.getInt("jersey_number"),
                              mrs.getString("position_name")),
                      pubId);
              return new PublicTeamRosterDetail(verNo, pubAt, members);
            },
            teamId);

    if (!rosters.isEmpty()) {
      rosterDetail = rosters.get(0);
    }

    List<PublicSportsEventSummary> events = findEventsForTeam(teamId, 20);

    return Optional.of(
        new PublicTeamDetail(
            header.teamId(),
            header.organizationId(),
            header.organizationName(),
            header.editionId(),
            header.editionName(),
            header.competitionId(),
            header.competitionName(),
            header.sportId(),
            header.sportName(),
            header.categoryId(),
            header.categoryName(),
            header.name(),
            header.slug(),
            rosterDetail,
            events));
  }

  // ==========================================
  // P6: Atleta
  // ==========================================

  Optional<PublicAthleteDetail> findAthleteDetail(UUID athleteId) {
    if (athleteId == null) {
      return Optional.empty();
    }

    List<AthleteHeader> headers =
        jdbcTemplate.query(
            "SELECT athlete_id, name, slug FROM public.sports_athlete "
                + "WHERE athlete_id = ? AND status = 'PUBLICADO'",
            (rs, rowNum) ->
                new AthleteHeader(
                    rs.getObject("athlete_id", UUID.class),
                    rs.getString("name"),
                    rs.getString("slug")),
            athleteId);

    if (headers.isEmpty()) {
      return Optional.empty();
    }

    AthleteHeader header = headers.get(0);

    // Participations in published rosters
    List<PublicAthleteTeamParticipation> participations =
        jdbcTemplate.query(
            "SELECT p.team_id, p.team_name, p.organization_id, p.organization_name, "
                + "p.edition_id, p.edition_name, p.sport_name, p.category_name, "
                + "m.jersey_number, m.position_name "
                + "FROM public.sports_roster_publication p "
                + "JOIN (SELECT roster_id, max(version_no) AS max_version "
                + "      FROM public.sports_roster_publication "
                + "      GROUP BY roster_id) latest "
                + "ON latest.roster_id = p.roster_id AND latest.max_version = p.version_no "
                + "JOIN public.sports_roster_publication_member m ON m.publication_id = p.publication_id "
                + "JOIN public.sports_team t ON t.team_id = p.team_id AND t.status = 'PUBLICADO' "
                + "JOIN public.sports_organization o ON o.organization_id = p.organization_id "
                + "JOIN public.sports_athlete a ON a.athlete_id = m.athlete_id "
                + "WHERE m.athlete_id = ? AND o.status = 'PUBLICADO' AND a.status = 'PUBLICADO' "
                + "ORDER BY p.edition_name DESC, p.team_name ASC",
            (rs, rowNum) ->
                new PublicAthleteTeamParticipation(
                    rs.getObject("team_id", UUID.class),
                    rs.getString("team_name"),
                    rs.getObject("organization_id", UUID.class),
                    rs.getString("organization_name"),
                    rs.getObject("edition_id", UUID.class),
                    rs.getString("edition_name"),
                    rs.getString("sport_name"),
                    rs.getString("category_name"),
                    rs.getObject("jersey_number") == null ? null : rs.getInt("jersey_number"),
                    rs.getString("position_name")),
            athleteId);

    List<UUID> teamIds =
        participations.stream().map(PublicAthleteTeamParticipation::teamId).distinct().toList();

    List<PublicSportsEventSummary> events =
        teamIds.isEmpty() ? List.of() : findEventsForTeamList(teamIds, 20);

    // Stats calculation
    int totalParticipations = findPublishedEventCountForTeamList(teamIds);
    int totalWins = 0;
    int totalLosses = 0;

    if (!teamIds.isEmpty()) {
      List<ConfirmedResultRow> confirmedResults = findConfirmedResultsForTeams(teamIds);
      for (ConfirmedResultRow cr : confirmedResults) {
        if (cr.teamScore() > cr.opponentScore()) {
          totalWins++;
        } else if (cr.teamScore() < cr.opponentScore()) {
          totalLosses++;
        }
      }
    }

    PublicAthleteStats stats = new PublicAthleteStats(totalParticipations, totalWins, totalLosses);

    return Optional.of(
        new PublicAthleteDetail(
            header.athleteId(), header.name(), header.slug(), participations, events, stats));
  }

  // ==========================================
  // Event summaries helpers
  // ==========================================

  private List<PublicSportsEventSummary> findEventsForCompetition(UUID competitionId, int limit) {
    String sql =
        "SELECT p.publication_id, p.event_id, p.competition_id, p.edition_id, "
            + "p.competition_name, p.edition_name, p.title, p.event_format, "
            + "p.scheduled_at, p.zone_id, p.venue_state, p.sport_id, p.sport_name, "
            + "p.category_id, p.category_name, p.phase_id, p.phase_name, "
            + "p.venue_id, p.venue_name, p.venue_space_id, p.venue_space_name "
            + "FROM public.sports_event_publication p "
            + "JOIN (SELECT event_id, max(version_no) AS max_version "
            + "      FROM public.sports_event_publication "
            + "      GROUP BY event_id) latest "
            + "ON latest.event_id = p.event_id AND latest.max_version = p.version_no "
            + "JOIN public.sports_event e ON e.event_id = p.event_id "
            + "WHERE p.competition_id = ? AND e.status <> 'RASCUNHO' "
            + "ORDER BY p.scheduled_at ASC, p.event_id ASC LIMIT ?";

    return queryEventSummaries(sql, competitionId, limit);
  }

  private List<PublicSportsEventSummary> findEventsForOrganization(UUID organizationId, int limit) {
    String sql =
        "SELECT DISTINCT p.publication_id, p.event_id, p.competition_id, p.edition_id, "
            + "p.competition_name, p.edition_name, p.title, p.event_format, "
            + "p.scheduled_at, p.zone_id, p.venue_state, p.sport_id, p.sport_name, "
            + "p.category_id, p.category_name, p.phase_id, p.phase_name, "
            + "p.venue_id, p.venue_name, p.venue_space_id, p.venue_space_name "
            + "FROM public.sports_event_publication p "
            + "JOIN (SELECT event_id, max(version_no) AS max_version "
            + "      FROM public.sports_event_publication "
            + "      GROUP BY event_id) latest "
            + "ON latest.event_id = p.event_id AND latest.max_version = p.version_no "
            + "JOIN public.sports_event_publication_slot ps ON ps.publication_id = p.publication_id "
            + "JOIN public.sports_team t ON t.team_id = ps.team_id "
            + "JOIN public.sports_organization o ON o.organization_id = t.organization_id "
            + "JOIN public.sports_event e ON e.event_id = p.event_id "
            + "WHERE t.organization_id = ? AND t.status = 'PUBLICADO' AND o.status = 'PUBLICADO' "
            + "AND e.status <> 'RASCUNHO' "
            + "ORDER BY p.scheduled_at DESC, p.event_id ASC LIMIT ?";

    return queryEventSummaries(sql, organizationId, limit);
  }

  private List<PublicSportsEventSummary> findEventsForTeam(UUID teamId, int limit) {
    return findEventsForTeamList(List.of(teamId), limit);
  }

  private List<PublicSportsEventSummary> findEventsForTeamList(List<UUID> teamIds, int limit) {
    if (teamIds.isEmpty()) {
      return List.of();
    }
    String inSql = String.join(",", Collections.nCopies(teamIds.size(), "?"));
    String sql =
        "SELECT DISTINCT p.publication_id, p.event_id, p.competition_id, p.edition_id, "
            + "p.competition_name, p.edition_name, p.title, p.event_format, "
            + "p.scheduled_at, p.zone_id, p.venue_state, p.sport_id, p.sport_name, "
            + "p.category_id, p.category_name, p.phase_id, p.phase_name, "
            + "p.venue_id, p.venue_name, p.venue_space_id, p.venue_space_name "
            + "FROM public.sports_event_publication p "
            + "JOIN (SELECT event_id, max(version_no) AS max_version "
            + "      FROM public.sports_event_publication "
            + "      GROUP BY event_id) latest "
            + "ON latest.event_id = p.event_id AND latest.max_version = p.version_no "
            + "JOIN public.sports_event_publication_slot ps ON ps.publication_id = p.publication_id "
            + "JOIN public.sports_team t ON t.team_id = ps.team_id "
            + "JOIN public.sports_organization o ON o.organization_id = t.organization_id "
            + "JOIN public.sports_event e ON e.event_id = p.event_id "
            + "WHERE ps.team_id IN ("
            + inSql
            + ") AND t.status = 'PUBLICADO' "
            + "AND o.status = 'PUBLICADO' AND e.status <> 'RASCUNHO' "
            + "ORDER BY p.scheduled_at DESC, p.event_id ASC LIMIT ?";

    List<Object> params = new ArrayList<>(teamIds);
    params.add(limit);

    return queryEventSummariesWithParams(sql, params.toArray());
  }

  private int findPublishedEventCountForTeamList(List<UUID> teamIds) {
    if (teamIds.isEmpty()) {
      return 0;
    }
    String inSql = String.join(",", Collections.nCopies(teamIds.size(), "?"));
    String sql =
        "SELECT count(DISTINCT p.event_id) "
            + "FROM public.sports_event_publication p "
            + "JOIN (SELECT event_id, max(version_no) AS max_version "
            + "      FROM public.sports_event_publication "
            + "      GROUP BY event_id) latest "
            + "ON latest.event_id = p.event_id AND latest.max_version = p.version_no "
            + "JOIN public.sports_event_publication_slot ps ON ps.publication_id = p.publication_id "
            + "JOIN public.sports_team t ON t.team_id = ps.team_id "
            + "JOIN public.sports_organization o ON o.organization_id = t.organization_id "
            + "JOIN public.sports_event e ON e.event_id = p.event_id "
            + "WHERE ps.team_id IN ("
            + inSql
            + ") AND t.status = 'PUBLICADO' "
            + "AND o.status = 'PUBLICADO' AND e.status <> 'RASCUNHO'";

    return jdbcTemplate.queryForObject(sql, Integer.class, teamIds.toArray());
  }

  private List<PublicSportsEventSummary> queryEventSummaries(String sql, UUID param1, int limit) {
    return queryEventSummariesWithParams(sql, new Object[] {param1, limit});
  }

  private List<PublicSportsEventSummary> queryEventSummariesWithParams(
      String sql, Object[] params) {
    List<EventSummaryRow> rows =
        jdbcTemplate.query(
            sql,
            (rs, rowNum) ->
                new EventSummaryRow(
                    rs.getObject("publication_id", UUID.class),
                    rs.getObject("event_id", UUID.class),
                    rs.getObject("competition_id", UUID.class),
                    rs.getObject("edition_id", UUID.class),
                    rs.getString("competition_name"),
                    rs.getString("edition_name"),
                    rs.getString("title"),
                    EventFormat.valueOf(rs.getString("event_format")),
                    rs.getTimestamp("scheduled_at").toInstant(),
                    rs.getString("zone_id"),
                    VenueState.valueOf(rs.getString("venue_state")),
                    rs.getObject("sport_id", UUID.class),
                    rs.getString("sport_name"),
                    rs.getObject("category_id", UUID.class),
                    rs.getString("category_name"),
                    rs.getObject("phase_id", UUID.class),
                    rs.getString("phase_name"),
                    rs.getObject("venue_id", UUID.class),
                    rs.getString("venue_name"),
                    rs.getObject("venue_space_id", UUID.class),
                    rs.getString("venue_space_name")),
            params);

    if (rows.isEmpty()) {
      return List.of();
    }

    List<PublicSportsEventSummary> list = new ArrayList<>();
    for (EventSummaryRow r : rows) {
      List<PublicSlotResponse> slots =
          jdbcTemplate.query(
              "SELECT slot_no, display_label FROM public.sports_event_publication_slot "
                  + "WHERE publication_id = ? ORDER BY slot_no ASC",
              (rs, rowNum) ->
                  new PublicSlotResponse(rs.getInt("slot_no"), rs.getString("display_label")),
              r.publicationId());

      list.add(
          new PublicSportsEventSummary(
              r.eventId(),
              r.competitionId(),
              r.editionId(),
              r.competitionName(),
              r.editionName(),
              r.title(),
              r.format(),
              r.scheduledAt(),
              r.zoneId(),
              r.venueState(),
              slots,
              r.sportId(),
              r.sportName(),
              r.categoryId(),
              r.categoryName(),
              r.phaseId(),
              r.phaseName(),
              r.venueId(),
              r.venueName(),
              r.venueSpaceId(),
              r.venueSpaceName()));
    }
    return list;
  }

  private List<ConfirmedResultRow> findConfirmedResultsForTeams(List<UUID> teamIds) {
    if (teamIds.isEmpty()) {
      return List.of();
    }
    String inSql = String.join(",", Collections.nCopies(teamIds.size(), "?"));
    String sql =
        "SELECT s1.team_id, s1.score AS team_score, s2.score AS opponent_score "
            + "FROM public.sports_result_version_slot s1 "
            + "JOIN public.sports_result_version v ON v.version_id = s1.version_id "
            + "JOIN public.sports_event e ON e.event_id = v.event_id AND e.confirmed_version_id = v.version_id "
            + "JOIN public.sports_result_version_slot s2 ON s2.version_id = v.version_id AND s2.slot_no <> s1.slot_no "
            + "WHERE s1.team_id IN ("
            + inSql
            + ") AND v.status IN ('CONFIRMADO', 'CORRIGIDO')";

    return jdbcTemplate.query(
        sql,
        (rs, rowNum) ->
            new ConfirmedResultRow(
                rs.getObject("team_id", UUID.class),
                rs.getInt("team_score"),
                rs.getInt("opponent_score")),
        teamIds.toArray());
  }

  private static LocalDate toLocalDate(Date date) {
    return date == null ? null : date.toLocalDate();
  }

  private static Instant toInstant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }

  // --- Private Projection Records ---

  private record CompetitionHeader(UUID competitionId, String name, String slug) {}

  private record OrganizationHeader(UUID organizationId, String name, String slug) {}

  private record TeamHeader(
      UUID teamId,
      UUID organizationId,
      String organizationName,
      UUID editionId,
      String editionName,
      UUID competitionId,
      String competitionName,
      UUID sportId,
      String sportName,
      UUID categoryId,
      String categoryName,
      String name,
      String slug) {}

  private record AthleteHeader(UUID athleteId, String name, String slug) {}

  private record VenueRow(
      UUID venueId, UUID competitionId, String name, String address, String mapUrl, String notes) {}

  private record EventDetailRow(
      UUID publicationId,
      UUID eventId,
      UUID competitionId,
      UUID editionId,
      long versionNo,
      String competitionName,
      String editionName,
      String title,
      EventFormat format,
      String status,
      String statusReason,
      Instant scheduledAt,
      String zoneId,
      VenueState venueState,
      String sourceReference,
      Instant publishedAt,
      UUID sportId,
      String sportName,
      UUID categoryId,
      String categoryName,
      UUID phaseId,
      String phaseName,
      UUID venueId,
      String venueName,
      UUID venueSpaceId,
      String venueSpaceName,
      UUID confirmedVersionId) {}

  private record EventSummaryRow(
      UUID publicationId,
      UUID eventId,
      UUID competitionId,
      UUID editionId,
      String competitionName,
      String editionName,
      String title,
      EventFormat format,
      Instant scheduledAt,
      String zoneId,
      VenueState venueState,
      UUID sportId,
      String sportName,
      UUID categoryId,
      String categoryName,
      UUID phaseId,
      String phaseName,
      UUID venueId,
      String venueName,
      UUID venueSpaceId,
      String venueSpaceName) {}

  private record ConfirmedResultRow(UUID teamId, int teamScore, int opponentScore) {}
}
