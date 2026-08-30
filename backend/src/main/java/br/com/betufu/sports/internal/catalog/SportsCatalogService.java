package br.com.betufu.sports.internal.catalog;

import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.identity.OperationalIdentityLookup.GrantedRole;
import br.com.betufu.platform.identity.OperationalIdentityLookup.OperationalIdentity;
import br.com.betufu.sports.internal.catalog.SportsCatalogModels.EditionRequest;
import br.com.betufu.sports.internal.catalog.SportsCatalogModels.NameSlugRequest;
import br.com.betufu.sports.internal.catalog.SportsCatalogModels.SportsCategorySummary;
import br.com.betufu.sports.internal.catalog.SportsCatalogModels.SportsCompetitionSummary;
import br.com.betufu.sports.internal.catalog.SportsCatalogModels.SportsEditionSummary;
import br.com.betufu.sports.internal.catalog.SportsCatalogModels.SportsPhaseSummary;
import br.com.betufu.sports.internal.catalog.SportsCatalogModels.SportsSportSummary;
import br.com.betufu.sports.internal.catalog.SportsCatalogModels.SportsVenueSpaceSummary;
import br.com.betufu.sports.internal.catalog.SportsCatalogModels.SportsVenueSummary;
import br.com.betufu.sports.internal.catalog.SportsCatalogModels.VenueRequest;
import br.com.betufu.sports.internal.catalog.SportsCatalogModels.VenueSpaceRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("runtime | integrated")
class SportsCatalogService {

    static final String GENERAL_ADMIN = "ADMINISTRADOR_GERAL";
    static final String COMPETITION_ADMIN = "ADMINISTRADOR_COMPETICAO";
    static final String MODERATOR = "MODERADOR";
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private final SportsCatalogRepository repository;
    private final OperationalIdentityLookup identityLookup;
    private final TransactionTemplate transactions;

    SportsCatalogService(SportsCatalogRepository repository,
            OperationalIdentityLookup identityLookup,
            PlatformTransactionManager transactionManager) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.identityLookup = Objects.requireNonNull(identityLookup, "identityLookup must not be null");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager,
                "transactionManager must not be null"));
    }

    SportsCatalogModels.SportsCatalogScope getScope(String issuer, String subject) {
        OperationalIdentity actor = resolve(issuer, subject);
        Access access = access(actor);
        List<SportsCompetitionSummary> competitions = repository.findCompetitions(access.global(), access.competitionIds());
        return new SportsCatalogModels.SportsCatalogScope(
                competitions,
                access.global() && competitions.isEmpty());
    }

    SportsCatalogModels.SportsCatalogTree getTree(String issuer, String subject, UUID competitionId) {
        OperationalIdentity actor = resolve(issuer, subject);
        requireReadableCompetition(actor, competitionId);
        return transactions.execute(status -> repository.tree(competitionId));
    }

    SportsCompetitionSummary createCompetition(String issuer, String subject, NameSlugRequest request) {
        requireGlobal(resolve(issuer, subject));
        validateNameSlug(request);
        UUID id = UUID.randomUUID();
        return transactions.execute(status -> repository.createCompetition(id, clean(request.name()), request.slug(), null));
    }

    SportsSportSummary createSport(String issuer, String subject, UUID competitionId, NameSlugRequest request) {
        OperationalIdentity actor = resolve(issuer, subject);
        requireWritableCompetition(actor, competitionId);
        validateNameSlug(request);
        UUID id = UUID.randomUUID();
        return transactions.execute(status -> {
            SportsSportSummary sport = repository.createSport(id, clean(request.name()), request.slug());
            // V10 keeps the tracer-compatible one-sport relationship explicit.
            throwIfNoUpdate(repository, competitionId, id);
            return sport;
        });
    }

    SportsEditionSummary createEdition(String issuer, String subject, UUID competitionId, EditionRequest request) {
        OperationalIdentity actor = resolve(issuer, subject);
        requireWritableCompetition(actor, competitionId);
        validateEdition(request);
        UUID id = UUID.randomUUID();
        return transactions.execute(status -> repository.createEdition(id, competitionId, clean(request.name()), request.slug(),
                sqlDate(request.startDate()), sqlDate(request.endDate())));
    }

    SportsCategorySummary createCategory(String issuer, String subject, UUID editionId, NameSlugRequest request) {
        OperationalIdentity actor = resolve(issuer, subject);
        UUID competitionId = repository.findCompetitionIdForEdition(editionId).orElseThrow(SportsCatalogService::notFound);
        requireWritableCompetition(actor, competitionId);
        validateNameSlug(request);
        return transactions.execute(status -> repository.createCategory(UUID.randomUUID(), editionId, clean(request.name()), request.slug()));
    }

    SportsPhaseSummary createPhase(String issuer, String subject, UUID categoryId, NameSlugRequest request) {
        OperationalIdentity actor = resolve(issuer, subject);
        UUID competitionId = repository.findCompetitionIdForCategory(categoryId).orElseThrow(SportsCatalogService::notFound);
        requireWritableCompetition(actor, competitionId);
        validateNameSlug(request);
        UUID editionId = repository.findCategory(categoryId).orElseThrow(SportsCatalogService::notFound).editionId();
        return transactions.execute(status -> repository.createPhase(UUID.randomUUID(), editionId, categoryId,
                clean(request.name()), request.slug()));
    }

    SportsVenueSummary createVenue(String issuer, String subject, UUID competitionId, VenueRequest request) {
        OperationalIdentity actor = resolve(issuer, subject);
        requireWritableCompetition(actor, competitionId);
        validateVenue(request);
        return transactions.execute(status -> repository.createVenue(UUID.randomUUID(), competitionId, clean(request.name()),
                cleanNullable(request.address()), cleanNullable(request.mapUrl()), cleanNullable(request.notes())));
    }

    SportsVenueSpaceSummary createVenueSpace(String issuer, String subject, UUID venueId, VenueSpaceRequest request) {
        OperationalIdentity actor = resolve(issuer, subject);
        UUID competitionId = repository.findCompetitionIdForVenue(venueId).orElseThrow(SportsCatalogService::notFound);
        requireWritableCompetition(actor, competitionId);
        validateVenueSpace(request);
        return transactions.execute(status -> repository.createVenueSpace(UUID.randomUUID(), venueId, clean(request.name()),
                cleanNullable(request.notes())));
    }

    SportsSportSummary updateSport(String issuer, String subject, UUID id, NameSlugRequest request, Long expected) {
        requireExpected(expected);
        OperationalIdentity actor = resolve(issuer, subject);
        SportsCatalogRepositoryAccess access = findSportAccess(id);
        requireWritableCompetition(actor, access.competitionId());
        validateNameSlug(request);
        updateOrConflict(() -> repository.updateSport(id, clean(request.name()), request.slug(), expected));
        return repository.findSport(id).orElseThrow(SportsCatalogService::notFound);
    }

    SportsEditionSummary updateEdition(String issuer, String subject, UUID id, EditionRequest request) {
        requireExpected(request.expectedLockVersion());
        OperationalIdentity actor = resolve(issuer, subject);
        SportsEditionSummary current = repository.findEdition(id).orElseThrow(SportsCatalogService::notFound);
        requireWritableCompetition(actor, current.competitionId());
        validateEdition(request);
        updateOrConflict(() -> repository.updateEdition(id, clean(request.name()), request.slug(), sqlDate(request.startDate()),
                sqlDate(request.endDate()), request.expectedLockVersion()));
        return repository.findEdition(id).orElseThrow(SportsCatalogService::notFound);
    }

    SportsCategorySummary updateCategory(String issuer, String subject, UUID id, NameSlugRequest request, Long expected) {
        requireExpected(expected);
        OperationalIdentity actor = resolve(issuer, subject);
        UUID competitionId = repository.findCompetitionIdForCategory(id).orElseThrow(SportsCatalogService::notFound);
        requireWritableCompetition(actor, competitionId);
        validateNameSlug(request);
        updateOrConflict(() -> repository.updateCategory(id, clean(request.name()), request.slug(), expected));
        return repository.findCategory(id).orElseThrow(SportsCatalogService::notFound);
    }

    SportsPhaseSummary updatePhase(String issuer, String subject, UUID id, NameSlugRequest request, Long expected) {
        requireExpected(expected);
        OperationalIdentity actor = resolve(issuer, subject);
        UUID competitionId = repository.findCompetitionIdForPhase(id).orElseThrow(SportsCatalogService::notFound);
        requireWritableCompetition(actor, competitionId);
        validateNameSlug(request);
        updateOrConflict(() -> repository.updatePhase(id, clean(request.name()), request.slug(), expected));
        return repository.findPhase(id).orElseThrow(SportsCatalogService::notFound);
    }

    SportsVenueSummary updateVenue(String issuer, String subject, UUID id, VenueRequest request) {
        requireExpected(request.expectedLockVersion());
        OperationalIdentity actor = resolve(issuer, subject);
        UUID competitionId = repository.findCompetitionIdForVenue(id).orElseThrow(SportsCatalogService::notFound);
        requireWritableCompetition(actor, competitionId);
        validateVenue(request);
        updateOrConflict(() -> repository.updateVenue(id, clean(request.name()), cleanNullable(request.address()),
                cleanNullable(request.mapUrl()), cleanNullable(request.notes()), request.expectedLockVersion()));
        return repository.findVenue(id).orElseThrow(SportsCatalogService::notFound);
    }

    SportsVenueSpaceSummary updateVenueSpace(String issuer, String subject, UUID id, VenueSpaceRequest request) {
        requireExpected(request.expectedLockVersion());
        OperationalIdentity actor = resolve(issuer, subject);
        UUID venueId = repository.findVenueIdForSpace(id).orElseThrow(SportsCatalogService::notFound);
        UUID competitionId = repository.findCompetitionIdForVenue(venueId).orElseThrow(SportsCatalogService::notFound);
        requireWritableCompetition(actor, competitionId);
        validateVenueSpace(request);
        updateOrConflict(() -> repository.updateVenueSpace(id, clean(request.name()), cleanNullable(request.notes()),
                request.expectedLockVersion()));
        return repository.findVenueSpace(id).orElseThrow(SportsCatalogService::notFound);
    }

    private void throwIfNoUpdate(SportsCatalogRepository repository, UUID competitionId, UUID sportId) {
        repository.assignCompetitionSport(competitionId, sportId);
    }

    private SportsCatalogRepositoryAccess findSportAccess(UUID sportId) {
        SportsSportSummary sport = repository.findSport(sportId).orElseThrow(SportsCatalogService::notFound);
        UUID competitionId = repository.findCompetitionForSport(sportId).orElseThrow(SportsCatalogService::notFound);
        return new SportsCatalogRepositoryAccess(competitionId, sport);
    }

    private OperationalIdentity resolve(String issuer, String subject) {
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
            throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
        }
        return identityLookup.lookupIdentity(issuer, subject)
                .orElseThrow(() -> new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN));
    }

    private static Access access(OperationalIdentity actor) {
        boolean global = actor.roles().stream().anyMatch(role -> GENERAL_ADMIN.equals(role.roleName()) && role.scopeRef() == null);
        Set<UUID> competitions = new LinkedHashSet<>();
        for (GrantedRole role : actor.roles()) {
            if ((COMPETITION_ADMIN.equals(role.roleName()) || MODERATOR.equals(role.roleName())) && role.scopeRef() != null) {
                competitions.add(role.scopeRef());
            }
        }
        return new Access(global, List.copyOf(competitions));
    }

    private static void requireGlobal(OperationalIdentity actor) {
        if (!access(actor).global()) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
        }
    }

    private static void requireReadableCompetition(OperationalIdentity actor, UUID competitionId) {
        if (competitionId == null || (!access(actor).global() && !access(actor).competitionIds().contains(competitionId))) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
        }
    }

    private static void requireWritableCompetition(OperationalIdentity actor, UUID competitionId) {
        if (competitionId == null) {
            throw notFound();
        }
        Access access = access(actor);
        boolean permitted = access.global() || actor.roles().stream().anyMatch(role ->
                COMPETITION_ADMIN.equals(role.roleName()) && competitionId.equals(role.scopeRef()));
        if (!permitted) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
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

    private static void validateEdition(EditionRequest request) {
        if (request == null || request.name() == null || request.slug() == null) throw validation();
        validateNameSlug(new NameSlugRequest(request.name(), request.slug()));
        if (request.startDate() != null && request.endDate() != null && request.endDate().isBefore(request.startDate())) {
            throw validation();
        }
    }

    private static void validateVenue(VenueRequest request) {
        if (request == null || request.name() == null) throw validation();
        requireLength(clean(request.name()), 160);
        requireLengthNullable(request.address(), 320);
        requireLengthNullable(request.mapUrl(), 2048);
        requireLengthNullable(request.notes(), 2000);
    }

    private static void validateVenueSpace(VenueSpaceRequest request) {
        if (request == null || request.name() == null) throw validation();
        requireLength(clean(request.name()), 160);
        requireLengthNullable(request.notes(), 1000);
    }

    private static void requireExpected(Long expected) {
        if (expected == null || expected < 0) throw validation();
    }

    private static void updateOrConflict(BooleanSupplier update) {
        if (!update.getAsBoolean()) throw new ApiProblemException(HttpStatus.CONFLICT, ApiErrorCode.STALE_VERSION);
    }

    private static String clean(String value) { return value == null ? null : value.trim(); }
    private static String cleanNullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static Date sqlDate(LocalDate date) { return date == null ? null : Date.valueOf(date); }
    private static void requireLength(String value, int max) {
        if (value == null || value.isBlank() || value.getBytes(StandardCharsets.UTF_8).length > max) throw validation();
    }
    private static void requireLengthNullable(String value, int max) {
        if (value != null && value.getBytes(StandardCharsets.UTF_8).length > max) throw validation();
    }
    private static ApiProblemException validation() { return new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED); }
    private static ApiProblemException notFound() { return new ApiProblemException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND); }

    private record Access(boolean global, List<UUID> competitionIds) { }
    private record SportsCatalogRepositoryAccess(UUID competitionId, SportsSportSummary sport) { }
    @FunctionalInterface private interface BooleanSupplier { boolean getAsBoolean(); }
}
