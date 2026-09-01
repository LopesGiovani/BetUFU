package br.com.betufu.platform.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.identity.OperationalIdentityLookup.GrantedRole;
import br.com.betufu.platform.identity.OperationalIdentityLookup.OperationalIdentity;
import br.com.betufu.platform.observability.CorrelationIdFilter;
import br.com.betufu.platform.security.BetufuRoleAuthorityContributor;
import br.com.betufu.platform.session.RedisContainerSupport;
import java.net.URI;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.docker.compose.enabled=false",
      "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
      "BETUFU_OIDC_CLIENT_ID=betufu-capabilities-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused",
      "BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1",
      "BETUFU_MATRICULA_HMAC_KEY_V1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE="
    })
@ActiveProfiles("integrated")
@Testcontainers
@Import(OperationalCapabilitiesIT.TestOidcConfiguration.class)
class OperationalCapabilitiesIT {

  private static final String ISSUER = "https://idp.invalid";
  private static final String CAPABILITIES_PATH = "/api/v1/operations/capabilities";

  private static final String POSTGRES_IMAGE_REFERENCE =
      "docker.io/library/postgres:18.4-trixie@sha256:"
          + "a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636";

  private static final DockerImageName POSTGRES_IMAGE =
      DockerImageName.parse(POSTGRES_IMAGE_REFERENCE).asCompatibleSubstituteFor("postgres");

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

  @Autowired private BetufuRoleAuthorityContributor betufuRoleAuthorityContributor;

  @MockitoSpyBean private OperationalIdentityLookup identityLookup;

  private MockMvc mockMvc;

  private UUID adminIdentityId;
  private UUID compAdminIdentityId;
  private UUID moderatorIdentityId;
  private UUID regularUserIdentityId;
  private UUID scopedAdminIdentityId;
  private UUID globalCompAdminIdentityId;
  private UUID globalModeratorIdentityId;
  private UUID unknownRoleIdentityId;
  private UUID multiRoleIdentityId;
  private UUID adminAndModIdentityId;
  private UUID targetIdentityId;
  private UUID scopeId;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute(
        "TRUNCATE TABLE public.audit_event, public.operational_role_grant, public.betufu_profile, public.ufu_enrollment, public.local_identity CASCADE");
    var connection = redisTemplate.getConnectionFactory().getConnection();
    connection.serverCommands().flushDb();
    reset(identityLookup);

    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .addFilters(correlationIdFilter, springSecurityFilterChain)
            .build();

    scopeId = UUID.randomUUID();

    adminIdentityId = createIdentity(ISSUER, "admin-sub", "admin_geral");
    compAdminIdentityId = createIdentity(ISSUER, "comp-admin-sub", "comp_admin");
    moderatorIdentityId = createIdentity(ISSUER, "moderator-sub", "moderador_user");
    regularUserIdentityId = createIdentity(ISSUER, "regular-sub", "regular_user");
    scopedAdminIdentityId = createIdentity(ISSUER, "scoped-admin-sub", "scoped_admin");
    globalCompAdminIdentityId = createIdentity(ISSUER, "global-comp-sub", "global_comp");
    globalModeratorIdentityId = createIdentity(ISSUER, "global-mod-sub", "global_mod");
    unknownRoleIdentityId = createIdentity(ISSUER, "unknown-role-sub", "unknown_role");
    multiRoleIdentityId = createIdentity(ISSUER, "multi-role-sub", "multi_role");
    adminAndModIdentityId = createIdentity(ISSUER, "admin-mod-sub", "admin_mod");
    targetIdentityId = createIdentity(ISSUER, "target-sub", "alvo_operacao");

    // 1. Papéis válidos
    grantRole(adminIdentityId, "ADMINISTRADOR_GERAL", null);
    grantRole(compAdminIdentityId, "ADMINISTRADOR_COMPETICAO", scopeId);
    grantRole(moderatorIdentityId, "MODERADOR", scopeId);

    // 2. Múltiplos papéis
    grantRole(multiRoleIdentityId, "ADMINISTRADOR_COMPETICAO", scopeId);
    grantRole(multiRoleIdentityId, "MODERADOR", scopeId);

    grantRole(adminAndModIdentityId, "ADMINISTRADOR_GERAL", null);
    grantRole(adminAndModIdentityId, "MODERADOR", scopeId);

    // 3. Projeções com escopo inválido via spy (a constraint PostgreSQL recusa persistência direta)
    doReturn(
            Optional.of(
                new OperationalIdentity(
                    scopedAdminIdentityId,
                    Set.of(new GrantedRole("ADMINISTRADOR_GERAL", scopeId)))))
        .when(identityLookup)
        .lookupIdentity(ISSUER, "scoped-admin-sub");

    doReturn(
            Optional.of(
                new OperationalIdentity(
                    globalCompAdminIdentityId,
                    Set.of(new GrantedRole("ADMINISTRADOR_COMPETICAO", null)))))
        .when(identityLookup)
        .lookupIdentity(ISSUER, "global-comp-sub");

    doReturn(
            Optional.of(
                new OperationalIdentity(
                    globalModeratorIdentityId, Set.of(new GrantedRole("MODERADOR", null)))))
        .when(identityLookup)
        .lookupIdentity(ISSUER, "global-mod-sub");
  }

  @Test
  @DisplayName("Requisição anônima recebe 401 Problem Details")
  void anonymousRequestGetsUnauthorized() throws Exception {
    mockMvc
        .perform(get(CAPABILITIES_PATH).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.type").value("about:blank"))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value("BETUFU-401-UNAUTHORIZED"));
  }

  @Test
  @DisplayName("Usuário autenticado sem identidade local recebe 200 com capabilities vazia")
  void authenticatedWithoutLocalIdentityGetsOkEmptyCapabilities() throws Exception {
    mockMvc
        .perform(
            get(CAPABILITIES_PATH)
                .with(oidcPrincipal(ISSUER, "non-existent-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities").isArray())
        .andExpect(jsonPath("$.capabilities").isEmpty());
  }

  @Test
  @DisplayName("Usuário comum autenticado sem papel operacional recebe 200 com capabilities vazia")
  void regularUserWithoutOperationalRoleGetsOkEmptyCapabilities() throws Exception {
    mockMvc
        .perform(
            get(CAPABILITIES_PATH)
                .with(oidcPrincipal(ISSUER, "regular-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities").isArray())
        .andExpect(jsonPath("$.capabilities").isEmpty());
  }

  @Test
  @DisplayName("ADMINISTRADOR_GERAL global recebe exatamente as 3 capacidades na ordem canônica")
  void adminGeralReceivesAllThreeCapabilitiesInCanonicalOrder() throws Exception {
    mockMvc
        .perform(
            get(CAPABILITIES_PATH)
                .with(oidcPrincipal(ISSUER, "admin-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities").isArray())
        .andExpect(jsonPath("$.capabilities.length()").value(5))
        .andExpect(jsonPath("$.capabilities[0]").value("VIEW_OPERATIONAL_PANEL"))
        .andExpect(jsonPath("$.capabilities[1]").value("MANAGE_ROLES"))
        .andExpect(jsonPath("$.capabilities[2]").value("CORRECT_ENROLLMENT"))
        .andExpect(jsonPath("$.capabilities[3]").value("MANAGE_SPORTS_CATALOG"))
        .andExpect(jsonPath("$.capabilities[4]").value("BOOTSTRAP_SPORTS_COMPETITION"));
  }

  @Test
  @DisplayName("ADMINISTRADOR_COMPETICAO escopado recebe somente VIEW_OPERATIONAL_PANEL")
  void competitionAdminReceivesOnlyViewOperationalPanel() throws Exception {
    mockMvc
        .perform(
            get(CAPABILITIES_PATH)
                .with(oidcPrincipal(ISSUER, "comp-admin-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities").isArray())
        .andExpect(jsonPath("$.capabilities.length()").value(2))
        .andExpect(jsonPath("$.capabilities[0]").value("VIEW_OPERATIONAL_PANEL"))
        .andExpect(jsonPath("$.capabilities[1]").value("MANAGE_SPORTS_CATALOG"));
  }

  @Test
  @DisplayName("MODERADOR escopado recebe somente VIEW_OPERATIONAL_PANEL")
  void moderatorReceivesOnlyViewOperationalPanel() throws Exception {
    mockMvc
        .perform(
            get(CAPABILITIES_PATH)
                .with(oidcPrincipal(ISSUER, "moderator-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities").isArray())
        .andExpect(jsonPath("$.capabilities.length()").value(1))
        .andExpect(jsonPath("$.capabilities[0]").value("VIEW_OPERATIONAL_PANEL"));
  }

  @Test
  @DisplayName(
      "ADMINISTRADOR_GERAL com scopeRef não nulo é recusado fail-closed e recebe capabilities vazia")
  void scopedAdminGeralReceivesEmptyCapabilities() throws Exception {
    mockMvc
        .perform(
            get(CAPABILITIES_PATH)
                .with(oidcPrincipal(ISSUER, "scoped-admin-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities").isArray())
        .andExpect(jsonPath("$.capabilities").isEmpty());
  }

  @Test
  @DisplayName(
      "Papéis escopados com scopeRef nulo são recusados fail-closed e recebem capabilities vazia")
  void globalScopedRolesWithoutScopeReceiveEmptyCapabilities() throws Exception {
    // ADMINISTRADOR_COMPETICAO sem escopo
    mockMvc
        .perform(
            get(CAPABILITIES_PATH)
                .with(oidcPrincipal(ISSUER, "global-comp-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities").isArray())
        .andExpect(jsonPath("$.capabilities").isEmpty());

    // MODERADOR sem escopo
    mockMvc
        .perform(
            get(CAPABILITIES_PATH)
                .with(oidcPrincipal(ISSUER, "global-mod-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities").isArray())
        .andExpect(jsonPath("$.capabilities").isEmpty());
  }

  @Test
  @DisplayName("Papel desconhecido no banco é ignorado fail-closed e recebe capabilities vazia")
  void unknownRoleReceivesEmptyCapabilities() throws Exception {
    mockMvc
        .perform(
            get(CAPABILITIES_PATH)
                .with(oidcPrincipal(ISSUER, "unknown-role-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities").isArray())
        .andExpect(jsonPath("$.capabilities").isEmpty());
  }

  @Test
  @DisplayName("Múltiplos papéis produzem união sem duplicatas na ordem canônica do enum")
  void multiRoleUsersReceiveDeduplicatedUnionInCanonicalOrder() throws Exception {
    // Usuário com ADMINISTRADOR_COMPETICAO e MODERADOR (ambos concedem VIEW_OPERATIONAL_PANEL)
    mockMvc
        .perform(
            get(CAPABILITIES_PATH)
                .with(oidcPrincipal(ISSUER, "multi-role-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities").isArray())
        .andExpect(jsonPath("$.capabilities.length()").value(2))
        .andExpect(jsonPath("$.capabilities[0]").value("VIEW_OPERATIONAL_PANEL"))
        .andExpect(jsonPath("$.capabilities[1]").value("MANAGE_SPORTS_CATALOG"));

    // Usuário com ADMINISTRADOR_GERAL global e MODERADOR escopado
    mockMvc
        .perform(
            get(CAPABILITIES_PATH)
                .with(oidcPrincipal(ISSUER, "admin-mod-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities").isArray())
        .andExpect(jsonPath("$.capabilities.length()").value(5))
        .andExpect(jsonPath("$.capabilities[0]").value("VIEW_OPERATIONAL_PANEL"))
        .andExpect(jsonPath("$.capabilities[1]").value("MANAGE_ROLES"))
        .andExpect(jsonPath("$.capabilities[2]").value("CORRECT_ENROLLMENT"))
        .andExpect(jsonPath("$.capabilities[3]").value("MANAGE_SPORTS_CATALOG"))
        .andExpect(jsonPath("$.capabilities[4]").value("BOOTSTRAP_SPORTS_COMPETITION"));
  }

  @Test
  @DisplayName(
      "Prova de par exato: divergência de 1 caractere no issuer ou subject devolve lista vazia")
  void exactPairLookupFailsIfIssuerOrSubjectDiffersBySingleCharacter() throws Exception {
    // Issuer com barra no final -> divergente byte a byte
    mockMvc
        .perform(
            get(CAPABILITIES_PATH)
                .with(oidcPrincipal(ISSUER + "/", "admin-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities").isArray())
        .andExpect(jsonPath("$.capabilities").isEmpty());

    // Subject com caractere adicional -> divergente byte a byte
    mockMvc
        .perform(
            get(CAPABILITIES_PATH)
                .with(oidcPrincipal(ISSUER, "admin-sub-x"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities").isArray())
        .andExpect(jsonPath("$.capabilities").isEmpty());

    // Subject com maiúscula (sem case folding)
    mockMvc
        .perform(
            get(CAPABILITIES_PATH)
                .with(oidcPrincipal(ISSUER, "Admin-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities").isArray())
        .andExpect(jsonPath("$.capabilities").isEmpty());
  }

  @Test
  @DisplayName("Extração empírica de iss aceita tipos String, java.net.URL e java.net.URI")
  void handlesEmpiricalIssTypesCorrectly() throws Exception {
    // String
    mockMvc
        .perform(
            get(CAPABILITIES_PATH)
                .with(oidcPrincipalWithCustomIss(ISSUER, "admin-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities.length()").value(5));

    // java.net.URL
    mockMvc
        .perform(
            get(CAPABILITIES_PATH)
                .with(oidcPrincipalWithCustomIss(URI.create(ISSUER).toURL(), "admin-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities.length()").value(5));

    // java.net.URI
    mockMvc
        .perform(
            get(CAPABILITIES_PATH)
                .with(oidcPrincipalWithCustomIss(URI.create(ISSUER), "admin-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.capabilities.length()").value(5));
  }

  @Test
  @DisplayName("Claims OIDC iss ou sub ausentes ou em branco resultam em 401 Unauthorized")
  void missingOrBlankOidcClaimsResultInUnauthorized() throws Exception {
    // iss ausente
    mockMvc
        .perform(
            get(CAPABILITIES_PATH)
                .with(oidcPrincipalWithClaims(Map.of(IdTokenClaimNames.SUB, "admin-sub")))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());

    // sub ausente
    OidcUser oidcUserWithoutSub = mock(OidcUser.class);
    doReturn(Map.of(IdTokenClaimNames.ISS, ISSUER)).when(oidcUserWithoutSub).getClaims();
    doReturn(Map.of(IdTokenClaimNames.ISS, ISSUER)).when(oidcUserWithoutSub).getAttributes();
    doReturn(List.of()).when(oidcUserWithoutSub).getAuthorities();
    doReturn("admin-sub").when(oidcUserWithoutSub).getName();

    mockMvc
        .perform(
            get(CAPABILITIES_PATH)
                .with(oidcLogin().oidcUser(oidcUserWithoutSub))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());

    // sub em branco
    OidcUser oidcUserWithBlankSub = mock(OidcUser.class);
    Map<String, Object> blankSubClaims =
        Map.of(IdTokenClaimNames.ISS, ISSUER, IdTokenClaimNames.SUB, "   ");
    doReturn(blankSubClaims).when(oidcUserWithBlankSub).getClaims();
    doReturn(blankSubClaims).when(oidcUserWithBlankSub).getAttributes();
    doReturn(List.of()).when(oidcUserWithBlankSub).getAuthorities();
    doReturn("blank-sub-test-principal").when(oidcUserWithBlankSub).getName();

    mockMvc
        .perform(
            get(CAPABILITIES_PATH)
                .with(oidcLogin().oidcUser(oidcUserWithBlankSub))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName(
      "O payload de resposta contém exclusivamente o campo capabilities e nenhuma informação privada")
  void payloadContainsStrictlyCapabilitiesFieldAndNoPrivateData() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get(CAPABILITIES_PATH)
                    .with(oidcPrincipal(ISSUER, "admin-sub"))
                    .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andReturn();

    String body = result.getResponse().getContentAsString();

    assertThat(body)
        .contains(
            "\"capabilities\":[\"VIEW_OPERATIONAL_PANEL\",\"MANAGE_ROLES\",\"CORRECT_ENROLLMENT\",\"MANAGE_SPORTS_CATALOG\",\"BOOTSTRAP_SPORTS_COMPETITION\"]")
        .doesNotContain(adminIdentityId.toString())
        .doesNotContain("admin_geral")
        .doesNotContain("ADMINISTRADOR_GERAL")
        .doesNotContain("identityId")
        .doesNotContain("roleName")
        .doesNotContain("scopeRef")
        .doesNotContain("scopeId")
        .doesNotContain("nickname")
        .doesNotContain("apelido")
        .doesNotContain("email")
        .doesNotContain("matricula")
        .doesNotContain("hmac")
        .doesNotContain("digest")
        .doesNotContain("token")
        .doesNotContain("mfa")
        .doesNotContain("claims");
  }

  @Test
  @DisplayName(
      "Coerência com endpoints autoritativos: capacidades informam navegação mas endpoints continuam impondo 403")
  void capabilitiesAreCoherentWithAuthoritativeEndpoints() throws Exception {
    // 1. Painel operacional (/api/v1/operations/panel)
    // - Admin geral -> 200
    mockMvc
        .perform(
            get("/api/v1/operations/panel")
                .with(oidcPrincipal(ISSUER, "admin-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
    // - Comp Admin -> 200
    mockMvc
        .perform(
            get("/api/v1/operations/panel")
                .with(oidcPrincipal(ISSUER, "comp-admin-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
    // - Moderador -> 200
    mockMvc
        .perform(
            get("/api/v1/operations/panel")
                .with(oidcPrincipal(ISSUER, "moderator-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
    // - Usuário comum -> 403
    mockMvc
        .perform(
            get("/api/v1/operations/panel")
                .with(oidcPrincipal(ISSUER, "regular-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
    // - Admin escopado (inválido) -> 403
    mockMvc
        .perform(
            get("/api/v1/operations/panel")
                .with(oidcPrincipal(ISSUER, "scoped-admin-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());

    // 2. Consulta de papéis (/api/v1/operations/roles/{targetId})
    String rolesPath = "/api/v1/operations/roles/" + targetIdentityId;
    // - Admin geral -> 200
    mockMvc
        .perform(
            get(rolesPath)
                .with(oidcPrincipal(ISSUER, "admin-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
    // - Comp Admin -> 403
    mockMvc
        .perform(
            get(rolesPath)
                .with(oidcPrincipal(ISSUER, "comp-admin-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
    // - Moderador -> 403
    mockMvc
        .perform(
            get(rolesPath)
                .with(oidcPrincipal(ISSUER, "moderator-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
    // - Usuário comum -> 403
    mockMvc
        .perform(
            get(rolesPath)
                .with(oidcPrincipal(ISSUER, "regular-sub"))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());

    // 3. Correção de matrícula (/api/v1/operations/enrollments/correction)
    String correctionPath = "/api/v1/operations/enrollments/correction";
    String invalidBody = "{\"targetIdentityId\":null}";
    // - Admin geral -> atravessa a segurança (400 por validação de request, provando que
    // autorização passou)
    mockMvc
        .perform(
            post(correctionPath)
                .with(oidcPrincipal(ISSUER, "admin-sub"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody))
        .andExpect(status().isBadRequest());
    // - Comp Admin -> 403
    mockMvc
        .perform(
            post(correctionPath)
                .with(oidcPrincipal(ISSUER, "comp-admin-sub"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody))
        .andExpect(status().isForbidden());
    // - Moderador -> 403
    mockMvc
        .perform(
            post(correctionPath)
                .with(oidcPrincipal(ISSUER, "moderator-sub"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody))
        .andExpect(status().isForbidden());
    // - Usuário comum -> 403
    mockMvc
        .perform(
            post(correctionPath)
                .with(oidcPrincipal(ISSUER, "regular-sub"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody))
        .andExpect(status().isForbidden());
  }

  // -----------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------

  private UUID createIdentity(String issuer, String subject, String nickname) {
    jdbcTemplate.update(
        "INSERT INTO public.local_identity (issuer, subject) VALUES (?, ?) ON CONFLICT (issuer, subject) DO NOTHING",
        issuer,
        subject);
    UUID identityId =
        jdbcTemplate.queryForObject(
            "SELECT identity_id FROM public.local_identity WHERE issuer = ? AND subject = ?",
            UUID.class,
            issuer,
            subject);
    jdbcTemplate.update(
        "INSERT INTO public.betufu_profile (identity_id, nickname) VALUES (?, ?) ON CONFLICT (identity_id) DO NOTHING",
        identityId,
        nickname);
    return identityId;
  }

  private void grantRole(UUID identityId, String roleName, UUID scopeRef) {
    if (scopeRef == null) {
      jdbcTemplate.update(
          "INSERT INTO public.operational_role_grant (identity_id, role_name, status)"
              + " VALUES (?, ?, 'VIGENTE') ON CONFLICT DO NOTHING",
          identityId,
          roleName);
    } else {
      jdbcTemplate.update(
          "INSERT INTO public.operational_role_grant (identity_id, role_name, scope_ref, status)"
              + " VALUES (?, ?, ?, 'VIGENTE') ON CONFLICT DO NOTHING",
          identityId,
          roleName,
          scopeRef);
    }
  }

  private RequestPostProcessor oidcPrincipal(String issuer, String subject) {
    return oidcPrincipalWithCustomIss(issuer, subject);
  }

  private RequestPostProcessor oidcPrincipalWithCustomIss(Object issuerValue, String subject) {
    Map<String, Object> claims = new HashMap<>();
    claims.put(IdTokenClaimNames.ISS, issuerValue);
    claims.put(IdTokenClaimNames.SUB, subject);
    return oidcPrincipalWithClaims(claims);
  }

  private RequestPostProcessor oidcPrincipalWithClaims(Map<String, Object> claims) {
    Instant now = Instant.now();
    OidcIdToken idToken =
        new OidcIdToken("capabilities-it-token", now, now.plusSeconds(3600), claims);

    Collection<GrantedAuthority> baseAuthorities = List.of(new OidcUserAuthority(idToken));
    Collection<? extends GrantedAuthority> mappedAuthorities =
        betufuRoleAuthorityContributor.mapAuthorities(baseAuthorities);

    OidcUser oidcUser = new DefaultOidcUser(mappedAuthorities, idToken);
    return oidcLogin().oidcUser(oidcUser);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestOidcConfiguration {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("betufu-capabilities-it")
              .clientSecret("unused")
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .scope("openid")
              .authorizationUri("https://idp.invalid/authorize")
              .tokenUri("https://idp.invalid/token")
              .jwkSetUri("https://idp.invalid/jwks")
              .issuerUri("https://idp.invalid")
              .userNameAttributeName(IdTokenClaimNames.SUB)
              .clientName("BETUFU Capabilities IT")
              .build();

      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
