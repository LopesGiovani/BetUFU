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
      "BETUFU_OIDC_CLIENT_ID=betufu-sports-roster-concurrency-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused",
      "BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1",
      "BETUFU_MATRICULA_HMAC_KEY_V1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE="
    })
@ActiveProfiles("integrated")
@Testcontainers
@Import(SportsRosterConcurrencyIT.TestOidcConfiguration.class)
class SportsRosterConcurrencyIT {

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
  private UUID teamId;
  private UUID athlete1;
  private UUID athlete2;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute(
        """
                TRUNCATE TABLE public.sports_roster_publication_member, public.sports_roster_publication,
                public.sports_roster_member, public.sports_roster, public.sports_athlete,
                public.sports_team, public.sports_organization, public.sports_category,
                public.sports_sport, public.sports_edition, public.sports_competition,
                public.audit_event, public.operational_role_grant, public.betufu_profile,
                public.ufu_enrollment, public.local_identity CASCADE
                """);
    try (var connection = redisTemplate.getConnectionFactory().getConnection()) {
      connection.serverCommands().flushDb();
    }
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .addFilters(correlationIdFilter, springSecurityFilterChain)
            .build();

    UUID adminId = createIdentity("admin-concurrency-sub", "admin_concurrency");
    competitionId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_competition (competition_id, name, slug) VALUES (?, ?, ?)",
        competitionId,
        "Competição Concorrência",
        "competicao-concorrencia");
    grantRole(adminId, "ADMINISTRADOR_COMPETICAO", competitionId);

    UUID editionId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_edition (edition_id, competition_id, name, slug) VALUES (?, ?, ?, ?)",
        editionId,
        competitionId,
        "Edição Concorrência",
        "edicao-concorrencia");

    UUID sportId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_sport (sport_id, name, slug) VALUES (?, ?, ?)",
        sportId,
        "Futsal",
        "futsal");

    UUID categoryId = UUID.randomUUID();
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

    teamId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_team (team_id, organization_id, edition_id, sport_id, category_id, name, slug, status) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, 'PUBLICADO')",
        teamId,
        organizationId,
        editionId,
        sportId,
        categoryId,
        "Time Concorrência",
        "time-concorrencia");

    athlete1 = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_athlete (athlete_id, name, slug, status) VALUES (?, ?, ?, 'PUBLICADO')",
        athlete1,
        "Atleta Um",
        "atleta-um");

    athlete2 = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_athlete (athlete_id, name, slug, status) VALUES (?, ?, ?, 'PUBLICADO')",
        athlete2,
        "Atleta Dois",
        "atleta-dois");
  }

  @Test
  @DisplayName("Atualizações simultâneas do rascunho de elenco serializam ou conflitam com 409")
  void concurrentDraftUpdatesSerializeOrConflictCleanly() throws Exception {
    int threads = 4;
    ExecutorService executor = Executors.newFixedThreadPool(threads);
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger okCount = new AtomicInteger(0);
    AtomicInteger conflictCount = new AtomicInteger(0);

    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
      final int jersey = 10 + i;
      futures.add(
          executor.submit(
              () -> {
                ready.countDown();
                try {
                  start.await();
                  int status =
                      mockMvc
                          .perform(
                              put("/api/v1/operations/sports/teams/" + teamId + "/roster")
                                  .with(oidcPrincipal("admin-concurrency-sub"))
                                  .with(csrf())
                                  .param("expectedLockVersion", "0")
                                  .contentType(MediaType.APPLICATION_JSON)
                                  .content(
                                      String.format(
                                          """
                                            {
                                              "members": [
                                                { "athleteId": "%s", "jerseyNumber": %d }
                                              ]
                                            }
                                            """,
                                          athlete1, jersey)))
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
            "SELECT lock_version FROM public.sports_roster WHERE team_id = ?", Long.class, teamId);
    assertThat(lockVersion).isEqualTo(1L);
  }

  @Test
  @DisplayName(
      "Publicações simultâneas no mesmo elenco produzem exatamente um snapshot e 409 para os concorrentes")
  void concurrentPublicationsSerializeCleanly() throws Exception {
    // Popula rascunho com versão 1
    mockMvc
        .perform(
            put("/api/v1/operations/sports/teams/" + teamId + "/roster")
                .with(oidcPrincipal("admin-concurrency-sub"))
                .with(csrf())
                .param("expectedLockVersion", "0")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    String.format(
                        """
                                {
                                  "members": [
                                    { "athleteId": "%s", "jerseyNumber": 10 }
                                  ]
                                }
                                """,
                        athlete1)))
        .andReturn();

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
                              post("/api/v1/operations/sports/teams/" + teamId + "/roster/publish")
                                  .with(oidcPrincipal("admin-concurrency-sub"))
                                  .with(csrf())
                                  .param("expectedLockVersion", "1"))
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
            "SELECT count(*) FROM public.sports_roster_publication WHERE team_id = ?",
            Integer.class,
            teamId);
    assertThat(pubCount).isEqualTo(1);
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
            "sports-roster-concurrency-token-" + subject,
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
              .clientId("betufu-sports-roster-concurrency-it")
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
              .clientName("BETUFU Sports Roster Concurrency Test")
              .build();
      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
