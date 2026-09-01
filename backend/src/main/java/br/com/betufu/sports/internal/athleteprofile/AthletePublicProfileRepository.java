package br.com.betufu.sports.internal.athleteprofile;

import br.com.betufu.sports.internal.athleteprofile.AthletePublicProfileModels.AthleteProfileEntity;
import br.com.betufu.sports.internal.athleteprofile.AthletePublicProfileModels.LinkedAthlete;
import java.sql.ResultSet;
import java.sql.SQLException;
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
class AthletePublicProfileRepository {

  private final JdbcTemplate jdbcTemplate;

  AthletePublicProfileRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
  }

  Optional<LinkedAthlete> findLinkedAthleteByIdentityId(UUID identityId) {
    String sql =
        """
                SELECT a.athlete_id, a.name, a.slug
                FROM public.sports_athlete_link_proposal p
                JOIN public.sports_athlete a ON a.athlete_id = p.athlete_id
                WHERE p.identity_id = ? AND p.status = 'ACEITA'
                """;
    return jdbcTemplate.query(sql, this::mapLinkedAthlete, identityId).stream().findFirst();
  }

  Optional<LinkedAthlete> findPublishedAthleteById(UUID athleteId) {
    String sql =
        """
                SELECT athlete_id, name, slug
                FROM public.sports_athlete
                WHERE athlete_id = ? AND status = 'PUBLICADO'
                """;
    return jdbcTemplate.query(sql, this::mapLinkedAthlete, athleteId).stream().findFirst();
  }

  Optional<AthleteProfileEntity> findProfileByAthleteId(UUID athleteId) {
    String sql =
        """
                SELECT athlete_id, course, bio, instagram_url, x_url, linkedin_url, strava_url, youtube_url,
                       lock_version, created_at, updated_at
                FROM public.sports_athlete_public_profile
                WHERE athlete_id = ?
                """;
    return jdbcTemplate.query(sql, this::mapProfileEntity, athleteId).stream().findFirst();
  }

  int insertProfile(
      UUID athleteId,
      String course,
      String bio,
      String instagramUrl,
      String xUrl,
      String linkedinUrl,
      String stravaUrl,
      String youtubeUrl) {
    String sql =
        """
                INSERT INTO public.sports_athlete_public_profile (
                    athlete_id, course, bio, instagram_url, x_url, linkedin_url, strava_url, youtube_url,
                    lock_version, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6))
                """;
    return jdbcTemplate.update(
        sql, athleteId, course, bio, instagramUrl, xUrl, linkedinUrl, stravaUrl, youtubeUrl);
  }

  int updateProfile(
      UUID athleteId,
      String course,
      String bio,
      String instagramUrl,
      String xUrl,
      String linkedinUrl,
      String stravaUrl,
      String youtubeUrl,
      long expectedLockVersion) {
    String sql =
        """
                UPDATE public.sports_athlete_public_profile
                SET course = ?,
                    bio = ?,
                    instagram_url = ?,
                    x_url = ?,
                    linkedin_url = ?,
                    strava_url = ?,
                    youtube_url = ?,
                    lock_version = lock_version + 1,
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE athlete_id = ? AND lock_version = ?
                """;
    return jdbcTemplate.update(
        sql,
        course,
        bio,
        instagramUrl,
        xUrl,
        linkedinUrl,
        stravaUrl,
        youtubeUrl,
        athleteId,
        expectedLockVersion);
  }

  int clearProfile(UUID athleteId, long expectedLockVersion) {
    String sql =
        """
                UPDATE public.sports_athlete_public_profile
                SET course = NULL,
                    bio = NULL,
                    instagram_url = NULL,
                    x_url = NULL,
                    linkedin_url = NULL,
                    strava_url = NULL,
                    youtube_url = NULL,
                    lock_version = lock_version + 1,
                    updated_at = CURRENT_TIMESTAMP(6)
                WHERE athlete_id = ? AND lock_version = ?
                """;
    return jdbcTemplate.update(sql, athleteId, expectedLockVersion);
  }

  private LinkedAthlete mapLinkedAthlete(ResultSet rs, int rowNum) throws SQLException {
    return new LinkedAthlete(
        rs.getObject("athlete_id", UUID.class), rs.getString("name"), rs.getString("slug"));
  }

  private AthleteProfileEntity mapProfileEntity(ResultSet rs, int rowNum) throws SQLException {
    Timestamp createdAt = rs.getTimestamp("created_at");
    Timestamp updatedAt = rs.getTimestamp("updated_at");
    return new AthleteProfileEntity(
        rs.getObject("athlete_id", UUID.class),
        rs.getString("course"),
        rs.getString("bio"),
        rs.getString("instagram_url"),
        rs.getString("x_url"),
        rs.getString("linkedin_url"),
        rs.getString("strava_url"),
        rs.getString("youtube_url"),
        rs.getLong("lock_version"),
        createdAt != null ? createdAt.toInstant() : Instant.now(),
        updatedAt != null ? updatedAt.toInstant() : Instant.now());
  }
}
