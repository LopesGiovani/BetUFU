package br.com.betufu.sports.internal.athlete;

import br.com.betufu.sports.internal.athlete.SportsAthleteModels.SportsAthleteSummary;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("runtime | integrated")
class SportsAthleteRepository {

  private final JdbcTemplate jdbc;

  SportsAthleteRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  List<SportsAthleteSummary> findAthletes() {
    return jdbc.query(
        "SELECT athlete_id, name, slug, status, lock_version "
            + "FROM public.sports_athlete ORDER BY lower(name), athlete_id",
        this::mapAthlete);
  }

  List<SportsAthleteSummary> findAthletesForCompetitions(List<UUID> competitionIds) {
    if (competitionIds == null || competitionIds.isEmpty()) {
      return List.of();
    }
    String inSql = String.join(",", Collections.nCopies(competitionIds.size(), "?"));
    return jdbc.query(
        "SELECT a.athlete_id, a.name, a.slug, a.status, a.lock_version "
            + "FROM public.sports_athlete a "
            + "WHERE EXISTS ("
            + "SELECT 1 FROM public.sports_roster_member m "
            + "JOIN public.sports_roster r ON r.roster_id = m.roster_id "
            + "JOIN public.sports_team t ON t.team_id = r.team_id "
            + "JOIN public.sports_edition e ON e.edition_id = t.edition_id "
            + "WHERE m.athlete_id = a.athlete_id AND e.competition_id IN ("
            + inSql
            + ")) ORDER BY lower(a.name), a.athlete_id",
        this::mapAthlete,
        competitionIds.toArray());
  }

  Optional<SportsAthleteSummary> findAthlete(UUID athleteId) {
    return jdbc
        .query(
            "SELECT athlete_id, name, slug, status, lock_version "
                + "FROM public.sports_athlete WHERE athlete_id = ?",
            this::mapAthlete,
            athleteId)
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

  SportsAthleteSummary createAthlete(
      UUID athleteId, String name, String slug, byte[] matriculaHmac, Short hmacVersion) {
    jdbc.update(
        "INSERT INTO public.sports_athlete "
            + "(athlete_id, name, slug, matricula_hmac, hmac_version) "
            + "VALUES (?, ?, ?, ?, ?)",
        athleteId,
        name,
        slug,
        matriculaHmac,
        hmacVersion);
    return findAthlete(athleteId).orElseThrow();
  }

  int updateAthlete(UUID athleteId, String name, String slug, long expectedLockVersion) {
    return jdbc.update(
        "UPDATE public.sports_athlete SET name = ?, slug = ?, "
            + "lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP(6) "
            + "WHERE athlete_id = ? AND lock_version = ?",
        name,
        slug,
        athleteId,
        expectedLockVersion);
  }

  int publishAthlete(UUID athleteId, long expectedLockVersion) {
    return jdbc.update(
        "UPDATE public.sports_athlete SET status = 'PUBLICADO', "
            + "lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP(6) "
            + "WHERE athlete_id = ? AND lock_version = ?",
        athleteId,
        expectedLockVersion);
  }

  Optional<SportsAthleteSummary> findAthleteByHmac(byte[] matriculaHmac) {
    if (matriculaHmac == null) {
      return Optional.empty();
    }
    return jdbc
        .query(
            "SELECT athlete_id, name, slug, status, lock_version "
                + "FROM public.sports_athlete WHERE matricula_hmac = ?",
            this::mapAthlete,
            matriculaHmac)
        .stream()
        .findFirst();
  }

  private SportsAthleteSummary mapAthlete(java.sql.ResultSet rs, int rowNum)
      throws java.sql.SQLException {
    return new SportsAthleteSummary(
        rs.getObject("athlete_id", UUID.class),
        rs.getString("name"),
        rs.getString("slug"),
        rs.getString("status"),
        rs.getLong("lock_version"));
  }
}
