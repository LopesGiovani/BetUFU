package br.com.betufu.sports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import br.com.betufu.platform.observability.CorrelationIdFilter;
import br.com.betufu.platform.security.BetufuRoleAuthorityContributor;
import br.com.betufu.platform.session.RedisContainerSupport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.docker.compose.enabled=false",
      "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
      "BETUFU_OIDC_CLIENT_ID=betufu-sports-event-result-concurrency-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused",
      "BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1",
      "BETUFU_MATRICULA_HMAC_KEY_V1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE="
    })
@ActiveProfiles("integrated")
@Testcontainers
@Import(SportsEventResultConcurrencyIT.TestOidcConfiguration.class)
class SportsEventResultConcurrencyIT {

  private static final String ISSUER = "https://idp.invalid";
  private static final DockerImageName POSTGRES_IMAGE =
      DockerImageName.parse(
              "docker.io/library/postgres:18.4-trixie@sha256:"
                  + "a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636")
          .asCompatibleSubstituteFor("postgres");

  @Container
  @ServiceConnection(name = "postgres")
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(POSTGRES_IMAGE)
          .withDatabaseName("betufu_app")
          .withUsername("betufu_app")
          .withPassword("betufu-test-only")
          .withInitScript("postgres-audit-runtime-role.sql");

  @Container static final GenericContainer<?> REDIS = RedisContainerSupport.REDIS;

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    RedisContainerSupport.registerProperties(registry);
  }

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private StringRedisTemplate redisTemplate;
  @Autowired private WebApplicationContext applicationContext;
  @Autowired private CorrelationIdFilter correlationIdFilter;

  @Autowired
  @Qualifier("springSecurityFilterChain")
  private FilterChainProxy springSecurityFilterChain;

  @Autowired private BetufuRoleAuthorityContributor authorityContributor;

  private MockMvc mockMvc;
  private UUID competitionId;
  private UUID editionId;
  private UUID sportId;
  private UUID categoryId;
  private UUID team1;
  private UUID team2;
  private UUID eventId;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute(
        """
                TRUNCATE TABLE public.sports_result_version_slot, public.sports_result_version,
                public.sports_event_publication_slot, public.sports_event_publication,
                public.sports_event_slot, public.sports_event,
                public.sports_roster_publication_member, public.sports_roster_publication,
                public.sports_roster_member, public.sports_roster, public.sports_athlete,
                public.sports_team, public.sports_organization, public.sports_phase,
                public.sports_category, public.sports_sport, public.sports_edition,
                public.sports_competition, public.audit_event, public.operational_role_grant,
                public.betufu_profile, public.ufu_enrollment, public.local_identity CASCADE
                """);
    try (var connection = redisTemplate.getConnectionFactory().getConnection()) {
      connection.serverCommands().flushDb();
    }
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .addFilters(correlationIdFilter, springSecurityFilterChain)
            .build();

    UUID adminId = createIdentity("admin-event-concurrency-sub", "event_concurrency");
    competitionId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_competition (competition_id, name, slug) VALUES (?, ?, ?)",
        competitionId,
        "Competição Concorrência",
        "competicao-concorrencia");
    grantRole(adminId, "ADMINISTRADOR_COMPETICAO", competitionId);

    editionId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_edition (edition_id, competition_id, name, slug) VALUES (?, ?, ?, ?)",
        editionId,
        competitionId,
        "Edição Concorrência",
        "edicao-concorrencia");

    sportId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_sport (sport_id, name, slug) VALUES (?, ?, ?)",
        sportId,
        "Futsal",
        "futsal");

    categoryId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_category (category_id, edition_id, name, slug) VALUES (?, ?, ?, ?)",
        categoryId,
        editionId,
        "Livre",
        "livre");

    UUID organizationId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_organization (organization_id, name, slug, status) VALUES (?, ?, ?, 'PUBLICADO')",
        organizationId,
        "Atlética Concorrência",
        "atletica-concorrencia");

    team1 = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_team (team_id, organization_id, edition_id, sport_id, category_id, name, slug, status) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, 'PUBLICADO')",
        team1,
        organizationId,
        editionId,
        sportId,
        categoryId,
        "Time Alpha",
        "time-alpha");

    team2 = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_team (team_id, organization_id, edition_id, sport_id, category_id, name, slug, status) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, 'PUBLICADO')",
        team2,
        organizationId,
        editionId,
        sportId,
        categoryId,
        "Time Beta",
        "time-beta");

    eventId = UUID.randomUUID();
    jdbcTemplate.update(
        """
                INSERT INTO public.sports_event (
                    event_id, competition_id, edition_id, title, event_format, status,
                    scheduled_at, zone_id, venue_state, source_reference, lock_version,
                    sport_id, category_id
                ) VALUES (?, ?, ?, 'Jogo Concorrência', 'HEAD_TO_HEAD', 'RASCUNHO',
                    CURRENT_TIMESTAMP(6), 'America/Sao_Paulo', 'LOCAL_A_DEFINIR', 'https://boletim.ufu.br/1', 0, ?, ?)
                """,
        eventId,
        competitionId,
        editionId,
        sportId,
        categoryId);

    jdbcTemplate.update(
        "INSERT INTO public.sports_event_slot (event_id, slot_no, display_label, team_id) VALUES (?, 1, 'Alpha', ?)",
        eventId,
        team1);
    jdbcTemplate.update(
        "INSERT INTO public.sports_event_slot (event_id, slot_no, display_label, team_id) VALUES (?, 2, 'Beta', ?)",
        eventId,
        team2);
  }

  @Test
  @DisplayName("Atualizações simultâneas do rascunho de evento serializam ou conflitam com 409")
  void concurrentEventDraftUpdatesSerializeOrConflictCleanly() throws Exception {
    int threads = 4;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger okCount = new AtomicInteger(0);
    AtomicInteger conflictCount = new AtomicInteger(0);

    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      final int index = i;
      futures.add(
          executor.submit(
              () -> {
                ready.countDown();
                try {
                  start.await();
                  int status =
                      mockMvc
                          .perform(
                              put("/api/v1/operations/sports/events/" + eventId)
                                  .with(oidcPrincipal("admin-event-concurrency-sub"))
                                  .with(csrf())
                                  .param("expectedLockVersion", "0")
                                  .contentType(MediaType.APPLICATION_JSON)
                                  .content(
                                      String.format(
                                          """
                                            {
                                              "title": "Jogo Concorrência %d",
                                              "format": "HEAD_TO_HEAD",
                                              "scheduledAt": "2026-09-15T18:00:00Z",
                                              "zoneId": "America/Sao_Paulo",
                                              "venueState": "LOCAL_A_DEFINIR",
                                              "sourceReference": "https://boletim.ufu.br/1",
                                              "sportId": "%s",
                                              "categoryId": "%s",
                                              "slots": [
                                                { "slotNo": 1, "displayLabel": "Alpha", "teamId": "%s" },
                                                { "slotNo": 2, "displayLabel": "Beta", "teamId": "%s" }
                                              ]
                                            }
                                            """,
                                          index, sportId, categoryId, team1, team2)))
                          .andReturn()
                          .getResponse()
                          .getStatus();
                  if (status == 200) {
                    okCount.incrementAndGet();
                  } else if (status == 409) {
                    conflictCount.incrementAndGet();
                  }
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              }));
    }

    ready.await(10, TimeUnit.SECONDS);
    start.countDown();

    for (Future<?> future : futures) {
      future.get(30, TimeUnit.SECONDS);
    }
    executor.shutdown();

    assertThat(okCount.get()).isEqualTo(1);
    assertThat(conflictCount.get()).isEqualTo(threads - 1);

    Long lockVersion =
        jdbcTemplate.queryForObject(
            "SELECT lock_version FROM public.sports_event WHERE event_id = ?", Long.class, eventId);
    assertThat(lockVersion).isEqualTo(1L);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.sports_event_lifecycle_history WHERE event_id = ?",
                Integer.class,
                eventId))
        .isEqualTo(0);
  }

  @Test
  @DisplayName(
      "Publicações simultâneas no mesmo evento produzem exatamente um snapshot e 409 para os concorrentes")
  void concurrentEventPublicationsSerializeCleanly() throws Exception {
    int threads = 4;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger okCount = new AtomicInteger(0);
    AtomicInteger conflictCount = new AtomicInteger(0);

    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      futures.add(
          executor.submit(
              () -> {
                ready.countDown();
                try {
                  start.await();
                  int status =
                      mockMvc
                          .perform(
                              post("/api/v1/operations/sports/events/" + eventId + "/publish")
                                  .with(oidcPrincipal("admin-event-concurrency-sub"))
                                  .with(csrf())
                                  .param("expectedLockVersion", "0"))
                          .andReturn()
                          .getResponse()
                          .getStatus();
                  if (status == 200) {
                    okCount.incrementAndGet();
                  } else if (status == 409) {
                    conflictCount.incrementAndGet();
                  }
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              }));
    }

    ready.await(10, TimeUnit.SECONDS);
    start.countDown();

    for (Future<?> future : futures) {
      future.get(30, TimeUnit.SECONDS);
    }
    executor.shutdown();

    assertThat(okCount.get()).isEqualTo(1);
    assertThat(conflictCount.get()).isEqualTo(threads - 1);

    Integer pubCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.sports_event_publication WHERE event_id = ?",
            Integer.class,
            eventId);
    assertThat(pubCount).isEqualTo(1);

    String status =
        jdbcTemplate.queryForObject(
            "SELECT status FROM public.sports_event WHERE event_id = ?", String.class, eventId);
    assertThat(status).isEqualTo("PUBLICADO");

    Map<String, Object> lifecycleRow =
        jdbcTemplate.queryForMap(
            """
                    SELECT previous_status, new_status, sequence_no, base_lock_version, lock_version
                    FROM public.sports_event_lifecycle_history
                    WHERE event_id = ?
                    """,
            eventId);
    assertThat(lifecycleRow.get("previous_status")).isEqualTo("RASCUNHO");
    assertThat(lifecycleRow.get("new_status")).isEqualTo("PUBLICADO");
    assertThat(((Number) lifecycleRow.get("sequence_no")).longValue()).isEqualTo(1L);
    assertThat(((Number) lifecycleRow.get("base_lock_version")).longValue()).isZero();
    assertThat(((Number) lifecycleRow.get("lock_version")).longValue()).isEqualTo(1L);
  }

  @Test
  @DisplayName(
      "Confirmações simultâneas de resultado produzem exatamente uma versão vencedora e 409 para os concorrentes")
  void concurrentResultConfirmationsSerializeCleanly() throws Exception {
    // Publica o evento primeiro (lockVersion vira 1)
    mockMvc
        .perform(
            post("/api/v1/operations/sports/events/" + eventId + "/publish")
                .with(oidcPrincipal("admin-event-concurrency-sub"))
                .with(csrf())
                .param("expectedLockVersion", "0"))
        .andReturn();

    int threads = 4;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger okCount = new AtomicInteger(0);
    AtomicInteger conflictCount = new AtomicInteger(0);

    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      final int score1 = 1 + i;
      futures.add(
          executor.submit(
              () -> {
                ready.countDown();
                try {
                  start.await();
                  int status =
                      mockMvc
                          .perform(
                              post("/api/v1/operations/sports/events/"
                                      + eventId
                                      + "/results/confirm")
                                  .with(oidcPrincipal("admin-event-concurrency-sub"))
                                  .with(csrf())
                                  .param("expectedLockVersion", "1")
                                  .contentType(MediaType.APPLICATION_JSON)
                                  .content(
                                      String.format(
                                          """
                                            {
                                              "sourceReference": "https://boletim.ufu.br/boletim-%d",
                                              "scores": [
                                                { "slotNo": 1, "score": %d },
                                                { "slotNo": 2, "score": 0 }
                                              ]
                                            }
                                            """,
                                          score1, score1)))
                          .andReturn()
                          .getResponse()
                          .getStatus();
                  if (status == 200) {
                    okCount.incrementAndGet();
                  } else if (status == 409) {
                    conflictCount.incrementAndGet();
                  }
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              }));
    }

    ready.await(10, TimeUnit.SECONDS);
    start.countDown();

    for (Future<?> future : futures) {
      future.get(30, TimeUnit.SECONDS);
    }
    executor.shutdown();

    assertThat(okCount.get()).isEqualTo(1);
    assertThat(conflictCount.get()).isEqualTo(threads - 1);

    Integer resultCount =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.sports_result_version WHERE event_id = ?",
            Integer.class,
            eventId);
    assertThat(resultCount).isEqualTo(1);

    UUID confirmedVersionId =
        jdbcTemplate.queryForObject(
            "SELECT confirmed_version_id FROM public.sports_event WHERE event_id = ?",
            UUID.class,
            eventId);
    assertThat(confirmedVersionId).isNotNull();
  }

  private UUID createIdentity(String subject, String nickname) {
    jdbcTemplate.update(
        "INSERT INTO public.local_identity (issuer, subject) VALUES (?, ?)", ISSUER, subject);
    UUID id =
        jdbcTemplate.queryForObject(
            "SELECT identity_id FROM public.local_identity WHERE issuer = ? AND subject = ?",
            UUID.class,
            ISSUER,
            subject);
    jdbcTemplate.update(
        "INSERT INTO public.betufu_profile (identity_id, nickname) VALUES (?, ?)", id, nickname);
    return id;
  }

  private void grantRole(UUID identityId, String role, UUID scope) {
    if (scope == null) {
      jdbcTemplate.update(
          "INSERT INTO public.operational_role_grant (identity_id, role_name, status) VALUES (?, ?, 'VIGENTE')",
          identityId,
          role);
    } else {
      jdbcTemplate.update(
          "INSERT INTO public.operational_role_grant (identity_id, role_name, scope_ref, status) VALUES (?, ?, ?, 'VIGENTE')",
          identityId,
          role,
          scope);
    }
  }

  private RequestPostProcessor oidcPrincipal(String subject) {
    Instant now = Instant.now();
    OidcIdToken idToken =
        new OidcIdToken(
            "sports-event-concurrency-token-" + subject,
            now,
            now.plusSeconds(3600),
            Map.of(IdTokenClaimNames.ISS, ISSUER, IdTokenClaimNames.SUB, subject));
    Collection<GrantedAuthority> authorities = List.of(new OidcUserAuthority(idToken));
    OidcUser oidcUser =
        new DefaultOidcUser(authorityContributor.mapAuthorities(authorities), idToken);
    return oidcLogin().oidcUser(oidcUser);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestOidcConfiguration {
    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("betufu-sports-event-result-concurrency-it")
              .clientSecret("unused")
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .scope("openid")
              .authorizationUri("https://idp.invalid/authorize")
              .tokenUri("https://idp.invalid/token")
              .jwkSetUri("https://idp.invalid/jwks")
              .issuerUri(ISSUER)
              .userNameAttributeName(IdTokenClaimNames.SUB)
              .clientName("BETUFU Sports Event Result Concurrency Test")
              .build();
      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
