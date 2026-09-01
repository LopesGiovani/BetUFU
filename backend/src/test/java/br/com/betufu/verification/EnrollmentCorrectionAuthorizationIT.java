package br.com.betufu.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.docker.compose.enabled=false",
      "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
      "BETUFU_OIDC_CLIENT_ID=betufu-enrollment-correction-authorization-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused",
      "BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1",
      "BETUFU_MATRICULA_HMAC_KEY_V1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE="
    })
@ActiveProfiles("integrated")
@Testcontainers
@Import(EnrollmentCorrectionAuthorizationIT.TestOidcConfiguration.class)
class EnrollmentCorrectionAuthorizationIT {

  private static final String ISSUER = "https://idp.invalid";
  private static final String ENDPOINT = "/api/v1/operations/enrollments/correction";

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
  private UUID targetIdentityId;

  @BeforeEach
  void setUp() {
    jdbcTemplate.execute(
        "TRUNCATE TABLE public.audit_event, public.operational_role_grant, public.betufu_profile, public.ufu_enrollment, public.local_identity CASCADE");
    try (var connection = redisTemplate.getConnectionFactory().getConnection()) {
      connection.serverCommands().flushDb();
    }

    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .addFilters(correlationIdFilter, springSecurityFilterChain)
            .build();

    UUID adminIdentityId = createIdentity("admin-sub", "admin_user");
    UUID competitionAdminIdentityId = createIdentity("comp-admin-sub", "comp_admin_user");
    UUID moderatorIdentityId = createIdentity("moderator-sub", "moderator_user");
    createIdentity("regular-sub", "regular_user");
    targetIdentityId = createIdentity("target-sub", "target_user");

    grantRole(adminIdentityId, "ADMINISTRADOR_GERAL", null);
    UUID scopeId = UUID.randomUUID();
    grantRole(competitionAdminIdentityId, "ADMINISTRADOR_COMPETICAO", scopeId);
    grantRole(moderatorIdentityId, "MODERADOR", scopeId);
  }

  @Test
  @DisplayName("Somente ADMINISTRADOR_GERAL acessa a correcao e recusas nao revelam papel ou area")
  void rejectsAnonymousAndEveryNonGlobalAdministratorWithGenericProblemDetails() throws Exception {
    String body = correctionBody();

    mockMvc
        .perform(
            post(ENDPOINT)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("BETUFU-401-UNAUTHORIZED"));

    assertGenericForbidden(performAs("regular-sub", body));
    assertGenericForbidden(performAs("comp-admin-sub", body));
    assertGenericForbidden(performAs("moderator-sub", body));

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.ufu_enrollment", Integer.class))
        .as("requisicoes barradas na cadeia nao podem alterar vinculos")
        .isZero();
  }

  @Test
  @DisplayName("ADMINISTRADOR_GERAL atravessa a cadeia e executa a correcao")
  void globalAdministratorExecutesCorrection() throws Exception {
    mockMvc
        .perform(
            post(ENDPOINT)
                .with(oidcPrincipal("admin-sub"))
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(correctionBody()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.targetIdentityId").value(targetIdentityId.toString()))
        .andExpect(jsonPath("$.status").value("ATIVO"))
        .andExpect(jsonPath("$.correctedAt").isNotEmpty())
        .andExpect(jsonPath("$.previousBindingReleased").value(false));
  }

  private MvcResult performAs(String subject, String body) throws Exception {
    return mockMvc
        .perform(
            post(ENDPOINT)
                .with(oidcPrincipal(subject))
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andReturn();
  }

  private static void assertGenericForbidden(MvcResult result) throws Exception {
    assertThat(result.getResponse().getStatus()).isEqualTo(403);
    assertThat(result.getResponse().getContentAsString())
        .contains("BETUFU-403-FORBIDDEN")
        .contains("Sem autoriza")
        .doesNotContain("ADMINISTRADOR_GERAL")
        .doesNotContainIgnoringCase("matrícula")
        .doesNotContainIgnoringCase("matricula")
        .doesNotContainIgnoringCase("correção")
        .doesNotContainIgnoringCase("correcao");
  }

  private String correctionBody() {
    return "{\"targetIdentityId\":\""
        + targetIdentityId
        + "\",\"matricula\":\"20242BCC909\","
        + "\"reason\":\"Correcao administrativa comprovada\"}";
  }

  private UUID createIdentity(String subject, String nickname) {
    jdbcTemplate.update(
        "INSERT INTO public.local_identity (issuer, subject) VALUES (?, ?)", ISSUER, subject);
    UUID identityId =
        jdbcTemplate.queryForObject(
            "SELECT identity_id FROM public.local_identity WHERE issuer = ? AND subject = ?",
            UUID.class,
            ISSUER,
            subject);
    jdbcTemplate.update(
        "INSERT INTO public.betufu_profile (identity_id, nickname) VALUES (?, ?)",
        identityId,
        nickname);
    return identityId;
  }

  private void grantRole(UUID identityId, String roleName, UUID scopeRef) {
    if (scopeRef == null) {
      jdbcTemplate.update(
          "INSERT INTO public.operational_role_grant (identity_id, role_name, status) VALUES (?, ?, 'VIGENTE')",
          identityId,
          roleName);
    } else {
      jdbcTemplate.update(
          "INSERT INTO public.operational_role_grant (identity_id, role_name, scope_ref, status) VALUES (?, ?, ?, 'VIGENTE')",
          identityId,
          roleName,
          scopeRef);
    }
  }

  private RequestPostProcessor oidcPrincipal(String subject) {
    Instant now = Instant.now();
    OidcIdToken idToken =
        new OidcIdToken(
            "enrollment-correction-authorization-token",
            now,
            now.plusSeconds(3600),
            Map.of(IdTokenClaimNames.ISS, ISSUER, IdTokenClaimNames.SUB, subject));

    Collection<GrantedAuthority> baseAuthorities = List.of(new OidcUserAuthority(idToken));
    Collection<? extends GrantedAuthority> mappedAuthorities =
        authorityContributor.mapAuthorities(baseAuthorities);
    OidcUser oidcUser = new DefaultOidcUser(mappedAuthorities, idToken);
    return oidcLogin().oidcUser(oidcUser);
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestOidcConfiguration {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("betufu-enrollment-correction-authorization-it")
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
              .clientName("BETUFU Enrollment Correction Authorization Test")
              .build();

      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
