package br.com.betufu.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import br.com.betufu.verification.internal.MatriculaHmac;
import br.com.betufu.verification.internal.MatriculaNormalizer;
import br.com.betufu.verification.internal.UfuEnrollment;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Prova de integração da rotação multi-versão da chave HMAC de matrícula (D-34, D-35, VERI-07).
 *
 * <p>Comprova que quando uma nova versão de chave é promovida (ex.: migração de v1 para v2):
 * <ul>
 *   <li>Matrícula previamente vinculada sob v1 permanece ativa e bloqueia novas tentativas sob v2 (D-35, VERI-07);</li>
 *   <li>Novas matrículas registradas após a rotação são gravadas com digest de v2 e versão numérica 2;</li>
 *   <li>Vínculos sob diferentes versões coexistem e são protegidos simultaneamente contra duplicidade;</li>
 *   <li>A consulta de candidatos abrange todas as versões ativas configuradas, impedindo falso negativo;</li>
 *   <li>Se um vínculo sob v1 for liberado (LIBERADO), a mesma matrícula pode ser vinculada sob v2 por outra conta (D-32).</li>
 * </ul>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "spring.docker.compose.enabled=false",
            "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
            "BETUFU_OIDC_CLIENT_ID=betufu-enrollment-rotation-it",
            "BETUFU_OIDC_CLIENT_SECRET=unused",
            "betufu.verification.matricula-hmac.current-version=v2",
            "betufu.verification.matricula-hmac.keys.v1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE=",
            "betufu.verification.matricula-hmac.keys.v2=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDI=",
            "BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v2",
            "BETUFU_MATRICULA_HMAC_KEY_V1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE=",
            "BETUFU_MATRICULA_HMAC_KEY_V2=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDI="
        })
@ActiveProfiles("integrated")
@Testcontainers
@Import(UfuEnrollmentRotationIT.TestOidcConfiguration.class)
class UfuEnrollmentRotationIT {

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

    @Autowired
    private EnrollmentService enrollmentService;

    @Autowired
    private MatriculaHmac matriculaHmac;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        jdbcTemplate.execute(
                "TRUNCATE TABLE public.audit_event, public.ufu_enrollment, public.betufu_profile, public.local_identity CASCADE");
        var connection = redisTemplate.getConnectionFactory().getConnection();
        connection.serverCommands().flushDb();

        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .addFilters(correlationIdFilter, springSecurityFilterChain)
                .build();
    }

    @Test
    @DisplayName("D-35 / VERI-07: Matrícula registrada sob v1 é recusada com 409 quando tentada por outra conta após rotação para v2")
    void sameMatriculaRegisteredUnderV1IsRejectedWhenAttemptedUnderV2ByDifferentAccount() throws Exception {
        String issuer = "https://idp.invalid";
        String user1Subject = "rotation-user-v1";
        String user2Subject = "rotation-user-v2-attempt";

        // 1. Cria identidade do Usuário 1 e vincula matrícula sob a versão de chave v1 (legada)
        UUID user1IdentityId = createLocalIdentity(issuer, user1Subject);
        String sampleMatricula = "20241BCC001";
        String normalizedMatricula = MatriculaNormalizer.normalize(sampleMatricula);
        byte[] v1Digest = matriculaHmac.calculate(normalizedMatricula, "v1");

        insertEnrollment(user1IdentityId, v1Digest, (short) 1, UfuEnrollment.STATUS_ATIVO);

        UUID user1EnrollmentId = jdbcTemplate.queryForObject(
                "SELECT enrollment_id FROM public.ufu_enrollment WHERE identity_id = ?",
                UUID.class,
                user1IdentityId);
        assertThat(user1EnrollmentId).isNotNull();

        // 2. Usuário 2 tenta registrar exatamente a mesma matrícula (com variação de formatação)
        // Sob a configuração corrente (v2), o serviço calcula candidatos [v1, v2] e encontra colisão no v1
        mockMvc.perform(put(ENROLLMENT_ME_PATH)
                        .with(oidcPrincipal(issuer, user2Subject))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matricula\":\" 2024-1.bcc.001 \"}"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("BETUFU-409-MATRICULA_DUPLICADA"))
                .andExpect(jsonPath("$.title").value("Matrícula já registrada"))
                .andExpect(jsonPath("$.detail").value("Esta matrícula já está registrada em outra conta BETUFU."))
                .andExpect(jsonPath("$.action").value(
                        "Se ela é sua, fale com um administrador geral para pedir a correção."));

        // 3. Prova que o vínculo do Usuário 1 permanece ativo e intacto sob v1
        Long totalActive = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.ufu_enrollment WHERE status = 'ATIVO'", Long.class);
        assertThat(totalActive).isEqualTo(1L);

        Long totalRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.ufu_enrollment", Long.class);
        assertThat(totalRows).isEqualTo(1L);

        Map<String, Object> activeRow = jdbcTemplate.queryForMap(
                "SELECT * FROM public.ufu_enrollment WHERE identity_id = ?", user1IdentityId);
        assertThat(activeRow.get("status")).isEqualTo("ATIVO");
        assertThat(((Number) activeRow.get("hmac_version")).shortValue()).isEqualTo((short) 1);
        assertThat((byte[]) activeRow.get("matricula_hmac")).isEqualTo(v1Digest);

        // 4. Usuário 1 lê seu status normalmente via GET /api/v1/enrollment/me
        mockMvc.perform(get(ENROLLMENT_ME_PATH)
                        .with(oidcPrincipal(issuer, user1Subject)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ATIVO"))
                .andExpect(jsonPath("$.createdAt").value(notNullValue()));

        // 5. Prova auditoria do bloqueio para o Usuário 2 com metadados seguros do vínculo conflitante
        UUID user2IdentityId = jdbcTemplate.queryForObject(
                "SELECT identity_id FROM public.local_identity WHERE issuer = ? AND subject = ?",
                UUID.class,
                issuer,
                user2Subject);

        List<AuditEvent> auditEvents = auditEventRepository.findAll();
        assertThat(auditEvents).hasSize(1);

        AuditEvent blockedEvent = auditEvents.getFirst();
        assertThat(blockedEvent.getAction()).isEqualTo("REGISTRO_MATRICULA");
        assertThat(blockedEvent.getOutcome()).isEqualTo("BLOQUEADO");
        assertThat(blockedEvent.getActorIdentityId()).isEqualTo(user2IdentityId);
        assertThat(blockedEvent.getTargetIdentityId()).isEqualTo(user2IdentityId);
        assertThat(blockedEvent.getCorrelationId()).isNotNull().isNotBlank();
        assertThat(blockedEvent.getNewValue()).isEqualTo("enrollmentId=" + user1EnrollmentId + ";keyVersion=1");
        assertThat(blockedEvent.getReason()).isEqualTo("Matrícula já registrada em outra conta");
        assertThat(blockedEvent.getNewValue()).doesNotContain("20241BCC001", "2024-1.bcc.001");
        assertThat(blockedEvent.getReason()).doesNotContain("20241BCC001", "2024-1.bcc.001");
    }

    @Test
    @DisplayName("D-34: Novo cadastro após rotação utiliza versão corrente v2 e persiste hmac_version = 2 com digest de 32 bytes")
    void newMatriculaRegisteredAfterRotationUsesCurrentV2KeyAndPersistsVersion2() throws Exception {
        String issuer = "https://idp.invalid";
        String userSubject = "rotation-new-user-v2";
        String matricula = "20242BCC002";

        // 1. Registra nova matrícula sob a versão corrente v2
        mockMvc.perform(put(ENROLLMENT_ME_PATH)
                        .with(oidcPrincipal(issuer, userSubject))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matricula\":\"" + matricula + "\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", aMapWithSize(2)))
                .andExpect(jsonPath("$.status").value("ATIVO"))
                .andExpect(jsonPath("$.createdAt").value(notNullValue()));

        // 2. Prova no banco que hmac_version é 2 e o digest bate com o cálculo de v2
        UUID identityId = jdbcTemplate.queryForObject(
                "SELECT identity_id FROM public.local_identity WHERE issuer = ? AND subject = ?",
                UUID.class,
                issuer,
                userSubject);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM public.ufu_enrollment WHERE identity_id = ?", identityId);
        assertThat(row.get("status")).isEqualTo("ATIVO");
        assertThat(((Number) row.get("hmac_version")).shortValue()).isEqualTo((short) 2);

        byte[] expectedV2Digest = matriculaHmac.calculateCurrent(MatriculaNormalizer.normalize(matricula));
        byte[] persistedDigest = (byte[]) row.get("matricula_hmac");
        assertThat(persistedDigest).isEqualTo(expectedV2Digest);
        assertThat(persistedDigest).hasSize(32);

        // 3. Prova auditoria com keyVersion=2
        UUID enrollmentId = (UUID) row.get("enrollment_id");
        List<AuditEvent> auditEvents = auditEventRepository.findAll();
        assertThat(auditEvents).hasSize(1);
        AuditEvent permitted = auditEvents.getFirst();
        assertThat(permitted.getAction()).isEqualTo("REGISTRO_MATRICULA");
        assertThat(permitted.getOutcome()).isEqualTo("PERMITIDO");
        assertThat(permitted.getNewValue()).isEqualTo("enrollmentId=" + enrollmentId + ";keyVersion=2");

        // 4. Tentativa de re-registro da mesma matrícula por outra conta também é recusada (sob v2)
        String otherUserSubject = "rotation-user-v2-duplicate";
        mockMvc.perform(put(ENROLLMENT_ME_PATH)
                        .with(oidcPrincipal(issuer, otherUserSubject))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matricula\":\"" + matricula + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BETUFU-409-MATRICULA_DUPLICADA"));
    }

    @Test
    @DisplayName("D-35: Vínculos ativos sob v1 e v2 coexistem no banco e ambos bloqueiam duplicidade multi-versão")
    void multiVersionSearchCoexistsBothV1AndV2ActiveEnrollmentsSimultaneously() throws Exception {
        String issuer = "https://idp.invalid";
        String userV1 = "user-coexist-v1";
        String userV2 = "user-coexist-v2";
        String userV3 = "user-coexist-v3";

        String matriculaA = "20241BCC010";
        String matriculaB = "20241BCC020";

        // 1. Cria Usuário 1 com Matrícula A sob v1
        UUID identityV1 = createLocalIdentity(issuer, userV1);
        byte[] v1DigestA = matriculaHmac.calculate(MatriculaNormalizer.normalize(matriculaA), "v1");
        insertEnrollment(identityV1, v1DigestA, (short) 1, UfuEnrollment.STATUS_ATIVO);

        // 2. Cria Usuário 2 com Matrícula B sob v2 via endpoint HTTP
        mockMvc.perform(put(ENROLLMENT_ME_PATH)
                        .with(oidcPrincipal(issuer, userV2))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matricula\":\"" + matriculaB + "\"}"))
                .andExpect(status().isOk());

        // 3. Prova que o banco possui 2 vínculos ATIVOS: um sob v1 e um sob v2
        Long totalActive = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.ufu_enrollment WHERE status = 'ATIVO'", Long.class);
        assertThat(totalActive).isEqualTo(2L);

        // 4. Usuário 3 tenta Matrícula A -> recusa 409 (encontra no v1)
        mockMvc.perform(put(ENROLLMENT_ME_PATH)
                        .with(oidcPrincipal(issuer, userV3))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matricula\":\"" + matriculaA + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BETUFU-409-MATRICULA_DUPLICADA"));

        // 5. Usuário 3 tenta Matrícula B -> recusa 409 (encontra no v2)
        mockMvc.perform(put(ENROLLMENT_ME_PATH)
                        .with(oidcPrincipal(issuer, userV3))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matricula\":\"" + matriculaB + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BETUFU-409-MATRICULA_DUPLICADA"));

        // 6. Contagem de registros ativos permanece exatamente 2
        Long finalActive = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.ufu_enrollment WHERE status = 'ATIVO'", Long.class);
        assertThat(finalActive).isEqualTo(2L);
    }

    @Test
    @DisplayName("D-32: Vínculo v1 com status LIBERADO permite novo cadastro da mesma matrícula sob chave v2 por outra conta")
    void releasedV1EnrollmentAllowsNewRegistrationUnderV2Key() throws Exception {
        String issuer = "https://idp.invalid";
        String originalUser = "user-v1-released";
        String newUser = "user-v2-rebound";
        String matricula = "20241BCC030";

        // 1. Cria vínculo v1 já liberado (LIBERADO) para o usuário original
        UUID origIdentityId = createLocalIdentity(issuer, originalUser);
        byte[] v1Digest = matriculaHmac.calculate(MatriculaNormalizer.normalize(matricula), "v1");
        insertEnrollment(origIdentityId, v1Digest, (short) 1, UfuEnrollment.STATUS_LIBERADO);

        // 2. Novo usuário registra a mesma matrícula sob chave corrente v2 com sucesso
        mockMvc.perform(put(ENROLLMENT_ME_PATH)
                        .with(oidcPrincipal(issuer, newUser))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"matricula\":\"" + matricula + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ATIVO"));

        // 3. Prova que existem 2 linhas para a mesma matrícula lógica: 1 LIBERADO (v1) e 1 ATIVO (v2)
        Long totalRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.ufu_enrollment", Long.class);
        assertThat(totalRows).isEqualTo(2L);

        Long totalActive = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.ufu_enrollment WHERE status = 'ATIVO'", Long.class);
        assertThat(totalActive).isEqualTo(1L);

        UUID newIdentityId = jdbcTemplate.queryForObject(
                "SELECT identity_id FROM public.local_identity WHERE issuer = ? AND subject = ?",
                UUID.class,
                issuer,
                newUser);

        Map<String, Object> activeRow = jdbcTemplate.queryForMap(
                "SELECT * FROM public.ufu_enrollment WHERE identity_id = ?", newIdentityId);
        assertThat(activeRow.get("status")).isEqualTo("ATIVO");
        assertThat(((Number) activeRow.get("hmac_version")).shortValue()).isEqualTo((short) 2);
    }

    @Test
    @DisplayName("D-35: Consulta de candidatos calcula digests para todas as versões ativas v1 e v2")
    void candidateDigestsCalculationCoversAllActiveVersions() {
        String matricula = "20241BCC040";
        String normalized = MatriculaNormalizer.normalize(matricula);

        byte[] d1 = matriculaHmac.calculate(normalized, "v1");
        byte[] d2 = matriculaHmac.calculate(normalized, "v2");

        // Os digests de chaves distintas para a mesma matrícula são diferentes
        assertThat(d1).isNotEqualTo(d2);

        // A versão corrente é v2
        assertThat(matriculaHmac.currentVersion()).isEqualTo("v2");
        assertThat(matriculaHmac.calculateCurrent(normalized)).isEqualTo(d2);

        // Inserção com d1 é encontrada quando buscamos com [d1, d2]
        UUID identity = createLocalIdentity("https://idp.invalid", "candidate-probe-user");
        insertEnrollment(identity, d1, (short) 1, UfuEnrollment.STATUS_ATIVO);

        List<UfuEnrollment> found = enrollmentRepository.findActiveByMatriculaHmacIn(List.of(d1, d2));
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().getMatriculaHmac()).isEqualTo(d1);

        // Se a busca fosse reduzida apenas a d2 (chave corrente), haveria falso negativo (D-35 discriminante)
        List<UfuEnrollment> missed = enrollmentRepository.findActiveByMatriculaHmacIn(List.of(d2));
        assertThat(missed).isEmpty();
    }

    private void insertEnrollment(UUID identityId, byte[] matriculaHmac, short hmacVersion, String status) {
        transactionTemplate.executeWithoutResult(
                s -> enrollmentRepository.insert(identityId, matriculaHmac, hmacVersion, status));
    }

    private UUID createLocalIdentity(String issuer, String subject) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO public.local_identity (identity_id, issuer, subject) VALUES (?, ?, ?)",
                id,
                issuer,
                subject);
        return id;
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
        return OidcIdToken.withTokenValue("rotation-it-" + UUID.randomUUID())
                .issuedAt(now.minusSeconds(60))
                .expiresAt(now.plusSeconds(300))
                .claim(IdTokenClaimNames.ISS, issuer)
                .claim(IdTokenClaimNames.SUB, subject)
                .claim("fixture_name", "rotation-it-user")
                .build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOidcConfiguration {

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
                    .clientId("betufu-enrollment-rotation-it")
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
                    .clientName("BETUFU Enrollment Rotation IT")
                    .build();

            return new InMemoryClientRegistrationRepository(registration);
        }
    }
}
