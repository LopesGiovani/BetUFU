package br.com.betufu.sports.internal.organization;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.identity.OperationalIdentityLookup.GrantedRole;
import br.com.betufu.platform.identity.OperationalIdentityLookup.OperationalIdentity;
import br.com.betufu.sports.internal.catalog.SportsCatalogModels.NameSlugRequest;
import br.com.betufu.sports.internal.organization.SportsOrganizationModels.SportsOrganizationSummary;
import br.com.betufu.sports.internal.organization.SportsOrganizationModels.SportsTeamSummary;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("runtime | integrated")
class SportsOrganizationService {

    private static final String GENERAL_ADMIN = "ADMINISTRADOR_GERAL";
    private static final String COMPETITION_ADMIN = "ADMINISTRADOR_COMPETICAO";
    private static final String MODERATOR = "MODERADOR";
    private static final Pattern SLUG = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");

    private final SportsOrganizationRepository repository;
    private final OperationalIdentityLookup identityLookup;
    private final TransactionTemplate transactions;

    SportsOrganizationService(
            SportsOrganizationRepository repository,
            OperationalIdentityLookup identityLookup,
            PlatformTransactionManager transactionManager) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.identityLookup = Objects.requireNonNull(identityLookup, "identityLookup must not be null");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager,
                "transactionManager must not be null"));
    }

    List<SportsOrganizationSummary> listOrganizations(String issuer, String subject) {
        requireCatalogAccess(resolve(issuer, subject));
        return repository.findOrganizations();
    }

    List<SportsTeamSummary> listTeams(String issuer, String subject, UUID competitionId) {
        requireReadableCompetition(resolve(issuer, subject), competitionId);
        return repository.findTeams(competitionId);
    }

    SportsOrganizationSummary createOrganization(String issuer, String subject, NameSlugRequest request) {
        requireCatalogAccess(resolve(issuer, subject));
        validateNameSlug(request);
        return transactions.execute(status -> repository.createOrganization(
                UUID.randomUUID(), clean(request.name()), request.slug()));
    }

    SportsOrganizationSummary updateOrganization(
            String issuer, String subject, UUID organizationId, NameSlugRequest request, Long expectedLockVersion) {
        requireExpected(expectedLockVersion);
        requireCatalogAccess(resolve(issuer, subject));
        validateNameSlug(request);
        repository.findOrganization(organizationId).orElseThrow(SportsOrganizationService::notFound);
        updateOrConflict(() -> repository.updateOrganization(
                organizationId, clean(request.name()), request.slug(), expectedLockVersion) > 0);
        return repository.findOrganization(organizationId).orElseThrow(SportsOrganizationService::notFound);
    }

    SportsOrganizationSummary publishOrganization(
            String issuer, String subject, UUID organizationId, Long expectedLockVersion) {
        requireExpected(expectedLockVersion);
        requireCatalogAccess(resolve(issuer, subject));
        repository.findOrganization(organizationId).orElseThrow(SportsOrganizationService::notFound);
        updateOrConflict(() -> repository.publishOrganization(organizationId, expectedLockVersion) > 0);
        return repository.findOrganization(organizationId).orElseThrow(SportsOrganizationService::notFound);
    }

    SportsTeamSummary createTeam(
            String issuer,
            String subject,
            UUID editionId,
            UUID sportId,
            UUID categoryId,
            UUID organizationId,
            NameSlugRequest request) {
        OperationalIdentity actor = resolve(issuer, subject);
        SportsOrganizationRepository.EditionScope scope = repository.findEditionScope(editionId)
                .orElseThrow(SportsOrganizationService::notFound);
        requireWritableCompetition(actor, scope.competitionId());
        validateNameSlug(request);
        if (repository.findOrganization(organizationId).isEmpty()) {
            throw notFound();
        }
        if (!scope.competitionSportId().equals(sportId)) {
            throw validation();
        }
        ensureCategoryBelongsToEdition(categoryId, editionId);
        return transactions.execute(status -> repository.createTeam(
                UUID.randomUUID(), organizationId, scope, sportId, categoryId,
                clean(request.name()), request.slug()));
    }

    SportsTeamSummary updateTeam(
            String issuer, String subject, UUID teamId, NameSlugRequest request, Long expectedLockVersion) {
        requireExpected(expectedLockVersion);
        OperationalIdentity actor = resolve(issuer, subject);
        UUID competitionId = repository.findCompetitionForTeam(teamId)
                .orElseThrow(SportsOrganizationService::notFound);
        requireWritableCompetition(actor, competitionId);
        validateNameSlug(request);
        updateOrConflict(() -> repository.updateTeam(teamId, clean(request.name()), request.slug(), expectedLockVersion) > 0);
        return repository.findTeam(teamId).orElseThrow(SportsOrganizationService::notFound);
    }

    SportsTeamSummary publishTeam(String issuer, String subject, UUID teamId, Long expectedLockVersion) {
        requireExpected(expectedLockVersion);
        OperationalIdentity actor = resolve(issuer, subject);
        UUID competitionId = repository.findCompetitionForTeam(teamId)
                .orElseThrow(SportsOrganizationService::notFound);
        requireWritableCompetition(actor, competitionId);
        updateOrConflict(() -> repository.publishTeam(teamId, expectedLockVersion) > 0);
        return repository.findTeam(teamId).orElseThrow(SportsOrganizationService::notFound);
    }

    private void ensureCategoryBelongsToEdition(UUID categoryId, UUID editionId) {
        // The composite FK is the final guard; this check turns a bad reference into a stable 400.
        boolean belongs = repository.findCategoryEdition(categoryId).map(editionId::equals).orElse(false);
        if (!belongs) {
            throw validation();
        }
    }

    private OperationalIdentity resolve(String issuer, String subject) {
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
            throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
        }
        return identityLookup.lookupIdentity(issuer, subject)
                .orElseThrow(() -> new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN));
    }

    private static void requireCatalogAccess(OperationalIdentity actor) {
        boolean allowed = actor.roles().stream().anyMatch(role ->
                (GENERAL_ADMIN.equals(role.roleName()) && role.scopeRef() == null)
                        || (COMPETITION_ADMIN.equals(role.roleName()) && role.scopeRef() != null));
        if (!allowed) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
        }
    }

    private static void requireReadableCompetition(OperationalIdentity actor, UUID competitionId) {
        if (competitionId == null || (!isGlobal(actor) && !hasCompetition(actor, competitionId))) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
        }
    }

    private static void requireWritableCompetition(OperationalIdentity actor, UUID competitionId) {
        if (competitionId == null) {
            throw notFound();
        }
        boolean permitted = isGlobal(actor) || actor.roles().stream().anyMatch(role ->
                COMPETITION_ADMIN.equals(role.roleName()) && competitionId.equals(role.scopeRef()));
        if (!permitted) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
        }
    }

    private static boolean isGlobal(OperationalIdentity actor) {
        return actor.roles().stream().anyMatch(role -> GENERAL_ADMIN.equals(role.roleName()) && role.scopeRef() == null);
    }

    private static boolean hasCompetition(OperationalIdentity actor, UUID competitionId) {
        return actor.roles().stream().anyMatch(role ->
                (COMPETITION_ADMIN.equals(role.roleName()) || MODERATOR.equals(role.roleName()))
                        && competitionId.equals(role.scopeRef()));
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
