package br.com.betufu.platform.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.platform.session.SelectiveTouchSessionRepository;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
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
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.docker.compose.enabled=false",
      "spring.session.data.redis.configure-action=none",
      "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
      "BETUFU_OIDC_CLIENT_ID=pause-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused",
      "betufu.web.public-origin=http://localhost:4200"
    })
@ActiveProfiles("integrated")
@Import(EmergencyPauseIT.OidcConfiguration.class)
class EmergencyPauseIT {
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
  @Autowired EmergencyPauseService service;
  final ObjectMapper json = new ObjectMapper();
  UUID admin, owner, global;
  Session administrator;

  @BeforeEach
  void data() {
    jdbc.execute("TRUNCATE local_identity,sports_competition,chat_channel,odds_market CASCADE");
    admin = identity("admin");
    owner = identity("owner");
    jdbc.update(
        "INSERT INTO operational_role_grant(identity_id,role_name,status) VALUES (?,'ADMINISTRADOR_GERAL','VIGENTE')",
        admin);
    global =
        jdbc.queryForObject(
            "INSERT INTO chat_channel(kind) VALUES ('GLOBAL') RETURNING channel_id", UUID.class);
    administrator = session("admin");
  }

  @Test
  void allFourTargetsPauseResumeWithImmutableHistoryAndExactRetry() throws Exception {
    UUID event = event(), market = market(event);
    var targets = Map.of("ACCOUNT", owner, "EVENT", event, "MARKET", market, "CHANNEL", global);
    for (var target : targets.entrySet()) {
      String body = body("PAUSE", target.getKey(), target.getValue(), UUID.randomUUID(), null);
      var response = call(administrator, "POST", "", body, true);
      assertThat(response.statusCode()).isEqualTo(201);
      assertThat(call(administrator, "POST", "", body, true).body()).isEqualTo(response.body());
      UUID pause = UUID.fromString(json.readTree(response.body()).get("factId").asString());
      var resumed =
          call(
              administrator,
              "POST",
              "",
              body("RESUME", target.getKey(), target.getValue(), UUID.randomUUID(), pause),
              true);
      assertThat(resumed.statusCode()).isEqualTo(201);
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM emergency_pause_fact", Integer.class))
        .isEqualTo(8);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM emergency_pause_target WHERE paused", Integer.class))
        .isZero();
    for (String mutation :
        List.of(
            "DELETE FROM emergency_pause_fact", "UPDATE emergency_pause_fact SET reason=reason"))
      assertThatThrownBy(() -> jdbc.execute(mutation))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void onlyGeneralAdministratorCanListOrMutateAndRevocationInvalidatesReplay() throws Exception {
    UUID mod = identity("moderator");
    jdbc.update(
        "INSERT INTO operational_role_grant(identity_id,role_name,scope_ref,status) VALUES (?,'MODERADOR',?,'VIGENTE')",
        mod,
        UUID.randomUUID());
    String body = body("PAUSE", "ACCOUNT", owner, UUID.randomUUID(), null);
    for (Session actor : List.of(session("moderator"), session("owner"))) {
      assertThat(call(actor, "GET", "", null, true).statusCode()).isEqualTo(403);
      assertThat(call(actor, "POST", "", body, true).statusCode()).isEqualTo(403);
    }
    assertThat(call(administrator, "POST", "", body, true).statusCode()).isEqualTo(201);
    jdbc.update(
        "UPDATE operational_role_grant SET status='REVOGADO',revoked_at=CURRENT_TIMESTAMP WHERE identity_id=?",
        admin);
    assertThat(call(administrator, "POST", "", body, true).statusCode()).isEqualTo(403);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM emergency_pause_fact", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void missingWrongNamespaceAndDraftTargetsAreRejected() throws Exception {
    for (String kind : List.of("ACCOUNT", "EVENT", "MARKET", "CHANNEL"))
      assertThat(
              call(
                      administrator,
                      "POST",
                      "",
                      body("PAUSE", kind, UUID.randomUUID(), UUID.randomUUID(), null),
                      true)
                  .statusCode())
          .isEqualTo(404);
    assertThat(
            call(
                    administrator,
                    "POST",
                    "",
                    body("PAUSE", "EVENT", owner, UUID.randomUUID(), null),
                    true)
                .statusCode())
        .isEqualTo(404);
    UUID draft = event(), draftMarket = market(draft);
    UUID channel =
        jdbc.queryForObject(
            "INSERT INTO chat_channel(kind,event_id) VALUES ('EVENT',?) RETURNING channel_id",
            UUID.class,
            draft);
    jdbc.update("UPDATE sports_event SET status='RASCUNHO' WHERE event_id=?", draft);
    for (var target : Map.of("EVENT", draft, "MARKET", draftMarket, "CHANNEL", channel).entrySet())
      assertThat(
              call(
                      administrator,
                      "POST",
                      "",
                      body("PAUSE", target.getKey(), target.getValue(), UUID.randomUUID(), null),
                      true)
                  .statusCode())
          .isEqualTo(404);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM emergency_pause_fact", Integer.class))
        .isZero();
  }

  @Test
  void staleResumeAndChangedCommandCannotLiftANewerPause() throws Exception {
    UUID key = UUID.randomUUID();
    var first = call(administrator, "POST", "", body("PAUSE", "ACCOUNT", owner, key, null), true);
    assertThat(first.statusCode()).isEqualTo(201);
    UUID pause = UUID.fromString(json.readTree(first.body()).get("factId").asString());
    assertThat(
            call(administrator, "POST", "", body("PAUSE", "CHANNEL", global, key, null), true)
                .statusCode())
        .isEqualTo(409);
    assertThat(
            call(
                    administrator,
                    "POST",
                    "",
                    body("RESUME", "ACCOUNT", owner, UUID.randomUUID(), pause),
                    true)
                .statusCode())
        .isEqualTo(201);
    assertThat(
            call(
                    administrator,
                    "POST",
                    "",
                    body("PAUSE", "ACCOUNT", owner, UUID.randomUUID(), null),
                    true)
                .statusCode())
        .isEqualTo(201);
    assertThat(
            call(
                    administrator,
                    "POST",
                    "",
                    body("RESUME", "ACCOUNT", owner, UUID.randomUUID(), pause),
                    true)
                .statusCode())
        .isEqualTo(409);
    assertThat(
            jdbc.queryForObject(
                "SELECT paused FROM emergency_pause_target WHERE target_kind='ACCOUNT' AND target_id=?",
                Boolean.class,
                owner))
        .isTrue();
  }

  @Test
  void anonymousCannotManageOrPredictAndOnlyAvailabilityGetCrossesTheBoundary() throws Exception {
    assertThat(call(null, "GET", "", null, true).statusCode()).isEqualTo(401);
    assertThat(
            call(null, "POST", "", body("PAUSE", "ACCOUNT", owner, UUID.randomUUID(), null), true)
                .statusCode())
        .isEqualTo(401);
    assertThat(request(null, "POST", "/api/v1/predictions", "{}", true).statusCode())
        .isEqualTo(401);
    assertThat(
            request(
                    null,
                    "GET",
                    "/api/v1/predictions/markets/" + UUID.randomUUID() + "/availability",
                    null,
                    true)
                .statusCode())
        .isEqualTo(404);
    assertThat(
            request(
                    null,
                    "POST",
                    "/api/v1/predictions/markets/" + UUID.randomUUID() + "/availability",
                    "{}",
                    true)
                .statusCode())
        .isEqualTo(401);
    assertThat(
            call(
                    administrator,
                    "POST",
                    "",
                    body("PAUSE", "ACCOUNT", owner, UUID.randomUUID(), null),
                    false)
                .statusCode())
        .isEqualTo(403);
  }

  @Test
  void globalAdministratorReceivesTheNavigationCapabilityButModeratorDoesNot() throws Exception {
    assertThat(request(administrator, "GET", "/api/v1/operations/capabilities", null, true).body())
        .contains("MANAGE_EMERGENCY_PAUSES");
    assertThat(
            request(session("owner"), "GET", "/api/v1/operations/capabilities", null, true).body())
        .doesNotContain("MANAGE_EMERGENCY_PAUSES");
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

  UUID event() {
    return new org.springframework.transaction.support.TransactionTemplate(transactions)
        .execute(
            tx -> {
              UUID competition =
                  jdbc.queryForObject(
                      "INSERT INTO sports_competition(name,slug) VALUES ('Pause',?) RETURNING competition_id",
                      UUID.class,
                      "pause-" + UUID.randomUUID());
              UUID edition =
                  jdbc.queryForObject(
                      "INSERT INTO sports_edition(competition_id,name,slug) VALUES (?,'Edition','edition') RETURNING edition_id",
                      UUID.class,
                      competition);
              UUID id =
                  jdbc.queryForObject(
                      "INSERT INTO sports_event(competition_id,edition_id,title,event_format,scheduled_at,zone_id,venue_state,source_reference,status) VALUES (?,?,'Pause','HEAD_TO_HEAD',now(),'America/Sao_Paulo','LOCAL_A_DEFINIR','https://fixture.invalid','PUBLICADO') RETURNING event_id",
                      UUID.class,
                      competition,
                      edition);
              UUID publication =
                  jdbc.queryForObject(
                      "INSERT INTO sports_event_publication(event_id,competition_id,edition_id,version_no,competition_name,edition_name,title,event_format,scheduled_at,zone_id,venue_state,source_reference,published_by_identity_id,base_lock_version) VALUES (?,?,?,1,'Pause','Edition','Pause','HEAD_TO_HEAD',now(),'America/Sao_Paulo','LOCAL_A_DEFINIR','https://fixture.invalid',?,0) RETURNING publication_id",
                      UUID.class,
                      id,
                      competition,
                      edition,
                      admin);
              jdbc.update(
                  "INSERT INTO sports_event_publication_slot(publication_id,slot_no,display_label) VALUES (?,1,'Home'),(?,2,'Away')",
                  publication,
                  publication);
              return id;
            });
  }

  UUID market(UUID event) {
    return new org.springframework.transaction.support.TransactionTemplate(transactions)
        .execute(
            tx -> {
              UUID id =
                  jdbc.queryForObject(
                      "INSERT INTO odds_market(event_id,market_type) VALUES (?,'MATCH_WINNER') RETURNING market_id",
                      UUID.class,
                      event);
              UUID version =
                  jdbc.queryForObject(
                      "INSERT INTO odds_market_version(market_id,version_no,state,close_deadline) VALUES (?,1,'OPEN',now()+interval '4 hours') RETURNING market_version_id",
                      UUID.class,
                      id);
              jdbc.update(
                  "UPDATE odds_market SET current_version_id=? WHERE market_id=?", version, id);
              UUID calculation =
                  jdbc.queryForObject(
                      "INSERT INTO odds_calculation(market_version_id,algorithm_version,configuration_digest,input_snapshot,fair_probability) VALUES (?,'fixture','fixture','{\"publishedOdds\":[2.0,2.0],\"confidenceBand\":\"HIGH\",\"sampleSize\":12}',0.5) RETURNING calculation_id",
                      UUID.class,
                      version);
              UUID publication =
                  jdbc.queryForObject(
                      "INSERT INTO odds_publication(calculation_id,margin,rounding_mode,published_odds) VALUES (?,0.05,'HALF_UP',2.0) RETURNING publication_id",
                      UUID.class,
                      calculation);
              jdbc.update(
                  "INSERT INTO odds_publication_selection(publication_id,selection_no,selection_key,published_odds) VALUES (?,1,'HOME',2.0),(?,2,'AWAY',2.0)",
                  publication,
                  publication);
              return id;
            });
  }

  @Test
  void runtimeFactsAreAppendOnlyAndProjectionCanBeRecovered() throws Exception {
    var response =
        call(
            administrator,
            "POST",
            "",
            body("PAUSE", "ACCOUNT", owner, UUID.randomUUID(), null),
            true);
    assertThat(response.statusCode()).isEqualTo(201);
    UUID pause = UUID.fromString(json.readTree(response.body()).get("factId").asString());
    jdbc.execute("DELETE FROM emergency_pause_target");
    assertThat(call(administrator, "GET", "", null, true).body()).contains(pause.toString());
    assertThat(
            call(
                    administrator,
                    "POST",
                    "",
                    body("RESUME", "ACCOUNT", owner, UUID.randomUUID(), pause),
                    true)
                .statusCode())
        .isEqualTo(201);
    jdbc.execute("CREATE ROLE emergency_pause_runtime LOGIN PASSWORD 'pause-runtime-test-only'");
    jdbc.execute("GRANT USAGE ON SCHEMA public TO emergency_pause_runtime");
    String migration;
    try (var resource = getClass().getResourceAsStream("/db/migration/V44__emergency_pauses.sql")) {
      migration = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
    }
    try (var connection = POSTGRES.createConnection("")) {
      try (var statement = connection.createStatement()) {
        statement.execute("SET betufu.runtime_role='emergency_pause_runtime'");
        statement.execute(migration.substring(migration.lastIndexOf("DO $$")));
      }
    }
    var runtime =
        new JdbcTemplate(
            new org.springframework.jdbc.datasource.DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), "emergency_pause_runtime", "pause-runtime-test-only"));
    assertThat(
            runtime.queryForObject(
                "SELECT rolsuper FROM pg_roles WHERE rolname=current_user", Boolean.class))
        .isFalse();
    runtime.update(
        "INSERT INTO emergency_pause_fact(target_kind,target_id,actor_identity_id,action,reason,client_command_id,recorded_at) VALUES ('ACCOUNT',?,?,'PAUSE','runtime insertion',?,CURRENT_TIMESTAMP)",
        owner,
        admin,
        UUID.randomUUID());
    for (String mutation :
        List.of(
            "DELETE FROM emergency_pause_fact",
            "UPDATE emergency_pause_fact SET reason=reason",
            "TRUNCATE emergency_pause_fact"))
      assertThatThrownBy(() -> runtime.execute(mutation))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  @Test
  void rollbackPreservesHistoryAndNoPauseBecomesEffective() {
    new org.springframework.transaction.support.TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              service.command(
                  "https://idp.invalid",
                  "admin",
                  administrator.getId(),
                  new EmergencyPausePort.Command(
                      UUID.randomUUID(),
                      EmergencyPausePort.Kind.ACCOUNT,
                      owner,
                      EmergencyPausePort.Action.PAUSE,
                      "rollback proof",
                      null));
              tx.setRollbackOnly();
            });
    assertThat(jdbc.queryForObject("SELECT count(*) FROM emergency_pause_fact", Integer.class))
        .isZero();
    assertThat(service.state(EmergencyPausePort.Kind.ACCOUNT, owner).paused()).isFalse();
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

  String body(String action, String kind, UUID target, UUID key, UUID resumed) throws Exception {
    var fields =
        new HashMap<String, Object>(
            Map.of(
                "action",
                action,
                "kind",
                kind,
                "targetId",
                target,
                "clientCommandId",
                key,
                "reason",
                "operational incident"));
    if (resumed != null) fields.put("resumedPauseId", resumed);
    return json.writeValueAsString(fields);
  }

  HttpResponse<String> call(Session actor, String method, String path, String body, boolean csrf)
      throws Exception {
    return request(actor, method, "/api/v1/operations/emergency-pauses" + path, body, csrf);
  }

  HttpResponse<String> request(Session actor, String method, String path, String body, boolean csrf)
      throws Exception {
    String cookie = "XSRF-TOKEN=pause-fixture";
    if (actor != null)
      cookie +=
          "; SESSION="
              + Base64.getEncoder().encodeToString(actor.getId().getBytes(StandardCharsets.UTF_8));
    var builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Accept", "application/json")
            .header("Origin", "http://localhost:4200")
            .header("Cookie", cookie)
            .header("Content-Type", "application/json");
    if (csrf) builder.header("X-XSRF-TOKEN", "pause-fixture");
    builder.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
    try (var client = HttpClient.newHttpClient()) {
      return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class OidcConfiguration {
    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      return new InMemoryClientRegistrationRepository(
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("pause-it")
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
