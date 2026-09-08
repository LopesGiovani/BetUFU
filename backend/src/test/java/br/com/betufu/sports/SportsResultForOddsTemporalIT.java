package br.com.betufu.sports.internal.odds;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.betufu.sports.ConfirmedSportsResultForOdds;
import br.com.betufu.sports.SportsResultForOddsPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class SportsResultForOddsTemporalIT {

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(DockerImageName.parse("postgres:18.4-trixie"))
          .withDatabaseName("betufu_app")
          .withUsername("betufu_app")
          .withPassword("betufu-test-only")
          .withInitScript("postgres-audit-runtime-role.sql");

  private JdbcTemplate jdbc;
  private TransactionTemplate transaction;
  private SportsResultForOddsPort producer;
  private UUID competitionId;
  private UUID editionId;
  private UUID eventId;
  private UUID identityId;
  private UUID teamA;
  private UUID teamB;
  private UUID sportId;
  private UUID categoryId;
  private Instant sportTime;

  @BeforeEach
  void setUp() {
    DriverManagerDataSource dataSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(dataSource);
    transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
    jdbc.execute(
        "TRUNCATE TABLE public.sports_outcome_head, public.sports_outcome_administrative_payload, "
            + "public.sports_outcome_sporting_payload, public.sports_outcome_fact, "
            + "public.sports_result_version_slot, public.sports_result_version, "
            + "public.sports_event_slot, public.sports_event, public.sports_team, "
            + "public.sports_organization, public.sports_category, public.sports_sport, "
            + "public.sports_edition, public.sports_competition, public.local_identity CASCADE");
    producer = new SportsResultForOddsProducer(new SportsOddsSourceRepository(jdbc));

    identityId =
        returning(
            "INSERT INTO public.local_identity (issuer, subject) VALUES ('https://idp.invalid', 'odds-temporal') RETURNING identity_id",
            UUID.class);
    competitionId =
        returning(
            "INSERT INTO public.sports_competition (name, slug) VALUES ('Comp temporal', 'comp-temporal') RETURNING competition_id",
            UUID.class);
    editionId =
        returning(
            "INSERT INTO public.sports_edition (competition_id, name, slug) VALUES (?, 'Edition temporal', 'edition-temporal') RETURNING edition_id",
            UUID.class,
            competitionId);
    sportId =
        returning(
            "INSERT INTO public.sports_sport (name, slug) VALUES ('Basquete', 'basquete') RETURNING sport_id",
            UUID.class);
    jdbc.update(
        "UPDATE public.sports_competition SET sport_id = ? WHERE competition_id = ?",
        sportId,
        competitionId);
    categoryId =
        returning(
            "INSERT INTO public.sports_category (edition_id, name, slug) VALUES (?, 'Livre', 'livre-temporal') RETURNING category_id",
            UUID.class,
            editionId);
    UUID organizationId =
        returning(
            "INSERT INTO public.sports_organization (name, slug, status) VALUES ('Atletica temporal', 'atletica-temporal', 'PUBLICADO') RETURNING organization_id",
            UUID.class);
    teamA = insertTeam(organizationId, "Temporal A", "temporal-a");
    teamB = insertTeam(organizationId, "Temporal B", "temporal-b");
    sportTime = Instant.parse("2026-10-10T14:00:00Z");
    eventId =
        returning(
            "INSERT INTO public.sports_event (competition_id, edition_id, title, event_format, status, scheduled_at, zone_id, venue_state, source_reference, sport_id, category_id) VALUES (?, ?, 'Temporal A x B', 'HEAD_TO_HEAD', 'PUBLICADO', ?, 'UTC', 'LOCAL_A_DEFINIR', 'https://source.invalid/event-temporal', ?, ?) RETURNING event_id",
            UUID.class,
            competitionId,
            editionId,
            Timestamp.from(sportTime),
            sportId,
            categoryId);
    jdbc.update(
        "INSERT INTO public.sports_event_slot (event_id, slot_no, display_label, team_id) VALUES (?, 1, 'Temporal A', ?)",
        eventId,
        teamA);
    jdbc.update(
        "INSERT INTO public.sports_event_slot (event_id, slot_no, display_label, team_id) VALUES (?, 2, 'Temporal B', ?)",
        eventId,
        teamB);
  }

  @Test
  void asOfCutoffDoesNotUseFutureCorrection() {
    Instant firstKnown = Instant.parse("2026-10-10T15:00:00Z");
    Instant correctionKnown = Instant.parse("2026-10-11T12:00:00Z");
    FactVersion first = insertSporting(1, "CONFIRMADO", null, firstKnown, firstKnown, 1, 0);
    insertSporting(2, "CORRIGIDO", first, correctionKnown, correctionKnown, 4, 2);

    List<ConfirmedSportsResultForOdds> beforeCorrection =
        producer.findAdmissibleResultsAsOf(
            competitionId, editionId, Instant.parse("2026-10-10T23:59:59Z"));
    assertThat(beforeCorrection).hasSize(1);
    assertThat(beforeCorrection.get(0).versionId()).isEqualTo(first.versionId());
    assertThat(beforeCorrection.get(0).scores())
        .extracting(ConfirmedSportsResultForOdds.Score::score)
        .containsExactly(1, 0);

    List<ConfirmedSportsResultForOdds> afterCorrection =
        producer.findAdmissibleResultsAsOf(
            competitionId, editionId, Instant.parse("2026-10-12T00:00:00Z"));
    assertThat(afterCorrection).hasSize(1);
    assertThat(afterCorrection.get(0).status())
        .isEqualTo(ConfirmedSportsResultForOdds.VersionStatus.CORRIGIDO);
    assertThat(afterCorrection.get(0).scores())
        .extracting(ConfirmedSportsResultForOdds.Score::score)
        .containsExactly(4, 2);
  }

  @Test
  void administrativeOutcomeIsReplayableWithoutSyntheticScore() {
    FactVersion first =
        insertSporting(
            1,
            "CONFIRMADO",
            null,
            Instant.parse("2026-10-10T15:00:00Z"),
            Instant.parse("2026-10-10T15:00:00Z"),
            2,
            2);
    UUID adminFact = UUID.randomUUID();
    Instant known = Instant.parse("2026-10-10T16:00:00Z");
    transaction.executeWithoutResult(
        ignored -> {
          jdbc.update(
              "INSERT INTO public.sports_outcome_fact (fact_id, event_id, competition_id, edition_id, version_no, outcome_kind, source_reference, reason, recorded_by_identity_id, sport_time, known_at, supersedes_fact_id) VALUES (?, ?, ?, ?, 2, 'ABANDONED_NO_VALID_RESULT', 'https://source.invalid/admin', 'Abandono', ?, ?, ?, ?)",
              adminFact,
              eventId,
              competitionId,
              editionId,
              identityId,
              Timestamp.from(sportTime),
              Timestamp.from(known),
              first.factId());
          jdbc.update(
              "INSERT INTO public.sports_outcome_administrative_payload (fact_id, event_id, administrative_reason) VALUES (?, ?, 'Abandono sem resultado valido')",
              adminFact,
              eventId);
          jdbc.update(
              "INSERT INTO public.sports_outcome_head (event_id, fact_id) VALUES (?, ?) ON CONFLICT (event_id) DO UPDATE SET fact_id = EXCLUDED.fact_id",
              eventId,
              adminFact);
          jdbc.update(
              "UPDATE public.sports_event SET confirmed_version_id = NULL WHERE event_id = ?",
              eventId);
        });

    List<ConfirmedSportsResultForOdds> results =
        producer.findAdmissibleResultsAsOf(competitionId, editionId, known.plusSeconds(1));
    assertThat(results).hasSize(1);
    assertThat(results.get(0).resultKind())
        .isEqualTo(br.com.betufu.sports.SportsResultKindForOdds.ABANDONED_NO_VALID_RESULT);
    assertThat(results.get(0).scores()).isEmpty();
  }

  private FactVersion insertSporting(
      long versionNo,
      String status,
      FactVersion supersedes,
      Instant confirmedAt,
      Instant knownAt,
      int scoreA,
      int scoreB) {
    UUID versionId = UUID.randomUUID();
    UUID factId = UUID.randomUUID();
    transaction.executeWithoutResult(
        ignored -> {
          jdbc.update(
              "INSERT INTO public.sports_result_version (version_id, event_id, competition_id, edition_id, version_no, status, source_reference, recorded_by_identity_id, confirmed_by_identity_id, recorded_at, confirmed_at, supersedes_version_id, base_lock_version, reason) VALUES (?, ?, ?, ?, ?, ?, 'https://source.invalid/result-temporal', ?, ?, ?, ?, ?, 0, ?)",
              versionId,
              eventId,
              competitionId,
              editionId,
              versionNo,
              status,
              identityId,
              identityId,
              Timestamp.from(knownAt),
              Timestamp.from(confirmedAt),
              supersedes == null ? null : supersedes.versionId(),
              status.equals("CORRIGIDO") ? "Placar corrigido" : null);
          jdbc.update(
              "INSERT INTO public.sports_result_version_slot (version_id, slot_no, score, display_label, team_id) VALUES (?, 1, ?, 'Temporal A', ?)",
              versionId,
              scoreA,
              teamA);
          jdbc.update(
              "INSERT INTO public.sports_result_version_slot (version_id, slot_no, score, display_label, team_id) VALUES (?, 2, ?, 'Temporal B', ?)",
              versionId,
              scoreB,
              teamB);
          jdbc.update(
              "INSERT INTO public.sports_outcome_fact (fact_id, event_id, competition_id, edition_id, version_no, outcome_kind, source_reference, reason, recorded_by_identity_id, sport_time, known_at, supersedes_fact_id) VALUES (?, ?, ?, ?, ?, 'SPORTING_RESULT', 'https://source.invalid/outcome-temporal', ?, ?, ?, ?, ?)",
              factId,
              eventId,
              competitionId,
              editionId,
              versionNo,
              null,
              identityId,
              Timestamp.from(sportTime),
              Timestamp.from(knownAt),
              supersedes == null ? null : supersedes.factId());
          jdbc.update(
              "INSERT INTO public.sports_outcome_sporting_payload (fact_id, event_id, confirmed_version_id, confirmed_version_status, confirmed_at) VALUES (?, ?, ?, ?, ?)",
              factId,
              eventId,
              versionId,
              status,
              Timestamp.from(confirmedAt));
          jdbc.update(
              "INSERT INTO public.sports_outcome_head (event_id, fact_id) VALUES (?, ?) ON CONFLICT (event_id) DO UPDATE SET fact_id = EXCLUDED.fact_id",
              eventId,
              factId);
          jdbc.update(
              "UPDATE public.sports_event SET confirmed_version_id = ? WHERE event_id = ?",
              versionId,
              eventId);
        });
    return new FactVersion(versionId, factId);
  }

  private UUID insertTeam(UUID organizationId, String name, String slug) {
    return returning(
        "INSERT INTO public.sports_team (organization_id, edition_id, sport_id, category_id, name, slug, status) VALUES (?, ?, ?, ?, ?, ?, 'PUBLICADO') RETURNING team_id",
        UUID.class,
        organizationId,
        editionId,
        sportId,
        categoryId,
        name,
        slug);
  }

  private <T> T returning(String sql, Class<T> type, Object... args) {
    return jdbc.queryForObject(sql, type, args);
  }

  private record FactVersion(UUID versionId, UUID factId) {}
}
