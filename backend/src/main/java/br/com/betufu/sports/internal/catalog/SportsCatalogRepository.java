package br.com.betufu.sports.internal.catalog;

import br.com.betufu.sports.internal.catalog.SportsCatalogModels.SportsCategorySummary;
import br.com.betufu.sports.internal.catalog.SportsCatalogModels.SportsCompetitionSummary;
import br.com.betufu.sports.internal.catalog.SportsCatalogModels.SportsEditionSummary;
import br.com.betufu.sports.internal.catalog.SportsCatalogModels.SportsPhaseSummary;
import br.com.betufu.sports.internal.catalog.SportsCatalogModels.SportsSportSummary;
import br.com.betufu.sports.internal.catalog.SportsCatalogModels.SportsVenueSpaceSummary;
import br.com.betufu.sports.internal.catalog.SportsCatalogModels.SportsVenueSummary;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("runtime | integrated")
class SportsCatalogRepository {

  private final JdbcTemplate jdbc;

  SportsCatalogRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  long countCompetitions() {
    Long count = jdbc.queryForObject("SELECT count(*) FROM public.sports_competition", Long.class);
    return count == null ? 0 : count;
  }

  List<SportsCompetitionSummary> findCompetitions(boolean global, List<UUID> authorizedIds) {
    if (!global && (authorizedIds == null || authorizedIds.isEmpty())) {
      return List.of();
    }
    String sql =
        "SELECT c.competition_id, c.name, c.slug, c.status, c.lock_version, "
            + "c.sport_id, s.name AS sport_name "
            + "FROM public.sports_competition c "
            + "LEFT JOIN public.sports_sport s ON s.sport_id = c.sport_id ";
    List<Object> args = new ArrayList<>();
    if (global) {
      sql += "ORDER BY lower(c.name), c.competition_id";
    } else {
      String placeholders = String.join(",", authorizedIds.stream().map(id -> "?").toList());
      sql +=
          "WHERE c.competition_id IN ("
              + placeholders
              + ") "
              + "ORDER BY lower(c.name), c.competition_id";
      args.addAll(authorizedIds);
    }
    return jdbc.query(sql, this::mapCompetition, args.toArray());
  }

  Optional<SportsCompetitionSummary> findCompetition(UUID competitionId) {
    return jdbc
        .query(
            "SELECT c.competition_id, c.name, c.slug, c.status, c.lock_version, "
                + "c.sport_id, s.name AS sport_name "
                + "FROM public.sports_competition c "
                + "LEFT JOIN public.sports_sport s ON s.sport_id = c.sport_id "
                + "WHERE c.competition_id = ?",
            (rs, rowNum) -> mapCompetition(rs, rowNum),
            competitionId)
        .stream()
        .findFirst();
  }

  SportsSportSummary createSport(UUID sportId, String name, String slug) {
    jdbc.update(
        "INSERT INTO public.sports_sport (sport_id, name, slug) VALUES (?, ?, ?)",
        sportId,
        name,
        slug);
    return new SportsSportSummary(sportId, name, slug, "ATIVA", 0);
  }

  void assignCompetitionSport(UUID competitionId, UUID sportId) {
    jdbc.update(
        "UPDATE public.sports_competition SET sport_id = ?, updated_at = CURRENT_TIMESTAMP(6) "
            + "WHERE competition_id = ?",
        sportId,
        competitionId);
  }

  Optional<UUID> findCompetitionForSport(UUID sportId) {
    return jdbc
        .query(
            "SELECT competition_id FROM public.sports_competition WHERE sport_id = ?",
            (rs, rowNum) -> rs.getObject("competition_id", UUID.class),
            sportId)
        .stream()
        .findFirst();
  }

  SportsCompetitionSummary createCompetition(
      UUID competitionId, String name, String slug, UUID sportId) {
    jdbc.update(
        "INSERT INTO public.sports_competition (competition_id, name, slug, sport_id) "
            + "VALUES (?, ?, ?, ?)",
        competitionId,
        name,
        slug,
        sportId);
    return findCompetition(competitionId).orElseThrow();
  }

  SportsEditionSummary createEdition(
      UUID editionId, UUID competitionId, String name, String slug, Date startDate, Date endDate) {
    jdbc.update(
        "INSERT INTO public.sports_edition "
            + "(edition_id, competition_id, name, slug, start_date, end_date) "
            + "VALUES (?, ?, ?, ?, ?, ?)",
        editionId,
        competitionId,
        name,
        slug,
        startDate,
        endDate);
    return new SportsEditionSummary(
        editionId,
        competitionId,
        name,
        slug,
        startDate == null ? null : startDate.toLocalDate(),
        endDate == null ? null : endDate.toLocalDate(),
        "ATIVA",
        0);
  }

  SportsCategorySummary createCategory(UUID categoryId, UUID editionId, String name, String slug) {
    jdbc.update(
        "INSERT INTO public.sports_category (category_id, edition_id, name, slug) "
            + "VALUES (?, ?, ?, ?)",
        categoryId,
        editionId,
        name,
        slug);
    return jdbc.queryForObject(
        "SELECT category_id, edition_id, name, slug, status, lock_version "
            + "FROM public.sports_category WHERE category_id = ?",
        (rs, rowNum) ->
            new SportsCategorySummary(
                rs.getObject("category_id", UUID.class),
                rs.getObject("edition_id", UUID.class),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getString("status"),
                rs.getLong("lock_version")),
        categoryId);
  }

  SportsPhaseSummary createPhase(
      UUID phaseId, UUID editionId, UUID categoryId, String name, String slug) {
    jdbc.update(
        "INSERT INTO public.sports_phase "
            + "(phase_id, edition_id, category_id, name, slug) VALUES (?, ?, ?, ?, ?)",
        phaseId,
        editionId,
        categoryId,
        name,
        slug);
    return jdbc.queryForObject(
        "SELECT phase_id, edition_id, category_id, name, slug, status, lock_version "
            + "FROM public.sports_phase WHERE phase_id = ?",
        (rs, rowNum) ->
            new SportsPhaseSummary(
                rs.getObject("phase_id", UUID.class),
                rs.getObject("edition_id", UUID.class),
                rs.getObject("category_id", UUID.class),
                rs.getString("name"),
                rs.getString("slug"),
                rs.getString("status"),
                rs.getLong("lock_version")),
        phaseId);
  }

  SportsVenueSummary createVenue(
      UUID venueId, UUID competitionId, String name, String address, String mapUrl, String notes) {
    jdbc.update(
        "INSERT INTO public.sports_venue "
            + "(venue_id, competition_id, name, address, map_url, notes) VALUES (?, ?, ?, ?, ?, ?)",
        venueId,
        competitionId,
        name,
        address,
        mapUrl,
        notes);
    return findVenue(venueId).orElseThrow();
  }

  SportsVenueSpaceSummary createVenueSpace(UUID spaceId, UUID venueId, String name, String notes) {
    jdbc.update(
        "INSERT INTO public.sports_venue_space "
            + "(venue_space_id, venue_id, name, notes) VALUES (?, ?, ?, ?)",
        spaceId,
        venueId,
        name,
        notes);
    return jdbc.queryForObject(
        "SELECT venue_space_id, venue_id, name, notes, lock_version "
            + "FROM public.sports_venue_space WHERE venue_space_id = ?",
        (rs, rowNum) ->
            new SportsVenueSpaceSummary(
                rs.getObject("venue_space_id", UUID.class),
                rs.getObject("venue_id", UUID.class),
                rs.getString("name"),
                rs.getString("notes"),
                rs.getLong("lock_version")),
        spaceId);
  }

  Optional<UUID> findCompetitionIdForEdition(UUID editionId) {
    return jdbc
        .query(
            "SELECT competition_id FROM public.sports_edition WHERE edition_id = ?",
            (rs, rowNum) -> rs.getObject("competition_id", UUID.class),
            editionId)
        .stream()
        .findFirst();
  }

  Optional<UUID> findCompetitionIdForCategory(UUID categoryId) {
    return jdbc
        .query(
            "SELECT e.competition_id FROM public.sports_category c "
                + "JOIN public.sports_edition e ON e.edition_id = c.edition_id "
                + "WHERE c.category_id = ?",
            (rs, rowNum) -> rs.getObject("competition_id", UUID.class),
            categoryId)
        .stream()
        .findFirst();
  }

  Optional<UUID> findCompetitionIdForPhase(UUID phaseId) {
    return jdbc
        .query(
            "SELECT e.competition_id FROM public.sports_phase p "
                + "JOIN public.sports_edition e ON e.edition_id = p.edition_id "
                + "WHERE p.phase_id = ?",
            (rs, rowNum) -> rs.getObject("competition_id", UUID.class),
            phaseId)
        .stream()
        .findFirst();
  }

  Optional<UUID> findCompetitionIdForVenue(UUID venueId) {
    return jdbc
        .query(
            "SELECT competition_id FROM public.sports_venue WHERE venue_id = ?",
            (rs, rowNum) -> rs.getObject("competition_id", UUID.class),
            venueId)
        .stream()
        .findFirst();
  }

  Optional<UUID> findVenueIdForSpace(UUID spaceId) {
    return jdbc
        .query(
            "SELECT venue_id FROM public.sports_venue_space WHERE venue_space_id = ?",
            (rs, rowNum) -> rs.getObject("venue_id", UUID.class),
            spaceId)
        .stream()
        .findFirst();
  }

  boolean updateSport(UUID id, String name, String slug, long expected) {
    return jdbc.update(
            "UPDATE public.sports_sport SET name = ?, slug = ?, lock_version = lock_version + 1, "
                + "updated_at = CURRENT_TIMESTAMP(6) WHERE sport_id = ? AND lock_version = ?",
            name,
            slug,
            id,
            expected)
        == 1;
  }

  boolean updateEdition(
      UUID id, String name, String slug, Date startDate, Date endDate, long expected) {
    return jdbc.update(
            "UPDATE public.sports_edition SET name = ?, slug = ?, start_date = ?, end_date = ?, "
                + "lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP(6) "
                + "WHERE edition_id = ? AND lock_version = ?",
            name,
            slug,
            startDate,
            endDate,
            id,
            expected)
        == 1;
  }

  boolean updateCategory(UUID id, String name, String slug, long expected) {
    return jdbc.update(
            "UPDATE public.sports_category SET name = ?, slug = ?, lock_version = lock_version + 1, "
                + "updated_at = CURRENT_TIMESTAMP(6) WHERE category_id = ? AND lock_version = ?",
            name,
            slug,
            id,
            expected)
        == 1;
  }

  boolean updatePhase(UUID id, String name, String slug, long expected) {
    return jdbc.update(
            "UPDATE public.sports_phase SET name = ?, slug = ?, lock_version = lock_version + 1, "
                + "updated_at = CURRENT_TIMESTAMP(6) WHERE phase_id = ? AND lock_version = ?",
            name,
            slug,
            id,
            expected)
        == 1;
  }

  boolean updateVenue(
      UUID id, String name, String address, String mapUrl, String notes, long expected) {
    return jdbc.update(
            "UPDATE public.sports_venue SET name = ?, address = ?, map_url = ?, notes = ?, "
                + "lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP(6) "
                + "WHERE venue_id = ? AND lock_version = ?",
            name,
            address,
            mapUrl,
            notes,
            id,
            expected)
        == 1;
  }

  boolean updateVenueSpace(UUID id, String name, String notes, long expected) {
    return jdbc.update(
            "UPDATE public.sports_venue_space SET name = ?, notes = ?, "
                + "lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP(6) "
                + "WHERE venue_space_id = ? AND lock_version = ?",
            name,
            notes,
            id,
            expected)
        == 1;
  }

  Optional<SportsSportSummary> findSport(UUID id) {
    return jdbc
        .query(
            "SELECT sport_id, name, slug, status, lock_version FROM public.sports_sport WHERE sport_id = ?",
            (rs, rowNum) ->
                new SportsSportSummary(
                    rs.getObject("sport_id", UUID.class),
                    rs.getString("name"),
                    rs.getString("slug"),
                    rs.getString("status"),
                    rs.getLong("lock_version")),
            id)
        .stream()
        .findFirst();
  }

  Optional<SportsEditionSummary> findEdition(UUID id) {
    return jdbc
        .query(
            "SELECT edition_id, competition_id, name, slug, start_date, end_date, status, lock_version "
                + "FROM public.sports_edition WHERE edition_id = ?",
            (rs, rowNum) ->
                new SportsEditionSummary(
                    rs.getObject("edition_id", UUID.class),
                    rs.getObject("competition_id", UUID.class),
                    rs.getString("name"),
                    rs.getString("slug"),
                    rs.getObject("start_date", LocalDateMapper.TYPE),
                    rs.getObject("end_date", LocalDateMapper.TYPE),
                    rs.getString("status"),
                    rs.getLong("lock_version")),
            id)
        .stream()
        .findFirst();
  }

  Optional<SportsCategorySummary> findCategory(UUID id) {
    return jdbc
        .query(
            "SELECT category_id, edition_id, name, slug, status, lock_version "
                + "FROM public.sports_category WHERE category_id = ?",
            (rs, rowNum) ->
                new SportsCategorySummary(
                    rs.getObject("category_id", UUID.class),
                    rs.getObject("edition_id", UUID.class),
                    rs.getString("name"),
                    rs.getString("slug"),
                    rs.getString("status"),
                    rs.getLong("lock_version")),
            id)
        .stream()
        .findFirst();
  }

  Optional<SportsPhaseSummary> findPhase(UUID id) {
    return jdbc
        .query(
            "SELECT phase_id, edition_id, category_id, name, slug, status, lock_version "
                + "FROM public.sports_phase WHERE phase_id = ?",
            (rs, rowNum) ->
                new SportsPhaseSummary(
                    rs.getObject("phase_id", UUID.class),
                    rs.getObject("edition_id", UUID.class),
                    rs.getObject("category_id", UUID.class),
                    rs.getString("name"),
                    rs.getString("slug"),
                    rs.getString("status"),
                    rs.getLong("lock_version")),
            id)
        .stream()
        .findFirst();
  }

  Optional<SportsVenueSummary> findVenue(UUID id) {
    List<SportsVenueSummary> venues =
        jdbc.query(
            "SELECT venue_id, competition_id, name, address, map_url, notes, lock_version "
                + "FROM public.sports_venue WHERE venue_id = ?",
            (rs, rowNum) -> mapVenue(rs, rowNum),
            id);
    if (venues.isEmpty()) return Optional.empty();
    return Optional.of(withSpaces(venues.getFirst()));
  }

  Optional<SportsVenueSpaceSummary> findVenueSpace(UUID id) {
    return jdbc
        .query(
            "SELECT venue_space_id, venue_id, name, notes, lock_version "
                + "FROM public.sports_venue_space WHERE venue_space_id = ?",
            (rs, rowNum) ->
                new SportsVenueSpaceSummary(
                    rs.getObject("venue_space_id", UUID.class),
                    rs.getObject("venue_id", UUID.class),
                    rs.getString("name"),
                    rs.getString("notes"),
                    rs.getLong("lock_version")),
            id)
        .stream()
        .findFirst();
  }

  SportsCatalogModels.SportsCatalogTree tree(UUID competitionId) {
    SportsCompetitionSummary competition = findCompetition(competitionId).orElseThrow();
    List<SportsSportSummary> sports =
        jdbc.query(
            "SELECT s.sport_id, s.name, s.slug, s.status, s.lock_version "
                + "FROM public.sports_sport s JOIN public.sports_competition c ON c.sport_id = s.sport_id "
                + "WHERE c.competition_id = ? ORDER BY lower(s.name), s.sport_id",
            (rs, rowNum) ->
                new SportsSportSummary(
                    rs.getObject("sport_id", UUID.class),
                    rs.getString("name"),
                    rs.getString("slug"),
                    rs.getString("status"),
                    rs.getLong("lock_version")),
            competitionId);
    List<SportsEditionSummary> editions =
        jdbc.query(
            "SELECT edition_id, competition_id, name, slug, start_date, end_date, status, lock_version "
                + "FROM public.sports_edition WHERE competition_id = ? ORDER BY lower(name), edition_id",
            (rs, rowNum) ->
                new SportsEditionSummary(
                    rs.getObject("edition_id", UUID.class),
                    rs.getObject("competition_id", UUID.class),
                    rs.getString("name"),
                    rs.getString("slug"),
                    rs.getObject("start_date", java.time.LocalDate.class),
                    rs.getObject("end_date", java.time.LocalDate.class),
                    rs.getString("status"),
                    rs.getLong("lock_version")),
            competitionId);
    List<SportsCategorySummary> categories =
        jdbc.query(
            "SELECT c.category_id, c.edition_id, c.name, c.slug, c.status, c.lock_version "
                + "FROM public.sports_category c JOIN public.sports_edition e ON e.edition_id = c.edition_id "
                + "WHERE e.competition_id = ? ORDER BY lower(c.name), c.category_id",
            (rs, rowNum) ->
                new SportsCategorySummary(
                    rs.getObject("category_id", UUID.class),
                    rs.getObject("edition_id", UUID.class),
                    rs.getString("name"),
                    rs.getString("slug"),
                    rs.getString("status"),
                    rs.getLong("lock_version")),
            competitionId);
    List<SportsPhaseSummary> phases =
        jdbc.query(
            "SELECT p.phase_id, p.edition_id, p.category_id, p.name, p.slug, p.status, p.lock_version "
                + "FROM public.sports_phase p JOIN public.sports_edition e ON e.edition_id = p.edition_id "
                + "WHERE e.competition_id = ? ORDER BY lower(p.name), p.phase_id",
            (rs, rowNum) ->
                new SportsPhaseSummary(
                    rs.getObject("phase_id", UUID.class),
                    rs.getObject("edition_id", UUID.class),
                    rs.getObject("category_id", UUID.class),
                    rs.getString("name"),
                    rs.getString("slug"),
                    rs.getString("status"),
                    rs.getLong("lock_version")),
            competitionId);
    List<SportsVenueSummary> venues =
        jdbc
            .query(
                "SELECT venue_id, competition_id, name, address, map_url, notes, lock_version "
                    + "FROM public.sports_venue WHERE competition_id = ? ORDER BY lower(name), venue_id",
                (rs, rowNum) -> mapVenue(rs, rowNum),
                competitionId)
            .stream()
            .map(this::withSpaces)
            .toList();
    return new SportsCatalogModels.SportsCatalogTree(
        competition, sports, editions, categories, phases, venues);
  }

  private SportsVenueSummary withSpaces(SportsVenueSummary venue) {
    List<SportsVenueSpaceSummary> spaces =
        jdbc.query(
            "SELECT venue_space_id, venue_id, name, notes, lock_version "
                + "FROM public.sports_venue_space WHERE venue_id = ? ORDER BY lower(name), venue_space_id",
            (rs, rowNum) ->
                new SportsVenueSpaceSummary(
                    rs.getObject("venue_space_id", UUID.class),
                    rs.getObject("venue_id", UUID.class),
                    rs.getString("name"),
                    rs.getString("notes"),
                    rs.getLong("lock_version")),
            venue.venueId());
    return new SportsVenueSummary(
        venue.venueId(),
        venue.competitionId(),
        venue.name(),
        venue.address(),
        venue.mapUrl(),
        venue.notes(),
        venue.lockVersion(),
        spaces);
  }

  private SportsCompetitionSummary mapCompetition(java.sql.ResultSet rs, int rowNum)
      throws java.sql.SQLException {
    return new SportsCompetitionSummary(
        rs.getObject("competition_id", UUID.class),
        rs.getString("name"),
        rs.getString("slug"),
        rs.getString("status"),
        rs.getLong("lock_version"),
        rs.getObject("sport_id", UUID.class),
        rs.getString("sport_name"));
  }

  private SportsVenueSummary mapVenue(java.sql.ResultSet rs, int rowNum)
      throws java.sql.SQLException {
    return new SportsVenueSummary(
        rs.getObject("venue_id", UUID.class),
        rs.getObject("competition_id", UUID.class),
        rs.getString("name"),
        rs.getString("address"),
        rs.getString("map_url"),
        rs.getString("notes"),
        rs.getLong("lock_version"),
        List.of());
  }

  private static final class LocalDateMapper {
    private static final Class<java.time.LocalDate> TYPE = java.time.LocalDate.class;
  }
}
