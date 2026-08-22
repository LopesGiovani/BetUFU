package br.com.betufu.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.betufu.audit.internal.AuditEvent;
import br.com.betufu.audit.internal.AuditEventRepository;
import br.com.betufu.platform.observability.CorrelationIdFilter;
import br.com.betufu.platform.session.RedisContainerSupport;
import br.com.betufu.verification.internal.EnrollmentService;
import br.com.betufu.verification.internal.UfuEnrollmentRepository;
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
            "BETUFU_OIDC_CLIENT_ID=betufu-enrollment-controller-it",
            "BETUFU_OIDC_CLIENT_SECRET=unused",
            "BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1",
            "BETUFU_MATRICULA_HMAC_KEY_V1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE=",
            "BETUFU_MATRICULA_HMAC_KEY_V2=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDI="
        })
@ActiveProfiles("integrated")
@Testcontainers
@Import(EnrollmentControllerIT.TestOidcConfiguration.class)
class EnrollmentControllerIT {

    private static final String ENROLLMENT_ME_PATH = "/api/v1/enrollment/me";

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
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private UfuEnrollmentRepository enrollmentRepository;

    @MockitoSpyBean
    private EnrollmentService enrollmentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE public.audit_event, public.ufu_enrollment, public.betufu_profile, public.local_identity CASCADE");
        var connection = redisTemplate.getConnectionFactory().getConnection();
        connection.serverCommands().flushDb();
        reset(enrollmentService);

        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .addFilters(correlationIdFilter, springSecurityFilterChain)
                .build();
    }

    @Test
    @DisplayName("Requisição não autenticada para GET ou PUT /api/v1/enrollment/me é redirecionada ou recusada")
    void unauthenticatedRequestIsRedirectedOrForbidden() throws Exception {
        mockMvc.perform(get(ENROLLMENT_ME_PATH))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(put(ENROLLMENT_ME_PATH).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matricula\":\"20241BCC001\"}"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("Usuário autenticado sem matrícula registrada lê status NAO_REGISTRADA")
    void authenticatedUserWithoutEnrollmentReadsNotRegisteredStatus() throws Exception {
        String issuer = "https://idp.invalid";
        String subject = "user-no-enrollment";

        mockMvc.perform(get(ENROLLMENT_ME_PATH)
                        .with(oidcPrincipal(issuer, subject)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", aMapWithSize(2)))
                .andExpect(jsonPath("$.status").value("NAO_REGISTRADA"))
                .andExpect(jsonPath("$.createdAt").doesNotExist());
    }

    @Test
    @DisplayName("VERI-06 / AUDT-01: Usuário registra matrícula com sucesso, recebe status ATIVO e evento é auditado")
    void authenticatedUserRegistersMatriculaSuccessfully() throws Exception {
        String issuer = "https://idp.invalid";
        String subject = "user-enrollment-success";

        // 1. Registra a matrícula
        mockMvc.perform(put(ENROLLMENT_ME_PATH)
                        .with(oidcPrincipal(issuer, subject))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matricula\":\" 2024-1.BCC.001 \"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", aMapWithSize(2)))
                .andExpect(jsonPath("$.status").value("ATIVO"))
                .andExpect(jsonPath("$.createdAt").value(notNullValue()));

        // 2. Relê o status via GET
        mockMvc.perform(get(ENROLLMENT_ME_PATH)
                        .with(oidcPrincipal(issuer, subject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(2)))
                .andExpect(jsonPath("$.status").value("ATIVO"))
                .andExpect(jsonPath("$.createdAt").value(notNullValue()));

        // 3. Prova no banco: digest binário de 32 bytes persistido sob versão 1
        UUID identityId = jdbcTemplate.queryForObject(
                "SELECT identity_id FROM public.local_identity WHERE issuer = ? AND subject = ?",
                UUID.class,
                issuer,
                subject);

        Map<String, Object> enrollmentRow = jdbcTemplate.queryForMap(
                "SELECT * FROM public.ufu_enrollment WHERE identity_id = ?", identityId);
        assertThat(enrollmentRow.get("status")).isEqualTo("ATIVO");
        assertThat(((Number) enrollmentRow.get("hmac_version")).shortValue()).isEqualTo((short) 1);
        byte[] hmacBytes = (byte[]) enrollmentRow.get("matricula_hmac");
        assertThat(hmacBytes).hasSize(32);
        UUID enrollmentId = (UUID) enrollmentRow.get("enrollment_id");

        // 4. Prova na auditoria: evento REGISTRO_MATRICULA PERMITIDO registrado com metadados seguros
        List<AuditEvent> auditEvents = auditEventRepository.findAll();
        assertThat(auditEvents).hasSize(1);
        AuditEvent audit = auditEvents.getFirst();
        assertThat(audit.getAction()).isEqualTo("REGISTRO_MATRICULA");
        assertThat(audit.getOutcome()).isEqualTo("PERMITIDO");
        assertThat(audit.getActorIdentityId()).isEqualTo(identityId);
        assertThat(audit.getTargetIdentityId()).isEqualTo(identityId);
        assertThat(audit.getCorrelationId()).isNotNull().isNotBlank();
        assertThat(audit.getNewValue()).isEqualTo("enrollmentId=" + enrollmentId + ";keyVersion=1");
        assertThat(audit.getPreviousValue()).isNull();
        assertThat(audit.getReason()).isNull();
        assertThat(audit.getNewValue()).doesNotContain("2024-1.BCC.001", "20241BCC001");
    }

    @Test
    @DisplayName("Validação Bean Validation: Recusa matrícula vazia e fora do padrão com mensagens literais do UI-SPEC e audita BLOQUEADO")
    void validationRejectsInvalidInputsWithPtBrMessages() throws Exception {
        String issuer = "https://idp.invalid";
        String subject = "user-validation-enrollment";

        // 1. Matrícula em branco
        mockMvc.perform(put(ENROLLMENT_ME_PATH)
                        .with(oidcPrincipal(issuer, subject))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matricula\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'matricula')].message")
                        .value(org.hamcrest.Matchers.hasItem("Informe o número da matrícula.")));

        // 2. Matrícula com símbolos inválidos
        mockMvc.perform(put(ENROLLMENT_ME_PATH)
                        .with(oidcPrincipal(issuer, subject))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matricula\":\"123!@#$\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'matricula')].message")
                        .value(org.hamcrest.Matchers.hasItem(
                                "Este número não corresponde ao formato de matrícula da UFU. Confira e digite novamente.")));

        // 3. Prova que o valor rejeitado não é refletido na resposta de erro (PRIV-01)
        MvcResult errorResult = mockMvc.perform(put(ENROLLMENT_ME_PATH)
                        .with(oidcPrincipal(issuer, subject))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matricula\":\"secret-raw-val-xyz!\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        String responseBody = new String(errorResult.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(responseBody).doesNotContain("secret-raw-val-xyz!");

        // 4. Prova que as 3 validações 400 foram auditadas como BLOQUEADO com correlationId e metadados seguros
        UUID identityId = jdbcTemplate.queryForObject(
                "SELECT identity_id FROM public.local_identity WHERE issuer = ? AND subject = ?",
                UUID.class,
                issuer,
                subject);

        List<AuditEvent> auditEvents = auditEventRepository.findAll();
        assertThat(auditEvents).hasSize(3);
        for (AuditEvent audit : auditEvents) {
            assertThat(audit.getAction()).isEqualTo("REGISTRO_MATRICULA");
            assertThat(audit.getOutcome()).isEqualTo("BLOQUEADO");
            assertThat(audit.getActorIdentityId()).isEqualTo(identityId);
            assertThat(audit.getTargetIdentityId()).isEqualTo(identityId);
            assertThat(audit.getCorrelationId()).isNotNull().isNotBlank();
            assertThat(audit.getNewValue()).isEqualTo("keyVersion=1");
            assertThat(audit.getReason()).isNotNull().isNotBlank();
            assertThat(audit.getNewValue()).doesNotContain("secret-raw-val-xyz!", "123!@#$");
            assertThat(audit.getReason()).doesNotContain("secret-raw-val-xyz!", "123!@#$");
        }
    }

    @Test
    @DisplayName("D-31 / BETUFU-409-CONTA_JA_VINCULADA: Recusa segunda matrícula na mesma conta e audita como BLOQUEADO")
    void rejectsSecondEnrollmentOnSameAccount() throws Exception {
        String issuer = "https://idp.invalid";
        String subject = "user-already-enrolled";

        // 1. Primeiro registro sucede
        mockMvc.perform(put(ENROLLMENT_ME_PATH)
                        .with(oidcPrincipal(issuer, subject))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matricula\":\"20241BCC001\"}"))
                .andExpect(status().isOk());

        // 2. Segunda tentativa na mesma conta retorna 409 CONTA_JA_VINCULADA
        mockMvc.perform(put(ENROLLMENT_ME_PATH)
                        .with(oidcPrincipal(issuer, subject))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matricula\":\"20241BCC002\"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BETUFU-409-CONTA_JA_VINCULADA"))
                .andExpect(jsonPath("$.title").value("Conta já possui matrícula"))
                .andExpect(jsonPath("$.detail").value("Sua conta já tem uma matrícula registrada."))
                .andExpect(jsonPath("$.action").value("Só um administrador geral pode alterá-la."));

        // 3. Prova auditoria do bloqueio com metadados seguros e vínculo existente
        UUID identityId = jdbcTemplate.queryForObject(
                "SELECT identity_id FROM public.local_identity WHERE issuer = ? AND subject = ?",
                UUID.class,
                issuer,
                subject);
        UUID enrollmentId = jdbcTemplate.queryForObject(
                "SELECT enrollment_id FROM public.ufu_enrollment WHERE identity_id = ?",
                UUID.class,
                identityId);

        List<AuditEvent> auditEvents = auditEventRepository.findAll();
        assertThat(auditEvents).hasSize(2);

        AuditEvent permittedEvent = auditEvents.get(0);
        assertThat(permittedEvent.getAction()).isEqualTo("REGISTRO_MATRICULA");
        assertThat(permittedEvent.getOutcome()).isEqualTo("PERMITIDO");
        assertThat(permittedEvent.getNewValue()).isEqualTo("enrollmentId=" + enrollmentId + ";keyVersion=1");

        AuditEvent blockedEvent = auditEvents.get(1);
        assertThat(blockedEvent.getAction()).isEqualTo("REGISTRO_MATRICULA");
        assertThat(blockedEvent.getOutcome()).isEqualTo("BLOQUEADO");
        assertThat(blockedEvent.getCorrelationId()).isNotNull().isNotBlank();
        assertThat(blockedEvent.getNewValue()).isEqualTo("enrollmentId=" + enrollmentId + ";keyVersion=1");
        assertThat(blockedEvent.getNewValue()).doesNotContain("20241BCC001", "20241BCC002");
        assertThat(blockedEvent.getReason()).doesNotContain("20241BCC001", "20241BCC002");
    }

    @Test
    @DisplayName("VERI-07 / D-35: Recusa matrícula já registrada em outra conta sob verificação multi-versão e audita como BLOQUEADO")
    void rejectsDuplicateMatriculaAcrossAccountsUnderMultiVersionHmac() throws Exception {
        String issuer = "https://idp.invalid";
        String subject1 = "user-account-1";
        String subject2 = "user-account-2";

        // 1. Conta 1 registra a matrícula
        mockMvc.perform(put(ENROLLMENT_ME_PATH)
                        .with(oidcPrincipal(issuer, subject1))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matricula\":\"20241BCC999\"}"))
                .andExpect(status().isOk());

        // 2. Conta 2 tenta registrar a mesma matrícula (com formato variante) -> 409 MATRICULA_DUPLICADA
        mockMvc.perform(put(ENROLLMENT_ME_PATH)
                        .with(oidcPrincipal(issuer, subject2))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matricula\":\" 2024-1.bcc.999 \"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BETUFU-409-MATRICULA_DUPLICADA"))
                .andExpect(jsonPath("$.title").value("Matrícula já registrada"))
                .andExpect(jsonPath("$.detail").value("Esta matrícula já está registrada em outra conta BETUFU."))
                .andExpect(jsonPath("$.action").value(
                        "Se ela é sua, fale com um administrador geral para pedir a correção."));

        // 3. Prova auditoria do bloqueio com identificador do vínculo e versão da chave
        UUID identity1 = jdbcTemplate.queryForObject(
                "SELECT identity_id FROM public.local_identity WHERE issuer = ? AND subject = ?",
                UUID.class,
                issuer,
                subject1);
        UUID identity2 = jdbcTemplate.queryForObject(
                "SELECT identity_id FROM public.local_identity WHERE issuer = ? AND subject = ?",
                UUID.class,
                issuer,
                subject2);
        UUID account1EnrollmentId = jdbcTemplate.queryForObject(
                "SELECT enrollment_id FROM public.ufu_enrollment WHERE identity_id = ?",
                UUID.class,
                identity1);

        List<AuditEvent> auditEvents = auditEventRepository.findAll();
        assertThat(auditEvents).hasSize(2);

        AuditEvent permittedEvent = auditEvents.get(0);
        assertThat(permittedEvent.getAction()).isEqualTo("REGISTRO_MATRICULA");
        assertThat(permittedEvent.getOutcome()).isEqualTo("PERMITIDO");
        assertThat(permittedEvent.getActorIdentityId()).isEqualTo(identity1);
        assertThat(permittedEvent.getNewValue()).isEqualTo("enrollmentId=" + account1EnrollmentId + ";keyVersion=1");

        AuditEvent blockedEvent = auditEvents.get(1);
        assertThat(blockedEvent.getAction()).isEqualTo("REGISTRO_MATRICULA");
        assertThat(blockedEvent.getOutcome()).isEqualTo("BLOQUEADO");
        assertThat(blockedEvent.getActorIdentityId()).isEqualTo(identity2);
        assertThat(blockedEvent.getCorrelationId()).isNotNull().isNotBlank();
        assertThat(blockedEvent.getNewValue()).isEqualTo("enrollmentId=" + account1EnrollmentId + ";keyVersion=1");
        assertThat(blockedEvent.getNewValue()).doesNotContain("20241BCC999", "2024-1.bcc.999");
        assertThat(blockedEvent.getReason()).doesNotContain("20241BCC999", "2024-1.bcc.999");
    }

    @Test
    @DisplayName("D-30 / BETUFU-429-TOO_MANY_ATTEMPTS: Bloqueia ao atingir limite de tentativas sem vazar contagem ou janela e audita BLOQUEADO")
    void rejectsWhenAttemptLimitIsExceededWithoutLeakingCounters() throws Exception {
        String issuer = "https://idp.invalid";
        String subject = "user-rate-limited";

        // Executa 5 tentativas inválidas que consomem o limite
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(put(ENROLLMENT_ME_PATH)
                            .with(oidcPrincipal(issuer, subject))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"matricula\":\"invalid!\"}"))
                    .andExpect(status().isBadRequest());
        }

        // 6a tentativa deve retornar 429 Too Many Attempts
        mockMvc.perform(put(ENROLLMENT_ME_PATH)
                        .with(oidcPrincipal(issuer, subject))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matricula\":\"20241BCC001\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BETUFU-429-TOO_MANY_ATTEMPTS"))
                .andExpect(jsonPath("$.title").value("Muitas tentativas"))
                .andExpect(jsonPath("$.detail").value("Muitas tentativas de registro."))
                .andExpect(jsonPath("$.action").value("Aguarde alguns minutos e tente novamente."))
                // Ausência estrutural de cabeçalhos vazadores
                .andExpect(header().doesNotExist("X-RateLimit-Remaining"))
                .andExpect(header().doesNotExist("X-RateLimit-Limit"))
                .andExpect(header().doesNotExist("X-RateLimit-Reset"))
                .andExpect(header().doesNotExist("Retry-After"));

        // 7a tentativa com formato inválido também retorna 429 porque a cota já foi excedida
        mockMvc.perform(put(ENROLLMENT_ME_PATH)
                        .with(oidcPrincipal(issuer, subject))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matricula\":\"invalid!\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("BETUFU-429-TOO_MANY_ATTEMPTS"));

        // Prova auditoria: 5 tentativas 400 + 2 tentativas 429 = 7 eventos BLOQUEADO com correlationId
        UUID identityId = jdbcTemplate.queryForObject(
                "SELECT identity_id FROM public.local_identity WHERE issuer = ? AND subject = ?",
                UUID.class,
                issuer,
                subject);

        List<AuditEvent> auditEvents = auditEventRepository.findAll();
        assertThat(auditEvents).hasSize(7);

        for (AuditEvent audit : auditEvents) {
            assertThat(audit.getAction()).isEqualTo("REGISTRO_MATRICULA");
            assertThat(audit.getOutcome()).isEqualTo("BLOQUEADO");
            assertThat(audit.getActorIdentityId()).isEqualTo(identityId);
            assertThat(audit.getTargetIdentityId()).isEqualTo(identityId);
            assertThat(audit.getCorrelationId()).isNotNull().isNotBlank();
            assertThat(audit.getNewValue()).isEqualTo("keyVersion=1");
            assertThat(audit.getReason()).isNotNull().isNotBlank();
            assertThat(audit.getNewValue()).doesNotContain("invalid!", "20241BCC001");
            assertThat(audit.getReason()).doesNotContain("invalid!", "20241BCC001");
        }

        AuditEvent rateLimitedAudit = auditEvents.get(5);
        assertThat(rateLimitedAudit.getReason()).contains("Limite de tentativas");
    }

    @Test
    @DisplayName("PRIV-01: Resposta do endpoint não vaza matrícula, HMAC, digest, e-mail ou versão")
    void responseDoesNotExposeForbiddenFields() throws Exception {
        String issuer = "https://idp.invalid";
        String subject = "user-allowlist-check";

        mockMvc.perform(put(ENROLLMENT_ME_PATH)
                        .with(oidcPrincipal(issuer, subject))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matricula\":\"20241BCC777\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(2)))
                .andExpect(jsonPath("$.status").value("ATIVO"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.matricula").doesNotExist())
                .andExpect(jsonPath("$.rawMatricula").doesNotExist())
                .andExpect(jsonPath("$.hmac").doesNotExist())
                .andExpect(jsonPath("$.digest").doesNotExist())
                .andExpect(jsonPath("$.matriculaHmac").doesNotExist())
                .andExpect(jsonPath("$.hmacVersion").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    @DisplayName("Parâmetros de consulta e cabeçalhos não podem falsificar identidade")
    void queryAndHeadersCannotSpoofIdentity() throws Exception {
        String authenticatedIssuer = "https://idp.invalid";
        String authenticatedSubject = "legitimate-enrollment-user";
        UUID spoofedIdentityId = UUID.randomUUID();

        mockMvc.perform(put(ENROLLMENT_ME_PATH)
                        .with(oidcPrincipal(authenticatedIssuer, authenticatedSubject))
                        .with(csrf())
                        .queryParam("identityId", spoofedIdentityId.toString())
                        .header("identityId", spoofedIdentityId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matricula\":\"20241BCC888\"}"))
                .andExpect(status().isOk());

        UUID actualIdentityId = jdbcTemplate.queryForObject(
                "SELECT identity_id FROM public.local_identity WHERE issuer = ? AND subject = ?",
                UUID.class,
                authenticatedIssuer,
                authenticatedSubject);

        assertThat(actualIdentityId).isNotNull().isNotEqualTo(spoofedIdentityId);
    }

    @Test
    @DisplayName("Violação de constraint diferente dos índices únicos resulta em erro genérico 500, libera cota e nunca em 409 duplicidade/conta vinculada")
    void unexpectedDatabaseConstraintViolationResultsInGenericErrorAndNotDomainDuplicate() throws Exception {
        String constraintName = "ck_ufu_enrollment_discriminant_reject";
        String issuer = "https://idp.invalid";
        String subject = "user-db-unexpected-constraint";
        try {
            jdbcTemplate.execute("ALTER TABLE public.ufu_enrollment ADD CONSTRAINT " + constraintName
                    + " CHECK (false) NOT VALID");

            // 1. Provoca a constraint inesperada que resulta em 500
            mockMvc.perform(put(ENROLLMENT_ME_PATH)
                            .with(oidcPrincipal(issuer, subject))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"matricula\":\"20241BCC555\"}"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("BETUFU-500-INTERNAL"))
                    .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not("BETUFU-409-MATRICULA_DUPLICADA")))
                    .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not("BETUFU-409-CONTA_JA_VINCULADA")));
        } finally {
            jdbcTemplate.execute("ALTER TABLE public.ufu_enrollment DROP CONSTRAINT IF EXISTS " + constraintName);
        }

        // 2. Confirma que a cota não foi consumida pelo 500: executa 5 tentativas inválidas que retornam 400
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(put(ENROLLMENT_ME_PATH)
                            .with(oidcPrincipal(issuer, subject))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"matricula\":\"invalid!\"}"))
                    .andExpect(status().isBadRequest());
        }

        // 3. A 6a tentativa (e 7a requisição total do usuário) retorna 429, provando que o 500 não consumiu a cota
        mockMvc.perform(put(ENROLLMENT_ME_PATH)
                        .with(oidcPrincipal(issuer, subject))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matricula\":\"20241BCC555\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("BETUFU-429-TOO_MANY_ATTEMPTS"));
    }

    @Test
    @DisplayName("Falha inesperada no caminho de Bean Validation libera a reserva e retorna 500 sanitizado")
    void unexpectedFailureDuringBeanValidationReleasesReservationAndReturnsSanitized500() throws Exception {
        String issuer = "https://idp.invalid";
        String subject = "user-audit-validation-fail";

        try {
            doThrow(new RuntimeException("Simulated audit collaborator failure"))
                    .when(enrollmentService)
                    .auditRejection(any(), any());

            // 1. Falha durante o tratamento de Bean Validation libera a reserva e retorna 500 sanitizado
            mockMvc.perform(put(ENROLLMENT_ME_PATH)
                            .with(oidcPrincipal(issuer, subject))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"matricula\":\"invalid!\"}"))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("BETUFU-500-INTERNAL"));

            verify(enrollmentService).auditRejection(any(UUID.class), any(String.class));
        } finally {
            reset(enrollmentService);
        }

        // 2. Confirma que a cota não foi consumida: executa 5 tentativas inválidas que retornam 400
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(put(ENROLLMENT_ME_PATH)
                            .with(oidcPrincipal(issuer, subject))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"matricula\":\"invalid!\"}"))
                    .andExpect(status().isBadRequest());
        }

        // 3. A 6a tentativa (e 7a requisição total do usuário) retorna 429, provando que a primeira falha liberou a reserva
        mockMvc.perform(put(ENROLLMENT_ME_PATH)
                        .with(oidcPrincipal(issuer, subject))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matricula\":\"20241BCC001\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("BETUFU-429-TOO_MANY_ATTEMPTS"));
    }

    @Test
    @DisplayName("Parse de versão HMAC: aceita estritamente v[1-9][0-9]* dentro do SMALLINT positivo e falha fechado antes de gravar")
    void strictlyParsesHmacVersionFormatFailClosed() {
        // Válidos
        assertThat(EnrollmentService.parseHmacVersion("v1")).isEqualTo((short) 1);
        assertThat(EnrollmentService.parseHmacVersion("v2")).isEqualTo((short) 2);
        assertThat(EnrollmentService.parseHmacVersion("v100")).isEqualTo((short) 100);
        assertThat(EnrollmentService.parseHmacVersion("v32767")).isEqualTo((short) 32767);

        // Inválidos: nulo, vazio, sem 'v', v0, v01, espaços antes/depois, tab, newline, negativo, letras, overflow de SMALLINT (32768)
        assertThatThrownBy(() -> EnrollmentService.parseHmacVersion(null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EnrollmentService.parseHmacVersion(""))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EnrollmentService.parseHmacVersion("1"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EnrollmentService.parseHmacVersion("v0"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EnrollmentService.parseHmacVersion("v01"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EnrollmentService.parseHmacVersion(" v1"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EnrollmentService.parseHmacVersion("v1 "))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EnrollmentService.parseHmacVersion("\tv1"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EnrollmentService.parseHmacVersion("v1\t"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EnrollmentService.parseHmacVersion("\nv1"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EnrollmentService.parseHmacVersion("v1\n"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EnrollmentService.parseHmacVersion("v-1"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EnrollmentService.parseHmacVersion("v32768"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> EnrollmentService.parseHmacVersion("version1"))
                .isInstanceOf(IllegalStateException.class);
    }

    private RequestPostProcessor oidcPrincipal(String issuer, String subject) {
        OidcUser oidcUser = new DefaultOidcUser(
                AuthorityUtils.createAuthorityList("OIDC_USER"),
                oidcIdToken(issuer, subject),
                "fixture_name");

        ClientRegistration registration =
                clientRegistrationRepository.findByRegistrationId("keycloak");

        assertThat(registration).isNotNull();

        return oidcLogin()
                .oidcUser(oidcUser)
                .clientRegistration(registration);
    }

    private OidcIdToken oidcIdToken(String issuer, String subject) {
        Instant now = Instant.now();
        return OidcIdToken.withTokenValue("enrollment-it-" + UUID.randomUUID())
                .issuedAt(now.minusSeconds(60))
                .expiresAt(now.plusSeconds(300))
                .claim(IdTokenClaimNames.ISS, issuer)
                .claim(IdTokenClaimNames.SUB, subject)
                .claim("fixture_name", "enrollment-it-user")
                .build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOidcConfiguration {

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
                    .clientId("betufu-enrollment-controller-it")
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
                    .clientName("BETUFU Enrollment Controller IT")
                    .build();

            return new InMemoryClientRegistrationRepository(registration);
        }
    }
}
