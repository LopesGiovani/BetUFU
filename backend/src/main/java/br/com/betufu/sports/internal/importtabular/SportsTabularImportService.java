package br.com.betufu.sports.internal.importtabular;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.identity.OperationalIdentityLookup.OperationalIdentity;
import br.com.betufu.sports.EnrollmentFingerprintPort;
import br.com.betufu.sports.EnrollmentFingerprintPort.Fingerprint;
import br.com.betufu.sports.internal.athlete.SportsAthleteCreated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("runtime | integrated")
public class SportsTabularImportService {

    private static final String GENERAL_ADMIN = "ADMINISTRADOR_GERAL";
    private static final String COMPETITION_ADMIN = "ADMINISTRADOR_COMPETICAO";
    private static final Duration BATCH_TTL = Duration.ofMinutes(30);

    public record SportsTabularImportErrorItem(int line, String field, String problem) {}

    public record SportsTabularImportSummaryItem(
            String organizationName,
            String teamName,
            String sportName,
            String categoryName,
            int athleteCount) {}

    public record SportsTabularImportPreview(
            UUID importId,
            String format,
            int totalRows,
            int validRows,
            int errorCount,
            List<SportsTabularImportErrorItem> errors,
            List<SportsTabularImportSummaryItem> summary,
            boolean readyToImport) {}

    public record SportsTabularImportResult(
            UUID importId,
            String status,
            int totalImportedAthletes,
            int totalImportedRosters,
            String message) {}

    record ValidatedBatch(
            UUID importId,
            UUID editionId,
            UUID competitionId,
            String format,
            List<ImportedRosterRow> rows,
            List<SportsTabularImportSummaryItem> summary,
            Instant expiresAt) {}

    private final SportsCsvParser csvParser;
    private final SportsXlsxParser xlsxParser;
    private final EnrollmentFingerprintPort fingerprintPort;
    private final OperationalIdentityLookup identityLookup;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher eventPublisher;

    private final ConcurrentHashMap<UUID, ValidatedBatch> validatedBatches = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, SportsTabularImportResult> completedImports = new ConcurrentHashMap<>();

    @Autowired
    public SportsTabularImportService(
            SportsCsvParser csvParser,
            SportsXlsxParser xlsxParser,
            EnrollmentFingerprintPort fingerprintPort,
            OperationalIdentityLookup identityLookup,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager,
            ApplicationEventPublisher eventPublisher) {
        this.csvParser = Objects.requireNonNull(csvParser, "csvParser must not be null");
        this.xlsxParser = Objects.requireNonNull(xlsxParser, "xlsxParser must not be null");
        this.fingerprintPort = Objects.requireNonNull(fingerprintPort, "fingerprintPort must not be null");
        this.identityLookup = Objects.requireNonNull(identityLookup, "identityLookup must not be null");
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
        this.eventPublisher = eventPublisher;
    }

    public SportsTabularImportPreview validateImport(
            String issuer,
            String subject,
            UUID editionId,
            byte[] fileBytes,
            String filename) {
        OperationalIdentity actor = resolveActor(issuer, subject);
        UUID competitionId = findCompetitionForEdition(editionId);
        requireWritableCompetition(actor, competitionId);

        String format = detectFormat(filename, fileBytes);
        List<ImportedRosterRow> rows;
        try {
            if ("XLSX".equalsIgnoreCase(format)) {
                rows = xlsxParser.parse(fileBytes);
            } else {
                rows = csvParser.parse(fileBytes);
            }
        } catch (ApiProblemException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.VALIDATION_FAILED);
        }

        if (rows.isEmpty()) {
            return new SportsTabularImportPreview(
                    null,
                    format,
                    0,
                    0,
                    1,
                    List.of(new SportsTabularImportErrorItem(1, "arquivo", "Arquivo não contém linhas de dados.")),
                    List.of(),
                    false);
        }

        Map<String, UUID> orgMap = loadOrganizations();
        Map<String, UUID> sportMap = loadSports(competitionId);
        Map<String, UUID> catMap = loadCategories(editionId);
        Map<String, TeamInfo> teamMap = loadTeams(editionId);

        List<SportsTabularImportErrorItem> errors = new ArrayList<>();
        Set<Integer> errorLines = new HashSet<>();

        Set<String> seenAthleteInTeam = new HashSet<>();
        Map<String, Set<Integer>> seenJerseyInTeam = new HashMap<>();
        Map<String, String> seenMatriculaToName = new HashMap<>();
        Set<String> seenExactRows = new HashSet<>();

        Map<UUID, Integer> teamAthleteCount = new HashMap<>();
        Map<UUID, TeamInfo> validTeams = new HashMap<>();

        for (ImportedRosterRow row : rows) {
            int line = row.lineNumber();
            boolean rowValid = true;

            if (row.athleteName() == null || row.athleteName().isBlank() || row.athleteName().length() > 160) {
                errors.add(new SportsTabularImportErrorItem(line, "nome_atleta", "Nome do atleta deve conter entre 1 e 160 caracteres."));
                rowValid = false;
            }

            if (row.matriculaCode() == null || row.matriculaCode().isBlank()) {
                errors.add(new SportsTabularImportErrorItem(line, "matricula", "Matrícula não pode ser vazia."));
                rowValid = false;
            } else {
                try {
                    Optional<Fingerprint> fp = fingerprintPort.computeFingerprint(row.matriculaCode());
                    if (fp.isEmpty()) {
                        errors.add(new SportsTabularImportErrorItem(line, "matricula", "Formato de matrícula inválido."));
                        rowValid = false;
                    }
                } catch (IllegalArgumentException e) {
                    errors.add(new SportsTabularImportErrorItem(line, "matricula", "Formato de matrícula inválido."));
                    rowValid = false;
                }
            }

            UUID orgId = orgMap.get(row.organizationSlug().toLowerCase(Locale.ROOT));
            if (orgId == null) {
                errors.add(new SportsTabularImportErrorItem(line, "organizacao_slug", "Organização '" + row.organizationSlug() + "' não encontrada."));
                rowValid = false;
            }

            UUID sportId = sportMap.get(row.sportSlug().toLowerCase(Locale.ROOT));
            if (sportId == null) {
                errors.add(new SportsTabularImportErrorItem(line, "modalidade_slug", "Modalidade '" + row.sportSlug() + "' não encontrada."));
                rowValid = false;
            }

            UUID catId = catMap.get(row.categorySlug().toLowerCase(Locale.ROOT));
            if (catId == null) {
                errors.add(new SportsTabularImportErrorItem(line, "categoria_slug", "Categoria '" + row.categorySlug() + "' não encontrada nesta edição."));
                rowValid = false;
            }

            TeamInfo team = null;
            if (orgId != null && sportId != null && catId != null) {
                String teamKey = orgId + ":" + sportId + ":" + catId;
                team = teamMap.get(teamKey);
                if (team == null) {
                    errors.add(new SportsTabularImportErrorItem(line, "equipe", "Equipe não encontrada para a combinação de organização, modalidade e categoria."));
                    rowValid = false;
                }
            }

            if (row.jerseyNumber() != null) {
                if (row.jerseyNumber() < 1 || row.jerseyNumber() > 99) {
                    errors.add(new SportsTabularImportErrorItem(line, "numero_camisa", "Número de camisa deve estar entre 1 e 99."));
                    rowValid = false;
                }
            }

            if (row.positionName() != null && row.positionName().length() > 64) {
                errors.add(new SportsTabularImportErrorItem(line, "posicao", "Posição não pode exceder 64 caracteres."));
                rowValid = false;
            }

            String exactRowKey = row.organizationSlug() + "|" + row.sportSlug() + "|" + row.categorySlug() + "|" + row.athleteName() + "|" + row.matriculaCode();
            if (!seenExactRows.add(exactRowKey)) {
                errors.add(new SportsTabularImportErrorItem(line, "linha", "Linha duplicada no arquivo."));
                rowValid = false;
            }

            if (team != null && row.athleteName() != null && !row.athleteName().isBlank()) {
                String teamAthleteKey = team.teamId() + ":" + row.athleteName().toLowerCase(Locale.ROOT);
                if (!seenAthleteInTeam.add(teamAthleteKey)) {
                    errors.add(new SportsTabularImportErrorItem(line, "nome_atleta", "Atleta duplicado na mesma equipe no arquivo."));
                    rowValid = false;
                }

                if (row.jerseyNumber() != null && row.jerseyNumber() >= 1 && row.jerseyNumber() <= 99) {
                    Set<Integer> jerseys = seenJerseyInTeam.computeIfAbsent(team.teamId().toString(), k -> new HashSet<>());
                    if (!jerseys.add(row.jerseyNumber())) {
                        errors.add(new SportsTabularImportErrorItem(line, "numero_camisa", "Número de camisa duplicado na mesma equipe no arquivo."));
                        rowValid = false;
                    }
                }
            }

            if (row.matriculaCode() != null && !row.matriculaCode().isBlank() && row.athleteName() != null && !row.athleteName().isBlank()) {
                String prevName = seenMatriculaToName.putIfAbsent(row.matriculaCode(), row.athleteName().trim());
                if (prevName != null && !prevName.equalsIgnoreCase(row.athleteName().trim())) {
                    errors.add(new SportsTabularImportErrorItem(line, "matricula", "Matrícula associada a nomes de atletas diferentes no arquivo."));
                    rowValid = false;
                }
            }

            if (!rowValid) {
                errorLines.add(line);
            } else if (team != null) {
                teamAthleteCount.merge(team.teamId(), 1, Integer::sum);
                validTeams.put(team.teamId(), team);
            }
        }

        List<SportsTabularImportSummaryItem> summaryItems = new ArrayList<>();
        for (Map.Entry<UUID, TeamInfo> entry : validTeams.entrySet()) {
            TeamInfo t = entry.getValue();
            int count = teamAthleteCount.getOrDefault(t.teamId(), 0);
            summaryItems.add(new SportsTabularImportSummaryItem(
                    t.organizationName(),
                    t.teamName(),
                    t.sportName(),
                    t.categoryName(),
                    count));
        }

        boolean ready = errors.isEmpty();
        UUID importId = null;
        if (ready) {
            importId = UUID.randomUUID();
            cleanExpiredBatches();
            validatedBatches.put(importId, new ValidatedBatch(
                    importId,
                    editionId,
                    competitionId,
                    format,
                    rows,
                    summaryItems,
                    Instant.now().plus(BATCH_TTL)));
        }

        int validRowCount = rows.size() - errorLines.size();
        return new SportsTabularImportPreview(
                importId,
                format,
                rows.size(),
                validRowCount,
                errors.size(),
                errors,
                ready ? summaryItems : List.of(),
                ready);
    }

    public SportsTabularImportResult commitImport(
            String issuer,
            String subject,
            UUID editionId,
            UUID importId) {
        OperationalIdentity actor = resolveActor(issuer, subject);
        UUID competitionId = findCompetitionForEdition(editionId);
        requireWritableCompetition(actor, competitionId);

        ValidatedBatch batch = validatedBatches.get(importId);
        if (batch == null || Instant.now().isAfter(batch.expiresAt()) || !batch.editionId().equals(editionId)) {
            throw new ApiProblemException(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.VALIDATION_FAILED);
        }

        List<UUID> newlyCreatedAthleteIds = new ArrayList<>();
        Set<UUID> affectedRosterIds = new HashSet<>();

        transactions.execute(status -> {
            Map<String, UUID> orgMap = loadOrganizations();
            Map<String, UUID> sportMap = loadSports(competitionId);
            Map<String, UUID> catMap = loadCategories(editionId);
            Map<String, TeamInfo> teamMap = loadTeams(editionId);

            for (ImportedRosterRow row : batch.rows()) {
                UUID orgId = orgMap.get(row.organizationSlug().toLowerCase(Locale.ROOT));
                UUID sportId = sportMap.get(row.sportSlug().toLowerCase(Locale.ROOT));
                UUID catId = catMap.get(row.categorySlug().toLowerCase(Locale.ROOT));
                TeamInfo team = teamMap.get(orgId + ":" + sportId + ":" + catId);
                if (team == null) {
                    throw new ApiProblemException(
                            HttpStatus.BAD_REQUEST,
                            ApiErrorCode.VALIDATION_FAILED);
                }

                Fingerprint fp = fingerprintPort.computeFingerprint(row.matriculaCode())
                        .orElseThrow(() -> new ApiProblemException(
                                HttpStatus.BAD_REQUEST,
                                ApiErrorCode.VALIDATION_FAILED));

                List<UUID> existingAthletes = jdbcTemplate.query(
                        "SELECT athlete_id FROM sports_athlete WHERE matricula_hmac = ? AND hmac_version = ?",
                        (rs, rowNum) -> (UUID) rs.getObject("athlete_id"),
                        fp.hash(), fp.version());

                UUID athleteId;
                if (!existingAthletes.isEmpty()) {
                    athleteId = existingAthletes.get(0);
                } else {
                    athleteId = UUID.randomUUID();
                    String baseSlug = generateSlug(row.athleteName());
                    String slug = resolveUniqueSlug(baseSlug);

                    jdbcTemplate.update(
                            """
                            INSERT INTO sports_athlete (athlete_id, name, slug, matricula_hmac, hmac_version, status, lock_version, created_at, updated_at)
                            VALUES (?, ?, ?, ?, ?, 'RASCUNHO', 0, NOW(), NOW())
                            """,
                            athleteId,
                            row.athleteName().trim(),
                            slug,
                            fp.hash(),
                            fp.version());

                    newlyCreatedAthleteIds.add(athleteId);
                }

                List<UUID> rosterIds = jdbcTemplate.query(
                        "SELECT roster_id FROM sports_roster WHERE team_id = ?",
                        (rs, rowNum) -> (UUID) rs.getObject("roster_id"),
                        team.teamId());

                UUID rosterId;
                if (rosterIds.isEmpty()) {
                    rosterId = UUID.randomUUID();
                    jdbcTemplate.update(
                            """
                            INSERT INTO sports_roster (roster_id, team_id, status, lock_version, created_at, updated_at)
                            VALUES (?, ?, 'RASCUNHO', 0, NOW(), NOW())
                            """,
                            rosterId,
                            team.teamId());
                } else {
                    rosterId = rosterIds.get(0);
                }
                affectedRosterIds.add(rosterId);

                jdbcTemplate.update(
                        """
                        INSERT INTO sports_roster_member (roster_id, athlete_id, jersey_number, position_name, created_at)
                        VALUES (?, ?, ?, ?, NOW())
                        ON CONFLICT (roster_id, athlete_id) DO UPDATE SET
                            jersey_number = EXCLUDED.jersey_number,
                            position_name = EXCLUDED.position_name
                        """,
                        rosterId,
                        athleteId,
                        row.jerseyNumber(),
                        row.positionName());
            }
            return null;
        });

        validatedBatches.remove(importId);

        String successMessage = batch.rows().size() + " atletas e vínculos foram criados como rascunho.";
        SportsTabularImportResult result = new SportsTabularImportResult(
                importId,
                "CONCLUIDO",
                batch.rows().size(),
                affectedRosterIds.size(),
                successMessage);

        completedImports.put(importId, result);

        if (eventPublisher != null) {
            for (UUID athleteId : newlyCreatedAthleteIds) {
                eventPublisher.publishEvent(new SportsAthleteCreated(athleteId));
            }
        }

        return result;
    }

    public SportsTabularImportResult getImportStatus(
            String issuer,
            String subject,
            UUID editionId,
            UUID importId) {
        OperationalIdentity actor = resolveActor(issuer, subject);
        UUID competitionId = findCompetitionForEdition(editionId);
        requireWritableCompetition(actor, competitionId);

        SportsTabularImportResult completed = completedImports.get(importId);
        if (completed != null) {
            return completed;
        }

        ValidatedBatch batch = validatedBatches.get(importId);
        if (batch != null && Instant.now().isBefore(batch.expiresAt())) {
            return new SportsTabularImportResult(
                    importId,
                    "VALIDADO_AGUARDANDO_CONFIRMACAO",
                    0,
                    0,
                    "Arquivo validado com sucesso. Confirmação pendente.");
        }

        throw new ApiProblemException(
                HttpStatus.NOT_FOUND,
                ApiErrorCode.NOT_FOUND);
    }

    private record TeamInfo(
            UUID teamId,
            UUID orgId,
            UUID sportId,
            UUID catId,
            String organizationName,
            String teamName,
            String sportName,
            String categoryName) {}

    private Map<String, UUID> loadOrganizations() {
        Map<String, UUID> map = new HashMap<>();
        jdbcTemplate.query(
                "SELECT organization_id, slug FROM sports_organization",
                rs -> {
                    map.put(rs.getString("slug").toLowerCase(Locale.ROOT), (UUID) rs.getObject("organization_id"));
                });
        return map;
    }

    private Map<String, UUID> loadSports(UUID competitionId) {
        Map<String, UUID> map = new HashMap<>();
        jdbcTemplate.query(
                "SELECT s.sport_id, s.slug FROM sports_sport s "
                        + "JOIN sports_competition c ON c.sport_id = s.sport_id "
                        + "WHERE c.competition_id = ?",
                rs -> {
                    map.put(rs.getString("slug").toLowerCase(Locale.ROOT), (UUID) rs.getObject("sport_id"));
                },
                competitionId);
        return map;
    }

    private Map<String, UUID> loadCategories(UUID editionId) {
        Map<String, UUID> map = new HashMap<>();
        jdbcTemplate.query(
                "SELECT category_id, slug FROM sports_category WHERE edition_id = ?",
                rs -> {
                    map.put(rs.getString("slug").toLowerCase(Locale.ROOT), (UUID) rs.getObject("category_id"));
                },
                editionId);
        return map;
    }

    private Map<String, TeamInfo> loadTeams(UUID editionId) {
        Map<String, TeamInfo> map = new HashMap<>();
        jdbcTemplate.query(
                """
                SELECT t.team_id, t.organization_id, t.sport_id, t.category_id, t.name as team_name,
                       o.name as org_name, s.name as sport_name, c.name as cat_name
                FROM sports_team t
                JOIN sports_organization o ON o.organization_id = t.organization_id
                JOIN sports_sport s ON s.sport_id = t.sport_id
                JOIN sports_category c ON c.category_id = t.category_id
                WHERE t.edition_id = ?
                """,
                rs -> {
                    UUID teamId = (UUID) rs.getObject("team_id");
                    UUID orgId = (UUID) rs.getObject("organization_id");
                    UUID sportId = (UUID) rs.getObject("sport_id");
                    UUID catId = (UUID) rs.getObject("category_id");
                    String teamName = rs.getString("team_name");
                    String orgName = rs.getString("org_name");
                    String sportName = rs.getString("sport_name");
                    String catName = rs.getString("cat_name");

                    String key = orgId + ":" + sportId + ":" + catId;
                    map.put(key, new TeamInfo(teamId, orgId, sportId, catId, orgName, teamName, sportName, catName));
                },
                editionId);
        return map;
    }

    private String detectFormat(String filename, byte[] bytes) {
        if (filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            return "XLSX";
        }
        if (bytes != null && bytes.length >= 4 && bytes[0] == 0x50 && bytes[1] == 0x4B) {
            return "XLSX";
        }
        return "CSV";
    }

    private UUID findCompetitionForEdition(UUID editionId) {
        List<UUID> compIds = jdbcTemplate.query(
                "SELECT competition_id FROM sports_edition WHERE edition_id = ?",
                (rs, rowNum) -> (UUID) rs.getObject("competition_id"),
                editionId);
        if (compIds.isEmpty()) {
            throw new ApiProblemException(
                    HttpStatus.NOT_FOUND,
                    ApiErrorCode.NOT_FOUND);
        }
        return compIds.get(0);
    }

    private OperationalIdentity resolveActor(String issuer, String subject) {
        if (issuer == null || subject == null) {
            throw new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED);
        }
        return identityLookup.lookupIdentity(issuer, subject)
                .orElseThrow(() -> new ApiProblemException(HttpStatus.UNAUTHORIZED, ApiErrorCode.UNAUTHORIZED));
    }

    private void requireWritableCompetition(OperationalIdentity actor, UUID competitionId) {
        boolean isGeneralAdmin = actor.roles().stream()
                .anyMatch(grant -> GENERAL_ADMIN.equals(grant.roleName()) && grant.scopeRef() == null);
        if (isGeneralAdmin) {
            return;
        }
        boolean isCompetitionAdmin = actor.roles().stream()
                .anyMatch(grant -> COMPETITION_ADMIN.equals(grant.roleName()) && competitionId.equals(grant.scopeRef()));
        if (!isCompetitionAdmin) {
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN,
                    ApiErrorCode.FORBIDDEN);
        }
    }

    private String generateSlug(String name) {
        if (name == null || name.isBlank()) {
            return "atleta-" + UUID.randomUUID().toString().substring(0, 8);
        }
        String normalized = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.isBlank()) {
            return "atleta-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return normalized;
    }

    private String resolveUniqueSlug(String baseSlug) {
        List<String> existing = jdbcTemplate.query(
                "SELECT slug FROM sports_athlete WHERE slug = ? OR slug LIKE ?",
                (rs, rowNum) -> rs.getString("slug"),
                baseSlug, baseSlug + "-%");
        if (!existing.contains(baseSlug)) {
            return baseSlug;
        }
        int counter = 2;
        while (existing.contains(baseSlug + "-" + counter)) {
            counter++;
        }
        return baseSlug + "-" + counter;
    }

    private void cleanExpiredBatches() {
        Instant now = Instant.now();
        validatedBatches.entrySet().removeIf(entry -> now.isAfter(entry.getValue().expiresAt()));
    }
}
