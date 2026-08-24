package br.com.betufu.platform.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.betufu.audit.AuditEntry;
import br.com.betufu.audit.AuditRecorder;
import br.com.betufu.platform.observability.CorrelationIdFilter;
import br.com.betufu.platform.security.BetufuRoleAuthorityContributor;
import br.com.betufu.platform.session.RedisContainerSupport;
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
            "BETUFU_OIDC_CLIENT_ID=betufu-panel-it",
            "BETUFU_OIDC_CLIENT_SECRET=unused",
            "BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1",
            "BETUFU_MATRICULA_HMAC_KEY_V1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE="
        })
@ActiveProfiles("integrated")
@Testcontainers
@Import(OperationalPanelIT.TestOidcConfiguration.class)
class OperationalPanelIT {

    private static final String ISSUER = "https://idp.invalid";
    private static final String PANEL_PATH = "/api/v1/operations/panel";

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
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    private BetufuRoleAuthorityContributor betufuRoleAuthorityContributor;

    @Autowired
    private AuditRecorder auditRecorder;

    private MockMvc mockMvc;

    private UUID adminIdentityId;
    private UUID compAdminIdentityId;
    private UUID moderatorIdentityId;
    private UUID regularUserIdentityId;
    private UUID targetUserIdentityId;
    private UUID scopeId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE public.audit_event, public.operational_role_grant, public.betufu_profile, public.ufu_enrollment, public.local_identity CASCADE");
        var connection = redisTemplate.getConnectionFactory().getConnection();
        connection.serverCommands().flushDb();

        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .addFilters(correlationIdFilter, springSecurityFilterChain)
                .build();

        scopeId = UUID.randomUUID();

        adminIdentityId = createIdentity(ISSUER, "admin-sub", "admin_geral");
        compAdminIdentityId = createIdentity(ISSUER, "comp-admin-sub", "comp_admin");
        moderatorIdentityId = createIdentity(ISSUER, "moderator-sub", "moderador_user");
        regularUserIdentityId = createIdentity(ISSUER, "regular-sub", "regular_user");
        targetUserIdentityId = createIdentity(ISSUER, "target-sub", "alvo_operacao");

        grantRole(adminIdentityId, "ADMINISTRADOR_GERAL", null);
        grantRole(compAdminIdentityId, "ADMINISTRADOR_COMPETICAO", scopeId);
        grantRole(moderatorIdentityId, "MODERADOR", scopeId);
    }

    @Test
    @DisplayName("Administrador Geral recebe os quatro blocos governados com dados reais do banco e auditoria")
    void adminGeralReceivesAllFourBlocks() throws Exception {
        // 1. Eventos de alteração de papel
        String privateRoleChangeReason = "ROLE-CHANGE-REASON-MUST-NOT-LEAVE-AUDIT";
        auditRecorder.record(AuditEntry.builder()
                .actorIdentityId(adminIdentityId)
                .actorRole("ADMINISTRADOR_GERAL")
                .targetIdentityId(targetUserIdentityId)
                .action(AuditEntry.ACTION_PAPEL_CONCEDIDO)
                .outcome(AuditEntry.OUTCOME_PERMITTED)
                .newValue("MODERADOR;scope=" + scopeId)
                .correlationId("corr-123")
                .build());

        auditRecorder.record(AuditEntry.builder()
                .actorIdentityId(adminIdentityId)
                .actorRole("ADMINISTRADOR_GERAL")
                .targetIdentityId(adminIdentityId)
                .action(AuditEntry.ACTION_AUTOCONCESSAO_PAPEL)
                .outcome(AuditEntry.OUTCOME_BLOCKED)
                .reason(privateRoleChangeReason)
                .correlationId("corr-456")
                .build());

        // 2. Eventos de recusa de matrícula (duplicidade e limite de tentativas)
        auditRecorder.record(AuditEntry.builder()
                .actorIdentityId(regularUserIdentityId)
                .targetIdentityId(regularUserIdentityId)
                .action(AuditEntry.ACTION_REGISTRO_MATRICULA)
                .outcome(AuditEntry.OUTCOME_BLOCKED)
                .reason("Conta já possui matrícula ativa")
                .correlationId("corr-789")
                .build());

        auditRecorder.record(AuditEntry.builder()
                .actorIdentityId(regularUserIdentityId)
                .targetIdentityId(regularUserIdentityId)
                .action(AuditEntry.ACTION_REGISTRO_MATRICULA)
                .outcome(AuditEntry.OUTCOME_BLOCKED)
                .reason("Matrícula já registrada em outra conta")
                .correlationId("corr-790")
                .build());

        auditRecorder.record(AuditEntry.builder()
                .actorIdentityId(regularUserIdentityId)
                .targetIdentityId(regularUserIdentityId)
                .action(AuditEntry.ACTION_REGISTRO_MATRICULA)
                .outcome(AuditEntry.OUTCOME_BLOCKED)
                .reason("Limite de tentativas excedido")
                .correlationId("corr-791")
                .build());

        // Recusa de validação existe na trilha, mas não pertence às duas categorias do painel.
        auditRecorder.record(AuditEntry.builder()
                .actorIdentityId(regularUserIdentityId)
                .targetIdentityId(regularUserIdentityId)
                .action(AuditEntry.ACTION_REGISTRO_MATRICULA)
                .outcome(AuditEntry.OUTCOME_BLOCKED)
                .reason("Formato de matrícula inválido")
                .correlationId("corr-792")
                .build());

        // 3. Evento de correção administrativa de matrícula com motivo por extenso
        String fullCorrectionReason = "Correção administrativa de matrícula solicitada via protocolo 23117.001234/2026-99";
        auditRecorder.record(AuditEntry.builder()
                .actorIdentityId(adminIdentityId)
                .actorRole("ADMINISTRADOR_GERAL")
                .targetIdentityId(targetUserIdentityId)
                .action(AuditEntry.ACTION_CORRECAO_MATRICULA)
                .outcome(AuditEntry.OUTCOME_PERMITTED)
                .reason(fullCorrectionReason)
                .correlationId("corr-901")
                .build());

        // 4. Cria vínculo de matrícula ativa para targetUserIdentityId (1 ativa entre 5 identidades -> 4 sem matrícula)
        byte[] dummyHmac = new byte[32];
        dummyHmac[0] = (byte) 0xAA;
        createActiveEnrollment(targetUserIdentityId, dummyHmac, (short) 1);

        mockMvc.perform(get(PANEL_PATH)
                        .with(oidcPrincipal(ISSUER, "admin-sub"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queriedAt").isNotEmpty())
                .andExpect(jsonPath("$.timeWindow.since").isNotEmpty())
                .andExpect(jsonPath("$.timeWindow.until").isNotEmpty())
                // Bloco 1: Alterações de papel
                .andExpect(jsonPath("$.roleChanges").exists())
                .andExpect(jsonPath("$.roleChanges.recentEvents").isArray())
                .andExpect(jsonPath("$.roleChanges.recentEvents[0].action").value(AuditEntry.ACTION_AUTOCONCESSAO_PAPEL))
                .andExpect(jsonPath("$.roleChanges.recentEvents[0].outcome").value(AuditEntry.OUTCOME_BLOCKED))
                .andExpect(jsonPath("$.roleChanges.recentEvents[0].hasReason").value(true))
                .andExpect(jsonPath("$.roleChanges.recentEvents[0].reason").doesNotExist())
                .andExpect(jsonPath("$.roleChanges.recentEvents[1].action").value(AuditEntry.ACTION_PAPEL_CONCEDIDO))
                .andExpect(jsonPath("$.roleChanges.recentEvents[1].outcome").value(AuditEntry.OUTCOME_PERMITTED))
                .andExpect(jsonPath("$.roleChanges.recentEvents[1].targetNickname").value("alvo_operacao"))
                // Bloco 2: Registros de matrícula recusados
                .andExpect(jsonPath("$.rejectedEnrollmentsCount").exists())
                .andExpect(jsonPath("$.rejectedEnrollmentsCount.duplicateCount").value(2))
                .andExpect(jsonPath("$.rejectedEnrollmentsCount.attemptLimitCount").value(1))
                .andExpect(jsonPath("$.rejectedEnrollmentsCount.totalCount").value(3))
                // Bloco 3: Correções administrativas de matrícula
                .andExpect(jsonPath("$.administrativeCorrections").exists())
                .andExpect(jsonPath("$.administrativeCorrections.recentEvents").isArray())
                .andExpect(jsonPath("$.administrativeCorrections.recentEvents[0].action").value(AuditEntry.ACTION_CORRECAO_MATRICULA))
                .andExpect(jsonPath("$.administrativeCorrections.recentEvents[0].outcome").value(AuditEntry.OUTCOME_PERMITTED))
                .andExpect(jsonPath("$.administrativeCorrections.recentEvents[0].hasReason").value(true))
                .andExpect(jsonPath("$.administrativeCorrections.recentEvents[0].reason").value(fullCorrectionReason))
                .andExpect(jsonPath("$.administrativeCorrections.recentEvents[0].targetNickname").value("alvo_operacao"))
                // Bloco 4: Contas sem matrícula registrada
                .andExpect(jsonPath("$.unregisteredEnrollmentsCount").exists())
                .andExpect(jsonPath("$.unregisteredEnrollmentsCount.count").value(4))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .doesNotContain(privateRoleChangeReason));
    }

    @Test
    @DisplayName("Administrador de Competição recebe APENAS o bloco de contas sem matrícula e NENHUM outro bloco")
    void competitionAdminReceivesOnlyUnregisteredEnrollmentsBlock() throws Exception {
        // Grava eventos de todos os tipos
        auditRecorder.record(AuditEntry.builder()
                .actorIdentityId(adminIdentityId)
                .actorRole("ADMINISTRADOR_GERAL")
                .targetIdentityId(targetUserIdentityId)
                .action(AuditEntry.ACTION_PAPEL_CONCEDIDO)
                .outcome(AuditEntry.OUTCOME_PERMITTED)
                .build());

        auditRecorder.record(AuditEntry.builder()
                .actorIdentityId(regularUserIdentityId)
                .targetIdentityId(regularUserIdentityId)
                .action(AuditEntry.ACTION_REGISTRO_MATRICULA)
                .outcome(AuditEntry.OUTCOME_BLOCKED)
                .reason("Conta já possui matrícula ativa")
                .build());

        auditRecorder.record(AuditEntry.builder()
                .actorIdentityId(adminIdentityId)
                .actorRole("ADMINISTRADOR_GERAL")
                .targetIdentityId(targetUserIdentityId)
                .action(AuditEntry.ACTION_CORRECAO_MATRICULA)
                .outcome(AuditEntry.OUTCOME_PERMITTED)
                .reason("Motivo de teste")
                .build());

        mockMvc.perform(get(PANEL_PATH)
                        .with(oidcPrincipal(ISSUER, "comp-admin-sub"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queriedAt").isNotEmpty())
                .andExpect(jsonPath("$.timeWindow").exists())
                // Apenas bloco de contas sem matrícula
                .andExpect(jsonPath("$.unregisteredEnrollmentsCount").exists())
                .andExpect(jsonPath("$.unregisteredEnrollmentsCount.count").value(5))
                // Demais blocos deliberadamente ausentes no servidor
                .andExpect(jsonPath("$.roleChanges").doesNotExist())
                .andExpect(jsonPath("$.rejectedEnrollmentsCount").doesNotExist())
                .andExpect(jsonPath("$.administrativeCorrections").doesNotExist());
    }

    @Test
    @DisplayName("Moderador recebe resposta válida 200 com conjunto vazio de blocos e NÃO erro ou recusa 403")
    void moderatorReceivesSuccessfulResponseWithEmptyScopeNotForbidden() throws Exception {
        auditRecorder.record(AuditEntry.builder()
                .actorIdentityId(adminIdentityId)
                .actorRole("ADMINISTRADOR_GERAL")
                .targetIdentityId(targetUserIdentityId)
                .action(AuditEntry.ACTION_PAPEL_CONCEDIDO)
                .outcome(AuditEntry.OUTCOME_PERMITTED)
                .build());

        mockMvc.perform(get(PANEL_PATH)
                        .with(oidcPrincipal(ISSUER, "moderator-sub"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queriedAt").isNotEmpty())
                .andExpect(jsonPath("$.timeWindow").exists())
                .andExpect(jsonPath("$.roleChanges").doesNotExist())
                .andExpect(jsonPath("$.rejectedEnrollmentsCount").doesNotExist())
                .andExpect(jsonPath("$.administrativeCorrections").doesNotExist())
                .andExpect(jsonPath("$.unregisteredEnrollmentsCount").doesNotExist());
    }

    @Test
    @DisplayName("A resposta do painel jamais contém e-mail, matrícula, digest, HMAC, senha, token ou lista de pessoas")
    void responseNeverContainsSensitiveOrPrivateData() throws Exception {
        auditRecorder.record(AuditEntry.builder()
                .actorIdentityId(adminIdentityId)
                .actorRole("ADMINISTRADOR_GERAL")
                .targetIdentityId(targetUserIdentityId)
                .action(AuditEntry.ACTION_PAPEL_CONCEDIDO)
                .outcome(AuditEntry.OUTCOME_PERMITTED)
                .previousValue("secret-prev-val")
                .newValue("secret-new-val")
                .idempotencyKey("idemp-key-12345")
                .correlationId("corr-98765")
                .build());

        auditRecorder.record(AuditEntry.builder()
                .actorIdentityId(regularUserIdentityId)
                .targetIdentityId(regularUserIdentityId)
                .action(AuditEntry.ACTION_REGISTRO_MATRICULA)
                .outcome(AuditEntry.OUTCOME_BLOCKED)
                .reason("Conta já possui matrícula ativa")
                .previousValue("raw-matricula-12345")
                .newValue("hmac-secret-digest")
                .correlationId("corr-11111")
                .build());

        MvcResult result = mockMvc.perform(get(PANEL_PATH)
                        .with(oidcPrincipal(ISSUER, "admin-sub"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();

        assertThat(body)
                .doesNotContain("secret-prev-val")
                .doesNotContain("secret-new-val")
                .doesNotContain("idemp-key-12345")
                .doesNotContain("raw-matricula-12345")
                .doesNotContain("hmac-secret-digest")
                .doesNotContain("email")
                .doesNotContain("matricula")
                .doesNotContain("hmac")
                .doesNotContain("digest")
                .doesNotContain("password")
                .doesNotContain("token");
    }

    @Test
    @DisplayName("Usuário comum autenticado sem papel operacional é barrado com 403 Forbidden")
    void regularUserWithoutOperationalRoleGetsForbidden() throws Exception {
        mockMvc.perform(get(PANEL_PATH)
                        .with(oidcPrincipal(ISSUER, "regular-sub"))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Requisição anônima com Accept application/json recebe 401 Unauthorized")
    void anonymousRequestGetsUnauthorized() throws Exception {
        mockMvc.perform(get(PANEL_PATH)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private UUID createIdentity(String issuer, String subject, String nickname) {
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

    private void grantRole(UUID identityId, String roleName, UUID scopeRef) {
        if (scopeRef == null) {
            jdbcTemplate.update(
                    "INSERT INTO public.operational_role_grant (identity_id, role_name, status)"
                            + " VALUES (?, ?, 'VIGENTE') ON CONFLICT DO NOTHING",
                    identityId, roleName);
        } else {
            jdbcTemplate.update(
                    "INSERT INTO public.operational_role_grant (identity_id, role_name, scope_ref, status)"
                            + " VALUES (?, ?, ?, 'VIGENTE') ON CONFLICT DO NOTHING",
                    identityId, roleName, scopeRef);
        }
    }

    private void createActiveEnrollment(UUID identityId, byte[] matriculaHmac, short version) {
        jdbcTemplate.update(
                "INSERT INTO public.ufu_enrollment (identity_id, matricula_hmac, hmac_version, status) VALUES (?, ?, ?, 'ATIVO')",
                identityId, matriculaHmac, version);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor oidcPrincipal(
            String issuer, String subject) {
        Map<String, Object> claims = Map.of(IdTokenClaimNames.ISS, issuer, IdTokenClaimNames.SUB, subject);
        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken("panel-it-token", now, now.plusSeconds(3600), claims);

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
            ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
                    .clientId("betufu-panel-it")
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
                    .clientName("BETUFU Panel IT")
                    .build();

            return new InMemoryClientRegistrationRepository(registration);
        }
    }
}
