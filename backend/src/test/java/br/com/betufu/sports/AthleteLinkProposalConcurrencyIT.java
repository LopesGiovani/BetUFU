package br.com.betufu.sports;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.docker.compose.enabled=false",
      "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
      "BETUFU_OIDC_CLIENT_ID=betufu-sports-link-concurrency-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused",
      "BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1",
      "BETUFU_MATRICULA_HMAC_KEY_V1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE="
    })
@ActiveProfiles("integrated")
@Testcontainers
@Import(AthleteLinkProposalConcurrencyIT.TestOidcConfiguration.class)
class AthleteLinkProposalConcurrencyIT {

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
  @Autowired private ObjectMapper objectMapper;

  private MockMvc mockMvc;
  private UUID teamA;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute(
        "TRUNCATE TABLE public.sports_athlete_link_review, public.sports_athlete_link_proposal, "
            + "public.sports_athlete, public.sports_team, public.sports_organization, "
            + "public.sports_category, public.sports_sport, public.sports_edition, public.sports_competition, "
            + "public.audit_event, public.operational_role_grant, public.betufu_profile, "
            + "public.ufu_enrollment, public.local_identity CASCADE");
    try (var connection = redisTemplate.getConnectionFactory().getConnection()) {
      connection.serverCommands().flushDb();
    }
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .addFilters(correlationIdFilter, springSecurityFilterChain)
            .build();

    UUID globalAdmin = createIdentity("global-admin-sub", "global_admin");
    grantRole(globalAdmin, "ADMINISTRADOR_GERAL", null);

    UUID competitionA = createCompetition("Competição A", "competicao-a");
    UUID editionA = createEdition(competitionA, "Edição A", "edicao-a");
    UUID sportA = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_sport (sport_id, name, slug) VALUES (?, ?, ?)",
        sportA,
        "Futsal",
        "futsal");
    jdbcTemplate.update(
        "UPDATE public.sports_competition SET sport_id = ? WHERE competition_id = ?",
        sportA,
        competitionA);
    UUID categoryA = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_category (category_id, edition_id, name, slug) VALUES (?, ?, ?, ?)",
        categoryA,
        editionA,
        "Livre",
        "livre");

    UUID organizationA = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_organization (organization_id, name, slug, status) VALUES (?, ?, ?, 'PUBLICADO')",
        organizationA,
        "Atlética Engenharia",
        "atletica-engenharia");

    teamA = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_team (team_id, edition_id, sport_id, category_id, organization_id, name, slug, status) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, 'PUBLICADO')",
        teamA,
        editionA,
        sportA,
        categoryA,
        organizationA,
        "Engenharia Futsal",
        "engenharia-futsal");
  }

  @Test
  @DisplayName(
      "Decisões concorrentes sobre a mesma proposta convergem para no máximo 1 estado terminal sem corrupção")
  void concurrentDecisionsOnSameProposalSerializeCleanly() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/operations/sports/teams/{teamId}/athletes", teamA)
                .with(csrf())
                .with(oidcUser("global-admin-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "name": "Atleta Concorrente",
                                  "slug": "atleta-concorrente",
                                  "matricula": "11223344"
                                }
                                """))
        .andExpect(status().isCreated());

    createIdentity("user-conc-sub", "conc_user");
    mockMvc
        .perform(
            put("/api/v1/enrollment/me")
                .with(csrf())
                .with(oidcUser("user-conc-sub"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                                {
                                  "matricula": "11223344"
                                }
                                """))
        .andExpect(status().isOk());

    UUID proposalId =
        jdbcTemplate.queryForObject(
            "SELECT proposal_id FROM public.sports_athlete_link_proposal LIMIT 1", UUID.class);
    assertThat(proposalId).isNotNull();

    int concurrency = 6;
    ExecutorService executor = Executors.newFixedThreadPool(concurrency);
    CountDownLatch ready = new CountDownLatch(concurrency);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Integer>> futures = new ArrayList<>();

    for (int i = 0; i < concurrency; i++) {
      final String decision = (i % 2 == 0) ? "ACCEPT" : "REJECT";
      futures.add(
          executor.submit(
              () -> {
                ready.countDown();
                assertThat(start.await(10, SECONDS)).isTrue();

                MvcResult result =
                    mockMvc
                        .perform(
                            put("/api/v1/me/sports/athlete-links/{proposalId}/decision", proposalId)
                                .with(csrf())
                                .with(oidcUser("user-conc-sub"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(String.format("{\"decision\": \"%s\"}", decision)))
                        .andReturn();
                return result.getResponse().getStatus();
              }));
    }

    assertThat(ready.await(10, SECONDS)).isTrue();
    start.countDown();

    List<Integer> statuses = new ArrayList<>();
    for (Future<Integer> f : futures) {
      statuses.add(f.get(30, SECONDS));
    }

    executor.shutdownNow();

    // Todas as requisições devolveram 200 (se repetiram a mesma decisão vencedora) ou 409 (se
    // tentaram a outra decisão após o terminal)
    assertThat(statuses).allMatch(s -> s == 200 || s == 409);

    // O estado final no banco de dados é único e consistente (ACEITA ou REJEITADA)
    String finalStatus =
        jdbcTemplate.queryForObject(
            "SELECT status FROM public.sports_athlete_link_proposal WHERE proposal_id = ?",
            String.class,
            proposalId);
    assertThat(finalStatus).isIn("ACEITA", "REJEITADA");

    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.sports_athlete_link_proposal WHERE proposal_id = ?",
            Integer.class,
            proposalId);
    assertThat(count).isEqualTo(1);
  }

  @Test
  @DisplayName(
      "Duas propostas que competem pela mesma identidade retornam 200 e 409 sem erro SQL exposto")
  void competingProposalsForSameIdentityReturnConflict() throws Exception {
    UUID identityId = createIdentity("user-competing-sub", "competing");
    UUID athleteA =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.sports_athlete (name, slug) VALUES ('Atleta A', 'atleta-a') RETURNING athlete_id",
            UUID.class);
    UUID athleteB =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.sports_athlete (name, slug) VALUES ('Atleta B', 'atleta-b') RETURNING athlete_id",
            UUID.class);
    UUID proposalA = UUID.randomUUID();
    UUID proposalB = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_athlete_link_proposal (proposal_id, athlete_id, identity_id) VALUES (?, ?, ?)",
        proposalA,
        athleteA,
        identityId);
    jdbcTemplate.update(
        "INSERT INTO public.sports_athlete_link_proposal (proposal_id, athlete_id, identity_id) VALUES (?, ?, ?)",
        proposalB,
        athleteB,
        identityId);

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<MvcResult>> futures =
        List.of(proposalA, proposalB).stream()
            .map(
                proposalId ->
                    executor.submit(
                        () -> {
                          ready.countDown();
                          assertThat(start.await(10, SECONDS)).isTrue();
                          return mockMvc
                              .perform(
                                  put(
                                          "/api/v1/me/sports/athlete-links/{proposalId}/decision",
                                          proposalId)
                                      .with(csrf())
                                      .with(oidcUser("user-competing-sub"))
                                      .contentType(MediaType.APPLICATION_JSON)
                                      .content("{\"decision\":\"ACCEPT\"}"))
                              .andReturn();
                        }))
            .toList();

    assertThat(ready.await(10, SECONDS)).isTrue();
    start.countDown();
    List<MvcResult> results = new ArrayList<>();
    for (Future<MvcResult> future : futures) {
      results.add(future.get(30, SECONDS));
    }
    executor.shutdownNow();

    assertThat(results)
        .extracting(result -> result.getResponse().getStatus())
        .containsExactlyInAnyOrder(200, 409);
    assertThat(
            results.stream()
                .filter(result -> result.getResponse().getStatus() == 409)
                .findFirst()
                .orElseThrow()
                .getResponse()
                .getContentAsString())
        .contains("BETUFU-409-STALE_VERSION")
        .doesNotContain("uq_sports_athlete_link_proposal_accepted_identity");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.sports_athlete_link_proposal "
                    + "WHERE identity_id = ? AND status = 'ACEITA'",
                Integer.class,
                identityId))
        .isEqualTo(1);
  }

  private UUID createIdentity(String sub, String nickname) {
    UUID identityId =
        jdbcTemplate.queryForObject(
            "INSERT INTO public.local_identity (issuer, subject) VALUES (?, ?) RETURNING identity_id",
            UUID.class,
            ISSUER,
            sub);
    jdbcTemplate.update(
        "INSERT INTO public.betufu_profile (identity_id, nickname) VALUES (?, ?)",
        identityId,
        nickname);
    return identityId;
  }

  private void grantRole(UUID identityId, String roleName, UUID scopeRef) {
    jdbcTemplate.update(
        "INSERT INTO public.operational_role_grant (identity_id, role_name, scope_ref, status) "
            + "VALUES (?, ?, ?, 'VIGENTE')",
        identityId,
        roleName,
        scopeRef);
  }

  private UUID createCompetition(String name, String slug) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO public.sports_competition (name, slug) VALUES (?, ?) RETURNING competition_id",
        UUID.class,
        name,
        slug);
  }

  private UUID createEdition(UUID competitionId, String name, String slug) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO public.sports_edition (competition_id, name, slug) VALUES (?, ?, ?) RETURNING edition_id",
        UUID.class,
        competitionId,
        name,
        slug);
  }

  private RequestPostProcessor oidcUser(String subject) {
    return oidcLogin().oidcUser(buildOidcUser(subject));
  }

  private OidcUser buildOidcUser(String subject) {
    Map<String, Object> claims =
        Map.of(
            IdTokenClaimNames.ISS,
            ISSUER,
            IdTokenClaimNames.SUB,
            subject,
            "preferred_username",
            subject);
    OidcIdToken idToken =
        new OidcIdToken(
            "mock-token-" + subject, Instant.now(), Instant.now().plusSeconds(300), claims);
    Collection<? extends GrantedAuthority> authorities =
        authorityContributor.mapAuthorities(List.of(new OidcUserAuthority(idToken)));
    return new DefaultOidcUser(authorities, idToken);
  }

  @TestConfiguration
  static class TestOidcConfiguration {
    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      return new InMemoryClientRegistrationRepository(
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("betufu-sports-link-concurrency-it")
              .clientSecret("unused")
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .scope("openid", "profile", "email")
              .authorizationUri("https://idp.invalid/auth")
              .tokenUri("https://idp.invalid/token")
              .userInfoUri("https://idp.invalid/userinfo")
              .jwkSetUri("https://idp.invalid/jwks")
              .userNameAttributeName("sub")
              .clientName("Keycloak")
              .build());
    }
  }
}
