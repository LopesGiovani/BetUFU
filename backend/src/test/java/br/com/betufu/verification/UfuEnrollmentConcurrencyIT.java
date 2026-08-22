package br.com.betufu.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import br.com.betufu.audit.internal.AuditEvent;
import br.com.betufu.audit.internal.AuditEventRepository;
import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.observability.CorrelationIdFilter;
import br.com.betufu.platform.session.RedisContainerSupport;
import br.com.betufu.verification.internal.EnrollmentService;
import br.com.betufu.verification.internal.EnrollmentStatusResponse;
import br.com.betufu.verification.internal.UfuEnrollmentRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
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
import org.springframework.http.HttpStatus;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Prova de concorrência e arbitragem do PostgreSQL sob corrida de unicidade (D-31, D-35, VERI-07).
 *
 * <p>Comprova que sob requisições simultâneas:
 * <ul>
 *   <li>Duas contas tentando vincular a mesma matrícula concorrentemente resultam em exatamente 1 vencedor (200 OK) e 1 perdedor (409 Conflict);</li>
 *   <li>A mesma conta tentando registrar matrículas distintas concorrentemente resulta em exatamente 1 vínculo ativo e 1 recusa (409 Conflict);</li>
 *   <li>A requisição perdedora é traduzida pelo serviço e jamais expõe mensagens de erro cruas do PostgreSQL;</li>
 *   <li>O banco de dados mantém exatamente 1 linha ativa e a trilha de auditoria registra adequadamente PERMITIDO e BLOQUEADO;</li>
 *   <li>Sob contenção com múltiplas threads simultâneas, o árbitro do PostgreSQL garante a criação de exatamente 1 vínculo ativo.</li>
 * </ul>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "spring.docker.compose.enabled=false",
            "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
            "BETUFU_OIDC_CLIENT_ID=betufu-enrollment-concurrency-it",
            "BETUFU_OIDC_CLIENT_SECRET=unused",
            "betufu.verification.matricula-hmac.current-version=v1",
            "betufu.verification.matricula-hmac.keys.v1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE=",
            "BETUFU_MATRICULA_HMAC_KEY_CURRENT_VERSION=v1",
            "BETUFU_MATRICULA_HMAC_KEY_V1=dGVzdC1vbmx5LWZhaWwtY2xvc2VkLWtleS0wMDAwMDE="
        })
@ActiveProfiles("integrated")
@Testcontainers
@Import(UfuEnrollmentConcurrencyIT.TestOidcConfiguration.class)
class UfuEnrollmentConcurrencyIT {

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
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE public.audit_event, public.ufu_enrollment, public.betufu_profile, public.local_identity CASCADE");
        var connection = redisTemplate.getConnectionFactory().getConnection();
        connection.serverCommands().flushDb();

        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .addFilters(correlationIdFilter, springSecurityFilterChain)
                .build();
    }

    @Test
    @DisplayName("VERI-07 / Corrida: Duas requisições simultâneas com a mesma matrícula para contas diferentes deixam exatamente 1 linha ativa e perdedora recebe 409 governado")
    void concurrentRegistrationOfSameMatriculaByDifferentAccountsResultsInExactlyOneWinnerAndOne409() throws Exception {
        String issuer = "https://idp.invalid";
        String userA = "race-user-a";
        String userB = "race-user-b";
        String sharedMatricula = "20241BCC501";

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<MvcResult> results = Collections.synchronizedList(new ArrayList<>());

        // Thread 1: Usuário A
        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = mockMvc.perform(put(ENROLLMENT_ME_PATH)
                                .with(oidcPrincipal(issuer, userA))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"matricula\":\"" + sharedMatricula + "\"}"))
                        .andReturn();
                results.add(res);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: Usuário B
        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = mockMvc.perform(put(ENROLLMENT_ME_PATH)
                                .with(oidcPrincipal(issuer, userB))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"matricula\":\"" + sharedMatricula + "\"}"))
                        .andReturn();
                results.add(res);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                doneLatch.countDown();
            }
        });

        // Disparo concorrente
        startLatch.countDown();
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(results).hasSize(2);

        // Classifica vencedor e perdedor
        MvcResult winner = results.stream().filter(r -> r.getResponse().getStatus() == 200).findFirst().orElse(null);
        MvcResult loser = results.stream().filter(r -> r.getResponse().getStatus() == 409).findFirst().orElse(null);

        assertThat(winner).isNotNull();
        assertThat(loser).isNotNull();

        // 1. Resposta do vencedor
        String winnerBody = winner.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode winnerJson = objectMapper.readTree(winnerBody);
        assertThat(winnerJson.get("status").asText()).isEqualTo("ATIVO");
        assertThat(winnerJson.get("createdAt")).isNotNull();

        // 2. Resposta do perdedor (Problem Details governado sem vazar detalhes de banco)
        String loserBody = loser.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode loserJson = objectMapper.readTree(loserBody);
        assertThat(loserJson.get("code").asText()).isEqualTo("BETUFU-409-MATRICULA_DUPLICADA");
        assertThat(loserJson.get("title").asText()).isEqualTo("Matrícula já registrada");
        assertThat(loserJson.get("detail").asText()).isEqualTo("Esta matrícula já está registrada em outra conta BETUFU.");
        assertThat(loserJson.get("action").asText()).contains("administrador geral");

        // Prova que o perdedor NUNCA recebe mensagem crua do banco (PSQLException, constraint uq_ufu_enrollment_active_hmac, SQLSTATE 23505)
        assertThat(loserBody).doesNotContain("uq_ufu_enrollment_active_hmac");
        assertThat(loserBody).doesNotContain("23505");
        assertThat(loserBody).doesNotContain("PSQLException");
        assertThat(loserBody).doesNotContain("DataIntegrityViolationException");
        assertThat(loserBody).doesNotContain(sharedMatricula);

        // 3. Prova no banco: exatamente 1 linha ativa e 1 linha no total
        Long totalActive = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.ufu_enrollment WHERE status = 'ATIVO'", Long.class);
        assertThat(totalActive).isEqualTo(1L);

        Long totalRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.ufu_enrollment", Long.class);
        assertThat(totalRows).isEqualTo(1L);

        // 4. Prova na auditoria: exatamente 1 evento PERMITIDO e 1 evento BLOQUEADO
        List<AuditEvent> auditEvents = auditEventRepository.findAll();
        assertThat(auditEvents).hasSize(2);

        long permittedCount = auditEvents.stream().filter(e -> "PERMITIDO".equals(e.getOutcome())).count();
        long blockedCount = auditEvents.stream().filter(e -> "BLOQUEADO".equals(e.getOutcome())).count();
        assertThat(permittedCount).isEqualTo(1L);
        assertThat(blockedCount).isEqualTo(1L);

        AuditEvent blockedEvent = auditEvents.stream().filter(e -> "BLOQUEADO".equals(e.getOutcome())).findFirst().orElseThrow();
        assertThat(blockedEvent.getReason()).isEqualTo("Matrícula já registrada em outra conta");
        assertThat(blockedEvent.getCorrelationId()).isNotNull().isNotBlank();
        assertThat(blockedEvent.getNewValue()).doesNotContain(sharedMatricula);
    }

    @Test
    @DisplayName("D-31 / Corrida: Duas requisições simultâneas para a mesma conta com matrículas distintas deixam exatamente 1 linha ativa e perdedora recebe 409 CONTA_JA_VINCULADA")
    void concurrentRegistrationOfDifferentMatriculasBySameAccountResultsInExactlyOneWinnerAndOne409() throws Exception {
        String issuer = "https://idp.invalid";
        String userAccount = "race-same-account";
        String matricula1 = "20241BCC601";
        String matricula2 = "20241BCC602";

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<MvcResult> results = Collections.synchronizedList(new ArrayList<>());

        // Thread 1: Matrícula 1
        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = mockMvc.perform(put(ENROLLMENT_ME_PATH)
                                .with(oidcPrincipal(issuer, userAccount))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"matricula\":\"" + matricula1 + "\"}"))
                        .andReturn();
                results.add(res);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: Matrícula 2
        executor.submit(() -> {
            try {
                startLatch.await();
                MvcResult res = mockMvc.perform(put(ENROLLMENT_ME_PATH)
                                .with(oidcPrincipal(issuer, userAccount))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"matricula\":\"" + matricula2 + "\"}"))
                        .andReturn();
                results.add(res);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                doneLatch.countDown();
            }
        });

        // Disparo concorrente
        startLatch.countDown();
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(results).hasSize(2);

        MvcResult winner = results.stream().filter(r -> r.getResponse().getStatus() == 200).findFirst().orElse(null);
        MvcResult loser = results.stream().filter(r -> r.getResponse().getStatus() == 409).findFirst().orElse(null);

        assertThat(winner).isNotNull();
        assertThat(loser).isNotNull();

        // 1. Resposta do perdedor: 409 CONTA_JA_VINCULADA
        String loserBody = loser.getResponse().getContentAsString(StandardCharsets.UTF_8);
        JsonNode loserJson = objectMapper.readTree(loserBody);
        assertThat(loserJson.get("code").asText()).isEqualTo("BETUFU-409-CONTA_JA_VINCULADA");
        assertThat(loserJson.get("title").asText()).isEqualTo("Conta já possui matrícula");
        assertThat(loserJson.get("detail").asText()).isEqualTo("Sua conta já tem uma matrícula registrada.");

        // Prova de não vazamento de constraint ou detalhes SQL
        assertThat(loserBody).doesNotContain("uq_ufu_enrollment_active_identity");
        assertThat(loserBody).doesNotContain("23505");
        assertThat(loserBody).doesNotContain("PSQLException");

        // 2. Prova no banco: exatamente 1 vínculo ativo para a identidade
        UUID identityId = jdbcTemplate.queryForObject(
                "SELECT identity_id FROM public.local_identity WHERE issuer = ? AND subject = ?",
                UUID.class,
                issuer,
                userAccount);

        Long totalActive = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.ufu_enrollment WHERE identity_id = ? AND status = 'ATIVO'",
                Long.class,
                identityId);
        assertThat(totalActive).isEqualTo(1L);

        Long totalRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.ufu_enrollment WHERE identity_id = ?",
                Long.class,
                identityId);
        assertThat(totalRows).isEqualTo(1L);

        // 3. Prova na auditoria: 1 PERMITIDO e 1 BLOQUEADO
        List<AuditEvent> auditEvents = auditEventRepository.findAll();
        assertThat(auditEvents).hasSize(2);
    }

    @Test
    @DisplayName("D-35 / Arbitragem no Service: Invocação concorrente direta de EnrollmentService.register produz 1 sucesso e 1 ApiProblemException(409)")
    void directConcurrentServiceRegistrationUnderDatabaseConstraintEnforcement() throws Exception {
        UUID identity1 = createLocalIdentity("https://idp.invalid", "service-race-1");
        UUID identity2 = createLocalIdentity("https://idp.invalid", "service-race-2");
        String sharedMatricula = "20241BCC700";

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        List<EnrollmentStatusResponse> successes = Collections.synchronizedList(new ArrayList<>());
        List<Exception> failures = Collections.synchronizedList(new ArrayList<>());

        executor.submit(() -> {
            try {
                startLatch.await();
                EnrollmentStatusResponse resp = enrollmentService.register(identity1, sharedMatricula);
                successes.add(resp);
            } catch (Exception e) {
                failures.add(e);
            } finally {
                doneLatch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                startLatch.await();
                EnrollmentStatusResponse resp = enrollmentService.register(identity2, sharedMatricula);
                successes.add(resp);
            } catch (Exception e) {
                failures.add(e);
            } finally {
                doneLatch.countDown();
            }
        });

        startLatch.countDown();
        boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(successes).hasSize(1);
        assertThat(failures).hasSize(1);

        // Resposta de sucesso é ATIVO
        assertThat(successes.getFirst().status()).isEqualTo("ATIVO");

        // Falha é ApiProblemException com MATRICULA_ALREADY_REGISTERED (não vazando PSQLException bruta)
        Exception failure = failures.getFirst();
        assertThat(failure).isInstanceOf(ApiProblemException.class);
        ApiProblemException problem = (ApiProblemException) failure;
        assertThat(problem.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(problem.errorCode()).isEqualTo(ApiErrorCode.MATRICULA_ALREADY_REGISTERED);

        // Banco de dados possui exatamente 1 linha ativa
        Long totalActive = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.ufu_enrollment WHERE status = 'ATIVO'", Long.class);
        assertThat(totalActive).isEqualTo(1L);
    }

    @Test
    @DisplayName("D-35 / Contenção de 10 Threads: Múltiplas requisições simultâneas para a mesma matrícula deixam exatamente 1 linha ativa no banco")
    void multiThreadedContentionUnderRaceConditionEnforcesSingleActiveEnrollment() throws Exception {
        String issuer = "https://idp.invalid";
        String sharedMatricula = "20241BCC900";
        int threadCount = 10;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<MvcResult> results = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            String subject = "contention-user-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    MvcResult res = mockMvc.perform(put(ENROLLMENT_ME_PATH)
                                    .with(oidcPrincipal(issuer, subject))
                                    .with(csrf())
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"matricula\":\"" + sharedMatricula + "\"}"))
                            .andReturn();
                    results.add(res);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(results).hasSize(threadCount);

        long count200 = results.stream().filter(r -> r.getResponse().getStatus() == 200).count();
        long countRejections = results.stream().filter(r -> r.getResponse().getStatus() == 409 || r.getResponse().getStatus() == 429).count();

        // Exatamente 1 requisição obteve 200 OK
        assertThat(count200).isEqualTo(1L);
        // Todas as outras 9 foram rejeitadas com 409 ou 429
        assertThat(countRejections).isEqualTo(9L);

        // Nenhuma resposta expôs erro 500 ou mensagem bruta de banco
        for (MvcResult res : results) {
            String content = res.getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(content).doesNotContain("PSQLException");
            assertThat(content).doesNotContain("uq_ufu_enrollment_active_hmac");
        }

        // Banco de dados possui exatamente 1 linha ativa
        Long totalActive = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.ufu_enrollment WHERE status = 'ATIVO'", Long.class);
        assertThat(totalActive).isEqualTo(1L);

        Long totalRows = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.ufu_enrollment", Long.class);
        assertThat(totalRows).isEqualTo(1L);
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
        return OidcIdToken.withTokenValue("concurrency-it-" + UUID.randomUUID())
                .issuedAt(now.minusSeconds(60))
                .expiresAt(now.plusSeconds(300))
                .claim(IdTokenClaimNames.ISS, issuer)
                .claim(IdTokenClaimNames.SUB, subject)
                .claim("fixture_name", "concurrency-it-user")
                .build();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOidcConfiguration {

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
                    .clientId("betufu-enrollment-concurrency-it")
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
                    .clientName("BETUFU Enrollment Concurrency IT")
                    .build();

            return new InMemoryClientRegistrationRepository(registration);
        }
    }
}
