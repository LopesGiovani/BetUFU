package br.com.betufu.sports;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.betufu.platform.observability.CorrelationIdFilter;
import br.com.betufu.platform.security.BetufuRoleAuthorityContributor;
import br.com.betufu.platform.session.RedisContainerSupport;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.docker.compose.enabled=false",
      "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
      "BETUFU_OIDC_CLIENT_ID=betufu-sports-organization-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused",
      "BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1",
      "BETUFU_MATRICULA_HMAC_KEY_V1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE="
    })
@ActiveProfiles("integrated")
@Testcontainers
@Import(SportsOrganizationIT.TestOidcConfiguration.class)
class SportsOrganizationIT {

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
  private UUID competitionA;
  private UUID editionA;
  private UUID sportA;
  private UUID categoryA;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute(
        "TRUNCATE TABLE public.sports_team, public.sports_organization, "
            + "public.sports_sport, public.sports_competition, public.audit_event, public.operational_role_grant, "
            + "public.betufu_profile, public.ufu_enrollment, public.local_identity CASCADE");
    try (var connection = redisTemplate.getConnectionFactory().getConnection()) {
      connection.serverCommands().flushDb();
    }
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .addFilters(correlationIdFilter, springSecurityFilterChain)
            .build();

    UUID globalAdmin = createIdentity("global-admin-sub", "global_admin");
    UUID competitionAAdmin = createIdentity("competition-a-admin-sub", "competition_a_admin");
    UUID competitionBAdmin = createIdentity("competition-b-admin-sub", "competition_b_admin");
    createIdentity("regular-sub", "regular_user");
    grantRole(globalAdmin, "ADMINISTRADOR_GERAL", null);

    competitionA = createCompetition("Competição A", "competicao-a");
    grantRole(competitionAAdmin, "ADMINISTRADOR_COMPETICAO", competitionA);
    editionA = createEdition(competitionA, "Edição A", "edicao-a");
    sportA = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_sport (sport_id, name, slug) VALUES (?, ?, ?)",
        sportA,
        "Futsal",
        "futsal");
    jdbcTemplate.update(
        "UPDATE public.sports_competition SET sport_id = ? WHERE competition_id = ?",
        sportA,
        competitionA);
    categoryA = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_category (category_id, edition_id, name, slug) VALUES (?, ?, ?, ?)",
        categoryA,
        editionA,
        "Livre",
        "livre");
  }

  @Test
  @DisplayName(
      "Equipe deriva a competição da edição, respeita escopo e publicação usa optimistic locking")
  void createsScopedTeamAndRejectsCrossCompetitionAndStaleWrites() throws Exception {
    UUID competitionB = createCompetition("Competição B", "competicao-b");
    grantRole(identityId("competition-b-admin-sub"), "ADMINISTRADOR_COMPETICAO", competitionB);
    UUID orgId = createOrganization();
    UUID teamId = createTeam(orgId);

    mockMvc
        .perform(
            get("/api/v1/operations/sports/competitions/" + competitionA + "/teams")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].competitionId").value(competitionA.toString()))
        .andExpect(jsonPath("$[0].organizationId").value(orgId.toString()))
        .andExpect(jsonPath("$[0].editionId").value(editionA.toString()));

    mockMvc
        .perform(
            get("/api/v1/operations/sports/competitions/" + competitionA + "/teams")
                .with(oidcPrincipal("competition-b-admin-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            put("/api/v1/operations/sports/teams/" + teamId)
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "0")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nome atualizado\",\"slug\":\"nome-atualizado\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lockVersion").value(1));

    mockMvc
        .perform(
            put("/api/v1/operations/sports/teams/" + teamId)
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "0")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"perde\",\"slug\":\"perde\"}"))
        .andExpect(status().isConflict());

    // A team path cannot smuggle a different competition: its edition is the authority.
    mockMvc
        .perform(
            get("/api/v1/operations/sports/competitions/" + competitionB + "/teams")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Catalogo global e somente do administrador geral e a leitura respeita a competicao")
  void listsOnlyReachableOrganizationsAndRejectsGlobalWritesForCompetitionAdmin() throws Exception {
    UUID competitionB = createCompetition("Competicao B", "competicao-b");
    grantRole(identityId("competition-b-admin-sub"), "ADMINISTRADOR_COMPETICAO", competitionB);
    UUID editionB = createEdition(competitionB, "Edicao B", "edicao-b");
    UUID sportB = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_sport (sport_id, name, slug) VALUES (?, ?, ?)",
        sportB,
        "Volei",
        "volei");
    jdbcTemplate.update(
        "UPDATE public.sports_competition SET sport_id = ? WHERE competition_id = ?",
        sportB,
        competitionB);
    UUID categoryB = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_category (category_id, edition_id, name, slug) VALUES (?, ?, ?, ?)",
        categoryB,
        editionB,
        "Livre B",
        "livre-b");

    UUID organizationA = createOrganization("Org A", "org-a");
    UUID organizationB = createOrganization("Org B", "org-b");
    createTeam(organizationA);
    createTeam(
        editionB,
        sportB,
        categoryB,
        organizationB,
        "competition-b-admin-sub",
        "Equipe B",
        "equipe-b");

    mockMvc
        .perform(
            get("/api/v1/operations/sports/organizations")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].organizationId").value(organizationA.toString()))
        .andExpect(jsonPath("$[1]").doesNotExist());

    mockMvc
        .perform(
            get("/api/v1/operations/sports/organizations")
                .with(oidcPrincipal("competition-b-admin-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].organizationId").value(organizationB.toString()))
        .andExpect(jsonPath("$[1]").doesNotExist());

    mockMvc
        .perform(
            get("/api/v1/operations/sports/organizations")
                .with(oidcPrincipal("global-admin-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].organizationId").value(organizationA.toString()))
        .andExpect(jsonPath("$[1].organizationId").value(organizationB.toString()));

    mockMvc
        .perform(
            post("/api/v1/operations/sports/organizations")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nao permitido\",\"slug\":\"nao-permitido\"}"))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            put("/api/v1/operations/sports/organizations/" + organizationA)
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "0")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Nao permitido\",\"slug\":\"nao-permitido\"}"))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/v1/operations/sports/organizations/" + organizationA + "/publish")
                .with(oidcPrincipal("competition-a-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "0"))
        .andExpect(status().isForbidden());

    Map<String, Object> organizationRow =
        jdbcTemplate.queryForMap(
            "SELECT name, status, lock_version FROM public.sports_organization WHERE organization_id = ?",
            organizationA);
    assertEquals("Org A", organizationRow.get("name"));
    assertEquals("RASCUNHO", organizationRow.get("status"));
    assertEquals(0L, organizationRow.get("lock_version"));
    assertEquals(
        0,
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.sports_organization WHERE slug = ?",
            Integer.class,
            "nao-permitido"));
  }

  @Test
  @DisplayName(
      "Favorito por organização usa FK publicada, permite remoção e não expõe favoriteTeamId")
  void savesOrganizationFavoriteOnlyWhenPublished() throws Exception {
    UUID orgId = createOrganization();
    mockMvc
        .perform(
            put("/api/v1/profile/me/favorite-organization")
                .with(oidcPrincipal("regular-sub"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"organizationId\":\"" + orgId + "\"}"))
        .andExpect(status().isNotFound());

    publishOrganization(orgId);
    mockMvc
        .perform(
            put("/api/v1/profile/me/favorite-organization")
                .with(oidcPrincipal("regular-sub"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"organizationId\":\"" + orgId + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.favoriteOrganizationId").value(orgId.toString()))
        .andExpect(jsonPath("$.favoriteTeamId").doesNotExist());

    mockMvc
        .perform(
            put("/api/v1/profile/me/favorite-organization")
                .with(oidcPrincipal("regular-sub"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"organizationId\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.favoriteOrganizationId").doesNotExist());
  }

  private UUID createOrganization() throws Exception {
    return createOrganization("Atletica Engenharia", "atletica-engenharia");
  }

  private UUID createOrganization(String name, String slug) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/operations/sports/organizations")
                    .with(oidcPrincipal("global-admin-sub"))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + name + "\",\"slug\":\"" + slug + "\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("RASCUNHO"))
            .andReturn();
    return uuid(result, "organizationId");
  }

  private UUID createTeam(UUID organizationId) throws Exception {
    return createTeam(
        editionA,
        sportA,
        categoryA,
        organizationId,
        "competition-a-admin-sub",
        "Engenharia Futsal",
        "engenharia-futsal");
  }

  private UUID createTeam(
      UUID editionId,
      UUID sportId,
      UUID categoryId,
      UUID organizationId,
      String subject,
      String name,
      String slug)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/operations/sports/editions/"
                        + editionId
                        + "/sports/"
                        + sportId
                        + "/categories/"
                        + categoryId
                        + "/organizations/"
                        + organizationId
                        + "/teams")
                    .with(oidcPrincipal(subject))
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"" + name + "\",\"slug\":\"" + slug + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
    return uuid(result, "teamId");
  }

  private void publishOrganization(UUID organizationId) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/operations/sports/organizations/" + organizationId + "/publish")
                .with(oidcPrincipal("global-admin-sub"))
                .with(csrf())
                .param("expectedLockVersion", "0"))
        .andExpect(status().isOk());
  }

  private UUID createCompetition(String name, String slug) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_competition (competition_id, name, slug) VALUES (?, ?, ?)",
        id,
        name,
        slug);
    return id;
  }

  private UUID createEdition(UUID competitionId, String name, String slug) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO public.sports_edition (edition_id, competition_id, name, slug) VALUES (?, ?, ?, ?)",
        id,
        competitionId,
        name,
        slug);
    return id;
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

  private UUID identityId(String subject) {
    return jdbcTemplate.queryForObject(
        "SELECT identity_id FROM public.local_identity WHERE issuer = ? AND subject = ?",
        UUID.class,
        ISSUER,
        subject);
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

  private UUID uuid(MvcResult result, String field) throws Exception {
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
    return UUID.fromString(body.path(field).asText());
  }

  private RequestPostProcessor oidcPrincipal(String subject) {
    Instant now = Instant.now();
    OidcIdToken idToken =
        new OidcIdToken(
            "sports-organization-token-" + subject,
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
              .clientId("betufu-sports-organization-it")
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
              .clientName("BETUFU Sports Organization Test")
              .build();
      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
