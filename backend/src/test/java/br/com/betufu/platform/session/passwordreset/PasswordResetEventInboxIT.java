package br.com.betufu.platform.session.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.betufu.audit.AuditRecorder;
import br.com.betufu.platform.security.OidcRevocationClient;
import br.com.betufu.platform.session.IdentitySessionTerminationService;
import br.com.betufu.platform.session.OidcSessionRevocationService;
import br.com.betufu.platform.session.RedisContainerSupport;
import br.com.betufu.platform.session.SelectiveTouchSessionRepository;
import br.com.betufu.platform.session.SessionInvalidationListener;
import br.com.betufu.platform.session.SessionSecurityConfiguration;
import br.com.betufu.platform.session.SessionTerminationReason;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
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
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
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

/**
 * Integration Test for Password Reset Event Inbox: entity, repository, idempotent service
 * processing, and isolated endpoint security configuration.
 *
 * <p>Proves:
 *
 * <ul>
 *   <li>Flyway migration V5_1 table contract and constraints in PostgreSQL 18;
 *   <li>Database-level uniqueness and concurrency arbitration on {@code source_event_id};
 *   <li>Single execution of session termination under heavy concurrent race conditions;
 *   <li>Idempotent duplicate handling (no second termination);
 *   <li>Recovery state preservation on failure (conserving FAILED status for 02-31 recovery);
 *   <li>Dedicated stateless security filter chain for {@code
 *       /api/v1/internal/events/password-reset};
 *   <li>Rejection of cookies/bearer tokens as HMAC substitutes (fail-closed without HMAC);
 *   <li>Strict HMAC signature verification in constant time;
 *   <li>Payload allowlist, body size limitation, and secret redaction.
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
      PasswordResetEventInboxIT.TestApplication.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.docker.compose.enabled=false",
      "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
      "BETUFU_OIDC_CLIENT_ID=betufu-inbox-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused",
      // A chave chega pelo modelo de propriedades, nao por campo proprio do endpoint.
      // Antes ela vinha de BETUFU_PASSWORD_RESET_HMAC_KEY_V1 ligada a um @Value no
      // PasswordResetEventEndpoint; esse caminho foi removido na Task 02-31-01, porque
      // D-40 exige exatamente duas chaves resolvidas por current/previous e nenhum
      // segredo default fora desse modelo.
      "betufu.session.password-reset.current-version=v1",
      "betufu.session.password-reset.keys.v1=MTEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
    })
@ActiveProfiles("integrated")
@Testcontainers
@Import(PasswordResetEventInboxIT.TestApplication.class)
class PasswordResetEventInboxIT {

  // Literais espelhados de SelectiveTouchSessionRepository, cujos campos sao package-private.
  // O tracer irmao usa o mesmo padrao; nao ampliar a visibilidade da producao por causa de teste.
  private static final String APPROVED_ACTIVITY_ATTRIBUTE =
      "br.com.betufu.platform.session.SelectiveTouchSessionRepository.approvedActivity";
  private static final String LAST_VALID_EPOCH_SECOND_ATTRIBUTE =
      "betufu.session.lastValidActivityAt.epochSecond";
  private static final String LAST_VALID_NANO_ATTRIBUTE = "betufu.session.lastValidActivityAt.nano";

  private static final String TARGET_SUBJECT_A = "inbox-target-user-alpha";
  private static final String CONTROL_SUBJECT_B = "inbox-control-user-beta";
  private static final String TEST_REALM = "betufu-local";
  private static final String KEY_ID = "v1";
  private static final String RAW_SECRET_KEY_BASE64 =
      "MTEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";
  private static final byte[] RAW_SECRET_KEY = Base64.getDecoder().decode(RAW_SECRET_KEY_BASE64);

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
  static void dynamicProperties(DynamicPropertyRegistry registry) {
    RedisContainerSupport.registerProperties(registry);
  }

  @Autowired private WebApplicationContext applicationContext;

  @Autowired private StringRedisTemplate redis;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private MutableTestClock clock;

  @Autowired private PasswordResetEventInboxRepository inboxRepository;

  @Autowired private PasswordResetEventInboxService inboxService;

  @Autowired private OAuth2AuthorizedClientService authorizedClientService;

  @Autowired private ClientRegistrationRepository clientRegistrationRepository;

  @Autowired private PasswordResetSignedEnvelopeVerifier verifier;

  @Autowired private PasswordResetEventProperties properties;

  @Autowired private PasswordResetEventEndpoint endpoint;

  private SelectiveTouchSessionRepository sessionRepository;
  private MockMvc mockMvc;

  @BeforeEach
  void setup() {
    redis.execute(
        (RedisCallback<Void>)
            connection -> {
              connection.serverCommands().flushDb();
              connection.serverCommands().setConfig("notify-keyspace-events", "KEA");
              return null;
            });
    jdbcTemplate.execute("TRUNCATE TABLE public.password_reset_event_inbox");
    // Alinhado a milissegundo de proposito. O envelope transporta o timestamp como epoch
    // em millis, entao um relogio com precisao de microssegundo faz now.minusSeconds(300)
    // voltar do transporte como 300s MAIS a fracao truncada — e a fronteira exata da janela
    // passa a falhar por menos de um milissegundo, sem que nada esteja errado na regra.
    clock.set(Instant.now().truncatedTo(ChronoUnit.MILLIS));
    sessionRepository =
        (SelectiveTouchSessionRepository) applicationContext.getBean("sessionRepository");
    mockMvc =
        MockMvcBuilders.webAppContextSetup(applicationContext)
            .apply(SecurityMockMvcConfigurers.springSecurity())
            .build();
    restoreCanonicalKeyConfiguration();
  }

  @AfterEach
  void restoreProperties() {
    restoreCanonicalKeyConfiguration();
  }

  /**
   * PasswordResetEventProperties e um bean singleton, e as provas de rotacao mutam currentVersion,
   * previousVersion e o mapa de chaves. Sem restaurar, o estado vaza para as provas seguintes e o
   * resultado passa a depender da ordem de execucao — uma prova que so passa quando roda primeiro
   * nao prova nada.
   */
  private void restoreCanonicalKeyConfiguration() {
    properties.setCurrentVersion(KEY_ID);
    properties.setPreviousVersion(null);
    properties.setKeys(Map.of(KEY_ID, RAW_SECRET_KEY_BASE64));
  }

  @Test
  @DisplayName(
      "Migration V5_1 cria public.password_reset_event_inbox com colunas e constraints exatas no PostgreSQL 18")
  void migrationV51CreatesTableAndConstraintsAsContracted() {
    List<String> columns =
        jdbcTemplate.queryForList(
            """
                SELECT attribute.attname
                FROM pg_catalog.pg_attribute attribute
                JOIN pg_catalog.pg_class relation
                  ON relation.oid = attribute.attrelid
                JOIN pg_catalog.pg_namespace namespace
                  ON namespace.oid = relation.relnamespace
                WHERE namespace.nspname = 'public'
                  AND relation.relname = 'password_reset_event_inbox'
                  AND attribute.attnum > 0
                  AND NOT attribute.attisdropped
                ORDER BY attribute.attnum
                """,
            String.class);

    assertThat(columns)
        .containsExactly(
            "inbox_id",
            "source_event_id",
            "subject",
            "realm_id",
            "event_type",
            "status",
            "created_at",
            "updated_at",
            "attempt_count",
            "claimed_at",
            "claim_instance_id",
            "delivered_at",
            "last_error");

    List<String> constraints =
        jdbcTemplate.queryForList(
            """
                SELECT constraint_record.conname
                FROM pg_catalog.pg_constraint constraint_record
                WHERE constraint_record.conrelid = 'public.password_reset_event_inbox'::regclass
                  AND constraint_record.contype IN ('p', 'u', 'c')
                ORDER BY constraint_record.conname
                """,
            String.class);

    assertThat(constraints)
        .contains(
            "pk_password_reset_event_inbox",
            "uq_password_reset_event_inbox_source_event",
            "ck_password_reset_event_inbox_source_event_not_empty",
            "ck_password_reset_event_inbox_source_event_octets",
            "ck_password_reset_event_inbox_subject_not_empty",
            "ck_password_reset_event_inbox_subject_octets",
            "ck_password_reset_event_inbox_realm_id_not_empty",
            "ck_password_reset_event_inbox_realm_id_octets",
            "ck_password_reset_event_inbox_status_not_empty",
            "ck_password_reset_event_inbox_status_octets",
            "ck_password_reset_event_inbox_status_values",
            "ck_password_reset_event_inbox_timestamp_order");
  }

  @Test
  @DisplayName("Check constraints rejeitam status inválido e campos vazios no PostgreSQL")
  void checkConstraintsRejectInvalidStatusAndEmptyFields() {
    // Status inválido
    DataIntegrityViolationException invalidStatusEx =
        catchThrowableOfType(
            () ->
                jdbcTemplate.update(
                    """
                        INSERT INTO public.password_reset_event_inbox (
                            source_event_id, subject, realm_id, status
                        ) VALUES (?, ?, ?, ?)
                        """,
                    "evt-check-1",
                    "sub-1",
                    "realm-1",
                    "INVALID_STATUS"),
            DataIntegrityViolationException.class);

    assertSqlStateAndConstraint(
        invalidStatusEx, "23514", "ck_password_reset_event_inbox_status_values");

    // source_event_id vazio
    DataIntegrityViolationException emptySourceEventEx =
        catchThrowableOfType(
            () ->
                jdbcTemplate.update(
                    """
                        INSERT INTO public.password_reset_event_inbox (
                            source_event_id, subject, realm_id, status
                        ) VALUES (?, ?, ?, ?)
                        """,
                    "",
                    "sub-1",
                    "realm-1",
                    "PENDING"),
            DataIntegrityViolationException.class);

    assertSqlStateAndConstraint(
        emptySourceEventEx, "23514", "ck_password_reset_event_inbox_source_event_not_empty");

    // subject vazio
    DataIntegrityViolationException emptySubjectEx =
        catchThrowableOfType(
            () ->
                jdbcTemplate.update(
                    """
                        INSERT INTO public.password_reset_event_inbox (
                            source_event_id, subject, realm_id, status
                        ) VALUES (?, ?, ?, ?)
                        """,
                    "evt-check-2",
                    "",
                    "realm-1",
                    "PENDING"),
            DataIntegrityViolationException.class);

    assertSqlStateAndConstraint(
        emptySubjectEx, "23514", "ck_password_reset_event_inbox_subject_not_empty");

    // realm_id vazio
    DataIntegrityViolationException emptyRealmEx =
        catchThrowableOfType(
            () ->
                jdbcTemplate.update(
                    """
                        INSERT INTO public.password_reset_event_inbox (
                            source_event_id, subject, realm_id, status
                        ) VALUES (?, ?, ?, ?)
                        """,
                    "evt-check-3",
                    "sub-1",
                    "",
                    "PENDING"),
            DataIntegrityViolationException.class);

    assertSqlStateAndConstraint(
        emptyRealmEx, "23514", "ck_password_reset_event_inbox_realm_id_not_empty");
  }

  @Test
  @DisplayName(
      "PasswordResetEventInboxRepository executa insertIfAbsent, consultas tipadas e transições de estado")
  void repositoryOperationsPerformInsertIfAbsentAndStateTransitions() {
    String sourceEventId = "evt-repo-" + UUID.randomUUID();

    // 1. insertIfAbsent com novo evento retorna 1
    int insertedFirst =
        inboxRepository.insertIfAbsent(
            sourceEventId,
            TARGET_SUBJECT_A,
            TEST_REALM,
            "UPDATE_CREDENTIAL",
            PasswordResetEventInboxEntity.STATUS_PENDING);
    assertThat(insertedFirst).isEqualTo(1);

    // 2. insertIfAbsent com mesmo sourceEventId retorna 0 (ON CONFLICT DO NOTHING)
    int insertedDuplicate =
        inboxRepository.insertIfAbsent(
            sourceEventId,
            TARGET_SUBJECT_A,
            TEST_REALM,
            "UPDATE_CREDENTIAL",
            PasswordResetEventInboxEntity.STATUS_PENDING);
    assertThat(insertedDuplicate).isZero();

    // 3. findBySourceEventId localiza a entidade com todos os campos
    Optional<PasswordResetEventInboxEntity> foundOpt =
        inboxRepository.findBySourceEventId(sourceEventId);
    assertThat(foundOpt).isPresent();
    PasswordResetEventInboxEntity entity = foundOpt.get();
    assertThat(entity.getSourceEventId()).isEqualTo(sourceEventId);
    assertThat(entity.getSubject()).isEqualTo(TARGET_SUBJECT_A);
    assertThat(entity.getRealmId()).isEqualTo(TEST_REALM);
    assertThat(entity.getEventType()).isEqualTo("UPDATE_CREDENTIAL");
    assertThat(entity.getStatus()).isEqualTo(PasswordResetEventInboxEntity.STATUS_PENDING);
    assertThat(entity.getCreatedAt()).isNotNull();
    assertThat(entity.getUpdatedAt()).isNotNull();
    assertThat(entity.getAttemptCount()).isZero();
    assertThat(entity.getDeliveredAt()).isNull();
    assertThat(entity.getLastError()).isNull();

    // 4. findById localiza a mesma entidade
    Optional<PasswordResetEventInboxEntity> byIdOpt = inboxRepository.findById(entity.getInboxId());
    assertThat(byIdOpt).isPresent();
    assertThat(byIdOpt.get().getSourceEventId()).isEqualTo(sourceEventId);

    // 5. markDelivered atualiza status para DELIVERED e registra delivered_at
    int markedDelivered = inboxRepository.markDelivered(sourceEventId);
    assertThat(markedDelivered).isEqualTo(1);

    PasswordResetEventInboxEntity deliveredEntity =
        inboxRepository.findBySourceEventId(sourceEventId).orElseThrow();
    assertThat(deliveredEntity.getStatus())
        .isEqualTo(PasswordResetEventInboxEntity.STATUS_DELIVERED);
    assertThat(deliveredEntity.getDeliveredAt()).isNotNull();

    // 6. findByStatus busca por status DELIVERED
    List<PasswordResetEventInboxEntity> deliveredList =
        inboxRepository.findByStatus(PasswordResetEventInboxEntity.STATUS_DELIVERED);
    assertThat(deliveredList)
        .extracting(PasswordResetEventInboxEntity::getSourceEventId)
        .contains(sourceEventId);

    // 7. markFailed atualiza status para FAILED e registra last_error
    int markedFailed = inboxRepository.markFailed(sourceEventId, "TestError: simulation");
    assertThat(markedFailed).isEqualTo(1);

    PasswordResetEventInboxEntity failedEntity =
        inboxRepository.findBySourceEventId(sourceEventId).orElseThrow();
    assertThat(failedEntity.getStatus()).isEqualTo(PasswordResetEventInboxEntity.STATUS_FAILED);
    assertThat(failedEntity.getLastError()).isEqualTo("TestError: simulation");
  }

  @Test
  @DisplayName("processEvent encerra todas as sessões do subject e marca inbox como DELIVERED")
  void processEventDeliversSuccessfullyAndTerminatesAllSubjectSessions() {
    SelectiveTouchSessionRepository.SelectiveSession sessionA1 =
        createAuthenticatedSession(TARGET_SUBJECT_A);
    sessionRepository.save(sessionA1);
    SelectiveTouchSessionRepository.SelectiveSession sessionB1 =
        createAuthenticatedSession(CONTROL_SUBJECT_B);
    sessionRepository.save(sessionB1);

    assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).hasSize(1);
    assertThat(sessionRepository.findByPrincipalName(CONTROL_SUBJECT_B)).hasSize(1);

    String sourceEventId = "evt-process-" + UUID.randomUUID();

    PasswordResetEventInboxService.InboxProcessingResult result =
        inboxService.processEvent(sourceEventId, TARGET_SUBJECT_A, TEST_REALM, "UPDATE_CREDENTIAL");

    assertThat(result.sourceEventId()).isEqualTo(sourceEventId);
    assertThat(result.status()).isEqualTo(PasswordResetEventInboxEntity.STATUS_DELIVERED);
    assertThat(result.effectExecuted()).isTrue();
    assertThat(result.terminatedSessionCount()).isEqualTo(1);

    // Sessões de Target A foram destruídas
    assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).isEmpty();
    assertThat(sessionRepository.findById(sessionA1.getId())).isNull();

    // Sessão de Control B sobrevive intacta
    assertThat(sessionRepository.findByPrincipalName(CONTROL_SUBJECT_B)).hasSize(1);
    assertThat(sessionRepository.findById(sessionB1.getId())).isNotNull();

    // Registro no PostgreSQL está DELIVERED
    PasswordResetEventInboxEntity record =
        inboxRepository.findBySourceEventId(sourceEventId).orElseThrow();
    assertThat(record.getStatus()).isEqualTo(PasswordResetEventInboxEntity.STATUS_DELIVERED);
    assertThat(record.getDeliveredAt()).isNotNull();
  }

  @Test
  @DisplayName(
      "Replay idempotente: processEvent duplicado retorna effectExecuted=false e não encerra novas sessões")
  void duplicateSourceEventIdIsIdempotentAndSkipsSessionTermination() {
    SelectiveTouchSessionRepository.SelectiveSession session1 =
        createAuthenticatedSession(TARGET_SUBJECT_A);
    sessionRepository.save(session1);

    String sourceEventId = "evt-replay-" + UUID.randomUUID();

    // 1ª execução
    PasswordResetEventInboxService.InboxProcessingResult result1 =
        inboxService.processEvent(sourceEventId, TARGET_SUBJECT_A, TEST_REALM, "UPDATE_CREDENTIAL");

    assertThat(result1.effectExecuted()).isTrue();
    assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).isEmpty();

    // Cria uma nova sessão para o usuário após o reset
    SelectiveTouchSessionRepository.SelectiveSession session2 =
        createAuthenticatedSession(TARGET_SUBJECT_A);
    sessionRepository.save(session2);
    assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).hasSize(1);

    // 2ª execução com mesmo sourceEventId (duplicata / replay)
    PasswordResetEventInboxService.InboxProcessingResult result2 =
        inboxService.processEvent(sourceEventId, TARGET_SUBJECT_A, TEST_REALM, "UPDATE_CREDENTIAL");

    assertThat(result2.effectExecuted()).isFalse();
    assertThat(result2.status()).isEqualTo(PasswordResetEventInboxEntity.STATUS_DELIVERED);
    assertThat(result2.terminatedSessionCount()).isZero();

    // A nova sessão sobrevive porque o efeito NÃO foi executado novamente
    assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).hasSize(1);
    assertThat(sessionRepository.findById(session2.getId())).isNotNull();

    // Apenas 1 registro existe no banco
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.password_reset_event_inbox WHERE source_event_id = ?",
            Long.class,
            sourceEventId);
    assertThat(count).isEqualTo(1L);
  }

  @Test
  @DisplayName(
      "Concorrência: Múltiplas threads simultâneas com o mesmo sourceEventId produzem exatamente 1 vencedor e 1 registro no banco")
  void concurrentDeliveriesOfSameSourceEventIdResultInSingleWinnerAndSingleEffect()
      throws Exception {
    SelectiveTouchSessionRepository.SelectiveSession session1 =
        createAuthenticatedSession(TARGET_SUBJECT_A);
    sessionRepository.save(session1);

    String sharedSourceEventId = "evt-race-" + UUID.randomUUID();
    int threadCount = 8;

    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    List<PasswordResetEventInboxService.InboxProcessingResult> results =
        Collections.synchronizedList(new ArrayList<>());
    List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

    for (int i = 0; i < threadCount; i++) {
      executor.submit(
          () -> {
            try {
              startLatch.await();
              PasswordResetEventInboxService.InboxProcessingResult res =
                  inboxService.processEvent(
                      sharedSourceEventId, TARGET_SUBJECT_A, TEST_REALM, "UPDATE_CREDENTIAL");
              results.add(res);
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
    assertThat(results).hasSize(threadCount);

    long winnersCount =
        results.stream()
            .filter(PasswordResetEventInboxService.InboxProcessingResult::effectExecuted)
            .count();
    long losersCount = results.stream().filter(r -> !r.effectExecuted()).count();

    // Exatamente 1 vencedor executou o efeito
    assertThat(winnersCount).isEqualTo(1L);
    assertThat(losersCount).isEqualTo(threadCount - 1);

    // Banco de dados possui exatamente 1 registro
    Long totalRows =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.password_reset_event_inbox WHERE source_event_id = ?",
            Long.class,
            sharedSourceEventId);
    assertThat(totalRows).isEqualTo(1L);
  }

  @Test
  @DisplayName(
      "Falha no efeito de terminação propaga exceção e conserva status FAILED para recuperação em 02-31")
  void sessionTerminationFailurePreservesFailedStatusForRecovery() {
    String sourceEventId = "evt-failure-" + UUID.randomUUID();

    // Cria serviço com stub de falha
    IdentitySessionTerminationService failingTerminationService =
        mock(IdentitySessionTerminationService.class);
    org.mockito.Mockito.when(
            failingTerminationService.terminateAllSessionsFor(
                TARGET_SUBJECT_A, SessionTerminationReason.SECURITY))
        .thenThrow(new IllegalStateException("Simulated downstream Redis failure"));

    PasswordResetEventInboxService failingInboxService =
        new PasswordResetEventInboxService(inboxRepository, failingTerminationService);

    // Execução falha com IllegalStateException
    assertThatThrownBy(
            () ->
                failingInboxService.processEvent(
                    sourceEventId, TARGET_SUBJECT_A, TEST_REALM, "UPDATE_CREDENTIAL"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Simulated downstream Redis failure");

    // O registro no PostgreSQL permanece com status FAILED e last_error preenchido
    Optional<PasswordResetEventInboxEntity> recordOpt =
        inboxRepository.findBySourceEventId(sourceEventId);
    assertThat(recordOpt).isPresent();
    PasswordResetEventInboxEntity record = recordOpt.get();
    assertThat(record.getStatus()).isEqualTo(PasswordResetEventInboxEntity.STATUS_FAILED);
    assertThat(record.getLastError())
        .as("o inbox grava código estável, nunca a classe nem a mensagem cruas da exceção")
        .isEqualTo(PasswordResetEventInboxService.ERROR_CODE_TERMINATION_FAILED)
        .doesNotContain("IllegalStateException")
        .doesNotContain("Simulated downstream Redis failure");

    // Nova chamada detecta o registro existente e não relata sucesso indevido
    PasswordResetEventInboxService.InboxProcessingResult retryResult =
        inboxService.processEvent(sourceEventId, TARGET_SUBJECT_A, TEST_REALM, "UPDATE_CREDENTIAL");

    assertThat(retryResult.effectExecuted()).isFalse();
    assertThat(retryResult.status()).isEqualTo(PasswordResetEventInboxEntity.STATUS_FAILED);
  }

  @Test
  @DisplayName(
      "Endpoint converte a primeira falha persistida em 503 sanitizado para permitir retry da origem")
  void endpointReturnsSanitized503WhenInitialProcessingThrowsAfterPersistingFailure() {
    String sourceEventId = "evt-endpoint-failure-" + UUID.randomUUID();
    IdentitySessionTerminationService failingTerminationService =
        mock(IdentitySessionTerminationService.class);
    org.mockito.Mockito.when(
            failingTerminationService.terminateAllSessionsFor(
                TARGET_SUBJECT_A, SessionTerminationReason.SECURITY))
        .thenThrow(new IllegalStateException("sensitive Redis topology detail"));

    PasswordResetEventInboxService failingInboxService =
        new PasswordResetEventInboxService(inboxRepository, failingTerminationService);
    org.springframework.beans.factory.ObjectProvider<PasswordResetEventProperties>
        propertiesProvider = mock(org.springframework.beans.factory.ObjectProvider.class);
    org.mockito.Mockito.when(propertiesProvider.getIfAvailable()).thenReturn(properties);
    PasswordResetEventEndpoint failingEndpoint =
        new PasswordResetEventEndpoint(verifier, failingInboxService, propertiesProvider);

    Instant now = clock.instant();
    String timestamp = String.valueOf(now.toEpochMilli());
    String jsonPayload =
        """
                {
                    "sourceEventId": "%s",
                    "subject": "%s",
                    "realmId": "%s",
                    "eventType": "UPDATE_CREDENTIAL",
                    "timestamp": "%s"
                }
                """
            .formatted(sourceEventId, TARGET_SUBJECT_A, TEST_REALM, timestamp);
    byte[] bodyBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);
    String signature =
        verifier.sign(
            RAW_SECRET_KEY,
            KEY_ID,
            timestamp,
            "POST",
            PasswordResetEventEndpoint.ENDPOINT_PATH,
            sourceEventId,
            verifier.computeBodySha256(bodyBytes));
    org.springframework.mock.web.MockHttpServletRequest request =
        new org.springframework.mock.web.MockHttpServletRequest();
    request.setMethod("POST");
    request.setRequestURI(PasswordResetEventEndpoint.ENDPOINT_PATH);

    org.springframework.http.ResponseEntity<PasswordResetEventEndpoint.EventResponse> response =
        failingEndpoint.handlePasswordResetEvent(
            KEY_ID, timestamp, signature, sourceEventId, bodyBytes, request);

    assertThat(response.getStatusCode().value()).isEqualTo(503);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().status()).isEqualTo(PasswordResetEventInboxEntity.STATUS_FAILED);
    assertThat(response.getBody().sourceEventId()).isEqualTo(sourceEventId);
    assertThat(response.getBody().effectExecuted()).isFalse();
    assertThat(response.toString())
        .doesNotContain("sensitive Redis topology detail")
        .doesNotContain("IllegalStateException");

    PasswordResetEventInboxEntity persisted =
        inboxRepository.findBySourceEventId(sourceEventId).orElseThrow();
    assertThat(persisted.getStatus()).isEqualTo(PasswordResetEventInboxEntity.STATUS_FAILED);
    assertThat(persisted.getLastError())
        .isEqualTo(PasswordResetEventInboxService.ERROR_CODE_TERMINATION_FAILED)
        .doesNotContain("sensitive Redis topology detail");
  }

  @Test
  @DisplayName("Validação de entrada: Parâmetros nulos ou em branco falham fechados antes do banco")
  void blankAndNullParametersFailClosed() {
    assertThatThrownBy(() -> inboxService.processEvent(null, "sub", "realm", "UPDATE_CREDENTIAL"))
        .isInstanceOf(NullPointerException.class);

    assertThatThrownBy(() -> inboxService.processEvent("evt", null, "realm", "UPDATE_CREDENTIAL"))
        .isInstanceOf(NullPointerException.class);

    assertThatThrownBy(() -> inboxService.processEvent("evt", "sub", null, "UPDATE_CREDENTIAL"))
        .isInstanceOf(NullPointerException.class);

    assertThatThrownBy(() -> inboxService.processEvent("   ", "sub", "realm", "UPDATE_CREDENTIAL"))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> inboxService.processEvent("evt", "   ", "realm", "UPDATE_CREDENTIAL"))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(() -> inboxService.processEvent("evt", "sub", "   ", "UPDATE_CREDENTIAL"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ---------------------------------------------------------------------------------------------
  // Task 02-30-03: Endpoint Security, Statelessness, HMAC Isolation and Configuration Tests
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "Task 02-30-03: Endpoint exige envelope HMAC exato e rejeita requisições sem assinatura ou com assinatura adulterada")
  void endpointRequiresExactHmacAndRejectsMissingOrInvalidSignature() throws Exception {
    SelectiveTouchSessionRepository.SelectiveSession sessionA =
        createAuthenticatedSession(TARGET_SUBJECT_A);
    sessionRepository.save(sessionA);

    String sourceEventId = "evt-sec-hmac-" + UUID.randomUUID();
    String jsonPayload =
        """
                {
                    "sourceEventId": "%s",
                    "subject": "%s",
                    "realmId": "%s",
                    "eventType": "UPDATE_CREDENTIAL",
                    "timestamp": "%s"
                }
                """
            .formatted(sourceEventId, TARGET_SUBJECT_A, TEST_REALM, clock.instant().toString());

    byte[] bodyBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);
    String timestampHeader = String.valueOf(clock.instant().toEpochMilli());

    // 1. Requisição sem cabeçalhos HMAC é rejeitada com 401 Unauthorized
    mockMvc
        .perform(
            post(PasswordResetEventEndpoint.ENDPOINT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content(bodyBytes))
        .andExpect(status().isUnauthorized());

    // 2. Requisição com assinatura inválida é rejeitada com 401 Unauthorized
    mockMvc
        .perform(
            post(PasswordResetEventEndpoint.ENDPOINT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PasswordResetEventEndpoint.HEADER_KEY_ID, KEY_ID)
                .header(PasswordResetEventEndpoint.HEADER_TIMESTAMP, timestampHeader)
                .header(
                    PasswordResetEventEndpoint.HEADER_SIGNATURE,
                    "bad-signature-00000000000000000000000000000000000000000000000000")
                .header(PasswordResetEventEndpoint.HEADER_SOURCE_EVENT_ID, sourceEventId)
                .content(bodyBytes))
        .andExpect(status().isUnauthorized());

    // Sessão permanece ativa no Redis e nenhum registro é criado no banco
    assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).hasSize(1);
    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.password_reset_event_inbox WHERE source_event_id = ?",
            Long.class,
            sourceEventId);
    assertThat(count).isZero();
  }

  @Test
  @DisplayName(
      "Task 02-30-03: Endpoint é stateless e não aceita cookie de sessão ou bearer token como substituto do HMAC")
  void endpointIgnoresCookiesAndBearerTokensAndRequiresHmac() throws Exception {
    SelectiveTouchSessionRepository.SelectiveSession sessionA =
        createAuthenticatedSession(TARGET_SUBJECT_A);
    sessionRepository.save(sessionA);

    String sourceEventId = "evt-sec-stateless-" + UUID.randomUUID();
    String jsonPayload =
        """
                {
                    "sourceEventId": "%s",
                    "subject": "%s",
                    "realmId": "%s",
                    "eventType": "UPDATE_CREDENTIAL",
                    "timestamp": "%s"
                }
                """
            .formatted(sourceEventId, TARGET_SUBJECT_A, TEST_REALM, clock.instant().toString());

    byte[] bodyBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);

    // Requisição contendo Bearer token e Cookie de sessão, mas sem HMAC
    mockMvc
        .perform(
            post(PasswordResetEventEndpoint.ENDPOINT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer eyJhbGciOiJSUzI1NiJ9.invalid.token")
                .header("Cookie", "SESSION=" + sessionA.getId())
                .content(bodyBytes))
        .andExpect(status().isUnauthorized());

    // Sessão permanece intacta
    assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).hasSize(1);
  }

  @Test
  @DisplayName(
      "Task 02-30-03: Endpoint falha fechado (401) quando o keyId é desconhecido ou não configurado")
  void endpointFailsClosedWhenKeyIdIsUnknownOrKeyMissing() throws Exception {
    SelectiveTouchSessionRepository.SelectiveSession sessionA =
        createAuthenticatedSession(TARGET_SUBJECT_A);
    sessionRepository.save(sessionA);

    String sourceEventId = "evt-sec-unknown-key-" + UUID.randomUUID();
    String jsonPayload =
        """
                {
                    "sourceEventId": "%s",
                    "subject": "%s",
                    "realmId": "%s",
                    "eventType": "UPDATE_CREDENTIAL",
                    "timestamp": "%s"
                }
                """
            .formatted(sourceEventId, TARGET_SUBJECT_A, TEST_REALM, clock.instant().toString());

    byte[] bodyBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);
    String bodySha256 = verifier.computeBodySha256(bodyBytes);
    String timestampHeader = String.valueOf(clock.instant().toEpochMilli());

    // Assinatura gerada para keyId desconhecido
    String signature =
        verifier.sign(
            RAW_SECRET_KEY,
            "unknown-key-id",
            timestampHeader,
            "POST",
            PasswordResetEventEndpoint.ENDPOINT_PATH,
            sourceEventId,
            bodySha256);

    mockMvc
        .perform(
            post(PasswordResetEventEndpoint.ENDPOINT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PasswordResetEventEndpoint.HEADER_KEY_ID, "unknown-key-id")
                .header(PasswordResetEventEndpoint.HEADER_TIMESTAMP, timestampHeader)
                .header(PasswordResetEventEndpoint.HEADER_SIGNATURE, signature)
                .header(PasswordResetEventEndpoint.HEADER_SOURCE_EVENT_ID, sourceEventId)
                .content(bodyBytes))
        .andExpect(status().isUnauthorized());

    assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).hasSize(1);
  }

  @Test
  @DisplayName(
      "Task 02-30-03: Cabeçalhos extras não alteram a canonicalização assinada e requisição válida tem sucesso")
  void endpointAcceptsValidHmacWithExtraHeadersWithoutCanonicalizationTampering() throws Exception {
    SelectiveTouchSessionRepository.SelectiveSession sessionA =
        createAuthenticatedSession(TARGET_SUBJECT_A);
    sessionRepository.save(sessionA);
    SelectiveTouchSessionRepository.SelectiveSession sessionB =
        createAuthenticatedSession(CONTROL_SUBJECT_B);
    sessionRepository.save(sessionB);

    String sourceEventId = "evt-sec-extra-headers-" + UUID.randomUUID();
    Instant now = clock.instant();
    String jsonPayload =
        """
                {
                    "sourceEventId": "%s",
                    "subject": "%s",
                    "realmId": "%s",
                    "eventType": "UPDATE_CREDENTIAL",
                    "timestamp": "%s"
                }
                """
            .formatted(sourceEventId, TARGET_SUBJECT_A, TEST_REALM, now.toString());

    byte[] bodyBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);
    String bodySha256 = verifier.computeBodySha256(bodyBytes);
    String timestampHeader = String.valueOf(now.toEpochMilli());
    String signature =
        verifier.sign(
            RAW_SECRET_KEY,
            KEY_ID,
            timestampHeader,
            "POST",
            PasswordResetEventEndpoint.ENDPOINT_PATH,
            sourceEventId,
            bodySha256);

    mockMvc
        .perform(
            post(PasswordResetEventEndpoint.ENDPOINT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PasswordResetEventEndpoint.HEADER_KEY_ID, KEY_ID)
                .header(PasswordResetEventEndpoint.HEADER_TIMESTAMP, timestampHeader)
                .header(PasswordResetEventEndpoint.HEADER_SIGNATURE, signature)
                .header(PasswordResetEventEndpoint.HEADER_SOURCE_EVENT_ID, sourceEventId)
                .header("X-Forwarded-For", "192.168.1.100")
                .header("X-Custom-Trace-Id", "trace-abc-123")
                .header("Cookie", "untrusted-cookie=123")
                .header("Authorization", "Bearer untrusted-token")
                .content(bodyBytes))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DELIVERED"))
        .andExpect(jsonPath("$.sourceEventId").value(sourceEventId))
        .andExpect(jsonPath("$.effectExecuted").value(true));

    // Sessões de Target A foram encerradas; Control B intacto
    assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).isEmpty();
    assertThat(sessionRepository.findByPrincipalName(CONTROL_SUBJECT_B)).hasSize(1);
  }

  @Test
  @DisplayName(
      "Endpoint confirma somente estados terminais e devolve 503 para estados que exigem retry")
  void endpointAcknowledgesOnlyTerminallyDeliveredInboxStates() throws Exception {
    for (String terminalStatus :
        List.of(
            PasswordResetEventInboxEntity.STATUS_DELIVERED,
            PasswordResetEventInboxEntity.STATUS_PROCESSED)) {
      String sourceEventId = "evt-terminal-" + terminalStatus + "-" + UUID.randomUUID();
      int inserted =
          inboxRepository.insertIfAbsent(
              sourceEventId, TARGET_SUBJECT_A, TEST_REALM, "UPDATE_CREDENTIAL", terminalStatus);
      assertThat(inserted).isEqualTo(1);

      performSignedEndpointRequest(sourceEventId)
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value(terminalStatus))
          .andExpect(jsonPath("$.sourceEventId").value(sourceEventId))
          .andExpect(jsonPath("$.effectExecuted").value(false))
          .andExpect(jsonPath("$.lastError").doesNotExist());
    }

    for (String retryableStatus :
        List.of(
            PasswordResetEventInboxEntity.STATUS_PENDING,
            PasswordResetEventInboxEntity.STATUS_CLAIMED,
            PasswordResetEventInboxEntity.STATUS_FAILED)) {
      String sourceEventId = "evt-retryable-" + retryableStatus + "-" + UUID.randomUUID();
      int inserted =
          inboxRepository.insertIfAbsent(
              sourceEventId, TARGET_SUBJECT_A, TEST_REALM, "UPDATE_CREDENTIAL", retryableStatus);
      assertThat(inserted).isEqualTo(1);
      if (PasswordResetEventInboxEntity.STATUS_FAILED.equals(retryableStatus)) {
        inboxRepository.markFailed(sourceEventId, "sensitive downstream detail must stay private");
      }

      performSignedEndpointRequest(sourceEventId)
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.status").value(retryableStatus))
          .andExpect(jsonPath("$.sourceEventId").value(sourceEventId))
          .andExpect(jsonPath("$.effectExecuted").value(false))
          .andExpect(jsonPath("$.lastError").doesNotExist());
    }
  }

  @Test
  @DisplayName(
      "Task 02-30-03: Body que excede o tamanho limite configurado é rejeitado com 413 Payload Too Large")
  void endpointRejectsPayloadExceedingMaxBodySize() throws Exception {
    String sourceEventId = "evt-sec-body-limit-" + UUID.randomUUID();
    // Cria um payload com padding gigante que excede o limite default de 64KB
    String hugePadding = " ".repeat(70000);
    String hugePayload =
        """
                {
                    "sourceEventId": "%s",
                    "subject": "%s",
                    "realmId": "%s",
                    "eventType": "UPDATE_CREDENTIAL",
                    "timestamp": "%s"%s
                }
                """
            .formatted(
                sourceEventId,
                TARGET_SUBJECT_A,
                TEST_REALM,
                clock.instant().toString(),
                hugePadding);

    byte[] bodyBytes = hugePayload.getBytes(StandardCharsets.UTF_8);
    String bodySha256 = verifier.computeBodySha256(bodyBytes);
    String timestampHeader = String.valueOf(clock.instant().toEpochMilli());
    String signature =
        verifier.sign(
            RAW_SECRET_KEY,
            KEY_ID,
            timestampHeader,
            "POST",
            PasswordResetEventEndpoint.ENDPOINT_PATH,
            sourceEventId,
            bodySha256);

    mockMvc
        .perform(
            post(PasswordResetEventEndpoint.ENDPOINT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PasswordResetEventEndpoint.HEADER_KEY_ID, KEY_ID)
                .header(PasswordResetEventEndpoint.HEADER_TIMESTAMP, timestampHeader)
                .header(PasswordResetEventEndpoint.HEADER_SIGNATURE, signature)
                .header(PasswordResetEventEndpoint.HEADER_SOURCE_EVENT_ID, sourceEventId)
                .content(bodyBytes))
        .andExpect(status().isPayloadTooLarge());

    Long count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM public.password_reset_event_inbox WHERE source_event_id = ?",
            Long.class,
            sourceEventId);
    assertThat(count).isZero();
  }

  @Test
  @DisplayName(
      "Task 02-30-03: Payload que não é objeto JSON ou contém campos proibidos é rejeitado com 400 Bad Request")
  void endpointRejectsNonJsonObjectAndProhibitedFields() throws Exception {
    String sourceEventId = "evt-sec-prohibited-" + UUID.randomUUID();
    String timestampHeader = String.valueOf(clock.instant().toEpochMilli());

    // 1. JSON Array é rejeitado
    byte[] arrayBytes = "[{\"sourceEventId\":\"evt-1\"}]".getBytes(StandardCharsets.UTF_8);
    String arraySha256 = verifier.computeBodySha256(arrayBytes);
    String arraySig =
        verifier.sign(
            RAW_SECRET_KEY,
            KEY_ID,
            timestampHeader,
            "POST",
            PasswordResetEventEndpoint.ENDPOINT_PATH,
            "evt-1",
            arraySha256);

    mockMvc
        .perform(
            post(PasswordResetEventEndpoint.ENDPOINT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PasswordResetEventEndpoint.HEADER_KEY_ID, KEY_ID)
                .header(PasswordResetEventEndpoint.HEADER_TIMESTAMP, timestampHeader)
                .header(PasswordResetEventEndpoint.HEADER_SIGNATURE, arraySig)
                .header(PasswordResetEventEndpoint.HEADER_SOURCE_EVENT_ID, "evt-1")
                .content(arrayBytes))
        .andExpect(status().isBadRequest());

    // 2. Campo proibido ("email" ou "password") é rejeitado
    String payloadWithEmail =
        """
                {
                    "sourceEventId": "%s",
                    "subject": "%s",
                    "realmId": "%s",
                    "email": "user@ufu.br",
                    "timestamp": "%s"
                }
                """
            .formatted(sourceEventId, TARGET_SUBJECT_A, TEST_REALM, clock.instant().toString());

    byte[] emailBytes = payloadWithEmail.getBytes(StandardCharsets.UTF_8);
    String emailSha256 = verifier.computeBodySha256(emailBytes);
    String emailSig =
        verifier.sign(
            RAW_SECRET_KEY,
            KEY_ID,
            timestampHeader,
            "POST",
            PasswordResetEventEndpoint.ENDPOINT_PATH,
            sourceEventId,
            emailSha256);

    mockMvc
        .perform(
            post(PasswordResetEventEndpoint.ENDPOINT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PasswordResetEventEndpoint.HEADER_KEY_ID, KEY_ID)
                .header(PasswordResetEventEndpoint.HEADER_TIMESTAMP, timestampHeader)
                .header(PasswordResetEventEndpoint.HEADER_SIGNATURE, emailSig)
                .header(PasswordResetEventEndpoint.HEADER_SOURCE_EVENT_ID, sourceEventId)
                .content(emailBytes))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "Task 02-30-03 & 02-31-01: PasswordResetEventProperties protege segredos em toString e resolve apenas current e previous")
  void propertiesRedactsSecretsInToStringAndResolvesValidKeys() {
    PasswordResetEventProperties testProps = new PasswordResetEventProperties();
    testProps.setCurrentVersion("v2");
    testProps.setPreviousVersion("v1");
    testProps.setKeys(
        Map.of(
            "v2", "MjEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
            "v1", "MTEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
            "v3", "MzEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
            "short", "dG9vLXNob3J0"));

    // Segredos nunca aparecem em toString (PRIV-01)
    assertThat(testProps.toString())
        .doesNotContain("MTEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
        .doesNotContain("MjEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
        .doesNotContain("MzEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=")
        .doesNotContain("dG9vLXNob3J0")
        .contains("currentVersion='v2'")
        .contains("previousVersion='v1'")
        .contains("configuredKeyIds");

    // Chave corrente v2 decodifica com sucesso
    byte[] v2Key = testProps.resolveKey("v2");
    assertThat(v2Key).isNotNull().hasSize(32);

    // Chave anterior v1 decodifica com sucesso
    byte[] v1Key = testProps.resolveKey("v1");
    assertThat(v1Key).isNotNull().hasSize(32);

    // Chave v3 presente no mapa mas que NÃO é current nem previous retorna null (D-40)
    assertThat(testProps.resolveKey("v3")).isNull();

    // Chave curta (< 32 bytes) falha fechada retornando null
    assertThat(testProps.resolveKey("short")).isNull();

    // Chave desconhecida ou nula retorna null
    assertThat(testProps.resolveKey("v4")).isNull();
    assertThat(testProps.resolveKey(null)).isNull();
    assertThat(testProps.resolveKey("   ")).isNull();
  }

  @Test
  @DisplayName(
      "Task 02-30-03: PasswordResetEventProperties exige Base64 válido e não faz fallback para UTF-8")
  void propertiesRequiresStrictBase64WithoutUtf8Fallback() {
    PasswordResetEventProperties props = new PasswordResetEventProperties();
    props.setCurrentVersion("valid-base64");
    props.setPreviousVersion("non-base64");
    props.setKeys(
        Map.of(
            "non-base64", "this-is-not-valid-base64!@#$",
            "valid-base64", "MTEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="));

    // String não-Base64 retorna null (sem fallback para bytes UTF-8)
    assertThat(props.resolveKey("non-base64")).isNull();

    // Base64 válido retorna os bytes decodificados
    assertThat(props.resolveKey("valid-base64")).isNotNull().hasSize(32);
  }

  @Test
  @DisplayName("D-40: chave HMAC de 31 bytes e recusada, e a fronteira de 32 bytes e aceita")
  void hmacKeyMinimumIsExactlyThirtyTwoBytes() {
    PasswordResetEventProperties props = new PasswordResetEventProperties();
    props.setCurrentVersion("v1");
    props.setKeys(Map.of("v1", Base64.getEncoder().encodeToString(new byte[31])));
    assertThat(props.resolveKey("v1")).isNull();
    assertThatThrownBy(
            () -> verifier.sign(new byte[31], "v1", "1", "POST", "/path", "event", "hash"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("32 bytes");

    props.setKeys(Map.of("v1", Base64.getEncoder().encodeToString(new byte[32])));
    assertThat(props.resolveKey("v1")).hasSize(32);
  }

  @Test
  @DisplayName(
      "Task 02-30-03: Verifier canonicaliza literalmente sem normalizar maiúsculas/minúsculas ou trim")
  void verifierCanonicalizesLiterallyWithoutNormalization() {
    String canonical1 =
        verifier.canonicalize(
            "v1", "123", "POST", "/api/v1/internal/events/password-reset", "evt-1", "abcd");
    String canonicalWithSpaces =
        verifier.canonicalize(
            "v1 ", "123", "POST", "/api/v1/internal/events/password-reset", "evt-1", "abcd");
    String canonicalLowerMethod =
        verifier.canonicalize(
            "v1", "123", "post", "/api/v1/internal/events/password-reset", "evt-1", "abcd");
    String canonicalUpperHash =
        verifier.canonicalize(
            "v1", "123", "POST", "/api/v1/internal/events/password-reset", "evt-1", "ABCD");

    assertThat(canonical1)
        .isEqualTo("v1\n123\nPOST\n/api/v1/internal/events/password-reset\nevt-1\nabcd");
    assertThat(canonicalWithSpaces).isNotEqualTo(canonical1);
    assertThat(canonicalLowerMethod).isNotEqualTo(canonical1);
    assertThat(canonicalUpperHash).isNotEqualTo(canonical1);
  }

  @Test
  @DisplayName("Task 02-30-03: Endpoint rejeita payload com campos duplicados com 400 Bad Request")
  void endpointRejectsDuplicateJsonFields() throws Exception {
    String sourceEventId = "evt-sec-dup-" + UUID.randomUUID();
    String jsonPayloadWithDuplicate =
        """
                {
                    "sourceEventId": "%s",
                    "sourceEventId": "%s",
                    "subject": "%s",
                    "realmId": "%s",
                    "eventType": "UPDATE_CREDENTIAL",
                    "timestamp": "%s"
                }
                """
            .formatted(
                sourceEventId,
                sourceEventId,
                TARGET_SUBJECT_A,
                TEST_REALM,
                clock.instant().toString());

    byte[] bodyBytes = jsonPayloadWithDuplicate.getBytes(StandardCharsets.UTF_8);
    String bodySha256 = verifier.computeBodySha256(bodyBytes);
    String timestampHeader = String.valueOf(clock.instant().toEpochMilli());
    String signature =
        verifier.sign(
            RAW_SECRET_KEY,
            KEY_ID,
            timestampHeader,
            "POST",
            PasswordResetEventEndpoint.ENDPOINT_PATH,
            sourceEventId,
            bodySha256);

    mockMvc
        .perform(
            post(PasswordResetEventEndpoint.ENDPOINT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PasswordResetEventEndpoint.HEADER_KEY_ID, KEY_ID)
                .header(PasswordResetEventEndpoint.HEADER_TIMESTAMP, timestampHeader)
                .header(PasswordResetEventEndpoint.HEADER_SIGNATURE, signature)
                .header(PasswordResetEventEndpoint.HEADER_SOURCE_EVENT_ID, sourceEventId)
                .content(bodyBytes))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName(
      "Task 02-30-03: Endpoint rejeita grafias alternativas/aliases como realm_id, issuer ou created_at com 400")
  void endpointRejectsFieldAliases() throws Exception {
    String sourceEventId = "evt-sec-alias-" + UUID.randomUUID();
    String jsonWithRealmIdUnderscore =
        """
                {
                    "sourceEventId": "%s",
                    "subject": "%s",
                    "realm_id": "%s",
                    "eventType": "UPDATE_CREDENTIAL",
                    "timestamp": "%s"
                }
                """
            .formatted(sourceEventId, TARGET_SUBJECT_A, TEST_REALM, clock.instant().toString());

    byte[] bodyBytes = jsonWithRealmIdUnderscore.getBytes(StandardCharsets.UTF_8);
    String bodySha256 = verifier.computeBodySha256(bodyBytes);
    String timestampHeader = String.valueOf(clock.instant().toEpochMilli());
    String signature =
        verifier.sign(
            RAW_SECRET_KEY,
            KEY_ID,
            timestampHeader,
            "POST",
            PasswordResetEventEndpoint.ENDPOINT_PATH,
            sourceEventId,
            bodySha256);

    mockMvc
        .perform(
            post(PasswordResetEventEndpoint.ENDPOINT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PasswordResetEventEndpoint.HEADER_KEY_ID, KEY_ID)
                .header(PasswordResetEventEndpoint.HEADER_TIMESTAMP, timestampHeader)
                .header(PasswordResetEventEndpoint.HEADER_SIGNATURE, signature)
                .header(PasswordResetEventEndpoint.HEADER_SOURCE_EVENT_ID, sourceEventId)
                .content(bodyBytes))
        .andExpect(status().isBadRequest());
  }

  // ---------------------------------------------------------------------------------------------
  // Task 02-31-01: Key Rotation, Replay Window Boundaries and Idempotency Tests
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName(
      "Task 02-31-01: Endpoint aceita chaves current e previous, e rejeita outras chaves configuradas ou desconhecidas (D-40)")
  void endpointAcceptsCurrentAndPreviousKeyVersionsAndRejectsOtherConfiguredKeys()
      throws Exception {
    byte[] keyV1Bytes = Base64.getDecoder().decode("MTEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    byte[] keyV2Bytes = Base64.getDecoder().decode("MjEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    byte[] keyV3Bytes = Base64.getDecoder().decode("MzEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");

    properties.setCurrentVersion("v2");
    properties.setPreviousVersion("v1");
    properties.setKeys(
        Map.of(
            "v1", "MTEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
            "v2", "MjEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
            "v3", "MzEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="));

    // 1. Chave corrente v2 é aceita
    SelectiveTouchSessionRepository.SelectiveSession session1 =
        createAuthenticatedSession(TARGET_SUBJECT_A);
    sessionRepository.save(session1);

    String sourceEventIdV2 = "evt-key-v2-" + UUID.randomUUID();
    Instant now = clock.instant();
    String jsonPayloadV2 =
        """
                {
                    "sourceEventId": "%s",
                    "subject": "%s",
                    "realmId": "%s",
                    "eventType": "UPDATE_CREDENTIAL",
                    "timestamp": "%s"
                }
                """
            .formatted(sourceEventIdV2, TARGET_SUBJECT_A, TEST_REALM, now.toString());
    byte[] bodyV2 = jsonPayloadV2.getBytes(StandardCharsets.UTF_8);
    String timestampHeader = String.valueOf(now.toEpochMilli());
    String sigV2 =
        verifier.sign(
            keyV2Bytes,
            "v2",
            timestampHeader,
            "POST",
            PasswordResetEventEndpoint.ENDPOINT_PATH,
            sourceEventIdV2,
            verifier.computeBodySha256(bodyV2));

    mockMvc
        .perform(
            post(PasswordResetEventEndpoint.ENDPOINT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PasswordResetEventEndpoint.HEADER_KEY_ID, "v2")
                .header(PasswordResetEventEndpoint.HEADER_TIMESTAMP, timestampHeader)
                .header(PasswordResetEventEndpoint.HEADER_SIGNATURE, sigV2)
                .header(PasswordResetEventEndpoint.HEADER_SOURCE_EVENT_ID, sourceEventIdV2)
                .content(bodyV2))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DELIVERED"))
        .andExpect(jsonPath("$.effectExecuted").value(true));

    assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).isEmpty();

    // 2. Chave anterior v1 é aceita
    SelectiveTouchSessionRepository.SelectiveSession session2 =
        createAuthenticatedSession(TARGET_SUBJECT_A);
    sessionRepository.save(session2);

    String sourceEventIdV1 = "evt-key-v1-" + UUID.randomUUID();
    String jsonPayloadV1 =
        """
                {
                    "sourceEventId": "%s",
                    "subject": "%s",
                    "realmId": "%s",
                    "eventType": "UPDATE_CREDENTIAL",
                    "timestamp": "%s"
                }
                """
            .formatted(sourceEventIdV1, TARGET_SUBJECT_A, TEST_REALM, now.toString());
    byte[] bodyV1 = jsonPayloadV1.getBytes(StandardCharsets.UTF_8);
    String sigV1 =
        verifier.sign(
            keyV1Bytes,
            "v1",
            timestampHeader,
            "POST",
            PasswordResetEventEndpoint.ENDPOINT_PATH,
            sourceEventIdV1,
            verifier.computeBodySha256(bodyV1));

    mockMvc
        .perform(
            post(PasswordResetEventEndpoint.ENDPOINT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PasswordResetEventEndpoint.HEADER_KEY_ID, "v1")
                .header(PasswordResetEventEndpoint.HEADER_TIMESTAMP, timestampHeader)
                .header(PasswordResetEventEndpoint.HEADER_SIGNATURE, sigV1)
                .header(PasswordResetEventEndpoint.HEADER_SOURCE_EVENT_ID, sourceEventIdV1)
                .content(bodyV1))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DELIVERED"))
        .andExpect(jsonPath("$.effectExecuted").value(true));

    assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).isEmpty();

    // 3. Chave v3 configurada no properties mas que NÃO é current nem previous é REJEITADA com 401
    SelectiveTouchSessionRepository.SelectiveSession session3 =
        createAuthenticatedSession(TARGET_SUBJECT_A);
    sessionRepository.save(session3);

    String sourceEventIdV3 = "evt-key-v3-" + UUID.randomUUID();
    String jsonPayloadV3 =
        """
                {
                    "sourceEventId": "%s",
                    "subject": "%s",
                    "realmId": "%s",
                    "eventType": "UPDATE_CREDENTIAL",
                    "timestamp": "%s"
                }
                """
            .formatted(sourceEventIdV3, TARGET_SUBJECT_A, TEST_REALM, now.toString());
    byte[] bodyV3 = jsonPayloadV3.getBytes(StandardCharsets.UTF_8);
    String sigV3 =
        verifier.sign(
            keyV3Bytes,
            "v3",
            timestampHeader,
            "POST",
            PasswordResetEventEndpoint.ENDPOINT_PATH,
            sourceEventIdV3,
            verifier.computeBodySha256(bodyV3));

    mockMvc
        .perform(
            post(PasswordResetEventEndpoint.ENDPOINT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PasswordResetEventEndpoint.HEADER_KEY_ID, "v3")
                .header(PasswordResetEventEndpoint.HEADER_TIMESTAMP, timestampHeader)
                .header(PasswordResetEventEndpoint.HEADER_SIGNATURE, sigV3)
                .header(PasswordResetEventEndpoint.HEADER_SOURCE_EVENT_ID, sourceEventIdV3)
                .content(bodyV3))
        .andExpect(status().isUnauthorized());

    // Sessão sobrevive intacta porque chave v3 foi rejeitada
    assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).hasSize(1);

    // 4. Chave desconhecida v4 é REJEITADA com 401
    String sourceEventIdV4 = "evt-key-v4-" + UUID.randomUUID();
    String jsonPayloadV4 =
        """
                {
                    "sourceEventId": "%s",
                    "subject": "%s",
                    "realmId": "%s",
                    "eventType": "UPDATE_CREDENTIAL",
                    "timestamp": "%s"
                }
                """
            .formatted(sourceEventIdV4, TARGET_SUBJECT_A, TEST_REALM, now.toString());
    byte[] bodyV4 = jsonPayloadV4.getBytes(StandardCharsets.UTF_8);
    String sigV4 =
        verifier.sign(
            keyV1Bytes,
            "v4",
            timestampHeader,
            "POST",
            PasswordResetEventEndpoint.ENDPOINT_PATH,
            sourceEventIdV4,
            verifier.computeBodySha256(bodyV4));

    mockMvc
        .perform(
            post(PasswordResetEventEndpoint.ENDPOINT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PasswordResetEventEndpoint.HEADER_KEY_ID, "v4")
                .header(PasswordResetEventEndpoint.HEADER_TIMESTAMP, timestampHeader)
                .header(PasswordResetEventEndpoint.HEADER_SIGNATURE, sigV4)
                .header(PasswordResetEventEndpoint.HEADER_SOURCE_EVENT_ID, sourceEventIdV4)
                .content(bodyV4))
        .andExpect(status().isUnauthorized());

    assertThat(sessionRepository.findByPrincipalName(TARGET_SUBJECT_A)).hasSize(1);
  }

  @Test
  @DisplayName(
      "Task 02-31-01: Endpoint aplica janela exata de ±5 minutos (300s) e rejeita timestamp fora da janela ou malformado (D-40)")
  void endpointEnforcesFiveMinuteReplayWindowBoundaries() throws Exception {
    properties.setCurrentVersion("v1");
    properties.setPreviousVersion(null);
    properties.setKeys(Map.of("v1", "MTEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="));

    Instant now = clock.instant();

    // 1. Timestamp no presente (now) -> 200 OK
    assertEndpointTimestampAccepted(now, "evt-time-now-");

    // 2. Limite exato no passado: now - 5 minutos (300 segundos) -> 200 OK
    assertEndpointTimestampAccepted(now.minusSeconds(300), "evt-time-minus-5m-");

    // 3. Limite exato no futuro: now + 5 minutos (300 segundos) -> 200 OK
    assertEndpointTimestampAccepted(now.plusSeconds(300), "evt-time-plus-5m-");

    // 4. Fora da janela no passado: now - 301 segundos -> 401 Unauthorized
    assertEndpointTimestampRejected(now.minusSeconds(301), "evt-time-past-301s-");

    // 5. Fora da janela no futuro: now + 301 segundos -> 401 Unauthorized
    assertEndpointTimestampRejected(now.plusSeconds(301), "evt-time-future-301s-");

    // 6. Timestamp malformado / não numérico -> 401 Unauthorized
    String sourceEventIdMalformed = "evt-time-malformed-" + UUID.randomUUID();
    String jsonPayload =
        """
                {
                    "sourceEventId": "%s",
                    "subject": "%s",
                    "realmId": "%s",
                    "eventType": "UPDATE_CREDENTIAL",
                    "timestamp": "%s"
                }
                """
            .formatted(sourceEventIdMalformed, TARGET_SUBJECT_A, TEST_REALM, now.toString());
    byte[] bodyBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);
    String bodySha256 = verifier.computeBodySha256(bodyBytes);
    String malformedTimestamp = "not-a-valid-timestamp";
    String sig =
        verifier.sign(
            RAW_SECRET_KEY,
            KEY_ID,
            malformedTimestamp,
            "POST",
            PasswordResetEventEndpoint.ENDPOINT_PATH,
            sourceEventIdMalformed,
            bodySha256);

    mockMvc
        .perform(
            post(PasswordResetEventEndpoint.ENDPOINT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PasswordResetEventEndpoint.HEADER_KEY_ID, KEY_ID)
                .header(PasswordResetEventEndpoint.HEADER_TIMESTAMP, malformedTimestamp)
                .header(PasswordResetEventEndpoint.HEADER_SIGNATURE, sig)
                .header(PasswordResetEventEndpoint.HEADER_SOURCE_EVENT_ID, sourceEventIdMalformed)
                .content(bodyBytes))
        .andExpect(status().isUnauthorized());

    // 7. Long.MIN_VALUE must be rejected as 401 instead of overflowing into HTTP 500.
    String extremeTimestamp = Long.toString(Long.MIN_VALUE);
    String extremeEventId = "evt-time-extreme-" + UUID.randomUUID();
    String extremePayload = jsonPayload.replace(sourceEventIdMalformed, extremeEventId);
    byte[] extremeBody = extremePayload.getBytes(StandardCharsets.UTF_8);
    String extremeSignature =
        verifier.sign(
            RAW_SECRET_KEY,
            KEY_ID,
            extremeTimestamp,
            "POST",
            PasswordResetEventEndpoint.ENDPOINT_PATH,
            extremeEventId,
            verifier.computeBodySha256(extremeBody));
    mockMvc
        .perform(
            post(PasswordResetEventEndpoint.ENDPOINT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PasswordResetEventEndpoint.HEADER_KEY_ID, KEY_ID)
                .header(PasswordResetEventEndpoint.HEADER_TIMESTAMP, extremeTimestamp)
                .header(PasswordResetEventEndpoint.HEADER_SIGNATURE, extremeSignature)
                .header(PasswordResetEventEndpoint.HEADER_SOURCE_EVENT_ID, extremeEventId)
                .content(extremeBody))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName(
      "Task 02-31-01: Verifier valida parsing de timestamps (millis, seconds, ISO-8601) e tolerância de 5 minutos")
  void verifierEnforcesTimestampParsingAndToleranceWindow() {
    // A janela so e verificavel se o verificador usar o relogio injetado. Se ele cair no
    // Clock.systemUTC() do construtor sem argumento, as fronteiras exatas (+-300s) passam a
    // depender do tempo real decorrido entre montar a requisicao e avaliar o envelope, e a
    // prova vira instavel em vez de discriminante.
    assertThat(verifier.getClock())
        .as("o verificador precisa usar o Clock injetado, nao Clock.systemUTC()")
        .isSameAs(clock);

    Instant now = clock.instant();

    // 1. Parsing epoch millis
    String epochMillisStr = String.valueOf(now.toEpochMilli());
    Optional<Instant> parsedMillis = verifier.parseTimestamp(epochMillisStr);
    assertThat(parsedMillis).isPresent();
    assertThat(parsedMillis.get().toEpochMilli()).isEqualTo(now.toEpochMilli());

    // 2. Parsing epoch seconds
    String epochSecStr = String.valueOf(now.getEpochSecond());
    Optional<Instant> parsedSec = verifier.parseTimestamp(epochSecStr);
    assertThat(parsedSec).isPresent();
    assertThat(parsedSec.get().getEpochSecond()).isEqualTo(now.getEpochSecond());

    // 3. Parsing ISO-8601
    String isoStr = now.toString();
    Optional<Instant> parsedIso = verifier.parseTimestamp(isoStr);
    assertThat(parsedIso).isPresent();
    assertThat(parsedIso.get()).isEqualTo(now);

    // 4. Invalid timestamp string returns empty
    assertThat(verifier.parseTimestamp(null)).isEmpty();
    assertThat(verifier.parseTimestamp("")).isEmpty();
    assertThat(verifier.parseTimestamp("   ")).isEmpty();
    assertThat(verifier.parseTimestamp("invalid-timestamp-format")).isEmpty();

    // 5. isTimestampValid tolerance check against now
    assertThat(verifier.isTimestampValid(epochMillisStr)).isTrue();
    assertThat(verifier.isTimestampValid(String.valueOf(now.minusSeconds(300).toEpochMilli())))
        .isTrue();
    assertThat(verifier.isTimestampValid(String.valueOf(now.plusSeconds(300).toEpochMilli())))
        .isTrue();
    assertThat(verifier.isTimestampValid(String.valueOf(now.minusSeconds(301).toEpochMilli())))
        .isFalse();
    assertThat(verifier.isTimestampValid(String.valueOf(now.plusSeconds(301).toEpochMilli())))
        .isFalse();
    assertThat(verifier.isTimestampValid("invalid")).isFalse();
  }

  private void assertEndpointTimestampAccepted(Instant eventInstant, String prefix)
      throws Exception {
    String sourceEventId = prefix + UUID.randomUUID();
    String jsonPayload =
        """
                {
                    "sourceEventId": "%s",
                    "subject": "%s",
                    "realmId": "%s",
                    "eventType": "UPDATE_CREDENTIAL",
                    "timestamp": "%s"
                }
                """
            .formatted(sourceEventId, TARGET_SUBJECT_A, TEST_REALM, eventInstant.toString());
    byte[] bodyBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);
    String bodySha256 = verifier.computeBodySha256(bodyBytes);
    String timestampHeader = String.valueOf(eventInstant.toEpochMilli());
    String signature =
        verifier.sign(
            RAW_SECRET_KEY,
            KEY_ID,
            timestampHeader,
            "POST",
            PasswordResetEventEndpoint.ENDPOINT_PATH,
            sourceEventId,
            bodySha256);

    mockMvc
        .perform(
            post(PasswordResetEventEndpoint.ENDPOINT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PasswordResetEventEndpoint.HEADER_KEY_ID, KEY_ID)
                .header(PasswordResetEventEndpoint.HEADER_TIMESTAMP, timestampHeader)
                .header(PasswordResetEventEndpoint.HEADER_SIGNATURE, signature)
                .header(PasswordResetEventEndpoint.HEADER_SOURCE_EVENT_ID, sourceEventId)
                .content(bodyBytes))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DELIVERED"))
        .andExpect(jsonPath("$.effectExecuted").value(true));
  }

  private org.springframework.test.web.servlet.ResultActions performSignedEndpointRequest(
      String sourceEventId) throws Exception {
    Instant now = clock.instant();
    String jsonPayload =
        """
                {
                    "sourceEventId": "%s",
                    "subject": "%s",
                    "realmId": "%s",
                    "eventType": "UPDATE_CREDENTIAL",
                    "timestamp": "%s"
                }
                """
            .formatted(sourceEventId, TARGET_SUBJECT_A, TEST_REALM, now.toString());
    byte[] bodyBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);
    String timestampHeader = String.valueOf(now.toEpochMilli());
    String signature =
        verifier.sign(
            RAW_SECRET_KEY,
            KEY_ID,
            timestampHeader,
            "POST",
            PasswordResetEventEndpoint.ENDPOINT_PATH,
            sourceEventId,
            verifier.computeBodySha256(bodyBytes));

    return mockMvc.perform(
        post(PasswordResetEventEndpoint.ENDPOINT_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .header(PasswordResetEventEndpoint.HEADER_KEY_ID, KEY_ID)
            .header(PasswordResetEventEndpoint.HEADER_TIMESTAMP, timestampHeader)
            .header(PasswordResetEventEndpoint.HEADER_SIGNATURE, signature)
            .header(PasswordResetEventEndpoint.HEADER_SOURCE_EVENT_ID, sourceEventId)
            .content(bodyBytes));
  }

  private void assertEndpointTimestampRejected(Instant eventInstant, String prefix)
      throws Exception {
    String sourceEventId = prefix + UUID.randomUUID();
    String jsonPayload =
        """
                {
                    "sourceEventId": "%s",
                    "subject": "%s",
                    "realmId": "%s",
                    "eventType": "UPDATE_CREDENTIAL",
                    "timestamp": "%s"
                }
                """
            .formatted(sourceEventId, TARGET_SUBJECT_A, TEST_REALM, eventInstant.toString());
    byte[] bodyBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);
    String bodySha256 = verifier.computeBodySha256(bodyBytes);
    String timestampHeader = String.valueOf(eventInstant.toEpochMilli());
    String signature =
        verifier.sign(
            RAW_SECRET_KEY,
            KEY_ID,
            timestampHeader,
            "POST",
            PasswordResetEventEndpoint.ENDPOINT_PATH,
            sourceEventId,
            bodySha256);

    mockMvc
        .perform(
            post(PasswordResetEventEndpoint.ENDPOINT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .header(PasswordResetEventEndpoint.HEADER_KEY_ID, KEY_ID)
                .header(PasswordResetEventEndpoint.HEADER_TIMESTAMP, timestampHeader)
                .header(PasswordResetEventEndpoint.HEADER_SIGNATURE, signature)
                .header(PasswordResetEventEndpoint.HEADER_SOURCE_EVENT_ID, sourceEventId)
                .content(bodyBytes))
        .andExpect(status().isUnauthorized());
  }

  private static void assertSqlStateAndConstraint(
      DataIntegrityViolationException failure, String expectedSqlState, String expectedConstraint) {
    assertThat(failure).isNotNull();
    assertThat(failure.getMostSpecificCause()).isInstanceOf(PSQLException.class);

    PSQLException postgresFailure = (PSQLException) failure.getMostSpecificCause();
    assertThat(postgresFailure.getSQLState()).isEqualTo(expectedSqlState);
    assertThat(postgresFailure.getServerErrorMessage()).isNotNull();
    assertThat(postgresFailure.getServerErrorMessage().getConstraint())
        .isEqualTo(expectedConstraint);
  }

  private SelectiveTouchSessionRepository.SelectiveSession createAuthenticatedSession(
      String subject) {
    saveAuthorizedClient(subject);
    SelectiveTouchSessionRepository.SelectiveSession session = sessionRepository.createSession();
    Instant now = clock.instant();

    OidcIdToken idToken =
        OidcIdToken.withTokenValue("inbox-token-" + subject)
            .issuer("https://idp.invalid")
            .subject(subject)
            .claim("preferred_username", "user-" + subject)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(3600))
            .build();

    DefaultOidcUser oidcUser =
        new DefaultOidcUser(
            AuthorityUtils.createAuthorityList("ROLE_USER"), idToken, IdTokenClaimNames.SUB);

    OAuth2AuthenticationToken authentication =
        new OAuth2AuthenticationToken(oidcUser, oidcUser.getAuthorities(), "keycloak");

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
    OAuth2AuthorizedClient client =
        new OAuth2AuthorizedClient(
            registration,
            principalName,
            new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "test-access-token",
                issuedAt,
                issuedAt.plusSeconds(300)),
            new OAuth2RefreshToken("test-refresh-token", issuedAt));
    authorizedClientService.saveAuthorizedClient(
        client, new TestingAuthenticationToken(principalName, "not-used", "ROLE_USER"));
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
    AuditRecorder testAuditRecorder() {
      return entry -> {};
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("betufu-inbox-it")
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
              .clientName("BETUFU Password Reset Event Inbox IT")
              .build();
      return new InMemoryClientRegistrationRepository(registration);
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
