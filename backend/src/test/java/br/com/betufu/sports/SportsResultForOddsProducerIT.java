package br.com.betufu.sports.internal.odds;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.betufu.sports.ConfirmedSportsResultForOdds;
import br.com.betufu.sports.SportsEventForOdds;
import br.com.betufu.sports.SportsResultForOddsPort;
import java.time.Instant;
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
class SportsResultForOddsProducerIT {

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
        insertReturning(
            "INSERT INTO public.local_identity (issuer, subject) VALUES ('https://idp.invalid', 'odds-producer') RETURNING identity_id",
            UUID.class);
    competitionId =
        insertReturning(
            "INSERT INTO public.sports_competition (name, slug) VALUES ('Comp odds', 'comp-odds') RETURNING competition_id",
            UUID.class);
    editionId =
        insertReturning(
            "INSERT INTO public.sports_edition (competition_id, name, slug) VALUES (?, 'Edition odds', 'edition-odds') RETURNING edition_id",
            UUID.class,
            competitionId);
    UUID sportId =
        insertReturning(
            "INSERT INTO public.sports_sport (name, slug) VALUES ('Futsal', 'futsal') RETURNING sport_id",
            UUID.class);
    jdbc.update(
        "UPDATE public.sports_competition SET sport_id = ? WHERE competition_id = ?",
        sportId,
        competitionId);
    UUID categoryId =
        insertReturning(
            "INSERT INTO public.sports_category (edition_id, name, slug) VALUES (?, 'Livre', 'livre') RETURNING category_id",
            UUID.class,
            editionId);
    UUID organizationId =
        insertReturning(
            "INSERT INTO public.sports_organization (name, slug, status) VALUES ('Atletica odds', 'atletica-odds', 'PUBLICADO') RETURNING organization_id",
            UUID.class);
    teamA = insertTeam(sportId, categoryId, organizationId, "Equipe A", "equipe-a");
    teamB = insertTeam(sportId, categoryId, organizationId, "Equipe B", "equipe-b");
    sportTime = Instant.parse("2026-10-10T14:00:00Z");
    eventId =
        insertReturning(
            "INSERT INTO public.sports_event (competition_id, edition_id, title, event_format, status, scheduled_at, zone_id, venue_state, source_reference, sport_id, category_id) VALUES (?, ?, 'A x B', 'HEAD_TO_HEAD', 'PUBLICADO', ?, 'UTC', 'LOCAL_A_DEFINIR', 'https://source.invalid/event', ?, ?) RETURNING event_id",
            UUID.class,
            competitionId,
            editionId,
            java.sql.Timestamp.from(sportTime),
            sportId,
            categoryId);
    jdbc.update(
        "INSERT INTO public.sports_event_slot (event_id, slot_no, display_label, team_id) VALUES (?, 1, 'Equipe A', ?)",
        eventId,
        teamA);
    jdbc.update(
        "INSERT INTO public.sports_event_slot (event_id, slot_no, display_label, team_id) VALUES (?, 2, 'Equipe B', ?)",
        eventId,
        teamB);
  }

  @Test
  void projectsEligibleEventWithoutOutcomeAndExactConfirmedVersion() {
    SportsEventForOdds event = producer.getEventForOdds(eventId).orElseThrow();
    assertThat(event.format()).isEqualTo(SportsEventForOdds.EventFormat.HEAD_TO_HEAD);
    assertThat(event.slots())
        .extracting(SportsEventForOdds.Slot::teamId)
        .containsExactly(teamA, teamB);
    assertThat(producer.getConfirmedResultForOdds(eventId, UUID.randomUUID())).isEmpty();

    Instant confirmedAt = Instant.parse("2026-10-10T15:00:00Z");
    UUID versionId = insertSportingOutcome(2, "CONFIRMADO", null, confirmedAt, confirmedAt);
    ConfirmedSportsResultForOdds result =
        producer.getConfirmedResultForOdds(eventId, versionId).orElseThrow();
    assertThat(result.versionId()).isEqualTo(versionId);
    assertThat(result.scores())
        .extracting(ConfirmedSportsResultForOdds.Score::score)
        .containsExactly(2, 1);
    assertThat(result.resultKind())
        .isEqualTo(br.com.betufu.sports.SportsResultKindForOdds.SPORTING_RESULT);
  }

  private UUID insertSportingOutcome(
      long versionNo, String status, UUID supersedes, Instant confirmedAt, Instant knownAt) {
    UUID versionId = UUID.randomUUID();
    UUID factId = UUID.randomUUID();
    transaction.executeWithoutResult(
        ignored -> {
          jdbc.update(
              "INSERT INTO public.sports_result_version (version_id, event_id, competition_id, edition_id, version_no, status, source_reference, recorded_by_identity_id, confirmed_by_identity_id, recorded_at, confirmed_at, supersedes_version_id, base_lock_version) VALUES (?, ?, ?, ?, ?, ?, 'https://source.invalid/result', ?, ?, ?, ?, ?, 0)",
              versionId,
              eventId,
              competitionId,
              editionId,
              versionNo,
              status,
              identityId,
              identityId,
              java.sql.Timestamp.from(knownAt),
              java.sql.Timestamp.from(confirmedAt),
              supersedes);
          jdbc.update(
              "INSERT INTO public.sports_result_version_slot (version_id, slot_no, score, display_label, team_id) VALUES (?, 1, 2, 'Equipe A', ?)",
              versionId,
              teamA);
          jdbc.update(
              "INSERT INTO public.sports_result_version_slot (version_id, slot_no, score, display_label, team_id) VALUES (?, 2, 1, 'Equipe B', ?)",
              versionId,
              teamB);
          jdbc.update(
              "INSERT INTO public.sports_outcome_fact (fact_id, event_id, competition_id, edition_id, version_no, outcome_kind, source_reference, recorded_by_identity_id, sport_time, known_at, supersedes_fact_id) VALUES (?, ?, ?, ?, ?, 'SPORTING_RESULT', 'https://source.invalid/outcome', ?, ?, ?, ?)",
              factId,
              eventId,
              competitionId,
              editionId,
              versionNo,
              identityId,
              java.sql.Timestamp.from(sportTime),
              java.sql.Timestamp.from(knownAt),
              supersedes);
          jdbc.update(
              "INSERT INTO public.sports_outcome_sporting_payload (fact_id, event_id, confirmed_version_id, confirmed_version_status, confirmed_at) VALUES (?, ?, ?, ?, ?)",
              factId,
              eventId,
              versionId,
              status,
              java.sql.Timestamp.from(confirmedAt));
          jdbc.update(
              "INSERT INTO public.sports_outcome_head (event_id, fact_id) VALUES (?, ?)",
              eventId,
              factId);
          jdbc.update(
              "UPDATE public.sports_event SET confirmed_version_id = ? WHERE event_id = ?",
              versionId,
              eventId);
        });
    return versionId;
  }

  private UUID insertTeam(
      UUID sportId, UUID categoryId, UUID organizationId, String name, String slug) {
    return insertReturning(
        "INSERT INTO public.sports_team (organization_id, edition_id, sport_id, category_id, name, slug, status) VALUES (?, ?, ?, ?, ?, ?, 'PUBLICADO') RETURNING team_id",
        UUID.class,
        organizationId,
        editionId,
        sportId,
        categoryId,
        name,
        slug);
  }

  private <T> T insertReturning(String sql, Class<T> type, Object... args) {
    return jdbc.queryForObject(sql, type, args);
  }
}
