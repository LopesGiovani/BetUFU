package br.com.betufu.sports.internal.athlete;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.identity.OperationalIdentityLookup.OperationalIdentity;
import br.com.betufu.sports.EnrollmentFingerprintPort;
import br.com.betufu.sports.EnrollmentFingerprintPort.Fingerprint;
import br.com.betufu.sports.internal.athlete.SportsAthleteModels.SportsAthleteCreateRequest;
import br.com.betufu.sports.internal.athlete.SportsAthleteModels.SportsAthleteSummary;
import br.com.betufu.sports.internal.catalog.SportsCatalogModels.NameSlugRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("runtime | integrated")
class SportsAthleteService {

  private static final String GENERAL_ADMIN = "ADMINISTRADOR_GERAL";
  private static final String COMPETITION_ADMIN = "ADMINISTRADOR_COMPETICAO";
  private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

  private final SportsAthleteRepository repository;
  private final EnrollmentFingerprintPort fingerprintPort;
  private final OperationalIdentityLookup identityLookup;
  private final TransactionTemplate transactions;
  private final ApplicationEventPublisher eventPublisher;

  @Autowired
  SportsAthleteService(
      SportsAthleteRepository repository,
      EnrollmentFingerprintPort fingerprintPort,
      OperationalIdentityLookup identityLookup,
      PlatformTransactionManager transactionManager,
      ApplicationEventPublisher eventPublisher) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.fingerprintPort =
        Objects.requireNonNull(fingerprintPort, "fingerprintPort must not be null");
    this.identityLookup = Objects.requireNonNull(identityLookup, "identityLookup must not be null");
    this.transactions =
        new TransactionTemplate(
            Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
    this.eventPublisher = eventPublisher;
  }

  List<SportsAthleteSummary> listAthletes(String issuer, String subject) {
    requireCatalogAccess(resolve(issuer, subject));
    return repository.findAthletes();
  }

  SportsAthleteSummary createAthlete(
      String issuer, String subject, UUID teamId, SportsAthleteCreateRequest request) {
    OperationalIdentity actor = resolve(issuer, subject);
    UUID competitionId =
        repository.findCompetitionForTeam(teamId).orElseThrow(SportsAthleteService::notFound);
    requireWritableCompetition(actor, competitionId);
    validateCreateRequest(request);

    Optional<Fingerprint> fingerprintOpt;
    try {
      fingerprintOpt = fingerprintPort.computeFingerprint(request.matricula());
    } catch (IllegalArgumentException e) {
      throw validation();
    }

    Fingerprint fingerprint = fingerprintOpt.orElse(null);
    byte[] hash = fingerprint != null ? fingerprint.hash() : null;
    Short version = fingerprint != null ? fingerprint.version() : null;

    SportsAthleteSummary summary;
    try {
      summary =
          transactions.execute(
              status ->
                  repository.createAthlete(
                      UUID.randomUUID(), clean(request.name()), request.slug(), hash, version));
    } catch (DataIntegrityViolationException e) {
      throw new ApiProblemException(HttpStatus.CONFLICT, ApiErrorCode.VALIDATION_FAILED);
    }

    if (eventPublisher != null && summary != null) {
      eventPublisher.publishEvent(new SportsAthleteCreated(summary.athleteId()));
    }

    return summary;
  }

  SportsAthleteSummary updateAthlete(
      String issuer,
      String subject,
      UUID athleteId,
      NameSlugRequest request,
      Long expectedLockVersion) {
    requireExpected(expectedLockVersion);
    requireCatalogAccess(resolve(issuer, subject));
    validateNameSlug(request);
    repository.findAthlete(athleteId).orElseThrow(SportsAthleteService::notFound);
    updateOrConflict(
        () ->
            repository.updateAthlete(
                    athleteId, clean(request.name()), request.slug(), expectedLockVersion)
                > 0);
    return repository.findAthlete(athleteId).orElseThrow(SportsAthleteService::notFound);
  }

  SportsAthleteSummary publishAthlete(
      String issuer, String subject, UUID athleteId, Long expectedLockVersion) {
    requireExpected(expectedLockVersion);
    requireCatalogAccess(resolve(issuer, subject));
    repository.findAthlete(athleteId).orElseThrow(SportsAthleteService::notFound);
    updateOrConflict(() -> repository.publishAthlete(athleteId, expectedLockVersion) > 0);
    return repository.findAthlete(athleteId).orElseThrow(SportsAthleteService::notFound);
  }

  private OperationalIdentity resolve(String issuer, String subject) {
    if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
      throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
    }
    return identityLookup
        .lookupIdentity(issuer, subject)
        .orElseThrow(() -> new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN));
  }

  private static void requireCatalogAccess(OperationalIdentity actor) {
    boolean allowed =
        actor.roles().stream()
            .anyMatch(
                role ->
                    (GENERAL_ADMIN.equals(role.roleName()) && role.scopeRef() == null)
                        || (COMPETITION_ADMIN.equals(role.roleName()) && role.scopeRef() != null));
    if (!allowed) {
      throw new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
    }
  }

  private static void requireWritableCompetition(OperationalIdentity actor, UUID competitionId) {
    if (competitionId == null) {
      throw notFound();
    }
    boolean permitted =
        isGlobal(actor)
            || actor.roles().stream()
                .anyMatch(
                    role ->
                        COMPETITION_ADMIN.equals(role.roleName())
                            && competitionId.equals(role.scopeRef()));
    if (!permitted) {
      throw new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
    }
  }

  private static boolean isGlobal(OperationalIdentity actor) {
    return actor.roles().stream()
        .anyMatch(role -> GENERAL_ADMIN.equals(role.roleName()) && role.scopeRef() == null);
  }

  private static void validateCreateRequest(SportsAthleteCreateRequest request) {
    if (request == null || request.name() == null || request.slug() == null) {
      throw validation();
    }
    requireLength(clean(request.name()), 160);
    requireLength(request.slug(), 96);
    if (!SLUG.matcher(request.slug()).matches()) {
      throw validation();
    }
  }

  private static void validateNameSlug(NameSlugRequest request) {
    if (request == null || request.name() == null || request.slug() == null) {
      throw validation();
    }
    requireLength(clean(request.name()), 160);
    requireLength(request.slug(), 96);
    if (!SLUG.matcher(request.slug()).matches()) {
      throw validation();
    }
  }

  private static void requireExpected(Long expected) {
    if (expected == null || expected < 0) {
      throw validation();
    }
  }

  private static void updateOrConflict(BooleanSupplier update) {
    if (!update.getAsBoolean()) {
      throw new ApiProblemException(HttpStatus.CONFLICT, ApiErrorCode.STALE_VERSION);
    }
  }

  private static String clean(String value) {
    return value == null ? null : value.trim();
  }

  private static void requireLength(String value, int max) {
    if (value == null || value.isBlank() || value.getBytes(StandardCharsets.UTF_8).length > max) {
      throw validation();
    }
  }

  private static ApiProblemException validation() {
    return new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
  }

  private static ApiProblemException notFound() {
    return new ApiProblemException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND);
  }

  @FunctionalInterface
  private interface BooleanSupplier {
    boolean getAsBoolean();
  }
}
