package br.com.betufu.sports.internal.organization;

import br.com.betufu.sports.internal.organization.SportsOrganizationModels.SportsOrganizationSummary;
import br.com.betufu.sports.internal.organization.SportsOrganizationModels.SportsTeamSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("runtime | integrated")
class SportsOrganizationRepository {

  private final JdbcTemplate jdbc;

  SportsOrganizationRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  List<SportsOrganizationSummary> findOrganizations() {
    return jdbc.query(
        "SELECT organization_id, name, slug, status, lock_version "
            + "FROM public.sports_organization ORDER BY lower(name), organization_id",
        this::mapOrganization);
  }

  List<SportsOrganizationSummary> findOrganizations(Collection<UUID> competitionIds) {
    if (competitionIds == null || competitionIds.isEmpty()) {
      return List.of();
    }
    String placeholders = String.join(", ", Collections.nCopies(competitionIds.size(), "?"));
    return jdbc.query(
        "SELECT o.organization_id, o.name, o.slug, o.status, o.lock_version "
            + "FROM public.sports_organization o "
            + "WHERE EXISTS ("
            + "SELECT 1 FROM public.sports_team t "
            + "JOIN public.sports_edition e ON e.edition_id = t.edition_id "
            + "WHERE t.organization_id = o.organization_id "
            + "AND e.competition_id IN ("
            + placeholders
            + ")) ORDER BY lower(o.name), o.organization_id",
        this::mapOrganization,
        competitionIds.toArray());
  }

  Optional<SportsOrganizationSummary> findOrganization(UUID organizationId) {
    return jdbc
        .query(
            "SELECT organization_id, name, slug, status, lock_version "
                + "FROM public.sports_organization WHERE organization_id = ?",
            (rs, rowNum) ->
                new SportsOrganizationSummary(
                    rs.getObject("organization_id", UUID.class),
                    rs.getString("name"),
                    rs.getString("slug"),
                    rs.getString("status"),
                    rs.getLong("lock_version")),
            organizationId)
        .stream()
        .findFirst();
  }

  SportsOrganizationSummary createOrganization(UUID organizationId, String name, String slug) {
    jdbc.update(
        "INSERT INTO public.sports_organization (organization_id, name, slug) VALUES (?, ?, ?)",
        organizationId,
        name,
        slug);
    return findOrganization(organizationId).orElseThrow();
  }

  int updateOrganization(UUID organizationId, String name, String slug, long expectedLockVersion) {
    return jdbc.update(
        "UPDATE public.sports_organization SET name = ?, slug = ?, "
            + "lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP(6) "
            + "WHERE organization_id = ? AND lock_version = ?",
        name,
        slug,
        organizationId,
        expectedLockVersion);
  }

  int publishOrganization(UUID organizationId, long expectedLockVersion) {
    return jdbc.update(
        "UPDATE public.sports_organization SET status = 'PUBLICADO', "
            + "lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP(6) "
            + "WHERE organization_id = ? AND lock_version = ?",
        organizationId,
        expectedLockVersion);
  }

  Optional<EditionScope> findEditionScope(UUID editionId) {
    return jdbc
        .query(
            "SELECT e.edition_id, e.competition_id, c.sport_id "
                + "FROM public.sports_edition e "
                + "JOIN public.sports_competition c ON c.competition_id = e.competition_id "
                + "WHERE e.edition_id = ?",
            (rs, rowNum) ->
                new EditionScope(
                    rs.getObject("edition_id", UUID.class),
                    rs.getObject("competition_id", UUID.class),
                    rs.getObject("sport_id", UUID.class)),
            editionId)
        .stream()
        .findFirst();
  }

  Optional<UUID> findCompetitionForTeam(UUID teamId) {
    return jdbc
        .query(
            "SELECT e.competition_id FROM public.sports_team t "
                + "JOIN public.sports_edition e ON e.edition_id = t.edition_id "
                + "WHERE t.team_id = ?",
            (rs, rowNum) -> rs.getObject("competition_id", UUID.class),
            teamId)
        .stream()
        .findFirst();
  }

  Optional<UUID> findCategoryEdition(UUID categoryId) {
    return jdbc
        .query(
            "SELECT edition_id FROM public.sports_category WHERE category_id = ?",
            (rs, rowNum) -> rs.getObject("edition_id", UUID.class),
            categoryId)
        .stream()
        .findFirst();
  }

  Optional<SportsTeamSummary> findTeam(UUID teamId) {
    return jdbc.query(teamSql("WHERE t.team_id = ?"), this::mapTeam, teamId).stream().findFirst();
  }

  List<SportsTeamSummary> findTeams(UUID competitionId) {
    return jdbc.query(
        teamSql("WHERE e.competition_id = ? ORDER BY lower(o.name), lower(t.name), t.team_id"),
        this::mapTeam,
        competitionId);
  }

  SportsTeamSummary createTeam(
      UUID teamId,
      UUID organizationId,
      EditionScope scope,
      UUID sportId,
      UUID categoryId,
      String name,
      String slug) {
    jdbc.update(
        "INSERT INTO public.sports_team "
            + "(team_id, organization_id, edition_id, sport_id, category_id, name, slug) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)",
        teamId,
        organizationId,
        scope.editionId(),
        sportId,
        categoryId,
        name,
        slug);
    return findTeam(teamId).orElseThrow();
  }

  int updateTeam(UUID teamId, String name, String slug, long expectedLockVersion) {
    return jdbc.update(
        "UPDATE public.sports_team SET name = ?, slug = ?, "
            + "lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP(6) "
            + "WHERE team_id = ? AND lock_version = ?",
        name,
        slug,
        teamId,
        expectedLockVersion);
  }

  int publishTeam(UUID teamId, long expectedLockVersion) {
    return jdbc.update(
        "UPDATE public.sports_team SET status = 'PUBLICADO', "
            + "lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP(6) "
            + "WHERE team_id = ? AND lock_version = ?",
        teamId,
        expectedLockVersion);
  }

  private String teamSql(String suffix) {
    return "SELECT t.team_id, t.organization_id, o.name AS organization_name, "
        + "e.competition_id, t.edition_id, t.sport_id, s.name AS sport_name, "
        + "t.category_id, ca.name AS category_name, t.name, t.slug, t.status, t.lock_version "
        + "FROM public.sports_team t "
        + "JOIN public.sports_organization o ON o.organization_id = t.organization_id "
        + "JOIN public.sports_edition e ON e.edition_id = t.edition_id "
        + "JOIN public.sports_sport s ON s.sport_id = t.sport_id "
        + "JOIN public.sports_category ca ON ca.category_id = t.category_id "
        + suffix;
  }

  private SportsTeamSummary mapTeam(java.sql.ResultSet rs, int rowNum)
      throws java.sql.SQLException {
    return new SportsTeamSummary(
        rs.getObject("team_id", UUID.class),
        rs.getObject("organization_id", UUID.class),
        rs.getString("organization_name"),
        rs.getObject("competition_id", UUID.class),
        rs.getObject("edition_id", UUID.class),
        rs.getObject("sport_id", UUID.class),
        rs.getString("sport_name"),
        rs.getObject("category_id", UUID.class),
        rs.getString("category_name"),
        rs.getString("name"),
        rs.getString("slug"),
        rs.getString("status"),
        rs.getLong("lock_version"));
  }

  private SportsOrganizationSummary mapOrganization(ResultSet rs, int rowNum) throws SQLException {
    return new SportsOrganizationSummary(
        rs.getObject("organization_id", UUID.class),
        rs.getString("name"),
        rs.getString("slug"),
        rs.getString("status"),
        rs.getLong("lock_version"));
  }

  record EditionScope(UUID editionId, UUID competitionId, UUID competitionSportId) {}
}
