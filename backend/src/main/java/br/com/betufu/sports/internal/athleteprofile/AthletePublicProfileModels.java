package br.com.betufu.sports.internal.athleteprofile;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class AthletePublicProfileModels {

  private AthletePublicProfileModels() {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record AthletePublicProfile(
      UUID athleteId,
      String athleteName,
      String athleteSlug,
      String course,
      String bio,
      String instagramUrl,
      String xUrl,
      String linkedinUrl,
      String stravaUrl,
      String youtubeUrl,
      long lockVersion,
      Instant updatedAt) {

    public AthletePublicProfile {
      Objects.requireNonNull(athleteId, "athleteId must not be null");
      Objects.requireNonNull(athleteName, "athleteName must not be null");
      Objects.requireNonNull(athleteSlug, "athleteSlug must not be null");
      Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
  }

  public record AthletePublicProfileUpdateRequest(
      @Size(max = 120, message = "Curso deve ter no máximo 120 caracteres") String course,
      @Size(max = 500, message = "Biografia deve ter no máximo 500 caracteres") String bio,
      @Size(max = 255, message = "URL do Instagram deve ter no máximo 255 caracteres")
          String instagramUrl,
      @Size(max = 255, message = "URL do X deve ter no máximo 255 caracteres") String xUrl,
      @Size(max = 255, message = "URL do LinkedIn deve ter no máximo 255 caracteres")
          String linkedinUrl,
      @Size(max = 255, message = "URL do Strava deve ter no máximo 255 caracteres")
          String stravaUrl,
      @Size(max = 255, message = "URL do YouTube deve ter no máximo 255 caracteres")
          String youtubeUrl,
      Long lockVersion) {}

  public record AthleteProfileEntity(
      UUID athleteId,
      String course,
      String bio,
      String instagramUrl,
      String xUrl,
      String linkedinUrl,
      String stravaUrl,
      String youtubeUrl,
      long lockVersion,
      Instant createdAt,
      Instant updatedAt) {

    public AthleteProfileEntity {
      Objects.requireNonNull(athleteId, "athleteId must not be null");
      Objects.requireNonNull(createdAt, "createdAt must not be null");
      Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
  }

  public record LinkedAthlete(UUID athleteId, String name, String slug) {

    public LinkedAthlete {
      Objects.requireNonNull(athleteId, "athleteId must not be null");
      Objects.requireNonNull(name, "name must not be null");
      Objects.requireNonNull(slug, "slug must not be null");
    }
  }
}
