package br.com.betufu.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.betufu.audit.internal.AuditEvent;
import br.com.betufu.audit.internal.AuditEventRepository;
import br.com.betufu.identity.internal.OperationalRoleGrantRepository;
import br.com.betufu.identity.internal.RoleGrantService;
import br.com.betufu.platform.observability.CorrelationIdFilter;
import br.com.betufu.platform.session.RedisContainerSupport;
import br.com.betufu.platform.session.SelectiveTouchSessionRepository;
import jakarta.servlet.Filter;
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
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.SessionRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
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
            "BETUFU_OIDC_CLIENT_ID=betufu-role-grant-controller-it",
            "BETUFU_OIDC_CLIENT_SECRET=unused",
            "BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1",
            "BETUFU_MATRICULA_HMAC_KEY_V1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE="
        })
@ActiveProfiles("integrated")
@Testcontainers
@Import(RoleGrantControllerIT.TestOidcConfiguration.class)
class RoleGrantControllerIT {

    private static final String ROLES_BASE_PATH = "/api/v1/operations/roles";
    private static final String ROLES_GRANTS_PATH = "/api/v1/operations/roles/grants";
    private static final String ROLES_REVOCATIONS_PATH = "/api/v1/operations/roles/revocations";

    private static final String POSTGRES_IMAGE_REFERENCE =
            "docker.io/library/postgres:18.4-trixie@sha256:"
                    + "a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636";

    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse(POSTGRES_IMAGE_REFERENCE)
                    .asCompatibleSubstituteFor("postgres");

    @Container
    @ServiceConnection(name = "postgres")
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(POSTGRES_IMAGE)
            .withDatabaseName("betufu_app")
            .withUsername("betufu_app")
            .withPassword("betufu-test-only")
            .withInitScript("postgres-audit-runtime-role.sql");

    @Container
    static final GenericContainer<?> REDIS = RedisContainerSupport.REDIS;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        RedisContainerSupport.registerProperties(registry);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private CorrelationIdFilter correlationIdFilter;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private OperationalRoleGrantRepository roleGrantRepository;

    @Autowired
    private RoleGrantService roleGrantService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE public.sports_competition, public.audit_event, "
                        + "public.operational_role_grant, public.betufu_profile, "
                        + "public.ufu_enrollment, public.local_identity CASCADE");
        var connection = redisTemplate.getConnectionFactory().getConnection();
        connection.serverCommands().flushDb();

        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .addFilters(correlationIdFilter, springSecurityFilterChain)
                .build();
    }

    @Test
    @DisplayName("Requisição não autenticada para endpoints de papéis é redirecionada")
    void unauthenticatedRequestIsRedirected() throws Exception {
        UUID targetId = UUID.randomUUID();
        mockMvc.perform(get(ROLES_BASE_PATH + "/" + targetId))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post(ROLES_GRANTS_PATH).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetIdentityId\":\"" + targetId + "\",\"role\":\"MODERADOR\",\"scopeRef\":\"11111111-2222-4333-8444-555555555555\"}"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Administrador concede papel a outra pessoa com sucesso e resposta reflete estado VIGENTE")
    void adminGrantsRoleToOtherUserSuccessfully() throws Exception {
        UUID actorId = createIdentityAndProfile("https://idp.invalid", "actor-sub-1", "admin_actor");
        UUID targetId = createIdentityAndProfile("https://idp.invalid", "target-sub-1", "user_target");
        grantAdminRole(actorId);

        OidcUser actorPrincipal = buildOidcUser("https://idp.invalid", "actor-sub-1");

        mockMvc.perform(post(ROLES_GRANTS_PATH)
                        .with(oidcLogin().oidcUser(actorPrincipal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetIdentityId\":\"" + targetId + "\",\"role\":\"MODERADOR\",\"scopeRef\":\"11111111-2222-4333-8444-555555555555\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identityId").value(targetId.toString()))
                .andExpect(jsonPath("$.nickname").value("user_target"))
                .andExpect(jsonPath("$.roles[?(@.role == 'MODERADOR')].status").value("VIGENTE"))
                .andExpect(jsonPath("$.roles[?(@.role == 'ADMINISTRADOR_GERAL')].status").value("NAO_CONCEDIDO"))
                .andExpect(jsonPath("$.roles[?(@.role == 'ADMINISTRADOR_COMPETICAO')].status").value("NAO_CONCEDIDO"));

        List<AuditEvent> auditEvents = auditEventRepository.findAll();
        assertThat(auditEvents).anySatisfy(event -> {
            assertThat(event.getActorIdentityId()).isEqualTo(actorId);
            assertThat(event.getTargetIdentityId()).isEqualTo(targetId);
            assertThat(event.getAction()).isEqualTo("PAPEL_CONCEDIDO");
            assertThat(event.getOutcome()).isEqualTo("PERMITIDO");
            assertThat(event.getNewValue())
                    .as("a trilha registra o papel com o escopo da concessao")
                    .isEqualTo("MODERADOR;scope=11111111-2222-4333-8444-555555555555");
        });
    }

    @Test
    @DisplayName("Guarda (a) - Autoconcessão é bloqueada com HTTP 403 e código SELF_GRANT_BLOCKED")
    void adminSelfGrantIsBlockedWithForbiddenProblemDetail() throws Exception {
        UUID actorId = createIdentityAndProfile("https://idp.invalid", "actor-self-sub", "admin_self");
        grantAdminRole(actorId);
        OidcUser actorPrincipal = buildOidcUser("https://idp.invalid", "actor-self-sub");

        mockMvc.perform(post(ROLES_GRANTS_PATH)
                        .with(oidcLogin().oidcUser(actorPrincipal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetIdentityId\":\"" + actorId + "\",\"role\":\"ADMINISTRADOR_COMPETICAO\",\"scopeRef\":\"11111111-2222-4333-8444-555555555555\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BETUFU-403-SELF_GRANT_BLOCKED"))
                .andExpect(jsonPath("$.title").value("Autoconcessão bloqueada"))
                .andExpect(jsonPath("$.action").value("Peça a outro administrador geral."));

        List<AuditEvent> auditEvents = auditEventRepository.findAll();
        assertThat(auditEvents).anySatisfy(event -> {
            assertThat(event.getActorIdentityId()).isEqualTo(actorId);
            assertThat(event.getTargetIdentityId()).isEqualTo(actorId);
            assertThat(event.getAction()).isEqualTo("AUTOCONCESSAO_PAPEL");
            assertThat(event.getOutcome()).isEqualTo("BLOQUEADO");
        });
    }

    @Test
    @DisplayName("Administrador revoga papel de outra pessoa com sucesso")
    void adminRevokesRoleSuccessfully() throws Exception {
        UUID actorId = createIdentityAndProfile("https://idp.invalid", "actor-sub-rev", "admin_revoker");
        UUID targetId = createIdentityAndProfile("https://idp.invalid", "target-sub-rev", "target_revoked");
        grantAdminRole(actorId);
        OidcUser actorPrincipal = buildOidcUser("https://idp.invalid", "actor-sub-rev");

        // Grant role first
        roleGrantService.grantRole(actorId, targetId, OperationalRole.MODERADOR, java.util.UUID.fromString("11111111-2222-4333-8444-555555555555"));

        mockMvc.perform(post(ROLES_REVOCATIONS_PATH)
                        .with(oidcLogin().oidcUser(actorPrincipal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetIdentityId\":\"" + targetId + "\",\"role\":\"MODERADOR\",\"scopeRef\":\"11111111-2222-4333-8444-555555555555\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identityId").value(targetId.toString()))
                .andExpect(jsonPath("$.roles[?(@.role == 'MODERADOR')].status").value("NAO_CONCEDIDO"));

        List<AuditEvent> auditEvents = auditEventRepository.findAll();
        assertThat(auditEvents).anySatisfy(event -> {
            assertThat(event.getActorIdentityId()).isEqualTo(actorId);
            assertThat(event.getTargetIdentityId()).isEqualTo(targetId);
            assertThat(event.getAction()).isEqualTo("PAPEL_REVOGADO");
            assertThat(event.getOutcome()).isEqualTo("PERMITIDO");
        });
    }

    @Test
    @DisplayName("Guarda (b) - Revogar o próprio papel de administrador geral com apenas 1 administrador é bloqueado com 409 LAST_ADMIN")
    void lastAdminRevocationIsBlockedWithConflictProblemDetail() throws Exception {
        UUID actorId = createIdentityAndProfile("https://idp.invalid", "only-admin-sub", "only_admin");
        OidcUser actorPrincipal = buildOidcUser("https://idp.invalid", "only-admin-sub");

        // Insert single active ADMINISTRADOR_GERAL grant
        grantAdminRole(actorId);

        mockMvc.perform(post(ROLES_REVOCATIONS_PATH)
                        .with(oidcLogin().oidcUser(actorPrincipal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetIdentityId\":\"" + actorId + "\",\"role\":\"ADMINISTRADOR_GERAL\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BETUFU-409-LAST_ADMIN"))
                .andExpect(jsonPath("$.title").value("Último administrador geral"))
                .andExpect(jsonPath("$.action").value("Conceda o papel a outra pessoa antes de revogar o seu."));

        List<AuditEvent> auditEvents = auditEventRepository.findAll();
        assertThat(auditEvents).anySatisfy(event -> {
            assertThat(event.getActorIdentityId()).isEqualTo(actorId);
            assertThat(event.getTargetIdentityId()).isEqualTo(actorId);
            assertThat(event.getAction()).isEqualTo("REVOGACAO_ULTIMO_ADMINISTRADOR");
            assertThat(event.getOutcome()).isEqualTo("BLOQUEADO");
        });
    }

    @Test
    @DisplayName("Guarda (b) - Revogar o próprio papel de administrador geral passa quando há 2 administradores vigentes")
    void selfAdminRevocationPassesWhenTwoAdminsExist() throws Exception {
        UUID admin1Id = createIdentityAndProfile("https://idp.invalid", "admin1-sub", "admin_one");
        UUID admin2Id = createIdentityAndProfile("https://idp.invalid", "admin2-sub", "admin_two");
        OidcUser admin1Principal = buildOidcUser("https://idp.invalid", "admin1-sub");

        // Insert two active ADMINISTRADOR_GERAL grants
        grantAdminRole(admin1Id);
        grantAdminRole(admin2Id);

        mockMvc.perform(post(ROLES_REVOCATIONS_PATH)
                        .with(oidcLogin().oidcUser(admin1Principal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetIdentityId\":\"" + admin1Id + "\",\"role\":\"ADMINISTRADOR_GERAL\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identityId").value(admin1Id.toString()))
                .andExpect(jsonPath("$.roles[?(@.role == 'ADMINISTRADOR_GERAL')].status").value("NAO_CONCEDIDO"));
    }

    @Test
    @DisplayName("GET /api/v1/operations/roles/{targetIdentityId} devolve os três papéis com seus estados e apelido")
    void getRolesReturnsAllThreeRolesWithStatus() throws Exception {
        UUID actorId = createIdentityAndProfile("https://idp.invalid", "actor-get-sub", "admin_viewer");
        UUID targetId = createIdentityAndProfile("https://idp.invalid", "target-get-sub", "consulted_user");
        grantAdminRole(actorId);
        OidcUser actorPrincipal = buildOidcUser("https://idp.invalid", "actor-get-sub");

        jdbcTemplate.update(
                "INSERT INTO public.operational_role_grant (identity_id, role_name, scope_ref, status) VALUES (?, 'ADMINISTRADOR_COMPETICAO', gen_random_uuid(), 'VIGENTE')",
                targetId);

        mockMvc.perform(get(ROLES_BASE_PATH + "/" + targetId)
                        .with(oidcLogin().oidcUser(actorPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identityId").value(targetId.toString()))
                .andExpect(jsonPath("$.nickname").value("consulted_user"))
                .andExpect(jsonPath("$.roles[?(@.role == 'ADMINISTRADOR_COMPETICAO')].status").value("VIGENTE"))
                .andExpect(jsonPath("$.roles[?(@.role == 'ADMINISTRADOR_GERAL')].status").value("NAO_CONCEDIDO"))
                .andExpect(jsonPath("$.roles[?(@.role == 'MODERADOR')].status").value("NAO_CONCEDIDO"));
    }

    @Test
    @DisplayName("Usuário autenticado sem papel ADMINISTRADOR_GERAL é recusado com 403 FORBIDDEN e auditado")
    void nonAdminUserIsForbiddenFromOperations() throws Exception {
        UUID nonAdminId = createIdentityAndProfile("https://idp.invalid", "non-admin-sub", "regular_user");
        UUID targetId = createIdentityAndProfile("https://idp.invalid", "target-sub-na", "target_user");
        OidcUser nonAdminPrincipal = buildOidcUser("https://idp.invalid", "non-admin-sub");

        // 1. GET roles
        mockMvc.perform(get(ROLES_BASE_PATH + "/" + targetId)
                        .with(oidcLogin().oidcUser(nonAdminPrincipal)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BETUFU-403-FORBIDDEN"))
                .andExpect(jsonPath("$.title").value("Sem autorização"));

        // 2. POST grant
        mockMvc.perform(post(ROLES_GRANTS_PATH)
                        .with(oidcLogin().oidcUser(nonAdminPrincipal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetIdentityId\":\"" + targetId + "\",\"role\":\"MODERADOR\",\"scopeRef\":\"11111111-2222-4333-8444-555555555555\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BETUFU-403-FORBIDDEN"))
                .andExpect(jsonPath("$.title").value("Sem autorização"));

        // 3. POST revoke
        mockMvc.perform(post(ROLES_REVOCATIONS_PATH)
                        .with(oidcLogin().oidcUser(nonAdminPrincipal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetIdentityId\":\"" + targetId + "\",\"role\":\"MODERADOR\",\"scopeRef\":\"11111111-2222-4333-8444-555555555555\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BETUFU-403-FORBIDDEN"))
                .andExpect(jsonPath("$.title").value("Sem autorização"));

        List<AuditEvent> auditEvents = auditEventRepository.findAll();
        assertThat(auditEvents).anySatisfy(event -> {
            assertThat(event.getActorIdentityId()).isEqualTo(nonAdminId);
            assertThat(event.getTargetIdentityId()).isEqualTo(targetId);
            assertThat(event.getAction()).isEqualTo("CONSULTA_OPERACIONAL");
            assertThat(event.getOutcome()).isEqualTo("BLOQUEADO");
        });
        assertThat(auditEvents).anySatisfy(event -> {
            assertThat(event.getActorIdentityId()).isEqualTo(nonAdminId);
            assertThat(event.getTargetIdentityId()).isEqualTo(targetId);
            assertThat(event.getAction()).isEqualTo("PAPEL_CONCEDIDO");
            assertThat(event.getOutcome()).isEqualTo("BLOQUEADO");
        });
        assertThat(auditEvents).anySatisfy(event -> {
            assertThat(event.getActorIdentityId()).isEqualTo(nonAdminId);
            assertThat(event.getTargetIdentityId()).isEqualTo(targetId);
            assertThat(event.getAction()).isEqualTo("PAPEL_REVOGADO");
            assertThat(event.getOutcome()).isEqualTo("BLOQUEADO");
        });
    }

    @Test
    @DisplayName("Concessão duplicada é idempotente e retorna 200 OK sem falha de transação")
    void duplicateGrantReturnsVigenteWithoutError() throws Exception {
        UUID actorId = createIdentityAndProfile("https://idp.invalid", "dup-actor-sub", "dup_admin");
        UUID targetId = createIdentityAndProfile("https://idp.invalid", "dup-target-sub", "dup_user");
        grantAdminRole(actorId);
        OidcUser actorPrincipal = buildOidcUser("https://idp.invalid", "dup-actor-sub");

        // First grant
        mockMvc.perform(post(ROLES_GRANTS_PATH)
                        .with(oidcLogin().oidcUser(actorPrincipal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetIdentityId\":\"" + targetId + "\",\"role\":\"MODERADOR\",\"scopeRef\":\"11111111-2222-4333-8444-555555555555\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[?(@.role == 'MODERADOR')].status").value("VIGENTE"));

        // Duplicate grant: must succeed idempotently without error
        mockMvc.perform(post(ROLES_GRANTS_PATH)
                        .with(oidcLogin().oidcUser(actorPrincipal))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetIdentityId\":\"" + targetId + "\",\"role\":\"MODERADOR\",\"scopeRef\":\"11111111-2222-4333-8444-555555555555\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[?(@.role == 'MODERADOR')].status").value("VIGENTE"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.operational_role_grant WHERE identity_id = ? AND role_name = 'MODERADOR' AND status = 'VIGENTE'",
                Integer.class,
                targetId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("Múltiplas concessões do mesmo papel com escopos diferentes são preservadas na resposta")
    void multipleGrantsOfSameRoleWithDifferentScopesAreReturned() throws Exception {
        UUID actorId = createIdentityAndProfile("https://idp.invalid", "scope-actor-sub", "scope_admin");
        UUID targetId = createIdentityAndProfile("https://idp.invalid", "scope-target-sub", "scope_user");
        grantAdminRole(actorId);
        OidcUser actorPrincipal = buildOidcUser("https://idp.invalid", "scope-actor-sub");

        UUID scopeA = createCompetition("Escopo A");
        UUID scopeB = createCompetition("Escopo B");

        roleGrantService.grantRole(actorId, targetId, OperationalRole.ADMINISTRADOR_COMPETICAO, scopeA);
        roleGrantService.grantRole(actorId, targetId, OperationalRole.ADMINISTRADOR_COMPETICAO, scopeB);

        mockMvc.perform(get(ROLES_BASE_PATH + "/" + targetId)
                        .with(oidcLogin().oidcUser(actorPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identityId").value(targetId.toString()))
                .andExpect(jsonPath("$.roles[?(@.role == 'ADMINISTRADOR_COMPETICAO')]", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$.roles[?(@.scopeRef == '" + scopeA + "')].status").value("VIGENTE"))
                .andExpect(jsonPath("$.roles[?(@.scopeRef == '" + scopeB + "')].status").value("VIGENTE"));
    }

    private void grantAdminRole(UUID identityId) {
        jdbcTemplate.update(
                "INSERT INTO public.operational_role_grant (identity_id, role_name, status) VALUES (?, 'ADMINISTRADOR_GERAL', 'VIGENTE') ON CONFLICT DO NOTHING",
                identityId);
    }

    private UUID createCompetition(String name) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO public.sports_competition (name, slug) VALUES (?, ?) RETURNING competition_id",
                UUID.class,
                name,
                "role-grant-" + UUID.randomUUID());
    }

    private UUID createIdentityAndProfile(String issuer, String subject, String nickname) {
        jdbcTemplate.update(
                "INSERT INTO public.local_identity (issuer, subject) VALUES (?, ?) ON CONFLICT (issuer, subject) DO NOTHING",
                issuer, subject);
        UUID identityId = jdbcTemplate.queryForObject(
                "SELECT identity_id FROM public.local_identity WHERE issuer = ? AND subject = ?",
                UUID.class, issuer, subject);
        jdbcTemplate.update(
                "INSERT INTO public.betufu_profile (identity_id, nickname) VALUES (?, ?) ON CONFLICT (identity_id) DO NOTHING",
                identityId, nickname);
        return identityId;
    }

    private static OidcUser buildOidcUser(String issuer, String subject) {
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
                "token-value",
                now,
                now.plusSeconds(3600),
                Map.of(
                        IdTokenClaimNames.ISS, issuer,
                        IdTokenClaimNames.SUB, subject));
        return new DefaultOidcUser(AuthorityUtils.NO_AUTHORITIES, idToken);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOidcConfiguration {

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
                    .clientId("betufu-role-grant-controller-it")
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
                    .clientName("BETUFU Role Grant Controller Test")
                    .build();

            return new InMemoryClientRegistrationRepository(registration);
        }
    }
}
