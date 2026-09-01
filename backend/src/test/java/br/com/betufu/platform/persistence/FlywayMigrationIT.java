package br.com.betufu.platform.persistence;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
class FlywayMigrationIT {

  private static final String RUNTIME_USER = "betufu_migration_runtime";
  private static final String RUNTIME_PASSWORD = "betufu-migration-runtime-test-only";

  private static final String POSTGRES_IMAGE_REFERENCE =
      "docker.io/library/postgres:18.4-trixie@sha256:"
          + "a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636";

  private static final DockerImageName POSTGRES_IMAGE =
      DockerImageName.parse(POSTGRES_IMAGE_REFERENCE).asCompatibleSubstituteFor("postgres");

  @Container
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(POSTGRES_IMAGE)
          .withDatabaseName("betufu_migration_test")
          .withUsername("betufu_migration_user")
          .withPassword("betufu-migration-pass");

  private DriverManagerDataSource dataSource;
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    dataSource = new DriverManagerDataSource();
    dataSource.setDriverClassName("org.postgresql.Driver");
    dataSource.setUrl(POSTGRESQL.getJdbcUrl());
    dataSource.setUsername(POSTGRESQL.getUsername());
    dataSource.setPassword(POSTGRESQL.getPassword());

    jdbcTemplate = new JdbcTemplate(dataSource);
    jdbcTemplate.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
    jdbcTemplate.execute(
        """
                DO $$
                BEGIN
                    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'betufu_migration_runtime') THEN
                        CREATE ROLE betufu_migration_runtime LOGIN;
                    END IF;
                END $$
                """);
    jdbcTemplate.execute("ALTER ROLE " + RUNTIME_USER + " PASSWORD '" + RUNTIME_PASSWORD + "'");
    jdbcTemplate.execute(
        "ALTER DATABASE "
            + POSTGRESQL.getDatabaseName()
            + " SET betufu.runtime_role TO '"
            + RUNTIME_USER
            + "'");
    jdbcTemplate.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
    jdbcTemplate.execute("REVOKE ALL PRIVILEGES ON SCHEMA public FROM " + RUNTIME_USER);
    jdbcTemplate.execute("GRANT USAGE ON SCHEMA public TO " + RUNTIME_USER);
    jdbcTemplate.execute(
        "ALTER DEFAULT PRIVILEGES IN SCHEMA public"
            + " GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO "
            + RUNTIME_USER);
    jdbcTemplate.execute(
        "ALTER DEFAULT PRIVILEGES IN SCHEMA public"
            + " GRANT USAGE, SELECT ON SEQUENCES TO "
            + RUNTIME_USER);
  }

  @Test
  @DisplayName("Aplica V1 a V15 sequencialmente no PostgreSQL 18 e verifica histórico autoritativo")
  void appliesV1AndV2MigrationsSequentially() {
    Flyway flyway =
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();

    MigrateResult result = flyway.migrate();
    assertThat(result.success).isTrue();
    assertThat(result.migrationsExecuted).isEqualTo(21);

    MigrationInfo[] applied = flyway.info().applied();
    assertThat(applied).hasSize(21);

    assertThat(applied[0].getVersion().getVersion()).isEqualTo("1");
    assertThat(applied[0].getState()).isEqualTo(MigrationState.SUCCESS);

    assertThat(applied[1].getVersion().getVersion()).isEqualTo("2");
    assertThat(applied[1].getState()).isEqualTo(MigrationState.SUCCESS);

    assertThat(applied[2].getVersion().getVersion()).isEqualTo("3");
    assertThat(applied[2].getState()).isEqualTo(MigrationState.SUCCESS);

    assertThat(applied[3].getVersion().getVersion()).isEqualTo("4");
    assertThat(applied[3].getState()).isEqualTo(MigrationState.SUCCESS);

    assertThat(applied[4].getVersion().getVersion()).isEqualTo("5");
    assertThat(applied[4].getState()).isEqualTo(MigrationState.SUCCESS);

    assertThat(applied[5].getVersion().getVersion()).isEqualTo("5.1");
    assertThat(applied[5].getState()).isEqualTo(MigrationState.SUCCESS);

    assertThat(applied[6].getVersion().getVersion()).isEqualTo("6");
    assertThat(applied[6].getState()).isEqualTo(MigrationState.SUCCESS);

    assertThat(applied[7].getVersion().getVersion()).isEqualTo("7");
    assertThat(applied[7].getState()).isEqualTo(MigrationState.SUCCESS);

    assertThat(applied[8].getVersion().getVersion()).isEqualTo("8");
    assertThat(applied[8].getState()).isEqualTo(MigrationState.SUCCESS);

    assertThat(applied[9].getVersion().getVersion()).isEqualTo("9");
    assertThat(applied[9].getState()).isEqualTo(MigrationState.SUCCESS);

    assertThat(applied[10].getVersion().getVersion()).isEqualTo("10");
    assertThat(applied[10].getState()).isEqualTo(MigrationState.SUCCESS);

    assertThat(applied[11].getVersion().getVersion()).isEqualTo("11");
    assertThat(applied[11].getState()).isEqualTo(MigrationState.SUCCESS);

    assertThat(applied[12].getVersion().getVersion()).isEqualTo("12");
    assertThat(applied[12].getState()).isEqualTo(MigrationState.SUCCESS);

    assertThat(applied[13].getVersion().getVersion()).isEqualTo("13");
    assertThat(applied[13].getState()).isEqualTo(MigrationState.SUCCESS);

    assertThat(applied[14].getVersion().getVersion()).isEqualTo("14");
    assertThat(applied[14].getState()).isEqualTo(MigrationState.SUCCESS);

    assertThat(applied[15].getVersion().getVersion()).isEqualTo("15");
    assertThat(applied[15].getState()).isEqualTo(MigrationState.SUCCESS);

    assertThat(applied[16].getVersion().getVersion()).isEqualTo("16");
    assertThat(applied[16].getState()).isEqualTo(MigrationState.SUCCESS);

    assertThat(applied[17].getVersion().getVersion()).isEqualTo("17");
    assertThat(applied[17].getState()).isEqualTo(MigrationState.SUCCESS);

    assertThat(applied[18].getVersion().getVersion()).isEqualTo("18");
    assertThat(applied[18].getState()).isEqualTo(MigrationState.SUCCESS);

    assertThat(applied[19].getVersion().getVersion()).isEqualTo("19");
    assertThat(applied[19].getState()).isEqualTo(MigrationState.SUCCESS);

    Integer guardCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.platform_guard WHERE guard_key = 'PLATFORM_SCHEMA_AUTHORITATIVE'",
            Integer.class);
    assertThat(guardCount).isEqualTo(1);
  }

  @Test
  @DisplayName("Execuções repetidas do migrate() são estritamente idempotentes")
  void reapplyingMigrationsIsIdempotent() {
    Flyway flyway =
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();

    MigrateResult firstRun = flyway.migrate();
    assertThat(firstRun.migrationsExecuted).isEqualTo(21);

    MigrateResult secondRun = flyway.migrate();
    assertThat(secondRun.migrationsExecuted).isEqualTo(0);

    MigrateResult thirdRun = flyway.migrate();
    assertThat(thirdRun.migrationsExecuted).isEqualTo(0);

    MigrationInfo[] applied = flyway.info().applied();
    assertThat(applied).hasSize(21);
    assertThat(applied).allMatch(info -> info.getState() == MigrationState.SUCCESS);
  }

  @Test
  @DisplayName("Inicializações concorrentes do Flyway serializam via advisory lock sem corrupção")
  void concurrentFlywayInitializationsSerializeCleanly() throws Exception {
    int concurrency = 2;
    ExecutorService executor = Executors.newFixedThreadPool(concurrency);
    CountDownLatch ready = new CountDownLatch(concurrency);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<MigrateResult>> futures = new ArrayList<>();

    try {
      for (int i = 0; i < concurrency; i++) {
        futures.add(
            executor.submit(
                () -> {
                  Flyway flyway =
                      Flyway.configure()
                          .dataSource(dataSource)
                          .locations("classpath:db/migration")
                          .load();

                  ready.countDown();
                  assertThat(start.await(10, SECONDS)).isTrue();
                  return flyway.migrate();
                }));
      }

      assertThat(ready.await(10, SECONDS)).isTrue();
      start.countDown();

      List<MigrateResult> results = new ArrayList<>();
      for (Future<MigrateResult> future : futures) {
        results.add(future.get(30, SECONDS));
      }

      assertThat(results).allSatisfy(r -> assertThat(r.success).isTrue());

      int totalExecuted = results.stream().mapToInt(r -> r.migrationsExecuted).sum();
      assertThat(totalExecuted).isEqualTo(21);

      Flyway verificationFlyway =
          Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load();

      MigrationInfo[] applied = verificationFlyway.info().applied();
      assertThat(applied).hasSize(21);
      assertThat(applied).allMatch(info -> info.getState() == MigrationState.SUCCESS);
    } finally {
      start.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
    }
  }

  @Test
  @DisplayName("V7 impede papel escopado sem escopo e papel global com escopo no proprio banco")
  void v7EnforcesRoleScopeConsistencyAtDatabaseLevel() {
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

    UUID identityId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.local_identity (issuer, subject) VALUES (?, ?) RETURNING identity_id",
            UUID.class,
            "https://example.invalid/realms/betufu-check",
            "subject-scope-check");

    // Papel escopado sem escopo: recusado pelo banco, nao apenas pelo servico
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO public.operational_role_grant (identity_id, role_name, scope_ref, status)"
                        + " VALUES (?, 'MODERADOR', NULL, 'VIGENTE')",
                    identityId))
        .as("MODERADOR sem scope_ref deve violar ck_operational_role_grant_scope")
        .hasMessageContaining("ck_operational_role_grant_scope");

    // Papel global com escopo: igualmente recusado
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO public.operational_role_grant (identity_id, role_name, scope_ref, status)"
                        + " VALUES (?, 'ADMINISTRADOR_GERAL', gen_random_uuid(), 'VIGENTE')",
                    identityId))
        .as("ADMINISTRADOR_GERAL com scope_ref deve violar ck_operational_role_grant_scope")
        .hasMessageContaining("ck_operational_role_grant_scope");

    // Controle positivo: as duas combinacoes validas passam, senao a CHECK estaria proibindo tudo
    assertThat(
            jdbcTemplate.update(
                "INSERT INTO public.operational_role_grant (identity_id, role_name, scope_ref, status)"
                    + " VALUES (?, 'MODERADOR', gen_random_uuid(), 'VIGENTE')",
                identityId))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.update(
                "INSERT INTO public.operational_role_grant (identity_id, role_name, scope_ref, status)"
                    + " VALUES (?, 'ADMINISTRADOR_GERAL', NULL, 'VIGENTE')",
                identityId))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("V9 preserva publicações e seus slots como snapshots imutáveis")
  void v9KeepsPublishedSnapshotsImmutable() {
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

    TransactionTemplate transaction =
        new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    UUID publicationId =
        transaction.execute(
            status -> {
              UUID identityId =
                  jdbcTemplate.queryForObject(
                      "INSERT INTO public.local_identity (issuer, subject) VALUES (?, ?) RETURNING identity_id",
                      UUID.class,
                      "https://example.invalid/realms/betufu-sports",
                      "sports-publisher");
              UUID competitionId =
                  jdbcTemplate.queryForObject(
                      "INSERT INTO public.sports_competition (name, slug) "
                          + "VALUES ('Competicao', 'competicao') RETURNING competition_id",
                      UUID.class);
              UUID editionId =
                  jdbcTemplate.queryForObject(
                      "INSERT INTO public.sports_edition (competition_id, name, slug) "
                          + "VALUES (?, 'Edicao', 'edicao') RETURNING edition_id",
                      UUID.class,
                      competitionId);
              UUID eventId =
                  jdbcTemplate.queryForObject(
                      "INSERT INTO public.sports_event "
                          + "(competition_id, edition_id, title, event_format, scheduled_at, zone_id, "
                          + "venue_state, source_reference) "
                          + "VALUES (?, ?, 'Evento', 'HEAD_TO_HEAD', CURRENT_TIMESTAMP, "
                          + "'America/Sao_Paulo', 'LOCAL_A_DEFINIR', 'https://example.invalid/source') "
                          + "RETURNING event_id",
                      UUID.class,
                      competitionId,
                      editionId);
              UUID createdPublicationId =
                  jdbcTemplate.queryForObject(
                      "INSERT INTO public.sports_event_publication "
                          + "(event_id, competition_id, edition_id, version_no, competition_name, "
                          + "edition_name, title, event_format, scheduled_at, zone_id, venue_state, "
                          + "source_reference, published_by_identity_id, base_lock_version) "
                          + "VALUES (?, ?, ?, 1, 'Competicao', 'Edicao', 'Evento', 'HEAD_TO_HEAD', "
                          + "CURRENT_TIMESTAMP, 'America/Sao_Paulo', 'LOCAL_A_DEFINIR', "
                          + "'https://example.invalid/source', ?, 0) RETURNING publication_id",
                      UUID.class,
                      eventId,
                      competitionId,
                      editionId,
                      identityId);
              jdbcTemplate.update(
                  "INSERT INTO public.sports_event_publication_slot "
                      + "(publication_id, slot_no, display_label) VALUES (?, 1, 'Equipe A')",
                  createdPublicationId);
              jdbcTemplate.update(
                  "INSERT INTO public.sports_event_publication_slot "
                      + "(publication_id, slot_no, display_label) VALUES (?, 2, 'Equipe B')",
                  createdPublicationId);
              return createdPublicationId;
            });

    assertThat(publicationId).isNotNull();

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO public.sports_event_publication_slot "
                        + "(publication_id, slot_no, display_label) VALUES (?, 3, 'Equipe tardia')",
                    publicationId))
        .hasMessageContaining("exactly two immutable slots");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.sports_event_publication SET title = 'Alterado' WHERE publication_id = ?",
                    publicationId))
        .hasMessageContaining("snapshots are immutable");
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "DELETE FROM public.sports_event_publication WHERE publication_id = ?",
                    publicationId))
        .hasMessageContaining("snapshots are immutable");
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.sports_event_publication_slot SET display_label = 'Alterado' "
                        + "WHERE publication_id = ? AND slot_no = 1",
                    publicationId))
        .hasMessageContaining("snapshots are immutable");
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "DELETE FROM public.sports_event_publication_slot "
                        + "WHERE publication_id = ? AND slot_no = 1",
                    publicationId))
        .hasMessageContaining("snapshots are immutable");
  }

  @Test
  @DisplayName("V12 garante integridade e constraints de public.sports_athlete no proprio banco")
  void v12EnforcesSportsAthleteConstraints() {
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

    UUID athleteId = UUID.randomUUID();
    byte[] validHmac = new byte[32];
    java.util.Arrays.fill(validHmac, (byte) 7);

    // Inserção válida com HMAC e versão
    assertThat(
            jdbcTemplate.update(
                "INSERT INTO public.sports_athlete (athlete_id, name, slug, matricula_hmac, hmac_version, status) "
                    + "VALUES (?, 'Carlos Atleta', 'carlos-atleta', ?, 1, 'RASCUNHO')",
                athleteId,
                validHmac))
        .isEqualTo(1);

    // Duplicidade de slug
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO public.sports_athlete (athlete_id, name, slug, status) "
                        + "VALUES (gen_random_uuid(), 'Outro Nome', 'carlos-atleta', 'RASCUNHO')"))
        .hasMessageContaining("uq_sports_athlete_slug");

    // Duplicidade de nome normalizado
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO public.sports_athlete (athlete_id, name, slug, status) "
                        + "VALUES (gen_random_uuid(), '  carlos atleta  ', 'outro-slug', 'RASCUNHO')"))
        .hasMessageContaining("uq_sports_athlete_name_normalized");

    // Duplicidade de matricula_hmac
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO public.sports_athlete (athlete_id, name, slug, matricula_hmac, hmac_version, status) "
                        + "VALUES (gen_random_uuid(), 'Terceiro Atleta', 'terceiro-atleta', ?, 1, 'RASCUNHO')",
                    validHmac))
        .hasMessageContaining("uq_sports_athlete_matricula_hmac");

    // HMAC com tamanho inválido (< 32 bytes)
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO public.sports_athlete (athlete_id, name, slug, matricula_hmac, hmac_version, status) "
                        + "VALUES (gen_random_uuid(), 'Quarto Atleta', 'quarto-atleta', 'short'::bytea, 1, 'RASCUNHO')"))
        .hasMessageContaining("ck_sports_athlete_hmac_octets");

    // Status inválido
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO public.sports_athlete (athlete_id, name, slug, status) "
                        + "VALUES (gen_random_uuid(), 'Quinto Atleta', 'quinto-atleta', 'INVALIDO')"))
        .hasMessageContaining("ck_sports_athlete_status");
  }

  @Test
  @DisplayName("V13 garante constraints de propostas de vínculo e fila de revisão no próprio banco")
  void v13EnforcesAthleteLinkProposalsAndReviewsConstraints() {
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

    UUID identity1 =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.local_identity (issuer, subject) VALUES ('https://idp.v13', 'sub-1') RETURNING identity_id",
            UUID.class);
    UUID identity2 =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.local_identity (issuer, subject) VALUES ('https://idp.v13', 'sub-2') RETURNING identity_id",
            UUID.class);

    UUID athlete1 = UUID.randomUUID();
    UUID athlete2 = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_athlete (athlete_id, name, slug, status) VALUES (?, 'Atleta 1', 'atleta-1', 'PUBLICADO')",
        athlete1);
    jdbcTemplate.update(
        "INSERT INTO public.sports_athlete (athlete_id, name, slug, status) VALUES (?, 'Atleta 2', 'atleta-2', 'PUBLICADO')",
        athlete2);

    // Inserção de proposta válida
    UUID proposal1 = UUID.randomUUID();
    assertThat(
            jdbcTemplate.update(
                "INSERT INTO public.sports_athlete_link_proposal (proposal_id, athlete_id, identity_id, status) "
                    + "VALUES (?, ?, ?, 'PENDENTE')",
                proposal1,
                athlete1,
                identity1))
        .isEqualTo(1);

    // Status de proposta inválido
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO public.sports_athlete_link_proposal (proposal_id, athlete_id, identity_id, status) "
                        + "VALUES (gen_random_uuid(), ?, ?, 'INVALIDO')",
                    athlete2,
                    identity2))
        .hasMessageContaining("ck_sports_athlete_link_proposal_decision_metadata");

    // Duas propostas pendentes para o mesmo par (athlete1, identity1) violam
    // uq_sports_athlete_link_proposal_pending_pair
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO public.sports_athlete_link_proposal (proposal_id, athlete_id, identity_id, status) "
                        + "VALUES (gen_random_uuid(), ?, ?, 'PENDENTE')",
                    athlete1,
                    identity1))
        .hasMessageContaining("uq_sports_athlete_link_proposal_pending_pair");

    // Aceita proposal1
    jdbcTemplate.update(
        "UPDATE public.sports_athlete_link_proposal "
            + "SET status = 'ACEITA', decided_by_identity_id = ?, decided_at = CURRENT_TIMESTAMP(6) "
            + "WHERE proposal_id = ?",
        identity1,
        proposal1);

    // Dois vínculos ACEITA para o mesmo athlete1 violam
    // uq_sports_athlete_link_proposal_accepted_athlete
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO public.sports_athlete_link_proposal "
                        + "(proposal_id, athlete_id, identity_id, status, decided_by_identity_id, decided_at) "
                        + "VALUES (gen_random_uuid(), ?, ?, 'ACEITA', ?, CURRENT_TIMESTAMP(6))",
                    athlete1,
                    identity2,
                    identity2))
        .hasMessageContaining("uq_sports_athlete_link_proposal_accepted_athlete");

    // Dois vínculos ACEITA para o mesmo identity1 violam
    // uq_sports_athlete_link_proposal_accepted_identity
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO public.sports_athlete_link_proposal "
                        + "(proposal_id, athlete_id, identity_id, status, decided_by_identity_id, decided_at) "
                        + "VALUES (gen_random_uuid(), ?, ?, 'ACEITA', ?, CURRENT_TIMESTAMP(6))",
                    athlete2,
                    identity1,
                    identity1))
        .hasMessageContaining("uq_sports_athlete_link_proposal_accepted_identity");

    // Inserção de revisão válida
    UUID reviewId = UUID.randomUUID();
    assertThat(
            jdbcTemplate.update(
                "INSERT INTO public.sports_athlete_link_review (review_id, proposal_id, athlete_id, identity_id, reason_code, status) "
                    + "VALUES (?, ?, ?, ?, 'CONTESTED', 'PENDENTE')",
                reviewId,
                proposal1,
                athlete1,
                identity1))
        .isEqualTo(1);

    // Reason code de revisão inválido
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO public.sports_athlete_link_review (review_id, athlete_id, reason_code, status) "
                        + "VALUES (gen_random_uuid(), ?, 'INVALID_REASON', 'PENDENTE')",
                    athlete2))
        .hasMessageContaining("ck_sports_athlete_link_review_reason");

    // Duplicidade de revisão por proposal_id
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO public.sports_athlete_link_review (review_id, proposal_id, athlete_id, identity_id, reason_code, status) "
                        + "VALUES (gen_random_uuid(), ?, ?, ?, 'CONTESTED', 'PENDENTE')",
                    proposal1,
                    athlete1,
                    identity1))
        .hasMessageContaining("uq_sports_athlete_link_review_proposal");
  }

  @Test
  @DisplayName("V14 exige validade e metadados coerentes para decisões de vínculo")
  void v14EnforcesAthleteLinkDecisionMetadata() {
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .cleanDisabled(false)
        .load()
        .clean();
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

    UUID identityId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.local_identity (issuer, subject) VALUES ('https://idp.invalid', ?) RETURNING identity_id",
            UUID.class,
            "v14-link-sub");
    UUID athleteId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.sports_athlete (name, slug) VALUES ('Atleta V14', 'atleta-v14') RETURNING athlete_id",
            UUID.class);
    UUID proposalId = UUID.randomUUID();
    assertThat(
            jdbcTemplate.update(
                "INSERT INTO public.sports_athlete_link_proposal (proposal_id, athlete_id, identity_id) VALUES (?, ?, ?)",
                proposalId,
                athleteId,
                identityId))
        .isEqualTo(1);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT expires_at > created_at FROM public.sports_athlete_link_proposal WHERE proposal_id = ?",
                Boolean.class,
                proposalId))
        .isTrue();

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.sports_athlete_link_proposal SET status = 'ACEITA' WHERE proposal_id = ?",
                    proposalId))
        .hasMessageContaining("ck_sports_athlete_link_proposal_decision_metadata");

    assertThat(
            jdbcTemplate.update(
                "UPDATE public.sports_athlete_link_proposal "
                    + "SET status = 'ACEITA', decided_by_identity_id = ?, decided_at = CURRENT_TIMESTAMP(6) "
                    + "WHERE proposal_id = ?",
                identityId,
                proposalId))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("V15 aplica restrições de elenco, locking e trigger de imutabilidade na publicação")
  void v15EnforcesSportsRosterConstraintsAndImmutability() {
    Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .cleanDisabled(false)
        .load()
        .clean();
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

    UUID identityId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.local_identity (issuer, subject) VALUES ('https://idp.invalid', ?) RETURNING identity_id",
            UUID.class,
            "v15-roster-sub");
    UUID athleteId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.sports_athlete (name, slug) VALUES ('Atleta V15', 'atleta-v15') RETURNING athlete_id",
            UUID.class);
    UUID competitionId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.sports_competition (name, slug) VALUES ('Comp V15', 'comp-v15') RETURNING competition_id",
            UUID.class);
    UUID editionId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.sports_edition (competition_id, name, slug) VALUES (?, 'Edição V15', 'edicao-v15') RETURNING edition_id",
            UUID.class,
            competitionId);
    UUID sportId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.sports_sport (name, slug) VALUES ('Sport V15', 'sport-v15') RETURNING sport_id",
            UUID.class);
    UUID categoryId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.sports_category (edition_id, name, slug) VALUES (?, 'Cat V15', 'cat-v15') RETURNING category_id",
            UUID.class,
            editionId);
    UUID organizationId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.sports_organization (name, slug) VALUES ('Org V15', 'org-v15') RETURNING organization_id",
            UUID.class);
    UUID teamId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.sports_team (organization_id, edition_id, sport_id, category_id, name, slug) "
                + "VALUES (?, ?, ?, ?, 'Time V15', 'time-v15') RETURNING team_id",
            UUID.class,
            organizationId,
            editionId,
            sportId,
            categoryId);

    UUID rosterId = UUID.randomUUID();
    assertThat(
            jdbcTemplate.update(
                "INSERT INTO public.sports_roster (roster_id, team_id, status, lock_version) VALUES (?, ?, 'RASCUNHO', 0)",
                rosterId,
                teamId))
        .isEqualTo(1);

    assertThat(
            jdbcTemplate.update(
                "INSERT INTO public.sports_roster_member (roster_id, athlete_id, jersey_number, position_name) VALUES (?, ?, 10, 'Ala')",
                rosterId,
                athleteId))
        .isEqualTo(1);

    UUID publicationId = UUID.randomUUID();
    assertThat(
            jdbcTemplate.update(
                """
                INSERT INTO public.sports_roster_publication (
                    publication_id, roster_id, team_id, competition_id, edition_id, organization_id,
                    sport_id, category_id, version_no, team_name, organization_name, edition_name,
                    competition_name, sport_name, category_name, published_by_identity_id, base_lock_version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, 'Time V15', 'Org V15', 'Edição V15', 'Comp V15', 'Sport V15', 'Cat V15', ?, 0)
                """,
                publicationId,
                rosterId,
                teamId,
                competitionId,
                editionId,
                organizationId,
                sportId,
                categoryId,
                identityId))
        .isEqualTo(1);

    assertThat(
            jdbcTemplate.update(
                "INSERT INTO public.sports_roster_publication_member (publication_id, athlete_id, athlete_name, athlete_slug, jersey_number, position_name) "
                    + "VALUES (?, ?, 'Atleta V15', 'atleta-v15', 10, 'Ala')",
                publicationId,
                athleteId))
        .isEqualTo(1);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.sports_roster_publication SET team_name = 'Novo Nome' WHERE publication_id = ?",
                    publicationId))
        .hasMessageContaining("snapshots are immutable");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "DELETE FROM public.sports_roster_publication WHERE publication_id = ?",
                    publicationId))
        .hasMessageContaining("snapshots are immutable");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.sports_roster_publication_member SET jersey_number = 99 WHERE publication_id = ?",
                    publicationId))
        .hasMessageContaining("snapshots are immutable");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "DELETE FROM public.sports_roster_publication_member WHERE publication_id = ?",
                    publicationId))
        .hasMessageContaining("snapshots are immutable");
  }

  @Test
  @DisplayName(
      "V16 impõe restrições de ciclo de vida de eventos e imutabilidade de versões de resultado")
  void v16EnforcesEventResultVersionsConstraintsAndImmutability() {
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

    UUID identityId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.local_identity (issuer, subject) VALUES ('https://idp.invalid', ?) RETURNING identity_id",
            UUID.class,
            "v16-event-result-sub");
    UUID competitionId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.sports_competition (name, slug) VALUES ('Comp V16', 'comp-v16') RETURNING competition_id",
            UUID.class);
    UUID editionId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.sports_edition (competition_id, name, slug) VALUES (?, 'Edição V16', 'edicao-v16') RETURNING edition_id",
            UUID.class,
            competitionId);

    UUID eventId = UUID.randomUUID();
    assertThat(
            jdbcTemplate.update(
                """
                INSERT INTO public.sports_event (
                    event_id, competition_id, edition_id, title, event_format, status,
                    scheduled_at, zone_id, venue_state, source_reference, lock_version
                ) VALUES (?, ?, ?, 'Evento V16', 'HEAD_TO_HEAD', 'PUBLICADO', CURRENT_TIMESTAMP(6), 'America/Sao_Paulo', 'LOCAL_A_DEFINIR', 'https://betufu.ufu.br', 0)
                """,
                eventId,
                competitionId,
                editionId))
        .isEqualTo(1);

    assertThat(
            jdbcTemplate.update(
                "INSERT INTO public.sports_event_slot (event_id, slot_no, display_label) VALUES (?, 1, 'Equipe 1')",
                eventId))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.update(
                "INSERT INTO public.sports_event_slot (event_id, slot_no, display_label) VALUES (?, 2, 'Equipe 2')",
                eventId))
        .isEqualTo(1);

    TransactionTemplate transaction =
        new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    UUID versionId =
        transaction.execute(
            status -> {
              UUID resultVersionId = UUID.randomUUID();
              assertThat(
                      jdbcTemplate.update(
                          """
                    INSERT INTO public.sports_result_version (
                        version_id, event_id, competition_id, edition_id, version_no, status,
                        source_reference, recorded_by_identity_id, confirmed_by_identity_id,
                        confirmed_at, base_lock_version
                    ) VALUES (?, ?, ?, ?, 1, 'CONFIRMADO', 'https://boletim.ufu.br', ?, ?, CURRENT_TIMESTAMP(6), 0)
                    """,
                          resultVersionId,
                          eventId,
                          competitionId,
                          editionId,
                          identityId,
                          identityId))
                  .isEqualTo(1);

              assertThat(
                      jdbcTemplate.update(
                          "INSERT INTO public.sports_result_version_slot (version_id, slot_no, score, display_label) VALUES (?, 1, 2, 'Equipe 1')",
                          resultVersionId))
                  .isEqualTo(1);
              assertThat(
                      jdbcTemplate.update(
                          "INSERT INTO public.sports_result_version_slot (version_id, slot_no, score, display_label) VALUES (?, 2, 1, 'Equipe 2')",
                          resultVersionId))
                  .isEqualTo(1);
              return resultVersionId;
            });

    assertThat(
            jdbcTemplate.update(
                "UPDATE public.sports_event SET confirmed_version_id = ? WHERE event_id = ?",
                versionId,
                eventId))
        .isEqualTo(1);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.sports_result_version SET source_reference = 'https://alterado.ufu.br' WHERE version_id = ?",
                    versionId))
        .hasMessageContaining("snapshots are immutable");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "DELETE FROM public.sports_result_version WHERE version_id = ?", versionId))
        .hasMessageContaining("snapshots are immutable");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.sports_result_version_slot SET score = 10 WHERE version_id = ?",
                    versionId))
        .hasMessageContaining("snapshots are immutable");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "DELETE FROM public.sports_result_version_slot WHERE version_id = ?",
                    versionId))
        .hasMessageContaining("snapshots are immutable");
  }

  @Test
  @DisplayName("V17 aplica restrições de chaveamento e imutabilidade de publicação no PostgreSQL")
  void v17EnforcesSportsBracketConstraintsAndImmutability() {
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

    UUID identityId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.local_identity (issuer, subject) VALUES (?, ?) RETURNING identity_id",
            UUID.class,
            "https://idp.invalid",
            "bracket-admin-v17");

    UUID competitionId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.sports_competition (name, slug) VALUES ('Competição V17', 'comp-v17') RETURNING competition_id",
            UUID.class);

    UUID editionId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.sports_edition (competition_id, name, slug) VALUES (?, 'Edição V17', 'ed-v17') RETURNING edition_id",
            UUID.class,
            competitionId);

    UUID otherCompetitionId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.sports_competition (name, slug) VALUES ('CompetiÃ§Ã£o V17 B', 'comp-v17-b') RETURNING competition_id",
            UUID.class);
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO public.sports_bracket (bracket_id, competition_id, edition_id, name, slug) VALUES (?, ?, ?, 'Fora do escopo', 'fora-do-escopo')",
                    UUID.randomUUID(),
                    otherCompetitionId,
                    editionId))
        .hasMessageContaining("sports_bracket");

    UUID bracketId = UUID.randomUUID();
    assertThat(
            jdbcTemplate.update(
                """
                INSERT INTO public.sports_bracket (
                    bracket_id, competition_id, edition_id, name, slug, status, lock_version
                ) VALUES (?, ?, ?, 'Chaveamento V17', 'chave-v17', 'PUBLICADO', 1)
                """,
                bracketId,
                competitionId,
                editionId))
        .isEqualTo(1);

    UUID matchId = UUID.randomUUID();
    assertThat(
            jdbcTemplate.update(
                """
                INSERT INTO public.sports_bracket_match (
                    match_id, bracket_id, round_no, match_no, round_name, is_third_place
                ) VALUES (?, ?, 1, 1, 'Final', false)
                """,
                matchId,
                bracketId))
        .isEqualTo(1);

    assertThat(
            jdbcTemplate.update(
                """
                INSERT INTO public.sports_bracket_match_slot (
                    match_id, slot_no, source_type, seed_label
                ) VALUES (?, 1, 'DIRECT', 'Equipe 1'), (?, 2, 'DIRECT', 'Equipe 2')
                """,
                matchId,
                matchId))
        .isEqualTo(2);

    UUID publicationId = UUID.randomUUID();
    assertThat(
            jdbcTemplate.update(
                """
                INSERT INTO public.sports_bracket_publication (
                    publication_id, bracket_id, competition_id, edition_id, version_no,
                    bracket_name, competition_name, edition_name, source_reference,
                    published_by_identity_id, base_lock_version
                ) VALUES (?, ?, ?, ?, 1, 'Chaveamento V17', 'Competição V17', 'Edição V17', 'https://boletim.ufu.br', ?, 0)
                """,
                publicationId,
                bracketId,
                competitionId,
                editionId,
                identityId))
        .isEqualTo(1);

    assertThat(
            jdbcTemplate.update(
                """
                INSERT INTO public.sports_bracket_publication_node (
                    publication_id, match_id, round_no, match_no, round_name, is_third_place,
                    slot1_source_type, slot1_seed_label, slot2_source_type, slot2_seed_label
                ) VALUES (?, ?, 1, 1, 'Final', false, 'DIRECT', 'Equipe 1', 'DIRECT', 'Equipe 2')
                """,
                publicationId,
                matchId))
        .isEqualTo(1);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.sports_bracket_publication SET source_reference = 'https://alterado.ufu.br' WHERE publication_id = ?",
                    publicationId))
        .hasMessageContaining("snapshots are immutable");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "DELETE FROM public.sports_bracket_publication WHERE publication_id = ?",
                    publicationId))
        .hasMessageContaining("snapshots are immutable");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.sports_bracket_publication_node SET round_name = 'Alterado' WHERE publication_id = ?",
                    publicationId))
        .hasMessageContaining("snapshots are immutable");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "DELETE FROM public.sports_bracket_publication_node WHERE publication_id = ?",
                    publicationId))
        .hasMessageContaining("snapshots are immutable");
  }

  @Test
  @DisplayName("V18 aplica restrições de classificação e imutabilidade de publicação no PostgreSQL")
  void v18EnforcesSportsStandingsConstraintsAndImmutability() {
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

    UUID identityId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.local_identity (issuer, subject) VALUES (?, ?) RETURNING identity_id",
            UUID.class,
            "https://idp.invalid",
            "standings-admin-v18");

    UUID competitionId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.sports_competition (name, slug) VALUES ('Competição V18', 'comp-v18') RETURNING competition_id",
            UUID.class);

    UUID editionId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.sports_edition (competition_id, name, slug) VALUES (?, 'Edição V18', 'ed-v18') RETURNING edition_id",
            UUID.class,
            competitionId);

    UUID otherCompetitionId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.sports_competition (name, slug) VALUES ('Competição V18 B', 'comp-v18-b') RETURNING competition_id",
            UUID.class);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO public.sports_standings (standings_id, competition_id, edition_id, name, slug) VALUES (?, ?, ?, 'Fora do escopo', 'fora-do-escopo')",
                    UUID.randomUUID(),
                    otherCompetitionId,
                    editionId))
        .hasMessageContaining("sports_standings");

    UUID standingsId = UUID.randomUUID();
    assertThat(
            jdbcTemplate.update(
                """
                INSERT INTO public.sports_standings (
                    standings_id, competition_id, edition_id, name, slug, status, lock_version
                ) VALUES (?, ?, ?, 'Classificação V18', 'class-v18', 'PUBLICADO', 1)
                """,
                standingsId,
                competitionId,
                editionId))
        .isEqualTo(1);

    assertThat(
            jdbcTemplate.update(
                """
                INSERT INTO public.sports_standings_column (standings_id, column_order, label)
                VALUES (?, 1, 'PTS'), (?, 2, 'J')
                """,
                standingsId,
                standingsId))
        .isEqualTo(2);

    UUID rowId = UUID.randomUUID();
    assertThat(
            jdbcTemplate.update(
                """
                INSERT INTO public.sports_standings_row (
                    row_id, standings_id, row_order, position, participant_name
                ) VALUES (?, ?, 1, 1, 'Equipe 1')
                """,
                rowId,
                standingsId))
        .isEqualTo(1);

    assertThat(
            jdbcTemplate.update(
                """
                INSERT INTO public.sports_standings_row_cell (row_id, column_order, cell_value)
                VALUES (?, 1, '6'), (?, 2, '2')
                """,
                rowId,
                rowId))
        .isEqualTo(2);

    UUID publicationId = UUID.randomUUID();
    assertThat(
            jdbcTemplate.update(
                """
                INSERT INTO public.sports_standings_publication (
                    publication_id, standings_id, competition_id, edition_id, version_no,
                    standings_name, competition_name, edition_name, source_reference,
                    published_by_identity_id, base_lock_version
                ) VALUES (?, ?, ?, ?, 1, 'Classificação V18', 'Competição V18', 'Edição V18', 'https://boletim.ufu.br', ?, 0)
                """,
                publicationId,
                standingsId,
                competitionId,
                editionId,
                identityId))
        .isEqualTo(1);

    assertThat(
            jdbcTemplate.update(
                """
                INSERT INTO public.sports_standings_publication_column (publication_id, column_order, label)
                VALUES (?, 1, 'PTS'), (?, 2, 'J')
                """,
                publicationId,
                publicationId))
        .isEqualTo(2);

    assertThat(
            jdbcTemplate.update(
                """
                INSERT INTO public.sports_standings_publication_row (
                    publication_id, row_order, position, participant_name
                ) VALUES (?, 1, 1, 'Equipe 1')
                """,
                publicationId))
        .isEqualTo(1);

    assertThat(
            jdbcTemplate.update(
                """
                INSERT INTO public.sports_standings_publication_cell (
                    publication_id, row_order, column_order, cell_value
                ) VALUES (?, 1, 1, '6'), (?, 1, 2, '2')
                """,
                publicationId,
                publicationId))
        .isEqualTo(2);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.sports_standings_publication SET source_reference = 'https://alterado.ufu.br' WHERE publication_id = ?",
                    publicationId))
        .hasMessageContaining("snapshots are immutable");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "DELETE FROM public.sports_standings_publication WHERE publication_id = ?",
                    publicationId))
        .hasMessageContaining("snapshots are immutable");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.sports_standings_publication_column SET label = 'Alterado' WHERE publication_id = ?",
                    publicationId))
        .hasMessageContaining("snapshots are immutable");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "DELETE FROM public.sports_standings_publication_column WHERE publication_id = ?",
                    publicationId))
        .hasMessageContaining("snapshots are immutable");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.sports_standings_publication_row SET participant_name = 'Alterado' WHERE publication_id = ?",
                    publicationId))
        .hasMessageContaining("snapshots are immutable");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "DELETE FROM public.sports_standings_publication_row WHERE publication_id = ?",
                    publicationId))
        .hasMessageContaining("snapshots are immutable");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.sports_standings_publication_cell SET cell_value = '99' WHERE publication_id = ?",
                    publicationId))
        .hasMessageContaining("snapshots are immutable");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "DELETE FROM public.sports_standings_publication_cell WHERE publication_id = ?",
                    publicationId))
        .hasMessageContaining("snapshots are immutable");
  }

  @Test
  @DisplayName("V19 aplica restrições de perfil público do atleta no PostgreSQL")
  void v19EnforcesAthletePublicProfileConstraints() {
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

    UUID athleteId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.sports_athlete (name, slug, status) VALUES ('Atleta V19', 'atleta-v19', 'PUBLICADO') RETURNING athlete_id",
            UUID.class);

    // Inserção válida
    assertThat(
            jdbcTemplate.update(
                """
                INSERT INTO public.sports_athlete_public_profile (
                    athlete_id, course, bio, instagram_url, x_url, linkedin_url, strava_url, youtube_url, lock_version
                ) VALUES (?, 'Engenharia', 'Bio teste', 'https://instagram.com/atleta', 'https://x.com/atleta',
                          'https://linkedin.com/in/atleta', 'https://strava.com/athletes/1', 'https://youtube.com/@atleta', 0)
                """,
                athleteId))
        .isEqualTo(1);

    // Atualização válida incrementando lock_version
    assertThat(
            jdbcTemplate.update(
                "UPDATE public.sports_athlete_public_profile SET bio = 'Nova bio', lock_version = lock_version + 1 WHERE athlete_id = ?",
                athleteId))
        .isEqualTo(1);

    // Curso vazio viola CHECK (deve ser NULL ou não-vazio)
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.sports_athlete_public_profile SET course = '   ' WHERE athlete_id = ?",
                    athleteId))
        .hasMessageContaining("ck_sports_athlete_public_profile_course");

    // Bio vazia viola CHECK
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.sports_athlete_public_profile SET bio = '   ' WHERE athlete_id = ?",
                    athleteId))
        .hasMessageContaining("ck_sports_athlete_public_profile_bio");

    // URL do Instagram com host inválido
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.sports_athlete_public_profile SET instagram_url = 'https://fake.com/atleta' WHERE athlete_id = ?",
                    athleteId))
        .hasMessageContaining("ck_sports_athlete_public_profile_instagram_url");

    // URL do Instagram com HTTP
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.sports_athlete_public_profile SET instagram_url = 'http://instagram.com/atleta' WHERE athlete_id = ?",
                    athleteId))
        .hasMessageContaining("ck_sports_athlete_public_profile_instagram_url");

    // URL do X com host inválido
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.sports_athlete_public_profile SET x_url = 'https://other.com/atleta' WHERE athlete_id = ?",
                    athleteId))
        .hasMessageContaining("ck_sports_athlete_public_profile_x_url");

    // URL do LinkedIn com host inválido
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.sports_athlete_public_profile SET linkedin_url = 'https://other.com/atleta' WHERE athlete_id = ?",
                    athleteId))
        .hasMessageContaining("ck_sports_athlete_public_profile_linkedin_url");

    // URL do Strava com host inválido
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.sports_athlete_public_profile SET strava_url = 'https://other.com/atleta' WHERE athlete_id = ?",
                    athleteId))
        .hasMessageContaining("ck_sports_athlete_public_profile_strava_url");

    // URL do YouTube com host inválido
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.sports_athlete_public_profile SET youtube_url = 'https://other.com/atleta' WHERE athlete_id = ?",
                    athleteId))
        .hasMessageContaining("ck_sports_athlete_public_profile_youtube_url");

    // FK para atleta inexistente
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO public.sports_athlete_public_profile (athlete_id) VALUES (?)",
                    UUID.randomUUID()))
        .hasMessageContaining("fk_sports_athlete_public_profile_athlete");
  }

  @Test
  @DisplayName("V20 aplica referência privada, limites e tupla consistente de mídia")
  void v20EnforcesAthleteProfileMediaConstraints() {
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

    UUID athleteId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.sports_athlete (name, slug, status) VALUES ('Atleta V20', 'atleta-v20', 'PUBLICADO') RETURNING athlete_id",
            UUID.class);

    assertThat(
            jdbcTemplate.update(
                "INSERT INTO public.sports_athlete_profile_media (athlete_id, object_key, content_type, byte_size, width, height, etag, media_version, lock_version) VALUES (?, 'athlete-profile/opaque', 'image/png', 165, 64, 64, 'etag-v20', 1, 1)",
                athleteId))
        .isEqualTo(1);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.sports_athlete_profile_media SET content_type = 'image/gif' WHERE athlete_id = ?",
                    athleteId))
        .hasMessageContaining("ck_sports_athlete_profile_media_content_type");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.sports_athlete_profile_media SET byte_size = 5242881 WHERE athlete_id = ?",
                    athleteId))
        .hasMessageContaining("ck_sports_athlete_profile_media_byte_size");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.sports_athlete_profile_media SET width = 2001, height = 2000 WHERE athlete_id = ?",
                    athleteId))
        .hasMessageContaining("ck_sports_athlete_profile_media_dimensions");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE public.sports_athlete_profile_media SET object_key = NULL WHERE athlete_id = ?",
                    athleteId))
        .hasMessageContaining("ck_sports_athlete_profile_media_reference_tuple");

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO public.sports_athlete_profile_media (athlete_id) VALUES (?)",
                    UUID.randomUUID()))
        .hasMessageContaining("fk_sports_athlete_profile_media_athlete");
  }
}
