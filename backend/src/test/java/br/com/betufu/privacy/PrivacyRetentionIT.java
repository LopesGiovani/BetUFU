package br.com.betufu.privacy.internal;

import static org.assertj.core.api.Assertions.*;

import br.com.betufu.chat.internal.ChatModerationRepository;
import br.com.betufu.chat.internal.ChatPushService;
import br.com.betufu.chat.internal.ChatRepository;
import br.com.betufu.chat.internal.privacy.ChatPrivacyAdapter;
import br.com.betufu.privacy.PrivacyRestrictedRecordsPort.DataClass;
import br.com.betufu.privacy.PrivacySubjectPort;
import java.sql.Timestamp;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class PrivacyRetentionIT {
  @Container
  static final PostgreSQLContainer PG =
      new PostgreSQLContainer(
          DockerImageName.parse("docker.io/library/postgres:18.4-trixie")
              .asCompatibleSubstituteFor("postgres"));

  static JdbcTemplate owner;
  JdbcTemplate runtime, operator;
  UUID admin, author, channel;
  final Instant now = Instant.parse("2026-10-06T12:00:00Z");
  volatile boolean authorized;
  PrivacyRetentionService service, runtimeService;
  PrivacyRetentionRegistry registry;
  TransactionTemplate tx;

  @BeforeAll
  static void roles() {
    owner = new JdbcTemplate(source(PG.getUsername(), PG.getPassword()));
    owner.execute("CREATE ROLE privacy_runtime LOGIN PASSWORD 'test-runtime'");
    owner.execute("CREATE ROLE betufu_privacy_retention NOLOGIN");
    owner.execute(
        "ALTER DATABASE " + PG.getDatabaseName() + " SET betufu.runtime_role='privacy_runtime'");
  }

  static DriverManagerDataSource source(String user, String password) {
    return new DriverManagerDataSource(PG.getJdbcUrl(), user, password);
  }

  @BeforeEach
  void setup() {
    owner.execute(
        "DROP SCHEMA public CASCADE; CREATE SCHEMA public; REVOKE CREATE ON SCHEMA public FROM PUBLIC; GRANT USAGE ON SCHEMA public TO privacy_runtime");
    owner.execute(
        "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO privacy_runtime");
    owner.execute(
        "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE,SELECT ON SEQUENCES TO privacy_runtime");
    Flyway.configure().dataSource(source(PG.getUsername(), PG.getPassword())).load().migrate();
    owner.execute(
        "DO $$ BEGIN IF NOT EXISTS(SELECT FROM pg_roles WHERE rolname='privacy_operator') THEN CREATE ROLE privacy_operator LOGIN PASSWORD 'test-operator'; END IF; END $$");
    owner.execute("GRANT privacy_runtime,betufu_privacy_retention TO privacy_operator");
    runtime = new JdbcTemplate(source("privacy_runtime", "test-runtime"));
    var ds = source("privacy_operator", "test-operator");
    operator = new JdbcTemplate(ds);
    tx = new TransactionTemplate(new DataSourceTransactionManager(ds));
    admin = identity("admin");
    author = identity("author");
    channel =
        runtime.queryForObject(
            "SELECT channel_id FROM chat_channel WHERE kind='GLOBAL'", UUID.class);
    authorized = true;
    registry = new PrivacyRetentionRegistry(operator);
    service = service(operator, registry);
    runtimeService = service(runtime, new PrivacyRetentionRegistry(runtime));
  }

  UUID identity(String subject) {
    return runtime.queryForObject(
        "INSERT INTO local_identity(issuer,subject) VALUES ('https://retention.invalid',?) RETURNING identity_id",
        UUID.class,
        subject);
  }

  PrivacyRetentionService service(JdbcTemplate jdbc, PrivacyRetentionRegistry registry) {
    var identities =
        new PrivacySubjectPort.Identity() {
          public Optional<UUID> findAuthenticatedIdentity(String issuer, String subject) {
            return Optional.empty();
          }

          public boolean isGeneralAdministrator(UUID id) {
            return authorized && admin.equals(id);
          }
        };
    var factory = new StaticListableBeanFactory();
    var chat = new ChatPrivacyAdapter(jdbc, factory.getBeanProvider(ChatPushService.class));
    return new PrivacyRetentionService(
        chat, registry, identities, jdbc, Clock.fixed(now, ZoneOffset.UTC));
  }

  UUID message(Instant posted) {
    return runtime.queryForObject(
        "INSERT INTO chat_message(channel_id,author_identity_id,body,posted_at) VALUES (?,?,'restricted original',?) RETURNING message_id",
        UUID.class,
        channel,
        author,
        Timestamp.from(posted));
  }

  UUID moderation(UUID message, Instant closed) {
    UUID id =
        runtime.queryForObject(
            "INSERT INTO chat_moderation_case(message_id,channel_id,status) VALUES (?,?,'RESOLVED') RETURNING case_id",
            UUID.class,
            message,
            channel);
    runtime.update(
        "INSERT INTO chat_moderation_decision_fact(case_id,actor_identity_id,decision_kind,reason,client_command_id,recorded_at) VALUES (?,?,'NO_ACTION','fixture',?,?)",
        id,
        admin,
        UUID.randomUUID(),
        Timestamp.from(closed));
    return id;
  }

  String state(UUID id) {
    var entries = service.dryRun(admin).entries();
    return entries.stream()
        .filter(
            e ->
                id.equals(e.recordId())
                    && (e.dataClass() == DataClass.MODERATION_EVIDENCE
                        || entries.stream()
                            .noneMatch(
                                v ->
                                    id.equals(v.recordId())
                                        && v.dataClass() == DataClass.MODERATION_EVIDENCE)))
        .findFirst()
        .orElseThrow()
        .state();
  }

  void apply(UUID message, DataClass type, String digest) {
    tx.executeWithoutResult(s -> service.apply(admin, digest, List.of(message), type));
  }

  @Test
  void common90DaysUsesInclusiveDeadlineAndDoesNotMutateFacts() {
    UUID exact = message(now.minus(90, ChronoUnit.DAYS));
    UUID fresh = message(now.minus(90, ChronoUnit.DAYS).plusNanos(1000));
    assertThat(state(exact)).isEqualTo("ELIGIBLE");
    assertThat(state(fresh)).isEqualTo("RETAINED");
    apply(exact, DataClass.COMMON_MESSAGES, service.dryRun(admin).digest());
    assertThat(state(exact)).isEqualTo("SUPPRESSED");
    assertThat(
            runtime.queryForObject(
                "SELECT body FROM chat_message WHERE message_id=?", String.class, exact))
        .isEqualTo("restricted original");
    assertThat(service.currentRegister(admin)).hasSize(1);
  }

  @Test
  void evidence180DaysStartsAtImmutableClosureNotMessageOrMutableProjectionTime() {
    UUID exact = message(now.minus(400, ChronoUnit.DAYS));
    moderation(exact, now.minus(180, ChronoUnit.DAYS));
    UUID fresh = message(now.minus(500, ChronoUnit.DAYS));
    moderation(fresh, now.minus(180, ChronoUnit.DAYS).plusNanos(1000));
    assertThat(state(exact)).isEqualTo("ELIGIBLE");
    assertThat(state(fresh)).isEqualTo("RETAINED");
    assertThat(service.dryRun(admin).entries())
        .filteredOn(e -> exact.equals(e.recordId()))
        .extracting(PrivacyRetentionService.Entry::dataClass)
        .containsExactly(DataClass.COMMON_MESSAGES, DataClass.MODERATION_EVIDENCE);
  }

  @Test
  void reopenedCaseAndPendingReviewBlockEvenWhenProjectionClaimsResolved() {
    UUID id = message(now.minus(400, ChronoUnit.DAYS));
    UUID c = moderation(id, now.minus(200, ChronoUnit.DAYS));
    runtime.update("UPDATE chat_moderation_case SET status='OPEN' WHERE case_id=?", c);
    assertThat(state(id)).isEqualTo("HELD");
    runtime.update("UPDATE chat_moderation_case SET status='RESOLVED' WHERE case_id=?", c);
    runtime.update(
        "INSERT INTO chat_moderation_review_fact(case_id,actor_identity_id,review_kind,reason,client_command_id,recorded_at) VALUES (?,?,'OPENED','review',?,?)",
        c,
        author,
        UUID.randomUUID(),
        Timestamp.from(now.minus(1, ChronoUnit.DAYS)));
    assertThat(state(id)).isEqualTo("HELD");
    runtime.update(
        "INSERT INTO chat_moderation_review_fact(case_id,actor_identity_id,review_kind,reason,client_command_id,recorded_at) VALUES (?,?,'REVIEWED','review',?,?)",
        c,
        admin,
        UUID.randomUUID(),
        Timestamp.from(now));
    assertThat(state(id)).isEqualTo("RETAINED");
  }

  @Test
  void unresolvedReportBlocksCommonExpirationAndInvalidatesApprovedInventory() {
    UUID id = message(now.minus(100, ChronoUnit.DAYS));
    String digest = service.dryRun(admin).digest();
    runtime.update(
        "INSERT INTO chat_report(reporter_identity_id,message_id,reason) VALUES (?,?,'SPAM')",
        admin,
        id);
    assertThat(state(id)).isEqualTo("HELD");
    assertThatThrownBy(() -> apply(id, DataClass.COMMON_MESSAGES, digest))
        .isInstanceOf(br.com.betufu.platform.api.ApiProblemException.class);
    assertThat(service.currentRegister(admin)).isEmpty();
  }

  @Test
  void legalHoldMustBeReleasedByMatchingFactAndCannotBeOverwritten() {
    UUID id = message(now.minus(100, ChronoUnit.DAYS));
    UUID hold =
        operator.queryForObject(
            "INSERT INTO privacy_retention_hold_fact(data_class,record_id,action,actor_identity_id,reason_reference) VALUES ('COMMON_MESSAGES',?,'HOLD',?,'legal-ref') RETURNING hold_id",
            UUID.class,
            id,
            admin);
    assertThat(state(id)).isEqualTo("HELD");
    assertThatThrownBy(
            () ->
                operator.update(
                    "INSERT INTO privacy_retention_hold_fact(data_class,record_id,action,releases_hold_id,actor_identity_id,reason_reference) VALUES ('COMMON_MESSAGES',?,'RELEASE',?,?,'legal-ref')",
                    UUID.randomUUID(),
                    hold,
                    admin))
        .isInstanceOf(DataAccessException.class);
    operator.update(
        "INSERT INTO privacy_retention_hold_fact(data_class,record_id,action,releases_hold_id,actor_identity_id,reason_reference) VALUES ('COMMON_MESSAGES',?,'RELEASE',?,?,'legal-ref')",
        id,
        hold,
        admin);
    assertThat(state(id)).isEqualTo("ELIGIBLE");
  }

  @Test
  void runtimeAndRevokedAdministratorsCannotApplyEvenWithValidDigest() {
    UUID id = message(now.minus(100, ChronoUnit.DAYS));
    String digest = service.dryRun(admin).digest();
    assertThatThrownBy(
            () -> runtimeService.apply(admin, digest, List.of(id), DataClass.COMMON_MESSAGES))
        .isInstanceOf(br.com.betufu.platform.api.ApiProblemException.class);
    authorized = false;
    assertThatThrownBy(() -> apply(id, DataClass.COMMON_MESSAGES, digest))
        .isInstanceOf(br.com.betufu.platform.api.ApiProblemException.class);
  }

  @Test
  void unsupportedClassesAndWholeRequestFailBeforeAnySuppression() {
    UUID id = message(now.minus(100, ChronoUnit.DAYS));
    String digest = service.dryRun(admin).digest();
    assertThat(service.dryRun(admin).entries()).filteredOn(e -> e.recordId() == null).hasSize(10);
    assertThatThrownBy(() -> apply(id, DataClass.LEDGER, digest))
        .isInstanceOf(br.com.betufu.platform.api.ApiProblemException.class);
    assertThatThrownBy(
            () ->
                tx.executeWithoutResult(
                    s ->
                        service.apply(
                            admin,
                            digest,
                            List.of(id, UUID.randomUUID()),
                            DataClass.COMMON_MESSAGES)))
        .isInstanceOf(br.com.betufu.platform.api.ApiProblemException.class);
    assertThat(service.currentRegister(admin)).isEmpty();
  }

  @Test
  void suppressionFactsAndPayloadsAreImmutableForRuntimeOperatorAndOwner() {
    for (String table :
        List.of(
            "privacy_suppression_fact",
            "privacy_retention_hold_fact",
            "privacy_restricted_export_payload")) {
      assertThatThrownBy(() -> owner.execute("DELETE FROM " + table))
          .isInstanceOf(DataAccessException.class)
          .hasMessageContaining("history is immutable");
      assertThatThrownBy(() -> runtime.execute("DELETE FROM " + table))
          .isInstanceOf(DataAccessException.class);
      assertThatThrownBy(() -> operator.execute("TRUNCATE " + table))
          .isInstanceOf(DataAccessException.class);
    }
    assertThatThrownBy(
            () ->
                runtime.update(
                    "INSERT INTO privacy_suppression_fact(data_class,record_id,actor_identity_id,inventory_digest,policy_reference) VALUES ('COMMON_MESSAGES',?,?,?,'forged')",
                    UUID.randomUUID(),
                    admin,
                    "a".repeat(64)))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void currentRegisterAndServingHideSuppressionAfterSnapshotAndLostProjection() {
    UUID id = message(now.minus(100, ChronoUnit.DAYS));
    long snapshotCursor =
        runtime.queryForObject("SELECT max(change_cursor) FROM chat_message", Long.class);
    assertThat(service.currentRegister(admin)).isEmpty();
    apply(id, DataClass.COMMON_MESSAGES, service.dryRun(admin).digest());
    runtime.execute("DELETE FROM chat_message_projection");
    var reads = new ChatRepository(runtime);
    Object room = ReflectionTestUtils.invokeMethod(reads, "global");
    List<?> history = ReflectionTestUtils.invokeMethod(reads, "history", room, null, 50);
    assertThat(history).hasSize(1);
    assertThat(history.getFirst())
        .extracting("text", "authorId", "removed")
        .containsExactly(null, null, true);
    List<?> changes = ReflectionTestUtils.invokeMethod(reads, "changes", room, snapshotCursor, 50);
    assertThat(changes).hasSize(1);
    assertThat(service.currentRegister(admin).getFirst().get("change_cursor")).isNotNull();
  }

  @Test
  void expiredEvidenceCannotBeReadAndBanProgressionSurvives() {
    UUID id = message(now.minus(400, ChronoUnit.DAYS));
    UUID c = moderation(id, now.minus(200, ChronoUnit.DAYS));
    runtime.update(
        "INSERT INTO chat_moderation_sanction_fact(case_id,actor_identity_id,target_identity_id,sanction_kind,reason,client_command_id,effective_from,recorded_at) VALUES (?,?,?,'CHAT_BAN','minimum audit',?,?,?)",
        c,
        admin,
        author,
        UUID.randomUUID(),
        Timestamp.from(now.minus(200, ChronoUnit.DAYS)),
        Timestamp.from(now.minus(200, ChronoUnit.DAYS)));
    apply(id, DataClass.MODERATION_EVIDENCE, service.dryRun(admin).digest());
    var moderation = new ChatModerationRepository(runtime);
    assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(moderation, "evidence", c))
        .isInstanceOf(br.com.betufu.platform.api.ApiProblemException.class);
    assertThat(
            runtime.queryForObject(
                "SELECT count(*) FROM chat_moderation_sanction_fact WHERE target_identity_id=? AND sanction_kind='CHAT_BAN'",
                Integer.class,
                author))
        .isEqualTo(1);
    assertThat(
            runtime.queryForObject(
                "SELECT body FROM chat_message WHERE message_id=?", String.class, id))
        .isEqualTo("restricted original");
  }

  @Test
  void holdCommittedDuringRealLockWaitInvalidatesPriorDryRun() throws Exception {
    UUID id = message(now.minus(100, ChronoUnit.DAYS));
    String digest = service.dryRun(admin).digest();
    try (var connection = source("privacy_operator", "test-operator").getConnection();
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
      connection.setAutoCommit(false);
      try (var statement =
          connection.prepareStatement(
              "INSERT INTO privacy_retention_hold_fact(data_class,record_id,action,actor_identity_id,reason_reference) VALUES ('MODERATION_EVIDENCE',?,'HOLD',?,'hold-after-dryrun')")) {
        statement.setObject(1, id);
        statement.setObject(2, admin);
        statement.executeUpdate();
      }
      var future = executor.submit(() -> apply(id, DataClass.COMMON_MESSAGES, digest));
      long deadline = System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos();
      boolean waiting = false;
      while (System.nanoTime() < deadline) {
        waiting =
            Boolean.TRUE.equals(
                owner.queryForObject(
                    "SELECT EXISTS(SELECT FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE '%pg_advisory_xact_lock(724627)%')",
                    Boolean.class));
        if (waiting) break;
        Thread.sleep(20);
      }
      assertThat(waiting).as("actual retention lock wait").isTrue();
      connection.commit();
      assertThatThrownBy(() -> future.get(15, java.util.concurrent.TimeUnit.SECONDS))
          .isInstanceOf(java.util.concurrent.ExecutionException.class)
          .hasCauseInstanceOf(br.com.betufu.platform.api.ApiProblemException.class);
    }
    assertThat(state(id)).isEqualTo("HELD");
    assertThat(service.currentRegister(admin)).isEmpty();
  }

  @Test
  void flywayOwnerRemainsNonSuperuserWithoutCreateRolePrivilege() {
    owner.execute(
        "CREATE ROLE privacy_migration LOGIN NOCREATEROLE NOSUPERUSER PASSWORD 'isolated-migration'");
    owner.execute("CREATE DATABASE privacy_migration_db OWNER privacy_migration");
    owner.execute("ALTER DATABASE privacy_migration_db SET betufu.runtime_role='privacy_runtime'");
    var ds =
        new DriverManagerDataSource(
            PG.getJdbcUrl().replace("/" + PG.getDatabaseName(), "/privacy_migration_db"),
            "privacy_migration",
            "isolated-migration");
    JdbcTemplate migration = new JdbcTemplate(ds);
    migration.execute("GRANT USAGE ON SCHEMA public TO privacy_runtime");
    Flyway.configure().dataSource(ds).load().migrate();
    assertThat(
            migration.queryForObject(
                "SELECT rolsuper OR rolcreaterole FROM pg_roles WHERE rolname=current_user",
                Boolean.class))
        .isFalse();
    assertThat(
            migration.queryForObject(
                "SELECT pg_has_role(current_user,'betufu_privacy_retention','MEMBER')",
                Boolean.class))
        .isFalse();
    assertThat(
            migration.queryForObject(
                "SELECT count(*) FROM privacy_suppression_fact", Integer.class))
        .isZero();
  }
}
