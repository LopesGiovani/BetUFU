package br.com.betufu.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.betufu.identity.internal.OperationalRoleGrantRepository;
import br.com.betufu.platform.observability.CorrelationIdFilter;
import br.com.betufu.platform.security.BetufuRoleAuthorityContributor;
import br.com.betufu.platform.session.RedisContainerSupport;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.client.oidc.authentication.OidcAuthorizationCodeAuthenticationProvider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
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
      "BETUFU_OIDC_CLIENT_ID=betufu-role-matrix-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused",
      "BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1",
      "BETUFU_MATRICULA_HMAC_KEY_V1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE="
    })
@ActiveProfiles("integrated")
@Testcontainers
@Import(RoleAuthorizationMatrixIT.TestOidcConfiguration.class)
class RoleAuthorizationMatrixIT {

  private static final String ISSUER = "https://idp.invalid";

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

  @Autowired private OperationalRoleGrantRepository roleGrantRepository;

  @Autowired private BetufuRoleAuthorityContributor betufuRoleAuthorityContributor;

  private MockMvc mockMvc;

  private UUID adminIdentityId;
  private UUID competitionAdminIdentityId;
  private UUID moderatorIdentityId;
  private UUID regularUserIdentityId;
  private UUID targetIdentityId;
  private UUID scopeId;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute(
        "TRUNCATE TABLE public.audit_event, public.operational_role_grant, public.betufu_profile, public.ufu_enrollment, public.local_identity CASCADE");
    var connection = redisTemplate.getConnectionFactory().getConnection();
    connection.serverCommands().flushDb();

    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .addFilters(correlationIdFilter, springSecurityFilterChain)
            .build();

    scopeId = UUID.randomUUID();

    adminIdentityId = createIdentity(ISSUER, "admin-sub", "admin_user");
    competitionAdminIdentityId = createIdentity(ISSUER, "comp-admin-sub", "comp_admin_user");
    moderatorIdentityId = createIdentity(ISSUER, "moderator-sub", "moderator_user");
    regularUserIdentityId = createIdentity(ISSUER, "regular-sub", "regular_user");
    targetIdentityId = createIdentity(ISSUER, "target-sub", "target_user");

    // insertGrant e @Modifying e exige transacao ativa; o setUp nao tem uma. A escrita
    // direta por jdbcTemplate e o padrao ja usado por RoleGrantControllerIT, que passa.
    grantRole(adminIdentityId, "ADMINISTRADOR_GERAL", null);
    grantRole(competitionAdminIdentityId, "ADMINISTRADOR_COMPETICAO", scopeId);
    grantRole(moderatorIdentityId, "MODERADOR", scopeId);
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

  // -----------------------------------------------------------------------
  // Matriz de autorização completa
  // -----------------------------------------------------------------------

  @Test
  @DisplayName("Matriz: GET /api/v1/session é anônimo e acessível por todos os papéis")
  void sessionEndpointIsPublic() throws Exception {
    mockMvc.perform(get("/api/v1/session")).andExpect(status().is2xxSuccessful());

    mockMvc
        .perform(get("/api/v1/session").with(oidcPrincipal(ISSUER, "regular-sub", null)))
        .andExpect(status().is2xxSuccessful());
  }

  @Test
  @DisplayName(
      "Fiação: o contexto expõe exatamente um GrantedAuthoritiesMapper e ele é o contributor BETUFU")
  void betufuContributorIsTheOnlyAuthoritiesMapperInContext() {
    // Esta e a propriedade que governa o pipeline: o Spring Security resolve o unico
    // GrantedAuthoritiesMapper do contexto. Se alguem remover o bean, registrar um mapper
    // concorrente ou trocar o tipo, esta prova fica vermelha.
    Map<String, GrantedAuthoritiesMapper> mappers =
        applicationContext.getBeansOfType(GrantedAuthoritiesMapper.class);

    assertThat(mappers.values())
        .as("exatamente um GrantedAuthoritiesMapper deve governar o pipeline de login")
        .hasSize(1);
    assertThat(mappers.values().iterator().next())
        .as("o mapper do contexto deve ser o contributor BETUFU, nao o mapper tecnico cru")
        .isInstanceOf(BetufuRoleAuthorityContributor.class);
  }

  @Test
  @DisplayName("Fiação: o provider OIDC real usa o único contributor BETUFU do contexto")
  void oidcProviderUsesTheSingleBetufuContributorBean() {
    // Inspeciona o provider criado pelo DSL do Spring, nao um mapper chamado manualmente
    // pelo teste. Os servicos opcionais de sessao nao participam dessa resolucao.
    assertOidcProviderUsesBetufuContributor(springSecurityFilterChain, "cadeia OIDC");
  }

  @Test
  @DisplayName("Matriz: GET /api/v1/diagnostic exige ROLE_TECHNICAL (Gate 1)")
  void diagnosticEndpointRequiresTechnicalRole() throws Exception {
    // Anônimo -> 401 Problem Details. O Accept e obrigatorio: sem ele o entry point de
    // Problem Details nao casa e a cadeia cai no LoginUrlAuthenticationEntryPoint, que
    // responde 302. E o header que todo cliente da API envia.
    mockMvc
        .perform(get("/api/v1/diagnostic").accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());

    // Usuário comum sem claim TECHNICAL -> 403 Forbidden
    mockMvc
        .perform(get("/api/v1/diagnostic").with(oidcPrincipal(ISSUER, "regular-sub", null)))
        .andExpect(status().isForbidden());

    // Administrador geral sem claim TECHNICAL -> 403 Forbidden (papéis operacionais não substituem
    // TECHNICAL)
    mockMvc
        .perform(get("/api/v1/diagnostic").with(oidcPrincipal(ISSUER, "admin-sub", null)))
        .andExpect(status().isForbidden());

    // Usuário com claim TECHNICAL -> a autorizacao PASSA. O DiagnosticController e
    // @Profile("dev | homolog") e este IT roda em "integrated", entao nao existe mapping:
    // a resposta correta aqui e 404, nunca 200.
    //
    // A prova esta justamente na diferenca entre 403 e 404. Com a claim TECHNICAL a
    // requisicao atravessa o matcher e chega ao despacho, que nao encontra handler; sem a
    // claim ela para em 403 nas tres assercoes acima. Se o ROLE_TECHNICAL deixasse de ser
    // concedido, este 404 viraria 403 e a prova ficaria vermelha.
    mockMvc
        .perform(
            get("/api/v1/diagnostic")
                .with(oidcPrincipal(ISSUER, "regular-sub", List.of("TECHNICAL"))))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName(
      "Matriz: Endpoints operacionais /api/v1/operations/roles/** exigem ADMINISTRADOR_GERAL")
  void rolesEndpointsRequireAdminGeral() throws Exception {
    // 1. GET /api/v1/operations/roles/{targetId}
    mockMvc
        .perform(get("/api/v1/operations/roles/" + targetIdentityId))
        .andExpect(status().is3xxRedirection());

    mockMvc
        .perform(
            get("/api/v1/operations/roles/" + targetIdentityId)
                .with(oidcPrincipal(ISSUER, "regular-sub", null)))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            get("/api/v1/operations/roles/" + targetIdentityId)
                .with(oidcPrincipal(ISSUER, "comp-admin-sub", null)))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            get("/api/v1/operations/roles/" + targetIdentityId)
                .with(oidcPrincipal(ISSUER, "moderator-sub", null)))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            get("/api/v1/operations/roles/" + targetIdentityId)
                .with(oidcPrincipal(ISSUER, "admin-sub", null)))
        .andExpect(status().isOk());

    // 2. POST /api/v1/operations/roles/grants
    String grantBody =
        "{\"targetIdentityId\":\""
            + targetIdentityId
            + "\",\"role\":\"MODERADOR\",\"scopeRef\":\""
            + scopeId
            + "\"}";

    mockMvc
        .perform(
            post("/api/v1/operations/roles/grants")
                .with(oidcPrincipal(ISSUER, "regular-sub", null))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(grantBody))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/v1/operations/roles/grants")
                .with(oidcPrincipal(ISSUER, "moderator-sub", null))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(grantBody))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/v1/operations/roles/grants")
                .with(oidcPrincipal(ISSUER, "comp-admin-sub", null))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(grantBody))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/v1/operations/roles/grants")
                .with(oidcPrincipal(ISSUER, "admin-sub", null))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(grantBody))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "Matriz: POST /api/v1/operations/enrollments/correction permite somente ADMINISTRADOR_GERAL")
  void enrollmentCorrectionRequiresAdminGeral() throws Exception {
    String correctionBody =
        "{\"targetIdentityId\":\""
            + targetIdentityId
            + "\",\"matricula\":\"20242BCC909\","
            + "\"reason\":\"Correcao administrativa comprovada\"}";

    mockMvc
        .perform(
            post("/api/v1/operations/enrollments/correction")
                .with(oidcPrincipal(ISSUER, "comp-admin-sub", null))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(correctionBody))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/v1/operations/enrollments/correction")
                .with(oidcPrincipal(ISSUER, "moderator-sub", null))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(correctionBody))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/v1/operations/enrollments/correction")
                .with(oidcPrincipal(ISSUER, "admin-sub", null))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(correctionBody))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "Matriz: Matcher /api/v1/operations/password-reset-events/** precede /api/v1/** e exige ADMINISTRADOR_GERAL")
  void passwordResetEventsRecoveryMatcherPrecedesBroadApiMatcherAndRequiresAdminGeral()
      throws Exception {
    String recoveryPath = "/api/v1/operations/password-reset-events/failed";

    // Anônimo -> 401 Problem Details (Accept obrigatorio; ver
    // diagnosticEndpointRequiresTechnicalRole)
    mockMvc
        .perform(get(recoveryPath).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());

    // Usuário comum autenticado -> 403 Forbidden (o matcher específico barra antes de /api/v1/**)
    mockMvc
        .perform(get(recoveryPath).with(oidcPrincipal(ISSUER, "regular-sub", null)))
        .andExpect(status().isForbidden());

    // Moderador autenticado -> 403 Forbidden
    mockMvc
        .perform(get(recoveryPath).with(oidcPrincipal(ISSUER, "moderator-sub", null)))
        .andExpect(status().isForbidden());

    // Administrador de competição autenticado -> 403 Forbidden
    mockMvc
        .perform(get(recoveryPath).with(oidcPrincipal(ISSUER, "comp-admin-sub", null)))
        .andExpect(status().isForbidden());

    // Administrador geral autenticado -> atravessa o matcher e NAO recebe 403. O que vem
    // depois e responsabilidade do controller, nao da matriz: enquanto o Plano 02-31 nao
    // existia, o resultado era 404 por falta de mapping; com o recovery entregue, passa a
    // ser 2xx. Esta prova fixa a propriedade que lhe cabe — a autorizacao passou —, e nao
    // o status do handler, que mudaria de novo a cada evolucao do endpoint.
    mockMvc
        .perform(get(recoveryPath).with(oidcPrincipal(ISSUER, "admin-sub", null)))
        .andExpect(
            result ->
                assertThat(result.getResponse().getStatus())
                    .as("administrador geral nao pode ser barrado pelo matcher")
                    .isNotEqualTo(403));
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

  private void assertOidcProviderUsesBetufuContributor(
      FilterChainProxy filterChain, String branchDescription) {
    List<OAuth2LoginAuthenticationFilter> oauth2Filters =
        filterChain.getFilters("/login/oauth2/code/keycloak").stream()
            .filter(OAuth2LoginAuthenticationFilter.class::isInstance)
            .map(OAuth2LoginAuthenticationFilter.class::cast)
            .toList();
    assertThat(oauth2Filters)
        .as("%s deve ter exatamente um filtro de callback OAuth2", branchDescription)
        .hasSize(1);

    Object managerValue =
        ReflectionTestUtils.getField(
            oauth2Filters.getFirst(),
            AbstractAuthenticationProcessingFilter.class,
            "authenticationManager");
    assertThat(managerValue).isInstanceOf(AuthenticationManager.class);

    List<AuthenticationProvider> providers =
        authenticationProviders((AuthenticationManager) managerValue);
    List<OidcAuthorizationCodeAuthenticationProvider> oidcProviders =
        providers.stream()
            .filter(OidcAuthorizationCodeAuthenticationProvider.class::isInstance)
            .map(OidcAuthorizationCodeAuthenticationProvider.class::cast)
            .toList();
    assertThat(oidcProviders)
        .as("%s deve ter exatamente um provider OIDC", branchDescription)
        .hasSize(1);

    Object mapper = ReflectionTestUtils.getField(oidcProviders.getFirst(), "authoritiesMapper");
    assertThat(mapper)
        .as("%s deve fiar o contributor BETUFU no provider OIDC real", branchDescription)
        .isSameAs(betufuRoleAuthorityContributor);
  }

  private static List<AuthenticationProvider> authenticationProviders(
      AuthenticationManager authenticationManager) {
    List<AuthenticationProvider> providers = new ArrayList<>();
    AuthenticationManager current = authenticationManager;
    while (current != null) {
      if (current instanceof ProviderManager providerManager) {
        providers.addAll(providerManager.getProviders());
        Object parent = ReflectionTestUtils.getField(providerManager, "parent");
        current =
            parent instanceof AuthenticationManager authenticationManagerParent
                ? authenticationManagerParent
                : null;
        continue;
      }

      // Spring Security 7 envolve o ProviderManager em
      // ObservationAuthenticationManager. Navegar pelo delegate mantém a prova focada no
      // provider efetivamente usado, sem depender de o suporte de observabilidade estar
      // ligado ou desligado no profile.
      Object delegate = ReflectionTestUtils.getField(current, "delegate");
      current =
          delegate instanceof AuthenticationManager authenticationManagerDelegate
              ? authenticationManagerDelegate
              : null;
    }
    return providers;
  }

  private org.springframework.test.web.servlet.request.RequestPostProcessor oidcPrincipal(
      String issuer, String subject, Object roleClaimValue) {
    Map<String, Object> claims =
        roleClaimValue == null
            ? Map.of(IdTokenClaimNames.ISS, issuer, IdTokenClaimNames.SUB, subject)
            : Map.of(
                IdTokenClaimNames.ISS,
                issuer,
                IdTokenClaimNames.SUB,
                subject,
                "betufu_roles",
                roleClaimValue);

    Instant now = Instant.now();
    OidcIdToken idToken = new OidcIdToken("matrix-it-token", now, now.plusSeconds(3600), claims);

    // oidcLogin() monta a Authentication direto e NUNCA invoca o GrantedAuthoritiesMapper:
    // ele so roda na troca OAuth2 real. Aplicar o contributor real aqui reproduz o que a
    // producao faz e torna este IT uma prova de ponta a ponta — concessao no banco,
    // contributor, autoridade no contexto e decisao do matcher.
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
              .clientId("betufu-role-matrix-it")
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
              .clientName("BETUFU Role Authorization Matrix Test")
              .build();

      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
