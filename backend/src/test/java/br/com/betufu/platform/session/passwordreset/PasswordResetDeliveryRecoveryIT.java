package br.com.betufu.platform.session.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import br.com.betufu.audit.AuditEntry;
import br.com.betufu.audit.AuditRecorder;
import br.com.betufu.platform.api.ProblemDetailsHandler;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.security.OidcRevocationClient;
import br.com.betufu.platform.security.SecurityConfiguration;
import br.com.betufu.platform.session.IdentitySessionTerminationService;
import br.com.betufu.platform.session.OidcSessionRevocationService;
import br.com.betufu.platform.session.RedisContainerSupport;
import br.com.betufu.platform.session.SelectiveTouchSessionRepository;
import br.com.betufu.platform.session.SessionInvalidationListener;
import br.com.betufu.platform.session.SessionSecurityConfiguration;
import br.com.betufu.platform.session.SessionTerminationReason;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration Test for Password Reset Event Inbox Delivery, Crash Recovery, Claim/Lease, Retention,
 * and Authorized Administrative Recovery (D-40 / ASVS L1 / OPER-06).
 *
 * <p>Proves:
 * <ul>
 *   <li>Crash between persistence and session termination leaves record in PENDING state, recoverable upon restart without double effect;
 *   <li>Crash during CLAIMED state with expired lease is reclaimed and delivered on restart;
 *   <li>Active claim lease is protected and cannot be stolen by another worker before expiration;
 *   <li>Concurrent recovery race produces exactly one claimant and single session termination execution;
 *   <li>Retention cleanup strictly removes only DELIVERED records older than 30 days and preserves exact boundaries;
 *   <li>FAILED records remain indefinitely in PostgreSQL until authorized resolution / replay;
 *   <li>Authorized replay reopens FAILED records and executes session termination successfully;
 *   <li>Recovery endpoint is protected by two independent guards (filter matcher and runtime contract lookup);
 *   <li>Scoped ADMINISTRADOR_GERAL grant is rejected (only global grant is accepted);
 *   <li>Dedicated M2M endpoint remains stateless and rejects browser sessions/cookies;
 *   <li>Audit trail records actor identity, timestamp, sourceEventId and result without leaking sensitive data.
 * </ul>
 */
@SpringBootTest(
        classes = {
            SessionSecurityConfiguration.class,
            IdentitySessionTerminationService.class,
            SessionInvalidationListener.class,
            OidcSessionRevocationService.class,
            PasswordResetSignedEnvelopeVerifier.class,
            PasswordResetEventInboxService.class,
            PasswordResetEventEndpoint.class,
            PasswordResetEventSecurityConfiguration.class,
            SecurityConfiguration.class,
            ProblemDetailsHandler.class,
            PasswordResetEventRecoveryController.class,
            PasswordResetDeliveryRecoveryIT.TestApplication.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "spring.docker.compose.enabled=false",
            "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
            "BETUFU_OIDC_CLIENT_ID=betufu-recovery-it",
            "BETUFU_OIDC_CLIENT_SECRET=unused",
            "betufu.session.password-reset.current-version=v1",
            "betufu.session.password-reset.keys.v1=MTEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
        })
@ActiveProfiles("integrated")
@Testcontainers
@Import(PasswordResetDeliveryRecoveryIT.TestApplication.class)
class PasswordResetDeliveryRecoveryIT {

    private static final String APPROVED_ACTIVITY_ATTRIBUTE =
            "br.com.betufu.platform.session.SelectiveTouchSessionRepository.approvedActivity";
    private static final String LAST_VALID_EPOCH_SECOND_ATTRIBUTE =
            "betufu.session.lastValidActivityAt.epochSecond";
    private static final String LAST_VALID_NANO_ATTRIBUTE =
            "betufu.session.lastValidActivityAt.nano";

    private static final String ISSUER = "https://idp.invalid";
    private static final String TARGET_SUBJECT_A = "recovery-target-user-alpha";
    private static final String CONTROL_SUBJECT_B = "recovery-control-user-beta";
    private static final String TEST_REALM = "betufu-local";

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
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        RedisContainerSupport.registerProperties(registry);
    }

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MutableTestClock clock;

    @Autowired
    private PasswordResetEventInboxRepository inboxRepository;

    @Autowired
    private PasswordResetEventInboxService inboxService;

    @Autowired
    private IdentitySessionTerminationService sessionTerminationService;

    @Autowired
    private ObjectProvider<FindByIndexNameSessionRepository<? extends Session>>
            sessionRepositoryProvider;

    @Autowired
    private OAuth2AuthorizedClientService authorizedClientService;

    @Autowired
    private OidcRevocationClient oidcRevocationClient;

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    @Autowired
    private TestOperationalIdentityLookup testIdentityLookup;

    @Autowired
    private TestAuditRecorder testAuditRecorder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private SelectiveTouchSessionRepository sessionRepository;
    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            connection.serverCommands().setConfig("notify-keyspace-events", "KEA");
            return null;
        });
        jdbcTemplate.execute("TRUNCATE TABLE public.password_reset_event_inbox");
        clock.set(Instant.now().truncatedTo(ChronoUnit.MILLIS));
        sessionRepository = (SelectiveTouchSessionRepository) applicationContext.getBean("sessionRepository");

        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .addFilters(springSecurityFilterChain)
                .build();

        testIdentityLookup.clear();
        testAuditRecorder.clear();
        org.mockito.Mockito.reset(oidcRevocationClient);
    }

    // -------------------------------------------------------------------------
    // Task 02-31-02: Crash / Restart, Claim / Lease, Concorrência e Retenção
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Crash antes do efeito deixa registro PENDING recuperável no restart sem efeito duplo")
    void crashBeforeSessionTerminationLeavesPendingEventRecoverableAfterRestartWithoutDoubleEffect() {
        String sourceEventId = "evt-crash-recovery-" + UUID.randomUUID();
        Instant now = clock.instant();

        // 1. Simula crash: evento foi persistido como PENDING no banco antes da interrupção/reinicialização
        int inserted = inboxRepository.insertIfAbsent(
                sourceEventId, TARGET_SUBJECT_A, TEST_REALM, "UPDATE_CREDENTIAL", PasswordResetEventInboxEntity.STATUS_PENDING);
        assertThat(inserted).isEqualTo(1);

        // 2. Cria sessões ativas para Target A e Control B no Redis
        SelectiveTouchSessionRepository.SelectiveSession sessionA1 = createAuthenticatedSession(TARGET_SUBJECT_A);
        sessionRepository.save(sessionA1);
        SelectiveTouchSessionRepository.SelectiveSession sessionB1 = createAuthenticatedSession(CONTROL_SUBJECT_B);
        sessionRepository.save(sessionB1);

        assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).hasSize(1);
        assertThat(sessionRepository.findByPrincipalName(CONTROL_SUBJECT_B)).hasSize(1);

        // 3. Simula restart / recuperação do consumidor: worker recupera eventos pendentes
        List<PasswordResetEventInboxService.InboxProcessingResult> recoveryResults =
                inboxService.recoverPendingEvents("worker-recovery-node-1", Duration.ofMinutes(5), now);

        assertThat(recoveryResults).hasSize(1);
        PasswordResetEventInboxService.InboxProcessingResult result = recoveryResults.get(0);
        assertThat(result.sourceEventId()).isEqualTo(sourceEventId);
        assertThat(result.status()).isEqualTo(PasswordResetEventInboxEntity.STATUS_DELIVERED);
        assertThat(result.effectExecuted()).isTrue();
        assertThat(result.terminatedSessionCount()).isEqualTo(1);

        // 4. Sessões de Target A foram encerradas; Control B permaneceu intacto
        assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).isEmpty();
        assertThat(sessionRepository.findById(sessionA1.getId())).isNull();
        assertThat(sessionRepository.findByPrincipalName(CONTROL_SUBJECT_B)).hasSize(1);
        assertThat(sessionRepository.findById(sessionB1.getId())).isNotNull();

        // 5. Registro no banco de dados está DELIVERED com delivered_at preenchido
        PasswordResetEventInboxEntity deliveredEntity = inboxRepository.findBySourceEventId(sourceEventId).orElseThrow();
        assertThat(deliveredEntity.getStatus()).isEqualTo(PasswordResetEventInboxEntity.STATUS_DELIVERED);
        assertThat(deliveredEntity.getDeliveredAt()).isNotNull();
        assertThat(deliveredEntity.getClaimInstanceId()).isNull();

        // 6. Prova de efeito único: Usuário cria nova sessão legítima após reset
        SelectiveTouchSessionRepository.SelectiveSession sessionA3 = createAuthenticatedSession(TARGET_SUBJECT_A);
        sessionRepository.save(sessionA3);
        assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).hasSize(1);

        // Reenvio do mesmo sourceEventId (duplicata / replay) não executa novo encerramento
        PasswordResetEventInboxService.InboxProcessingResult duplicateResult =
                inboxService.processEvent(sourceEventId, TARGET_SUBJECT_A, TEST_REALM, "UPDATE_CREDENTIAL");

        assertThat(duplicateResult.effectExecuted()).isFalse();
        assertThat(duplicateResult.status()).isEqualTo(PasswordResetEventInboxEntity.STATUS_DELIVERED);
        assertThat(duplicateResult.terminatedSessionCount()).isZero();

        // Nova sessão sobrevive intacta
        assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).hasSize(1);
        assertThat(sessionRepository.findById(sessionA3.getId())).isNotNull();

        // Apenas 1 registro existe no banco
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.password_reset_event_inbox WHERE source_event_id = ?",
                Long.class,
                sourceEventId);
        assertThat(count).isEqualTo(1L);
    }

    @Test
    @DisplayName("Crash durante estado CLAIMED com lease expirado é retomado por outro worker e concluído com sucesso")
    void crashDuringClaimedStateWithExpiredLeaseIsReclaimedAndDeliveredOnRestart() {
        String sourceEventId = "evt-stale-lease-" + UUID.randomUUID();
        Instant now = clock.instant();
        Instant tenMinutesAgo = now.minus(Duration.ofMinutes(10));

        // 1. Insere registro e simula claim antigo de um worker que caiu
        inboxRepository.insertIfAbsent(
                sourceEventId, TARGET_SUBJECT_A, TEST_REALM, "UPDATE_CREDENTIAL", PasswordResetEventInboxEntity.STATUS_PENDING);
        int claimed = inboxRepository.claim(sourceEventId, "crashed-worker-instance", tenMinutesAgo, now);
        assertThat(claimed).isEqualTo(1);

        PasswordResetEventInboxEntity claimedEntity = inboxRepository.findBySourceEventId(sourceEventId).orElseThrow();
        assertThat(claimedEntity.getStatus()).isEqualTo(PasswordResetEventInboxEntity.STATUS_CLAIMED);
        assertThat(claimedEntity.getClaimInstanceId()).isEqualTo("crashed-worker-instance");

        // 2. Sessão ativa para Target A
        SelectiveTouchSessionRepository.SelectiveSession sessionA = createAuthenticatedSession(TARGET_SUBJECT_A);
        sessionRepository.save(sessionA);
        assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).hasSize(1);

        // 3. Novo worker executa recuperação com lease de 5 minutos (10m > 5m, portanto expirado)
        List<PasswordResetEventInboxService.InboxProcessingResult> recoveryResults =
                inboxService.recoverPendingEvents("surviving-worker-instance", Duration.ofMinutes(5), now);

        assertThat(recoveryResults).hasSize(1);
        assertThat(recoveryResults.get(0).status()).isEqualTo(PasswordResetEventInboxEntity.STATUS_DELIVERED);
        assertThat(recoveryResults.get(0).effectExecuted()).isTrue();

        // 4. Sessão foi encerrada e registro está DELIVERED
        assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).isEmpty();
        PasswordResetEventInboxEntity deliveredEntity = inboxRepository.findBySourceEventId(sourceEventId).orElseThrow();
        assertThat(deliveredEntity.getStatus()).isEqualTo(PasswordResetEventInboxEntity.STATUS_DELIVERED);
        assertThat(deliveredEntity.getDeliveredAt()).isNotNull();
        assertThat(deliveredEntity.getAttemptCount()).isEqualTo(2); // 1 do crashed + 1 da recuperação
    }

    @Test
    @DisplayName("Claim ativo com lease vigente não é roubado por outro worker concorrente")
    void activeClaimWithValidLeaseIsNotStolenByAnotherWorker() {
        String sourceEventId = "evt-active-lease-" + UUID.randomUUID();
        Instant now = clock.instant();
        Instant oneMinuteAgo = now.minus(Duration.ofMinutes(1));

        inboxRepository.insertIfAbsent(
                sourceEventId, TARGET_SUBJECT_A, TEST_REALM, "UPDATE_CREDENTIAL", PasswordResetEventInboxEntity.STATUS_PENDING);
        inboxRepository.claim(sourceEventId, "active-worker-instance", oneMinuteAgo, now);

        SelectiveTouchSessionRepository.SelectiveSession sessionA = createAuthenticatedSession(TARGET_SUBJECT_A);
        sessionRepository.save(sessionA);

        // Outro worker tenta recuperar com lease de 5 minutos (1m < 5m, lease ainda ativo)
        List<PasswordResetEventInboxService.InboxProcessingResult> recoveryResults =
                inboxService.recoverPendingEvents("other-worker-instance", Duration.ofMinutes(5), now);

        assertThat(recoveryResults).isEmpty();

        // Registro permanece CLAIMED pelo worker original e sessão segue ativa
        PasswordResetEventInboxEntity entity = inboxRepository.findBySourceEventId(sourceEventId).orElseThrow();
        assertThat(entity.getStatus()).isEqualTo(PasswordResetEventInboxEntity.STATUS_CLAIMED);
        assertThat(entity.getClaimInstanceId()).isEqualTo("active-worker-instance");
        assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).hasSize(1);
    }

    @Test
    @DisplayName("Concorrência de recuperação: Múltiplas threads disputando o mesmo registro pendente produzem exatamente 1 vencedor e efeito único")
    void concurrentRecoveryOfSamePendingRecordYieldsSingleClaimantAndSingleEffect() throws Exception {
        String sharedSourceEventId = "evt-race-recovery-" + UUID.randomUUID();
        Instant now = clock.instant();

        inboxRepository.insertIfAbsent(
                sharedSourceEventId, TARGET_SUBJECT_A, TEST_REALM, "UPDATE_CREDENTIAL", PasswordResetEventInboxEntity.STATUS_PENDING);

        SelectiveTouchSessionRepository.SelectiveSession sessionA = createAuthenticatedSession(TARGET_SUBJECT_A);
        sessionRepository.save(sessionA);

        int threadCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<PasswordResetEventInboxService.InboxProcessingResult> allResults = Collections.synchronizedList(new ArrayList<>());
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            final String workerId = "race-worker-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    List<PasswordResetEventInboxService.InboxProcessingResult> results =
                            inboxService.recoverPendingEvents(workerId, Duration.ofMinutes(5), now);
                    allResults.addAll(results);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(errors).isEmpty();

        // Exatamente 1 thread obteve o claim e executou o efeito
        long executedCount = allResults.stream().filter(PasswordResetEventInboxService.InboxProcessingResult::effectExecuted).count();
        assertThat(executedCount).isEqualTo(1L);

        // Sessão destruída e estado final DELIVERED
        assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).isEmpty();
        PasswordResetEventInboxEntity entity = inboxRepository.findBySourceEventId(sharedSourceEventId).orElseThrow();
        assertThat(entity.getStatus()).isEqualTo(PasswordResetEventInboxEntity.STATUS_DELIVERED);
    }

    @Test
    @DisplayName("Retenção: Cleanup remove somente DELIVERED com mais de 30 dias; FAILED e registros pendentes/claimed são preservados")
    void cleanupRemovesOnlyDeliveredOlderThanThirtyDaysAndPreservesAllOtherStates() {
        Instant now = clock.instant();

        // 1. DELIVERED com 31 dias (anterior a 30d -> DEVE SER APAGADO)
        String evtDel31d = "evt-del-31d-" + UUID.randomUUID();
        insertFixture(evtDel31d, "DELIVERED", now.minus(Duration.ofDays(31)), now.minus(Duration.ofDays(31)), null);

        // 2. DELIVERED com 30 dias e 1 segundo (anterior a 30d -> DEVE SER APAGADO)
        String evtDel30dPlus1s = "evt-del-30d-1s-" + UUID.randomUUID();
        insertFixture(evtDel30dPlus1s, "DELIVERED", now.minus(Duration.ofDays(30)).minusSeconds(1), now.minus(Duration.ofDays(30)).minusSeconds(1), null);

        // 3. DELIVERED exatamente na fronteira de 30 dias (cutoff estrito < -> DEVE SER PRESERVADO)
        String evtDel30dExact = "evt-del-30d-exact-" + UUID.randomUUID();
        insertFixture(evtDel30dExact, "DELIVERED", now.minus(Duration.ofDays(30)), now.minus(Duration.ofDays(30)), null);

        // 4. DELIVERED com 29 dias (recente -> DEVE SER PRESERVADO)
        String evtDel29d = "evt-del-29d-" + UUID.randomUUID();
        insertFixture(evtDel29d, "DELIVERED", now.minus(Duration.ofDays(29)), now.minus(Duration.ofDays(29)), null);

        // 5. DELIVERED com 1 hora (recente -> DEVE SER PRESERVADO)
        String evtDel1h = "evt-del-1h-" + UUID.randomUUID();
        insertFixture(evtDel1h, "DELIVERED", now.minus(Duration.ofHours(1)), now.minus(Duration.ofHours(1)), null);

        // 6. FAILED com 45 dias (falha antiga -> NUNCA DEVE SER APAGADA até resolução autorizada)
        String evtFailed45d = "evt-failed-45d-" + UUID.randomUUID();
        insertFixture(evtFailed45d, "FAILED", now.minus(Duration.ofDays(45)), null, "TERMINATION_FAILED");

        // 7. FAILED com 1 dia (falha recente -> PRESERVADA)
        String evtFailed1d = "evt-failed-1d-" + UUID.randomUUID();
        insertFixture(evtFailed1d, "FAILED", now.minus(Duration.ofDays(1)), null, "TERMINATION_FAILED");

        // 8. PENDING com 45 dias (pendente antigo -> PRESERVADO)
        String evtPending45d = "evt-pending-45d-" + UUID.randomUUID();
        insertFixture(evtPending45d, "PENDING", now.minus(Duration.ofDays(45)), null, null);

        // 9. CLAIMED com 45 dias (claimed antigo -> PRESERVADO)
        String evtClaimed45d = "evt-claimed-45d-" + UUID.randomUUID();
        insertFixture(evtClaimed45d, "CLAIMED", now.minus(Duration.ofDays(45)), null, null);

        // Executa cleanup de 30 dias
        int deletedCount = inboxService.cleanupDeliveredOlderThan(Duration.ofDays(30), now);

        // Exatamente os 2 registros DELIVERED estritamente anteriores a 30 dias foram removidos
        assertThat(deletedCount).isEqualTo(2);

        // Registros apagados não existem mais
        assertThat(inboxRepository.findBySourceEventId(evtDel31d)).isEmpty();
        assertThat(inboxRepository.findBySourceEventId(evtDel30dPlus1s)).isEmpty();

        // Todos os demais registros continuam existindo com seus estados intactos
        assertThat(inboxRepository.findBySourceEventId(evtDel30dExact)).isPresent();
        assertThat(inboxRepository.findBySourceEventId(evtDel29d)).isPresent();
        assertThat(inboxRepository.findBySourceEventId(evtDel1h)).isPresent();

        // FAILED permanece até resolução autorizada (D-40)
        Optional<PasswordResetEventInboxEntity> failed45dOpt = inboxRepository.findBySourceEventId(evtFailed45d);
        assertThat(failed45dOpt).isPresent();
        assertThat(failed45dOpt.get().getStatus()).isEqualTo(PasswordResetEventInboxEntity.STATUS_FAILED);
        assertThat(failed45dOpt.get().getLastError()).isEqualTo("TERMINATION_FAILED");

        assertThat(inboxRepository.findBySourceEventId(evtFailed1d)).isPresent();
        assertThat(inboxRepository.findBySourceEventId(evtPending45d)).isPresent();
        assertThat(inboxRepository.findBySourceEventId(evtClaimed45d)).isPresent();
    }

    @Test
    @DisplayName("FAILED é retido e pode ser reprocessado mediante ação autorizada de replay")
    void failedEventsAreRetainedUntilAuthorizedReplayExecutesSuccessfully() {
        String sourceEventId = "evt-replay-authorized-" + UUID.randomUUID();
        UUID adminIdentityId = UUID.randomUUID();
        Instant now = clock.instant();

        // Insere registro FAILED com 15 dias
        insertFixture(sourceEventId, "FAILED", now.minus(Duration.ofDays(15)), null, "TERMINATION_FAILED");

        SelectiveTouchSessionRepository.SelectiveSession sessionA = createAuthenticatedSession(TARGET_SUBJECT_A);
        sessionRepository.save(sessionA);
        assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).hasSize(1);

        // Reprocessa o evento FAILED
        Optional<PasswordResetEventInboxService.InboxProcessingResult> replayResultOpt =
                inboxService.reprocessFailedEvent(sourceEventId, adminIdentityId, now);

        assertThat(replayResultOpt).isPresent();
        PasswordResetEventInboxService.InboxProcessingResult replayResult = replayResultOpt.get();
        assertThat(replayResult.status()).isEqualTo(PasswordResetEventInboxEntity.STATUS_DELIVERED);
        assertThat(replayResult.effectExecuted()).isTrue();
        assertThat(replayResult.terminatedSessionCount()).isEqualTo(1);

        // Sessão encerrada e registro atualizado para DELIVERED
        assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).isEmpty();
        PasswordResetEventInboxEntity deliveredEntity = inboxRepository.findBySourceEventId(sourceEventId).orElseThrow();
        assertThat(deliveredEntity.getStatus()).isEqualTo(PasswordResetEventInboxEntity.STATUS_DELIVERED);
        assertThat(deliveredEntity.getLastError()).isNull();

        // Segunda tentativa de replay em registro já DELIVERED é ignorada
        Optional<PasswordResetEventInboxService.InboxProcessingResult> secondReplayOpt =
                inboxService.reprocessFailedEvent(sourceEventId, adminIdentityId, now);

        assertThat(secondReplayOpt).isPresent();
        assertThat(secondReplayOpt.get().effectExecuted()).isFalse();
        assertThat(secondReplayOpt.get().status()).isEqualTo(PasswordResetEventInboxEntity.STATUS_DELIVERED);
    }

    @Test
    @DisplayName("Reprocessamento de FAILED preserva FAILED se o downstream falhar novamente")
    void failedEventReprocessFailsAgainIfDownstreamStillFailing() {
        String sourceEventId = "evt-replay-failing-" + UUID.randomUUID();
        UUID adminIdentityId = UUID.randomUUID();
        Instant now = clock.instant();

        insertFixture(sourceEventId, "FAILED", now.minus(Duration.ofDays(5)), null, "TERMINATION_FAILED");

        // Cria serviço com downstream que falha
        IdentitySessionTerminationService failingTermination = mock(IdentitySessionTerminationService.class);
        org.mockito.Mockito.when(failingTermination.terminateAllSessionsFor(
                        TARGET_SUBJECT_A, SessionTerminationReason.SECURITY))
                .thenThrow(new RuntimeException("Redis unavailable"));

        PasswordResetEventInboxService customService =
                new PasswordResetEventInboxService(
                        inboxRepository,
                        failingTermination,
                        testAuditRecorder,
                        transactionManager);

        Optional<PasswordResetEventInboxService.InboxProcessingResult> resultOpt =
                customService.reprocessFailedEvent(sourceEventId, adminIdentityId, now);

        assertThat(resultOpt).isPresent();
        assertThat(resultOpt.get().status()).isEqualTo(PasswordResetEventInboxEntity.STATUS_FAILED);
        assertThat(resultOpt.get().effectExecuted()).isFalse();

        PasswordResetEventInboxEntity entity = inboxRepository.findBySourceEventId(sourceEventId).orElseThrow();
        assertThat(entity.getStatus()).isEqualTo(PasswordResetEventInboxEntity.STATUS_FAILED);
        assertThat(entity.getLastError()).isEqualTo("TERMINATION_FAILED");
    }

    // -------------------------------------------------------------------------
    // Task 02-31-03: Autorização de Recovery, Duas Guardas, Auditoria e Replay
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Recovery: Administrador Geral autenticado consulta FAILED e reprocessa evento com sucesso e auditoria")
    void authorizedAdminCanListFailedEventsAndReprocessSuccessfullyWithAudit() throws Exception {
        String sourceEventId = "evt-recovery-http-" + UUID.randomUUID();
        UUID adminIdentityId = UUID.randomUUID();
        String adminSubject = "admin-operator-global";
        Instant now = clock.instant();

        // 1. Prepara fixture FAILED e sessão ativa do usuário afetado
        insertFixture(sourceEventId, "FAILED", now.minus(Duration.ofDays(10)), null, "TERMINATION_FAILED");
        SelectiveTouchSessionRepository.SelectiveSession sessionA = createAuthenticatedSession(TARGET_SUBJECT_A);
        sessionRepository.save(sessionA);
        assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).hasSize(1);

        // 2. Consulta GET /api/v1/operations/password-reset-events/failed
        mockMvc.perform(get("/api/v1/operations/password-reset-events/failed")
                        .with(oidcAdmin(adminSubject, adminIdentityId, true, null))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].sourceEventId").value(sourceEventId))
                .andExpect(jsonPath("$.items[0].status").value("FAILED"))
                .andExpect(jsonPath("$.items[0].lastError").value("TERMINATION_FAILED"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(50))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));

        // Consulta individual pela rota canonica publicada no OpenAPI.
        mockMvc.perform(get("/api/v1/operations/password-reset-events/" + sourceEventId)
                        .with(oidcAdmin(adminSubject, adminIdentityId, true, null))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceEventId").value(sourceEventId))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.lastError").value("TERMINATION_FAILED"));

        // 3. Executa POST /api/v1/operations/password-reset-events/{sourceEventId}/reprocess
        mockMvc.perform(post("/api/v1/operations/password-reset-events/" + sourceEventId + "/reprocess")
                        .with(oidcAdmin(adminSubject, adminIdentityId, true, null))
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceEventId").value(sourceEventId))
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.effectExecuted").value(true))
                .andExpect(jsonPath("$.terminatedSessionCount").value(1))
                .andExpect(jsonPath("$.actorIdentityId").value(adminIdentityId.toString()));

        // 4. Sessão do usuário foi destruída e registro no banco atualizado para DELIVERED
        assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).isEmpty();
        PasswordResetEventInboxEntity delivered = inboxRepository.findBySourceEventId(sourceEventId).orElseThrow();
        assertThat(delivered.getStatus()).isEqualTo("DELIVERED");
        assertThat(delivered.getLastError()).isNull();

        // 5. Auditoria foi registrada sem dados sensíveis
        List<AuditEntry> auditEntries = testAuditRecorder.getEntries();
        assertThat(auditEntries).hasSize(2);
        assertThat(auditEntries).allSatisfy(entry -> {
            assertThat(entry.actorIdentityId()).isEqualTo(adminIdentityId);
            assertThat(entry.action()).isEqualTo(AuditEntry.ACTION_REPROCESSAMENTO_RESET_SENHA);
            assertThat(entry.reason()).contains(sourceEventId);
        });
        assertThat(auditEntries.get(0).previousValue()).isEqualTo("FAILED");
        assertThat(auditEntries.get(0).newValue()).isEqualTo("CLAIMED");
        assertThat(auditEntries.get(1).previousValue()).isEqualTo("CLAIMED");
        assertThat(auditEntries.get(1).newValue()).isEqualTo("DELIVERED");

        // 6. Segundo reprocessamento em registro DELIVERED é idempotente (effectExecuted = false)
        mockMvc.perform(post("/api/v1/operations/password-reset-events/" + sourceEventId + "/reprocess")
                        .with(oidcAdmin(adminSubject, adminIdentityId, true, null))
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.effectExecuted").value(false));
    }

    @Test
    @DisplayName("Guarda 1 (Filtro): Bloqueia requisição sem ROLE_ADMINISTRADOR_GERAL antes do controller")
    void guard1_rejectsUserWithoutAdminGeralRoleInSecurityContext() throws Exception {
        UUID userId = UUID.randomUUID();
        String userSub = "regular-user-sub";

        // Usuário tem concessão global no lookup, mas o SecurityContext não possui a autoridade ROLE_ADMINISTRADOR_GERAL
        testIdentityLookup.registerIdentity(
                ISSUER, userSub, userId,
                Set.of(new OperationalIdentityLookup.GrantedRole("ADMINISTRADOR_GERAL", null)));

        OidcIdToken idToken = new OidcIdToken("token-user", clock.instant(), clock.instant().plusSeconds(3600),
                Map.of(IdTokenClaimNames.ISS, ISSUER, IdTokenClaimNames.SUB, userSub));
        OidcUser oidcUser = new DefaultOidcUser(
                AuthorityUtils.createAuthorityList("ROLE_USER"),
                idToken);

        mockMvc.perform(get("/api/v1/operations/password-reset-events/failed")
                        .with(oidcLogin().oidcUser(oidcUser))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Guarda 2 (Controller): Bloqueia requisição com ROLE_ADMINISTRADOR_GERAL se OperationalIdentityLookup negar")
    void guard2_rejectsUserWithAdminGeralAuthorityIfPlatformIdentityLookupDenies() throws Exception {
        String forgedSubject = "forged-admin-sub";

        // Usuário tem a autoridade ROLE_ADMINISTRADOR_GERAL injetada na sessão/token,
        // mas o contrato OperationalIdentityLookup devolve vazio (identidade sem concessão no banco)
        OidcIdToken idToken = new OidcIdToken("token-forged", clock.instant(), clock.instant().plusSeconds(3600),
                Map.of(IdTokenClaimNames.ISS, ISSUER, IdTokenClaimNames.SUB, forgedSubject));
        OidcUser oidcUser = new DefaultOidcUser(
                AuthorityUtils.createAuthorityList("ROLE_USER", "ROLE_ADMINISTRADOR_GERAL"),
                idToken);

        mockMvc.perform(get("/api/v1/operations/password-reset-events/failed")
                        .with(oidcLogin().oidcUser(oidcUser))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Guarda 2 (Controller): Rejeita concessão escopada de ADMINISTRADOR_GERAL (scopeRef != null)")
    void guard2_rejectsScopedAdminGeralGrant() throws Exception {
        UUID adminId = UUID.randomUUID();
        UUID scopeId = UUID.randomUUID();
        String scopedSubject = "scoped-admin-sub";

        // Concessão de ADMINISTRADOR_GERAL com scopeRef preenchido (escopada) -> deve ser recusada
        mockMvc.perform(get("/api/v1/operations/password-reset-events/failed")
                        .with(oidcAdmin(scopedSubject, adminId, false, scopeId))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Recovery: Reprocessamento de sourceEventId inexistente devolve 404 Not Found")
    void reprocessNonExistentEventReturnsNotFound() throws Exception {
        UUID adminId = UUID.randomUUID();
        String adminSub = "admin-sub-404";

        mockMvc.perform(post("/api/v1/operations/password-reset-events/evt-unknown-" + UUID.randomUUID() + "/reprocess")
                        .with(oidcAdmin(adminSub, adminId, true, null))
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Crash real depois do commit do claim deixa CLAIMED duravel e recuperavel apos o lease")
    void committedClaimSurvivesCrashBeforeExternalEffectAndIsReclaimed() {
        String sourceEventId = "evt-real-crash-" + UUID.randomUUID();
        Instant now = clock.instant();
        insertFixture(sourceEventId, "PENDING", now.minusSeconds(30), null, null);
        sessionRepository.save(createAuthenticatedSession(TARGET_SUBJECT_A));

        PasswordResetEventInboxService crashingService = new PasswordResetEventInboxService(
                inboxRepository,
                sessionTerminationService,
                testAuditRecorder,
                transactionManager) {
            @Override
            protected void afterClaimCommitted(String claimedSourceEventId) {
                throw new IllegalStateException("simulated crash after durable claim");
            }
        };

        assertThatThrownBy(() -> crashingService.recoverPendingEvents(
                "worker-that-crashes", Duration.ofMinutes(5), now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("durable claim");

        PasswordResetEventInboxEntity claimed = inboxRepository.findBySourceEventId(sourceEventId).orElseThrow();
        assertThat(claimed.getStatus()).isEqualTo("CLAIMED");
        assertThat(claimed.getClaimInstanceId()).isEqualTo("worker-that-crashes");
        assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).hasSize(1);

        List<PasswordResetEventInboxService.InboxProcessingResult> recovered =
                inboxService.recoverPendingEvents(
                        "replacement-worker", Duration.ofMinutes(5), now.plus(Duration.ofMinutes(6)));
        assertThat(recovered).singleElement().satisfies(result -> {
            assertThat(result.status()).isEqualTo("DELIVERED");
            assertThat(result.effectExecuted()).isTrue();
        });
        assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).isEmpty();
    }

    @Test
    @DisplayName("Crash depois de remover o cliente OAuth nunca vira DELIVERED sem confirmacao remota")
    void crashAfterAuthorizedClientRemovalRequiresPositiveRevocationEvidenceAfterRestart() {
        String sourceEventId = "evt-crash-after-client-removal-" + UUID.randomUUID();
        Instant now = clock.instant();
        insertFixture(sourceEventId, "PENDING", now.minusSeconds(30), null, null);
        sessionRepository.save(createAuthenticatedSession(TARGET_SUBJECT_A));

        org.mockito.Mockito.doThrow(new SimulatedProcessCrash())
                .when(oidcRevocationClient)
                .revokeRefreshToken(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString());

        assertThatThrownBy(() -> inboxService.recoverPendingEvents(
                        "worker-crashes-after-client-removal", Duration.ofMinutes(5), now))
                .isInstanceOf(SimulatedProcessCrash.class);

        PasswordResetEventInboxEntity claimed =
                inboxRepository.findBySourceEventId(sourceEventId).orElseThrow();
        assertThat(claimed.getStatus()).isEqualTo("CLAIMED");
        assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).isEmpty();
        OAuth2AuthorizedClient removedClient = authorizedClientService.loadAuthorizedClient(
                IdentitySessionTerminationService.DEFAULT_REGISTRATION_ID,
                TARGET_SUBJECT_A);
        assertThat(removedClient).isNull();

        org.mockito.Mockito.reset(oidcRevocationClient);
        OidcSessionRevocationService restartedRevocation = applicationContext
                .getAutowireCapableBeanFactory()
                .createBean(OidcSessionRevocationService.class);
        IdentitySessionTerminationService restartedTermination =
                new IdentitySessionTerminationService(
                        sessionRepositoryProvider,
                        new SessionInvalidationListener(restartedRevocation),
                        restartedRevocation);
        PasswordResetEventInboxService restartedService = new PasswordResetEventInboxService(
                inboxRepository, restartedTermination, testAuditRecorder, transactionManager);

        assertThat(restartedService.recoverPendingEvents(
                        "replacement-after-process-restart",
                        Duration.ofMinutes(5),
                        now.plus(Duration.ofMinutes(6))))
                .singleElement()
                .satisfies(result -> {
                    assertThat(result.status()).isEqualTo("FAILED");
                    assertThat(result.terminatedSessionCount()).isZero();
                });

        PasswordResetEventInboxEntity retained =
                inboxRepository.findBySourceEventId(sourceEventId).orElseThrow();
        assertThat(retained.getStatus()).isEqualTo("FAILED");
        assertThat(retained.getLastError()).isEqualTo("TERMINATION_FAILED");
        assertThat(retained.getDeliveredAt()).isNull();
    }

    @Test
    @DisplayName("Summary parcial permanece FAILED em entrega, recovery e replay administrativo")
    void partialTerminationSummaryNeverBecomesDelivered() {
        IdentitySessionTerminationService partialTermination = mock(IdentitySessionTerminationService.class);
        org.mockito.Mockito.when(partialTermination.terminateAllSessionsFor(
                        TARGET_SUBJECT_A, SessionTerminationReason.SECURITY))
                .thenReturn(new IdentitySessionTerminationService.TerminationSummary(
                        1,
                        List.of(OidcSessionRevocationService.Outcome.REMOTE_FAILURE),
                        List.of()));
        PasswordResetEventInboxService service = new PasswordResetEventInboxService(
                inboxRepository, partialTermination, testAuditRecorder, transactionManager);
        Instant now = clock.instant();

        String directId = "evt-partial-direct-" + UUID.randomUUID();
        assertThat(service.processEvent(directId, TARGET_SUBJECT_A, TEST_REALM, "UPDATE_CREDENTIAL").status())
                .isEqualTo("FAILED");

        String recoveryId = "evt-partial-recovery-" + UUID.randomUUID();
        insertFixture(recoveryId, "PENDING", now.minusSeconds(30), null, null);
        assertThat(service.recoverPendingEvents("partial-worker", Duration.ofMinutes(5), now))
                .singleElement()
                .satisfies(result -> assertThat(result.status()).isEqualTo("FAILED"));

        String replayId = "evt-partial-replay-" + UUID.randomUUID();
        insertFixture(replayId, "FAILED", now.minusSeconds(30), null, "TERMINATION_FAILED");
        assertThat(service.reprocessFailedEvent(replayId, UUID.randomUUID(), now))
                .hasValueSatisfying(result -> assertThat(result.status()).isEqualTo("FAILED"));

        assertThat(List.of(directId, recoveryId, replayId)).allSatisfy(id -> {
            PasswordResetEventInboxEntity entity = inboxRepository.findBySourceEventId(id).orElseThrow();
            assertThat(entity.getStatus()).isEqualTo("FAILED");
            assertThat(entity.getLastError()).isEqualTo("TERMINATION_FAILED");
        });
    }

    @Test
    @DisplayName("Replay FAILED sem evidencia positiva de revogacao permanece FAILED apos restart")
    void failedReplayWithoutPositiveGrantEvidenceRemainsFailedAfterBackendRestart() {
        authorizedClientService.removeAuthorizedClient(
                IdentitySessionTerminationService.DEFAULT_REGISTRATION_ID,
                TARGET_SUBJECT_A);
        OidcSessionRevocationService restartedRevocation = applicationContext
                .getAutowireCapableBeanFactory()
                .createBean(OidcSessionRevocationService.class);
        IdentitySessionTerminationService restartedTermination =
                new IdentitySessionTerminationService(
                        sessionRepositoryProvider,
                        new SessionInvalidationListener(restartedRevocation),
                        restartedRevocation);
        PasswordResetEventInboxService restartedService = new PasswordResetEventInboxService(
                inboxRepository, restartedTermination, testAuditRecorder, transactionManager);
        String sourceEventId = "evt-restart-without-proof-" + UUID.randomUUID();
        Instant now = clock.instant();
        insertFixture(
                sourceEventId,
                "FAILED",
                now.minus(Duration.ofMinutes(10)),
                null,
                "TERMINATION_FAILED");

        assertThat(restartedService.reprocessFailedEvent(sourceEventId, UUID.randomUUID(), now))
                .hasValueSatisfying(result -> {
                    assertThat(result.status()).isEqualTo("FAILED");
                    assertThat(result.effectExecuted()).isTrue();
                    assertThat(result.terminatedSessionCount()).isZero();
                });

        PasswordResetEventInboxEntity retained =
                inboxRepository.findBySourceEventId(sourceEventId).orElseThrow();
        assertThat(retained.getStatus()).isEqualTo("FAILED");
        assertThat(retained.getLastError()).isEqualTo("TERMINATION_FAILED");
    }

    @Test
    @DisplayName("Falha ao persistir resultado de auditoria impede DELIVERED e deixa erro estavel")
    void replayCannotCompleteWhenResultAuditFails() {
        String sourceEventId = "evt-audit-result-failure-" + UUID.randomUUID();
        Instant now = clock.instant();
        insertFixture(sourceEventId, "FAILED", now.minusSeconds(30), null, "TERMINATION_FAILED");
        sessionRepository.save(createAuthenticatedSession(TARGET_SUBJECT_A));
        AtomicInteger auditCalls = new AtomicInteger();
        AuditRecorder failsOnResult = entry -> {
            if (auditCalls.incrementAndGet() == 2) {
                throw new IllegalStateException("simulated durable audit failure");
            }
        };
        PasswordResetEventInboxService service = new PasswordResetEventInboxService(
                inboxRepository, sessionTerminationService, failsOnResult, transactionManager);

        assertThatThrownBy(() -> service.reprocessFailedEvent(sourceEventId, UUID.randomUUID(), now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("durable audit failure");

        PasswordResetEventInboxEntity failed = inboxRepository.findBySourceEventId(sourceEventId).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo("FAILED");
        assertThat(failed.getLastError()).isEqualTo("AUDIT_FAILED");
    }

    @Test
    @DisplayName("Listagem FAILED e paginada e recusa tamanho acima do limite")
    void failedListingIsBoundedAndPaginated() throws Exception {
        UUID adminId = UUID.randomUUID();
        String adminSubject = "pagination-admin";
        Instant now = clock.instant();
        for (int index = 0; index < 3; index += 1) {
            insertFixture("evt-page-" + index, "FAILED", now.plusMillis(index), null, "TERMINATION_FAILED");
        }

        mockMvc.perform(get("/api/v1/operations/password-reset-events/failed?page=0&size=2")
                        .with(oidcAdmin(adminSubject, adminId, true, null))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.hasNext").value(true));

        mockMvc.perform(get("/api/v1/operations/password-reset-events/failed?page=0&size=101")
                        .with(oidcAdmin(adminSubject, adminId, true, null))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Driver operacional chama recovery, cleanup e atualiza metrica FAILED em lote limitado")
    void maintenanceDriverInvokesAllProductionOperations() {
        PasswordResetEventInboxService service = mock(PasswordResetEventInboxService.class);
        PasswordResetEventInboxRepository repository = mock(PasswordResetEventInboxRepository.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        Instant now = clock.instant();
        org.mockito.Mockito.when(service.recoverPendingEvents(
                        "maintenance-test", Duration.ofMinutes(5), now, 10))
                .thenReturn(List.of());
        org.mockito.Mockito.when(service.cleanupDeliveredOlderThan(Duration.ofDays(30), now))
                .thenReturn(2);
        org.mockito.Mockito.when(repository.countByStatus("FAILED")).thenReturn(3L);
        PasswordResetEventMaintenanceConfiguration.PasswordResetEventMaintenanceJob job =
                new PasswordResetEventMaintenanceConfiguration.PasswordResetEventMaintenanceJob(
                        service,
                        repository,
                        registry,
                        Clock.fixed(now, ZoneOffset.UTC),
                        Duration.ofMinutes(5),
                        Duration.ofDays(30),
                        10,
                        "maintenance-test");

        job.runCycle();

        assertThat(job.failedEventCount()).isEqualTo(3);
        assertThat(registry.get("betufu.password_reset.inbox.failed").gauge().value()).isEqualTo(3.0);
        org.mockito.Mockito.verify(service).recoverPendingEvents(
                "maintenance-test", Duration.ofMinutes(5), now, 10);
        org.mockito.Mockito.verify(service).cleanupDeliveredOlderThan(Duration.ofDays(30), now);
        org.mockito.Mockito.verify(repository).countByStatus("FAILED");
    }

    @Test
    @DisplayName("Cadeia M2M: Permanece estritamente STATELESS e recusa autenticação por cookie ou sessão")
    void dedicatedM2MChainRemainsStatelessAndRejectsSessionCookies() throws Exception {
        UUID adminId = UUID.randomUUID();
        String adminSub = "admin-sub-m2m";

        // Requisicao para a rota M2M com cookie/sessao OIDC mas SEM os headers HMAC -> 401.
        // O 401 e a resposta correta e e o proprio ponto da prova: falta material de
        // autenticacao, e sessao autenticada nao o substitui. Um 400 afirmaria corpo
        // malformado, o que nao e o caso — o corpo enviado aqui e valido.
        mockMvc.perform(post(PasswordResetEventEndpoint.ENDPOINT_PATH)
                        .with(oidcAdmin(adminSub, adminId, true, null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceEventId\":\"evt-m2m-test\",\"subject\":\"target\",\"realmId\":\"betufu-local\",\"eventType\":\"UPDATE_CREDENTIAL\",\"timestamp\":\"2026-08-23T19:00:00Z\"}"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void insertFixture(
            String sourceEventId,
            String status,
            Instant createdAt,
            Instant deliveredAt,
            String lastError) {
        jdbcTemplate.update(
                """
                INSERT INTO public.password_reset_event_inbox (
                    source_event_id, subject, realm_id, event_type, status, created_at, updated_at, delivered_at, last_error
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                sourceEventId,
                TARGET_SUBJECT_A,
                TEST_REALM,
                "UPDATE_CREDENTIAL",
                status,
                java.sql.Timestamp.from(createdAt),
                java.sql.Timestamp.from(createdAt),
                deliveredAt != null ? java.sql.Timestamp.from(deliveredAt) : null,
                lastError);
    }

    private SelectiveTouchSessionRepository.SelectiveSession createAuthenticatedSession(String subject) {
        saveAuthorizedClient(subject);
        SelectiveTouchSessionRepository.SelectiveSession session = sessionRepository.createSession();
        Instant now = clock.instant();

        OidcIdToken idToken = OidcIdToken.withTokenValue("recovery-token-" + subject)
                .issuer(ISSUER)
                .subject(subject)
                .claim("preferred_username", "user-" + subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();

        DefaultOidcUser oidcUser = new DefaultOidcUser(
                AuthorityUtils.createAuthorityList("ROLE_USER"),
                idToken,
                IdTokenClaimNames.SUB);

        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                oidcUser,
                oidcUser.getAuthorities(),
                "keycloak");

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);

        session.setAttribute("SPRING_SECURITY_CONTEXT", securityContext);
        session.setAttribute(LAST_VALID_EPOCH_SECOND_ATTRIBUTE, now.getEpochSecond());
        session.setAttribute(LAST_VALID_NANO_ATTRIBUTE, now.getNano());
        session.setAttribute(APPROVED_ACTIVITY_ATTRIBUTE, now);

        return session;
    }

    private void saveAuthorizedClient(String principalName) {
        ClientRegistration registration = clientRegistrationRepository.findByRegistrationId("keycloak");
        Instant issuedAt = clock.instant();
        OAuth2AuthorizedClient client = new OAuth2AuthorizedClient(
                registration,
                principalName,
                new OAuth2AccessToken(
                        OAuth2AccessToken.TokenType.BEARER,
                        "test-access-token",
                        issuedAt,
                        issuedAt.plusSeconds(300)),
                new OAuth2RefreshToken("test-refresh-token", issuedAt));
        authorizedClientService.saveAuthorizedClient(
                client,
                new TestingAuthenticationToken(principalName, "not-used", "ROLE_USER"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor oidcAdmin(
            String subject, UUID identityId, boolean globalAdmin, UUID scopeRef, String... additionalAuthorities) {
        if (identityId != null) {
            Set<OperationalIdentityLookup.GrantedRole> roles = new java.util.HashSet<>();
            if (globalAdmin) {
                roles.add(new OperationalIdentityLookup.GrantedRole("ADMINISTRADOR_GERAL", null));
            } else if (scopeRef != null) {
                roles.add(new OperationalIdentityLookup.GrantedRole("ADMINISTRADOR_GERAL", scopeRef));
            }
            testIdentityLookup.registerIdentity(ISSUER, subject, identityId, roles);
        }

        Instant now = clock.instant();
        Map<String, Object> claims = Map.of(
                IdTokenClaimNames.ISS, ISSUER,
                IdTokenClaimNames.SUB, subject);

        OidcIdToken idToken = new OidcIdToken("token-" + subject, now, now.plusSeconds(3600), claims);
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        if (globalAdmin || scopeRef != null) {
            authorities.add(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR_GERAL"));
        }
        for (String auth : additionalAuthorities) {
            authorities.add(new SimpleGrantedAuthority(auth));
        }

        OidcUser oidcUser = new DefaultOidcUser(authorities, idToken);
        return oidcLogin().oidcUser(oidcUser);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class TestApplication {

        @Bean
        @Primary
        MutableTestClock mutableTestClock() {
            return new MutableTestClock(Instant.EPOCH, ZoneOffset.UTC);
        }

        @Bean
        @Primary
        OidcRevocationClient mockOidcRevocationClient() {
            return mock(OidcRevocationClient.class);
        }

        @Bean
        @Primary
        TestOperationalIdentityLookup testOperationalIdentityLookup() {
            return new TestOperationalIdentityLookup();
        }

        @Bean
        @Primary
        TestAuditRecorder testAuditRecorder() {
            return new TestAuditRecorder();
        }

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
                    .clientId("betufu-recovery-it")
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
                    .clientName("BETUFU Password Reset Delivery Recovery IT")
                    .build();
            return new InMemoryClientRegistrationRepository(registration);
        }
    }

    static final class TestOperationalIdentityLookup implements OperationalIdentityLookup {
        private final Map<String, OperationalIdentity> identities = new ConcurrentHashMap<>();

        void registerIdentity(String issuer, String subject, UUID identityId, Set<GrantedRole> roles) {
            identities.put(issuer + ":" + subject, new OperationalIdentity(identityId, roles));
        }

        void clear() {
            identities.clear();
        }

        @Override
        public Optional<OperationalIdentity> lookupIdentity(String issuer, String subject) {
            if (issuer == null || subject == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(identities.get(issuer + ":" + subject));
        }
    }

    private static final class SimulatedProcessCrash extends Error {
        private static final long serialVersionUID = 1L;
    }

    static final class TestAuditRecorder implements AuditRecorder {
        private final List<AuditEntry> entries = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void record(AuditEntry entry) {
            entries.add(Objects.requireNonNull(entry));
        }

        List<AuditEntry> getEntries() {
            return List.copyOf(entries);
        }

        void clear() {
            entries.clear();
        }
    }

    static final class MutableTestClock extends Clock {

        private final AtomicReference<Instant> instant;
        private final ZoneId zone;

        private MutableTestClock(Instant instant, ZoneId zone) {
            this(new AtomicReference<>(Objects.requireNonNull(instant)), zone);
        }

        private MutableTestClock(AtomicReference<Instant> instant, ZoneId zone) {
            this.instant = instant;
            this.zone = Objects.requireNonNull(zone);
        }

        void set(Instant newInstant) {
            instant.set(Objects.requireNonNull(newInstant));
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId newZone) {
            return new MutableTestClock(instant, newZone);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }
}
