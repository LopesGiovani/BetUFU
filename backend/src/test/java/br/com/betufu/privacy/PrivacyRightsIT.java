package br.com.betufu.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.platform.session.SelectiveTouchSessionRepository;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
class PrivacyRightsIT {
  private static final String RUNTIME_USER = "betufu_privacy_runtime";
  private static final String RUNTIME_PASSWORD = "privacy-test-only";
  private static final DockerImageName POSTGRES_IMAGE =
      DockerImageName.parse("docker.io/library/postgres:18.4-trixie")
          .asCompatibleSubstituteFor("postgres");

  @Container
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(POSTGRES_IMAGE)
          .withDatabaseName("betufu_privacy_test")
          .withUsername("betufu_privacy_owner")
          .withPassword("privacy-owner-test-only");

  private static JdbcTemplate owner;
  private JdbcTemplate runtime;
  private UUID identity;

  @BeforeAll
  static void configureRuntimeRole() {
    DriverManagerDataSource source = source(POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
    owner = new JdbcTemplate(source);
    owner.execute("CREATE ROLE " + RUNTIME_USER + " LOGIN PASSWORD '" + RUNTIME_PASSWORD + "'");
    owner.execute(
        "ALTER DATABASE "
            + POSTGRESQL.getDatabaseName()
            + " SET betufu.runtime_role TO '"
            + RUNTIME_USER
            + "'");
  }

  @BeforeEach
  void migrateFreshSchema() {
    owner.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public");
    owner.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC");
    owner.execute("GRANT USAGE ON SCHEMA public TO " + RUNTIME_USER);
    owner.execute(
        "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO "
            + RUNTIME_USER);
    owner.execute(
        "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO "
            + RUNTIME_USER);
    Flyway.configure()
        .dataSource(source(POSTGRESQL.getUsername(), POSTGRESQL.getPassword()))
        .load()
        .migrate();
    runtime = new JdbcTemplate(source(RUNTIME_USER, RUNTIME_PASSWORD));
    identity =
        owner.queryForObject(
            "INSERT INTO public.local_identity (issuer, subject) VALUES (?, ?) RETURNING identity_id",
            UUID.class,
            "https://example.invalid/privacy",
            UUID.randomUUID().toString());
  }

  @Test
  void rightsFactsAreAppendOnlyAndProjectionCanBeRebuilt() {
    UUID caseId = insertAccountCase();
    UUID eventId =
        runtime.queryForObject(
            "INSERT INTO public.privacy_rights_case_event(case_id,event_kind,actor_identity_id) VALUES (?, 'REQUESTED', ?) RETURNING event_id",
            UUID.class,
            caseId,
            identity);
    runtime.update(
        "INSERT INTO public.privacy_rights_case_state(case_id,state,latest_event_id) VALUES (?, 'PENDING', ?)",
        caseId,
        eventId);
    runtime.update(
        "UPDATE public.privacy_rights_case_state SET state='RESPONDED' WHERE case_id=?", caseId);
    assertThat(
            runtime.queryForObject(
                "SELECT state FROM public.privacy_rights_case_state WHERE case_id=?",
                String.class,
                caseId))
        .isEqualTo("RESPONDED");
    assertSqlState(
        "42501",
        () -> runtime.update("UPDATE public.privacy_rights_case_fact SET request_kind='ACCESS'"));
    assertSqlState("42501", () -> runtime.update("DELETE FROM public.privacy_rights_case_event"));
    assertSqlState("42501", () -> runtime.execute("TRUNCATE public.privacy_rights_export_fact"));
  }

  @Test
  void athleteWithoutAccountRequiresProofAndFactsDoNotExposePayloads() {
    UUID athlete = UUID.randomUUID();
    UUID caseId =
        runtime.queryForObject(
            "INSERT INTO public.privacy_rights_case_fact(subject_kind,subject_ref,request_kind,proof_reference,verified_channel) VALUES ('ATHLETE_WITHOUT_ACCOUNT', ?, 'ACCESS', 'restricted-proof-ref', 'VERIFIED_OPERATOR_CHANNEL') RETURNING case_id",
            UUID.class,
            athlete);
    assertThat(caseId).isNotNull();
    assertThat(
            runtime.queryForObject(
                "SELECT proof_reference FROM public.privacy_rights_case_fact WHERE case_id=?",
                String.class,
                caseId))
        .isEqualTo("restricted-proof-ref");
    assertSqlState(
        "23514",
        () ->
            runtime.update(
                "INSERT INTO public.privacy_rights_case_fact(subject_kind,request_kind,verified_channel) VALUES ('ATHLETE_WITHOUT_ACCOUNT','ACCESS','VERIFIED_OPERATOR_CHANNEL')"));
  }

  @Test
  void runtimeCannotRewriteHistoricalFacts() {
    insertAccountCase();
    for (String table :
        new String[] {
          "privacy_rights_case_fact",
          "privacy_rights_case_event",
          "privacy_rights_export_fact",
          "privacy_rights_disposition_fact"
        }) {
      assertThat(
              runtime.queryForObject(
                  "SELECT has_table_privilege(current_user, ?, 'UPDATE')",
                  Boolean.class,
                  "public." + table))
          .isFalse();
      assertThat(
              runtime.queryForObject(
                  "SELECT has_table_privilege(current_user, ?, 'DELETE')",
                  Boolean.class,
                  "public." + table))
          .isFalse();
    }
  }

  @Test
  void historicalTriggersRejectOwnerMutationsEvenOnEmptyTables() {
    for (String table :
        new String[] {
          "privacy_rights_case_fact",
          "privacy_rights_case_event",
          "privacy_rights_export_fact",
          "privacy_rights_disposition_fact"
        }) {
      assertThatThrownBy(() -> owner.execute("DELETE FROM public." + table))
          .isInstanceOf(DataAccessException.class)
          .hasMessageContaining("history is immutable");
      String column =
          table.equals("privacy_rights_case_event")
              ? "event_id"
              : table.equals("privacy_rights_export_fact")
                  ? "export_id"
                  : table.equals("privacy_rights_disposition_fact") ? "disposition_id" : "case_id";
      assertThatThrownBy(
              () -> owner.execute("UPDATE public." + table + " SET " + column + "=" + column))
          .isInstanceOf(DataAccessException.class)
          .hasMessageContaining("history is immutable");
    }
  }

  private UUID insertAccountCase() {
    return runtime.queryForObject(
        "INSERT INTO public.privacy_rights_case_fact(subject_kind,owner_identity_id,request_kind,verified_channel) VALUES ('ACCOUNT', ?, 'ACCESS', 'AUTHENTICATED_SESSION') RETURNING case_id",
        UUID.class,
        identity);
  }

  private static DriverManagerDataSource source(String user, String password) {
    return new DriverManagerDataSource(POSTGRESQL.getJdbcUrl(), user, password);
  }

  private static void assertSqlState(String state, Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOf(DataAccessException.class)
        .satisfies(
            error -> {
              Throwable cause = error;
              while (cause.getCause() != null) cause = cause.getCause();
              assertThat(cause).isInstanceOf(SQLException.class);
              assertThat(((SQLException) cause).getSQLState()).isEqualTo(state);
            });
  }
}

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.docker.compose.enabled=false",
      "spring.session.data.redis.configure-action=none",
      "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
      "BETUFU_OIDC_CLIENT_ID=privacy-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused",
      "betufu.web.public-origin=http://localhost:4200"
    })
@ActiveProfiles("integrated")
@Import(PrivacyRightsHttpIT.OidcConfiguration.class)
class PrivacyRightsHttpIT {
  @ServiceConnection(name = "postgres")
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
              DockerImageName.parse(
                      "docker.io/library/postgres:18.4-trixie@sha256:a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("betufu_app")
          .withUsername("betufu_app")
          .withPassword("pause-test-only")
          .withInitScript("postgres-audit-runtime-role.sql");

  static final GenericContainer<?> REDIS =
      new GenericContainer<>(
              DockerImageName.parse(
                  "docker.io/library/redis:8.10.0-trixie@sha256:344e3945a0b431c8ff1eecd58c5573538126bd756f02fc7e218ddf1fc2546366"))
          .withExposedPorts(6379);

  static {
    POSTGRES.start();
    REDIS.start();
  }

  @DynamicPropertySource
  static void redis(DynamicPropertyRegistry r) {
    r.add("BETUFU_REDIS_HOST", REDIS::getHost);
    r.add("BETUFU_REDIS_PORT", () -> REDIS.getMappedPort(6379));
    r.add("BETUFU_REDIS_PASSWORD", () -> "");
  }

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired SessionRepository<?> sessions;
  @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

  final ObjectMapper json = new ObjectMapper();
  UUID admin, owner, other;
  Session administrator;

  @BeforeEach
  void preparePrivacyCases() {
    jdbc.execute("TRUNCATE local_identity,sports_athlete CASCADE");
    admin = identity("admin");
    owner = identity("owner");
    other = identity("other");
    jdbc.update(
        "INSERT INTO operational_role_grant(identity_id,role_name,status) VALUES (?,'ADMINISTRADOR_GERAL','VIGENTE')",
        admin);
    administrator = session("admin");
  }

  @Test
  void ownersIncludingPausedAccountsRequestAndViewWhileThirdPartiesAreDenied() throws Exception {
    jdbc.update(
        "INSERT INTO emergency_pause_fact(target_kind,target_id,actor_identity_id,action,reason,client_command_id,recorded_at) VALUES ('ACCOUNT',?,?,'PAUSE','fixture',?,now())",
        owner,
        admin,
        UUID.randomUUID());
    jdbc.update(
        "INSERT INTO emergency_pause_target(target_kind,target_id,paused,current_pause_id) SELECT target_kind,target_id,true,pause_fact_id FROM emergency_pause_fact");
    Session account = session("owner");
    var created = call(account, "POST", "", accountBody("ACCESS"), true);
    assertThat(created.statusCode()).isEqualTo(201);
    String caseId = json.readTree(created.body()).get("caseId").asString();
    assertThat(
            jdbc.queryForObject(
                "SELECT owner_identity_id FROM privacy_rights_case_fact WHERE case_id=?",
                UUID.class,
                UUID.fromString(caseId)))
        .isEqualTo(owner);
    var view = call(account, "GET", "/" + caseId, null, true);
    assertThat(view.statusCode()).isEqualTo(200);
    assertThat(view.headers().firstValue("Cache-Control")).contains("private, no-store");
    assertThat(view.body())
        .contains("REQUESTED", "PENDING")
        .doesNotContain("proofReference", "ownerIdentityId", "subjectRef", "issuer");
    assertThat(call(session("other"), "GET", "/" + caseId, null, true).statusCode()).isEqualTo(403);
    assertThat(call(administrator, "GET", "/" + caseId, null, true).statusCode()).isEqualTo(200);
    assertThat(call(null, "GET", "/" + caseId, null, true).statusCode()).isEqualTo(401);
    sessions.deleteById(account.getId());
    assertThat(call(account, "GET", "/" + caseId, null, true).statusCode()).isEqualTo(401);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM privacy_rights_disposition_fact", Integer.class))
        .isZero();
  }

  @Test
  void authenticatedMutationsRequireCsrfAndDoNotAcceptForgedOwnerOrUnknownRights()
      throws Exception {
    Session account = session("owner");
    assertThat(call(null, "POST", "", accountBody("ACCESS"), true).statusCode()).isEqualTo(401);
    assertThat(call(account, "POST", "", accountBody("ACCESS"), false).statusCode()).isEqualTo(403);
    assertThat(call(account, "POST", "", accountBody("UNKNOWN"), true).statusCode()).isEqualTo(400);
    assertThat(
            call(
                    account,
                    "POST",
                    "",
                    accountBody("ACCESS").replace("null", "\"" + other + "\""),
                    true)
                .statusCode())
        .isEqualTo(400);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM privacy_rights_case_fact", Integer.class))
        .isZero();
  }

  @Test
  void athleteWithoutAccountRequiresGeneralAdministratorExistingAthleteAndRestrictedProof()
      throws Exception {
    UUID athlete =
        jdbc.queryForObject(
            "INSERT INTO sports_athlete(name,slug) VALUES ('Privacy Athlete','privacy-athlete') RETURNING athlete_id",
            UUID.class);
    String body = athleteBody(athlete, "restricted-proof-ref");
    assertThat(call(session("owner"), "POST", "", body, true).statusCode()).isEqualTo(403);
    assertThat(call(null, "POST", "", body, true).statusCode()).isEqualTo(401);
    assertThat(
            call(
                    administrator,
                    "POST",
                    "",
                    athleteBody(UUID.randomUUID(), "restricted-proof-ref"),
                    true)
                .statusCode())
        .isEqualTo(403);
    assertThat(call(administrator, "POST", "", athleteBody(athlete, ""), true).statusCode())
        .isEqualTo(400);
    var created = call(administrator, "POST", "", body, true);
    assertThat(created.statusCode()).isEqualTo(201);
    String caseId = json.readTree(created.body()).get("caseId").asString();
    var view = call(administrator, "GET", "/" + caseId, null, true);
    assertThat(view.body()).doesNotContain("restricted-proof-ref", athlete.toString());
    assertThat(call(session("other"), "GET", "/" + caseId, null, true).statusCode()).isEqualTo(403);
    jdbc.update("UPDATE operational_role_grant SET status='REVOGADO' WHERE identity_id=?", admin);
    assertThat(call(administrator, "POST", "", body, true).statusCode()).isEqualTo(403);
  }

  @Test
  void onlyCurrentGeneralAdministratorAnalyzesAndAccessCannotBeCompletedWithoutExport()
      throws Exception {
    Session account = session("owner");
    var created = call(account, "POST", "", accountBody("ACCESS"), true);
    assertThat(created.statusCode()).isEqualTo(201);
    String caseId = json.readTree(created.body()).get("caseId").asString();
    String responses = "/" + caseId + "/responses";
    assertThat(call(account, "POST", responses, responseBody("APPROVE"), true).statusCode())
        .isEqualTo(403);
    assertThat(call(administrator, "POST", responses, responseBody("APPROVE"), true).statusCode())
        .isEqualTo(409);
    assertThat(call(administrator, "POST", responses, responseBody("ANALYZE"), true).statusCode())
        .isEqualTo(200);
    jdbc.update("DELETE FROM privacy_rights_case_state WHERE case_id=?", UUID.fromString(caseId));
    assertThat(call(account, "GET", "/" + caseId, null, true).body()).contains("UNDER_REVIEW");
    assertThat(call(administrator, "POST", responses, responseBody("REJECT"), false).statusCode())
        .isEqualTo(403);
    assertThat(call(administrator, "POST", responses, responseBody("REJECT"), true).statusCode())
        .isEqualTo(200);
    assertThat(call(administrator, "POST", responses, responseBody("REJECT"), true).statusCode())
        .isEqualTo(409);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM privacy_rights_case_event WHERE case_id=?",
                Integer.class,
                UUID.fromString(caseId)))
        .isEqualTo(3);
    assertThat(call(account, "GET", "/" + caseId, null, true).body())
        .contains("REJECTED", "ANALYZED", "Private operational response");
  }

  @Test
  void noticeResponseIsAuditedAndRevokedAdministratorCannotRespond() throws Exception {
    var created = call(session("owner"), "POST", "", accountBody("NOTICE"), true);
    String caseId = json.readTree(created.body()).get("caseId").asString();
    jdbc.update("UPDATE operational_role_grant SET status='REVOGADO' WHERE identity_id=?", admin);
    assertThat(
            call(administrator, "POST", "/" + caseId + "/responses", responseBody("APPROVE"), true)
                .statusCode())
        .isEqualTo(403);
    jdbc.update("UPDATE operational_role_grant SET status='VIGENTE' WHERE identity_id=?", admin);
    var response =
        call(administrator, "POST", "/" + caseId + "/responses", responseBody("APPROVE"), true);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).contains("RESPONDED", "RESPONSE_SENT");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM privacy_rights_case_event WHERE event_kind='RESPONSE_SENT' AND actor_identity_id=?",
                Integer.class,
                admin))
        .isEqualTo(1);
  }

  @Test
  void realLockWaitRechecksRevokedAuthorityAndDeletedSession() throws Exception {
    String caseId =
        json.readTree(call(session("owner"), "POST", "", accountBody("NOTICE"), true).body())
            .get("caseId")
            .asString();
    for (boolean revokeRole : new boolean[] {true, false}) {
      jdbc.update("UPDATE operational_role_grant SET status='VIGENTE' WHERE identity_id=?", admin);
      administrator = session("admin");
      Session actor = administrator;
      java.util.concurrent.CompletableFuture<HttpResponse<String>> response;
      try (var connection = POSTGRES.createConnection("")) {
        connection.setAutoCommit(false);
        try (var statement =
            connection.prepareStatement(
                "SELECT case_id FROM privacy_rights_case_fact WHERE case_id=? FOR UPDATE")) {
          statement.setObject(1, UUID.fromString(caseId));
          statement.executeQuery().close();
        }
        response =
            java.util.concurrent.CompletableFuture.supplyAsync(
                () -> {
                  try {
                    return call(
                        actor, "POST", "/" + caseId + "/responses", responseBody("APPROVE"), true);
                  } catch (Exception error) {
                    throw new java.util.concurrent.CompletionException(error);
                  }
                });
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos();
        boolean waiting = false;
        while (System.nanoTime() < deadline) {
          waiting =
              Boolean.TRUE.equals(
                  jdbc.queryForObject(
                      "SELECT EXISTS(SELECT FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE '%privacy_rights_case_fact%')",
                      Boolean.class));
          if (waiting) break;
          Thread.sleep(25);
        }
        assertThat(waiting).as("actual privacy row-lock wait").isTrue();
        if (revokeRole)
          jdbc.update(
              "UPDATE operational_role_grant SET status='REVOGADO' WHERE identity_id=?", admin);
        else sessions.deleteById(actor.getId());
        connection.commit();
      }
      assertThat(response.get(30, java.util.concurrent.TimeUnit.SECONDS).statusCode())
          .isEqualTo(revokeRole ? 403 : 401);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM privacy_rights_case_event WHERE event_kind='RESPONSE_SENT'",
                  Integer.class))
          .isZero();
    }
  }

  @Test
  void concurrentFinalDecisionsAppendOnlyOneTerminalEvent() throws Exception {
    String caseId =
        json.readTree(call(session("owner"), "POST", "", accountBody("NOTICE"), true).body())
            .get("caseId")
            .asString();
    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var start = new java.util.concurrent.CountDownLatch(1);
      java.util.concurrent.Callable<Integer> command =
          () -> {
            start.await();
            return call(
                    administrator,
                    "POST",
                    "/" + caseId + "/responses",
                    responseBody("APPROVE"),
                    true)
                .statusCode();
          };
      var first = executor.submit(command);
      var second = executor.submit(command);
      start.countDown();
      assertThat(
              List.of(
                  first.get(30, java.util.concurrent.TimeUnit.SECONDS),
                  second.get(30, java.util.concurrent.TimeUnit.SECONDS)))
          .containsExactlyInAnyOrder(200, 409);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM privacy_rights_case_event WHERE event_kind='RESPONSE_SENT'",
                Integer.class))
        .isEqualTo(1);
  }

  UUID identity(String subject) {
    UUID id =
        jdbc.queryForObject(
            "INSERT INTO local_identity(issuer,subject) VALUES ('https://idp.invalid',?) RETURNING identity_id",
            UUID.class,
            subject);
    jdbc.update("INSERT INTO betufu_profile(identity_id,nickname) VALUES (?,?)", id, subject);
    return id;
  }

  String responseBody(String decision) throws Exception {
    return json.writeValueAsString(
        Map.of("decision", decision, "responseText", "Private operational response"));
  }

  String accountBody(String kind) {
    return "{\"subjectKind\":\"ACCOUNT\",\"requestKind\":\""
        + kind
        + "\",\"verifiedChannel\":\"AUTHENTICATED_SESSION\",\"subjectRef\":null,\"proofReference\":null}";
  }

  String athleteBody(UUID athlete, String proof) throws Exception {
    return json.writeValueAsString(
        Map.of(
            "subjectKind",
            "ATHLETE_WITHOUT_ACCOUNT",
            "requestKind",
            "ACCESS",
            "verifiedChannel",
            "VERIFIED_OPERATOR_CHANNEL",
            "subjectRef",
            athlete,
            "proofReference",
            proof));
  }

  HttpResponse<String> call(Session actor, String method, String path, String body, boolean csrf)
      throws Exception {
    String cookie = "XSRF-TOKEN=privacy-fixture";
    if (actor != null)
      cookie +=
          "; SESSION="
              + Base64.getEncoder().encodeToString(actor.getId().getBytes(StandardCharsets.UTF_8));
    var builder =
        HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/privacy/requests" + path))
            .timeout(java.time.Duration.ofSeconds(30))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header("Origin", "http://localhost:4200")
            .header("Cookie", cookie);
    if (csrf) builder.header("X-XSRF-TOKEN", "privacy-fixture");
    builder.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
    try (var client = HttpClient.newHttpClient()) {
      return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  Session session(String subject) {
    Session session = sessions.createSession();
    Instant now = Instant.now();
    var principal =
        new DefaultOidcUser(
            List.of(),
            new OidcIdToken(
                "fixture",
                now,
                now.plusSeconds(3600),
                Map.of("iss", "https://idp.invalid", "sub", subject)));
    var context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(new OAuth2AuthenticationToken(principal, List.of(), "keycloak"));
    session.setAttribute("SPRING_SECURITY_CONTEXT", context);
    session.setAttribute("betufu.session.lastValidActivityAt.epochSecond", now.getEpochSecond());
    session.setAttribute("betufu.session.lastValidActivityAt.nano", now.getNano());
    session.setAttribute("betufu.session.authentication.amr.count", 0);
    session.setAttribute(
        SelectiveTouchSessionRepository.class.getName() + ".approvedActivity", now);
    ((SessionRepository) sessions).save(session);
    return session;
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class OidcConfiguration {
    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      return new InMemoryClientRegistrationRepository(
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("privacy-it")
              .clientSecret("unused")
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .scope("openid")
              .authorizationUri("https://idp.invalid/authorize")
              .tokenUri("https://idp.invalid/token")
              .jwkSetUri("https://idp.invalid/jwks")
              .issuerUri("https://idp.invalid")
              .userNameAttributeName("sub")
              .build());
    }
  }
}

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.docker.compose.enabled=false",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration,"
          + "org.springframework.boot.session.autoconfigure.SessionAutoConfiguration,"
          + "org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration",
      "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
      "BETUFU_OIDC_CLIENT_ID=privacy-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused"
    })
@ActiveProfiles("integrated")
@Testcontainers
@Import(PrivacyRightsHttpIT.OidcConfiguration.class)
class PrivacyRightsWithoutSessionsIT {
  @Container
  @ServiceConnection(name = "postgres")
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer(
              DockerImageName.parse(
                      "docker.io/library/postgres:18.4-trixie@sha256:a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636")
                  .asCompatibleSubstituteFor("postgres"))
          .withDatabaseName("betufu_app")
          .withUsername("betufu_app")
          .withPassword("privacy-no-session-test-only")
          .withInitScript("postgres-audit-runtime-role.sql");

  @Autowired org.springframework.web.context.WebApplicationContext context;
  @Autowired org.springframework.security.web.FilterChainProxy filters;
  @Autowired JdbcTemplate jdbc;

  @Test
  void missingSessionRepositoryAllowsContextStartupButNeverGrantsPrivacyAccess() throws Exception {
    assertThat(context.getBeansOfType(SessionRepository.class)).isEmpty();
    var mvc =
        org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
            .addFilters(filters)
            .build();
    mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/api/v1/privacy/requests")
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.oidcLogin()
                        .idToken(
                            token ->
                                token.claim("iss", "https://idp.invalid").claim("sub", "owner")))
                .with(
                    org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(
                    "{\"subjectKind\":\"ACCOUNT\",\"requestKind\":\"ACCESS\",\"verifiedChannel\":\"AUTHENTICATED_SESSION\",\"subjectRef\":null,\"proofReference\":null}"))
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                .isUnauthorized());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM privacy_rights_case_fact", Integer.class))
        .isZero();
  }
}
