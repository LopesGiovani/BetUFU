package br.com.betufu.sports.internal.standings;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.identity.OperationalIdentityLookup.OperationalIdentity;
import br.com.betufu.sports.internal.standings.SportsStandingsModels.SportsStandingsCorrectRequest;
import br.com.betufu.sports.internal.standings.SportsStandingsModels.SportsStandingsCreateRequest;
import br.com.betufu.sports.internal.standings.SportsStandingsModels.SportsStandingsPublishRequest;
import br.com.betufu.sports.internal.standings.SportsStandingsModels.SportsStandingsRow;
import br.com.betufu.sports.internal.standings.SportsStandingsModels.SportsStandingsSnapshot;
import br.com.betufu.sports.internal.standings.SportsStandingsModels.SportsStandingsSummary;
import br.com.betufu.sports.internal.standings.SportsStandingsModels.SportsStandingsUpdateRequest;
import br.com.betufu.sports.internal.standings.SportsStandingsRepository.EditionContext;
import br.com.betufu.sports.internal.standings.SportsStandingsRepository.PublicationRecord;
import br.com.betufu.sports.internal.standings.SportsStandingsRepository.StandingsRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("runtime | integrated")
public class SportsStandingsService {

    private static final String GENERAL_ADMIN = "ADMINISTRADOR_GERAL";
    private static final String COMPETITION_ADMIN = "ADMINISTRADOR_COMPETICAO";

    private final SportsStandingsRepository repository;
    private final OperationalIdentityLookup identityLookup;
    private final TransactionTemplate transactions;

    SportsStandingsService(
            SportsStandingsRepository repository,
            OperationalIdentityLookup identityLookup,
            PlatformTransactionManager transactionManager) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.identityLookup = Objects.requireNonNull(identityLookup, "identityLookup must not be null");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(
                transactionManager, "transactionManager must not be null"));
    }

    public SportsStandingsSnapshot getPublicStandings(UUID standingsId) {
        StandingsRecord standings = repository.findStandingsRecord(standingsId)
                .orElseThrow(SportsStandingsService::notFound);

        if (!"PUBLICADO".equals(standings.status())) {
            throw notFound();
        }

        PublicationRecord publication = repository.findLatestPublication(standingsId)
                .orElseThrow(SportsStandingsService::notFound);
        List<String> columns = repository.findPublicationColumns(publication.publicationId());
        List<SportsStandingsRow> rows = repository.findPublicationRows(publication.publicationId());

        return buildSnapshot(standings, Optional.of(publication), columns, rows);
    }

    public SportsStandingsSnapshot getStandingsPreview(String issuer, String subject, UUID standingsId) {
        OperationalIdentity actor = resolveIdentity(issuer, subject);
        StandingsRecord standings = repository.findStandingsRecord(standingsId)
                .orElseThrow(SportsStandingsService::notFound);
        requireScopeAccess(actor, standings.competitionId());

        Optional<PublicationRecord> publication = repository.findLatestPublication(standingsId);
        List<String> columns;
        List<SportsStandingsRow> rows;

        if ("PUBLICADO".equals(standings.status()) && publication.isPresent()) {
            columns = repository.findPublicationColumns(publication.get().publicationId());
            rows = repository.findPublicationRows(publication.get().publicationId());
        } else {
            columns = repository.findColumns(standingsId);
            rows = repository.findRows(standingsId);
        }

        return buildSnapshot(standings, publication, columns, rows);
    }

    public List<SportsStandingsSummary> listStandingsByEdition(String issuer, String subject, UUID editionId) {
        OperationalIdentity actor = resolveIdentity(issuer, subject);
        EditionContext edition = repository.findEditionContext(editionId)
                .orElseThrow(SportsStandingsService::notFound);
        requireScopeAccess(actor, edition.competitionId());

        return repository.listStandingsByEdition(editionId);
    }

    public SportsStandingsSummary createStandings(
            String issuer,
            String subject,
            UUID editionId,
            SportsStandingsCreateRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validateCreateRequest(request);

        OperationalIdentity actor = resolveIdentity(issuer, subject);
        EditionContext edition = repository.findEditionContext(editionId)
                .orElseThrow(SportsStandingsService::notFound);
        requireWritableCompetition(actor, edition.competitionId());

        if (!repository.catalogEntitiesExist(editionId, request.sportId(), request.categoryId(), request.phaseId())) {
            throw notFound();
        }

        return repository.createStandings(edition.competitionId(), editionId, request);
    }

    public SportsStandingsSnapshot updateStandings(
            String issuer,
            String subject,
            UUID standingsId,
            Long expectedLockVersion,
            SportsStandingsUpdateRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        requireExpected(expectedLockVersion);
        validateUpdateRequest(request);

        OperationalIdentity actor = resolveIdentity(issuer, subject);
        StandingsRecord standings = repository.findStandingsRecord(standingsId)
                .orElseThrow(SportsStandingsService::notFound);
        requireWritableCompetition(actor, standings.competitionId());

        // Published snapshots are immutable; corrections create the next version.
        if (!"RASCUNHO".equals(standings.status())) {
            throw validation();
        }

        if (!repository.catalogEntitiesExist(standings.editionId(), request.sportId(), request.categoryId(), request.phaseId())) {
            throw notFound();
        }

        validateColumnsAndRows(standings.editionId(), request.columns(), request.rows());

        return transactions.execute(status -> {
            int updated = repository.updateStandingsMetadata(standingsId, request, expectedLockVersion);
            if (updated == 0) {
                throw staleVersion();
            }
            repository.replaceColumnsAndRows(standingsId, request.columns(), request.rows());
            StandingsRecord reloaded = repository.findStandingsRecord(standingsId).orElseThrow();
            List<String> cols = repository.findColumns(standingsId);
            List<SportsStandingsRow> rws = repository.findRows(standingsId);
            Optional<PublicationRecord> pub = repository.findLatestPublication(standingsId);
            return buildSnapshot(reloaded, pub, cols, rws);
        });
    }

    public SportsStandingsSnapshot publishStandings(
            String issuer,
            String subject,
            UUID standingsId,
            Long expectedLockVersion,
            SportsStandingsPublishRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        requireExpected(expectedLockVersion);
        if (request.sourceReference() == null || request.sourceReference().isBlank()) {
            throw validation();
        }

        OperationalIdentity actor = resolveIdentity(issuer, subject);
        StandingsRecord standings = repository.findStandingsRecord(standingsId)
                .orElseThrow(SportsStandingsService::notFound);
        requireWritableCompetition(actor, standings.competitionId());

        if (!"RASCUNHO".equals(standings.status())) {
            throw validation();
        }

        List<String> columns = repository.findColumns(standingsId);
        List<SportsStandingsRow> rows = repository.findRows(standingsId);
        if (columns.isEmpty() || rows.isEmpty()) {
            throw validation();
        }

        return transactions.execute(status -> {
            int updated = repository.setStandingsPublished(standingsId, expectedLockVersion);
            if (updated == 0) {
                throw staleVersion();
            }
            repository.createPublication(
                    standings, actor.identityId(), request.sourceReference(), null, expectedLockVersion, columns, rows);

            StandingsRecord reloaded = repository.findStandingsRecord(standingsId).orElseThrow();
            Optional<PublicationRecord> publication = repository.findLatestPublication(standingsId);
            return buildSnapshot(reloaded, publication, columns, rows);
        });
    }

    public SportsStandingsSnapshot correctStandings(
            String issuer,
            String subject,
            UUID standingsId,
            Long expectedLockVersion,
            SportsStandingsCorrectRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        requireExpected(expectedLockVersion);
        if (request.reason() == null || request.reason().isBlank()
                || request.sourceReference() == null || request.sourceReference().isBlank()) {
            throw validation();
        }

        OperationalIdentity actor = resolveIdentity(issuer, subject);
        StandingsRecord standings = repository.findStandingsRecord(standingsId)
                .orElseThrow(SportsStandingsService::notFound);
        requireWritableCompetition(actor, standings.competitionId());

        if (!"PUBLICADO".equals(standings.status())) {
            throw validation();
        }

        boolean hasNewData = request.columns() != null && request.rows() != null;
        if (hasNewData) {
            validateColumnsAndRows(standings.editionId(), request.columns(), request.rows());
        }

        return transactions.execute(status -> {
            if (hasNewData) {
                repository.replaceColumnsAndRows(standingsId, request.columns(), request.rows());
            }
            int updated = repository.updateStandingsLockVersion(standingsId, expectedLockVersion);
            if (updated == 0) {
                throw staleVersion();
            }

            List<String> cols = repository.findColumns(standingsId);
            List<SportsStandingsRow> rws = repository.findRows(standingsId);
            repository.createPublication(
                    standings, actor.identityId(), request.sourceReference(), request.reason(),
                    expectedLockVersion, cols, rws);

            StandingsRecord reloaded = repository.findStandingsRecord(standingsId).orElseThrow();
            Optional<PublicationRecord> publication = repository.findLatestPublication(standingsId);
            return buildSnapshot(reloaded, publication, cols, rws);
        });
    }

    private void validateColumnsAndRows(UUID editionId, List<String> columns, List<SportsStandingsRow> rows) {
        if (columns == null || columns.isEmpty() || rows == null) {
            throw validation();
        }

        Set<String> columnLabels = new HashSet<>();
        for (String col : columns) {
            if (col == null || col.isBlank()) {
                throw validation();
            }
            if (!columnLabels.add(col.trim().toLowerCase())) {
                throw validation(); // duplicate column header
            }
        }

        int colCount = columns.size();
        List<UUID> teamIds = new ArrayList<>();
        for (SportsStandingsRow row : rows) {
            if (row.position() < 1 || row.participantName() == null || row.participantName().isBlank()) {
                throw validation();
            }
            if (row.values() == null || row.values().size() != colCount) {
                throw validation(); // values count must match declared columns count
            }
            if (row.teamId() != null) {
                teamIds.add(row.teamId());
            }
        }

        if (!teamIds.isEmpty() && !repository.teamsBelongToEdition(editionId, teamIds)) {
            throw validation();
        }
    }

    private SportsStandingsSnapshot buildSnapshot(
            StandingsRecord standings,
            Optional<PublicationRecord> publication,
            List<String> columns,
            List<SportsStandingsRow> rows) {

        Long versionNo = publication.map(PublicationRecord::versionNo).orElse(null);
        String sourceReference = publication.map(PublicationRecord::sourceReference).orElse(null);
        Instant publishedAt = publication.map(PublicationRecord::publishedAt).orElse(null);

        return new SportsStandingsSnapshot(
                standings.standingsId(), standings.competitionId(), standings.editionId(), standings.sportId(),
                standings.categoryId(), standings.phaseId(), standings.name(), standings.slug(), standings.status(),
                versionNo, sourceReference, standings.competitionName(), standings.editionName(),
                standings.sportName(), standings.categoryName(), standings.phaseName(), publishedAt,
                standings.lockVersion(), columns, rows);
    }

    private void validateCreateRequest(SportsStandingsCreateRequest request) {
        if (request.name() == null || request.name().isBlank()
                || request.slug() == null || request.slug().isBlank()) {
            throw validation();
        }
        if (request.columns() != null) {
            Set<String> columnLabels = new HashSet<>();
            for (String col : request.columns()) {
                if (col == null || col.isBlank() || !columnLabels.add(col.trim().toLowerCase())) {
                    throw validation();
                }
            }
        }
    }

    private void validateUpdateRequest(SportsStandingsUpdateRequest request) {
        if (request.name() == null || request.name().isBlank()
                || request.slug() == null || request.slug().isBlank()
                || request.columns() == null || request.rows() == null) {
            throw validation();
        }
    }

    private OperationalIdentity resolveIdentity(String issuer, String subject) {
        if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
            throw unauthorized();
        }
        return identityLookup.lookupIdentity(issuer, subject)
                .orElseThrow(SportsStandingsService::unauthorized);
    }

    private void requireScopeAccess(OperationalIdentity actor, UUID competitionId) {
        if (hasGeneralAdmin(actor) || hasCompetitionAdmin(actor, competitionId)) {
            return;
        }
        throw forbidden();
    }

    private void requireWritableCompetition(OperationalIdentity actor, UUID competitionId) {
        if (hasGeneralAdmin(actor) || hasCompetitionAdmin(actor, competitionId)) {
            return;
        }
        throw forbidden();
    }

    private boolean hasGeneralAdmin(OperationalIdentity actor) {
        return actor.roles().stream()
                .anyMatch(r -> GENERAL_ADMIN.equals(r.roleName()) && r.scopeRef() == null);
    }

    private boolean hasCompetitionAdmin(OperationalIdentity actor, UUID competitionId) {
        return actor.roles().stream()
                .anyMatch(r -> COMPETITION_ADMIN.equals(r.roleName())
                        && competitionId.equals(r.scopeRef()));
    }

    private static void requireExpected(Long expectedLockVersion) {
        if (expectedLockVersion == null || expectedLockVersion < 0) {
            throw validation();
        }
    }

    private static ApiProblemException validation() {
        return new ApiProblemException(HttpStatus.BAD_REQUEST, ApiErrorCode.VALIDATION_FAILED);
    }

    private static ApiProblemException notFound() {
        return new ApiProblemException(HttpStatus.NOT_FOUND, ApiErrorCode.NOT_FOUND);
    }

    private static ApiProblemException staleVersion() {
        return new ApiProblemException(HttpStatus.CONFLICT, ApiErrorCode.STALE_VERSION);
    }

    private static ApiProblemException forbidden() {
        return new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
    }

    private static ApiProblemException unauthorized() {
        return new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
    }
}
