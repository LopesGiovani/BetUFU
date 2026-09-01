package br.com.betufu.sports.internal.athleteprofile;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.sports.internal.athleteprofile.AthletePublicProfileModels.AthleteProfileEntity;
import br.com.betufu.sports.internal.athleteprofile.AthletePublicProfileModels.AthletePublicProfile;
import br.com.betufu.sports.internal.athleteprofile.AthletePublicProfileModels.AthletePublicProfileUpdateRequest;
import br.com.betufu.sports.internal.athleteprofile.AthletePublicProfileModels.LinkedAthlete;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("runtime | integrated")
class AthletePublicProfileService {

  private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>", Pattern.DOTALL);
  private static final Pattern SCRIPT_OR_EVENT_PATTERN =
      Pattern.compile(
          "(?i)(<script|<iframe|<embed|<object|javascript:|data:|onload|onerror|onclick)",
          Pattern.CASE_INSENSITIVE);

  private static final Set<String> INSTAGRAM_HOSTS =
      Set.of("instagram.com", "www.instagram.com", "instagr.am");
  private static final Set<String> X_HOSTS =
      Set.of("x.com", "www.x.com", "twitter.com", "www.twitter.com");
  private static final Set<String> LINKEDIN_HOSTS = Set.of("linkedin.com", "www.linkedin.com");
  private static final Set<String> STRAVA_HOSTS = Set.of("strava.com", "www.strava.com");
  private static final Set<String> YOUTUBE_HOSTS =
      Set.of("youtube.com", "www.youtube.com", "youtu.be");

  private final AthletePublicProfileRepository repository;
  private final OperationalIdentityLookup identityLookup;
  private final TransactionTemplate transactions;

  AthletePublicProfileService(
      AthletePublicProfileRepository repository,
      OperationalIdentityLookup identityLookup,
      PlatformTransactionManager transactionManager) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.identityLookup = Objects.requireNonNull(identityLookup, "identityLookup must not be null");
    this.transactions =
        new TransactionTemplate(
            Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
  }

  AthletePublicProfile getMyProfile(String issuer, String subject) {
    UUID identityId = resolveIdentityId(issuer, subject);
    LinkedAthlete athlete =
        repository
            .findLinkedAthleteByIdentityId(identityId)
            .orElseThrow(AthletePublicProfileService::notFound);

    return repository
        .findProfileByAthleteId(athlete.athleteId())
        .map(entity -> toPublicProfile(athlete, entity))
        .orElseGet(() -> toEmptyProfile(athlete));
  }

  AthletePublicProfile updateMyProfile(
      String issuer, String subject, AthletePublicProfileUpdateRequest request) {
    UUID identityId = resolveIdentityId(issuer, subject);
    LinkedAthlete athlete =
        repository
            .findLinkedAthleteByIdentityId(identityId)
            .orElseThrow(AthletePublicProfileService::notFound);

    String sanitizedCourse = sanitizeText(request.course(), 120, "Curso");
    String sanitizedBio = sanitizeText(request.bio(), 500, "Biografia");
    String validatedInstagram =
        validateSocialUrl(request.instagramUrl(), "Instagram", INSTAGRAM_HOSTS);
    String validatedX = validateSocialUrl(request.xUrl(), "X (Twitter)", X_HOSTS);
    String validatedLinkedIn = validateSocialUrl(request.linkedinUrl(), "LinkedIn", LINKEDIN_HOSTS);
    String validatedStrava = validateSocialUrl(request.stravaUrl(), "Strava", STRAVA_HOSTS);
    String validatedYouTube = validateSocialUrl(request.youtubeUrl(), "YouTube", YOUTUBE_HOSTS);

    return transactions.execute(
        status -> {
          Optional<AthleteProfileEntity> existingOpt =
              repository.findProfileByAthleteId(athlete.athleteId());
          if (existingOpt.isPresent()) {
            AthleteProfileEntity existing = existingOpt.get();
            if (request.lockVersion() != null && request.lockVersion() != existing.lockVersion()) {
              throw conflict();
            }
            long expectedLockVersion =
                request.lockVersion() != null ? request.lockVersion() : existing.lockVersion();
            int updated =
                repository.updateProfile(
                    athlete.athleteId(),
                    sanitizedCourse,
                    sanitizedBio,
                    validatedInstagram,
                    validatedX,
                    validatedLinkedIn,
                    validatedStrava,
                    validatedYouTube,
                    expectedLockVersion);
            if (updated == 0) {
              throw conflict();
            }
          } else {
            if (request.lockVersion() != null && request.lockVersion() != 0) {
              throw conflict();
            }
            repository.insertProfile(
                athlete.athleteId(),
                sanitizedCourse,
                sanitizedBio,
                validatedInstagram,
                validatedX,
                validatedLinkedIn,
                validatedStrava,
                validatedYouTube);
          }

          AthleteProfileEntity updatedEntity =
              repository
                  .findProfileByAthleteId(athlete.athleteId())
                  .orElseThrow(AthletePublicProfileService::notFound);
          return toPublicProfile(athlete, updatedEntity);
        });
  }

  AthletePublicProfile clearMyProfile(String issuer, String subject) {
    UUID identityId = resolveIdentityId(issuer, subject);
    LinkedAthlete athlete =
        repository
            .findLinkedAthleteByIdentityId(identityId)
            .orElseThrow(AthletePublicProfileService::notFound);

    return transactions.execute(
        status -> {
          Optional<AthleteProfileEntity> existingOpt =
              repository.findProfileByAthleteId(athlete.athleteId());
          if (existingOpt.isPresent()) {
            AthleteProfileEntity existing = existingOpt.get();
            int updated = repository.clearProfile(athlete.athleteId(), existing.lockVersion());
            if (updated == 0) {
              throw conflict();
            }
            AthleteProfileEntity updatedEntity =
                repository
                    .findProfileByAthleteId(athlete.athleteId())
                    .orElseThrow(AthletePublicProfileService::notFound);
            return toPublicProfile(athlete, updatedEntity);
          }
          return toEmptyProfile(athlete);
        });
  }

  AthletePublicProfile getPublicProfile(UUID athleteId) {
    if (athleteId == null) {
      throw notFound();
    }
    LinkedAthlete athlete =
        repository
            .findPublishedAthleteById(athleteId)
            .orElseThrow(AthletePublicProfileService::notFound);

    return repository
        .findProfileByAthleteId(athleteId)
        .map(entity -> toPublicProfile(athlete, entity))
        .orElseGet(() -> toEmptyProfile(athlete));
  }

  private static AthletePublicProfile toPublicProfile(
      LinkedAthlete athlete, AthleteProfileEntity entity) {
    return new AthletePublicProfile(
        athlete.athleteId(),
        athlete.name(),
        athlete.slug(),
        entity.course(),
        entity.bio(),
        entity.instagramUrl(),
        entity.xUrl(),
        entity.linkedinUrl(),
        entity.stravaUrl(),
        entity.youtubeUrl(),
        entity.lockVersion(),
        entity.updatedAt());
  }

  private static AthletePublicProfile toEmptyProfile(LinkedAthlete athlete) {
    return new AthletePublicProfile(
        athlete.athleteId(),
        athlete.name(),
        athlete.slug(),
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        0L,
        Instant.now());
  }

  private UUID resolveIdentityId(String issuer, String subject) {
    if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
      throw unauthorized();
    }
    return identityLookup
        .findIdentityId(issuer, subject)
        .orElseThrow(AthletePublicProfileService::unauthorized);
  }

  private static String sanitizeText(String input, int maxLength, String fieldName) {
    if (input == null) {
      return null;
    }
    String trimmed = input.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    if (trimmed.length() > maxLength) {
      throw validation(fieldName + " excede o limite de " + maxLength + " caracteres");
    }
    if (HTML_TAG_PATTERN.matcher(trimmed).find()
        || SCRIPT_OR_EVENT_PATTERN.matcher(trimmed).find()) {
      throw validation("HTML ou scripts não são permitidos em " + fieldName);
    }
    return trimmed;
  }

  private static String validateSocialUrl(String url, String platform, Set<String> allowedHosts) {
    if (url == null) {
      return null;
    }
    String trimmed = url.trim();
    if (trimmed.isEmpty()) {
      return null;
    }
    if (trimmed.length() > 255) {
      throw validation("URL de " + platform + " excede 255 caracteres");
    }
    if (SCRIPT_OR_EVENT_PATTERN.matcher(trimmed).find()
        || HTML_TAG_PATTERN.matcher(trimmed).find()) {
      throw validation("URL de " + platform + " contém caracteres ou protocolo inválido");
    }
    URI uri;
    try {
      uri = URI.create(trimmed);
    } catch (Exception e) {
      throw validation("URL de " + platform + " inválida");
    }
    if (!"https".equalsIgnoreCase(uri.getScheme())) {
      throw validation("URL de " + platform + " deve usar o protocolo HTTPS");
    }
    if (uri.getUserInfo() != null) {
      throw validation("URL de " + platform + " não pode conter credenciais de usuário");
    }
    String host = uri.getHost();
    if (host == null) {
      throw validation("URL de " + platform + " com host inválido");
    }
    String lowerHost = host.toLowerCase(Locale.ROOT);
    if (!allowedHosts.contains(lowerHost)) {
      throw validation("Host não permitido para " + platform + ": " + host);
    }
    String path = uri.getRawPath();
    if (path == null || path.isBlank() || "/".equals(path)) {
      throw validation("URL de " + platform + " deve conter o caminho do perfil");
    }
    return trimmed;
  }

  private static ApiProblemException validation(String ignoredDetail) {
    return new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
  }

  private static ApiProblemException notFound() {
    return new ApiProblemException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND);
  }

  private static ApiProblemException unauthorized() {
    return new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
  }

  private static ApiProblemException conflict() {
    return new ApiProblemException(HttpStatus.CONFLICT, ApiErrorCode.STALE_VERSION);
  }
}
