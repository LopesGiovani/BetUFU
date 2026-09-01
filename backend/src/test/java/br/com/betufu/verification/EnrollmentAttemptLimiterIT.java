package br.com.betufu.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import br.com.betufu.platform.session.RedisContainerSupport;
import br.com.betufu.verification.internal.EnrollmentAttemptLimiter;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    classes = {
      EnrollmentAttemptLimiter.class,
      EnrollmentAttemptLimiterIT.TestConfigurationContext.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.docker.compose.enabled=false",
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
          + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
      "BETUFU_DB_JDBC_URL=jdbc:postgresql://database.invalid/betufu",
      "BETUFU_DB_USER=unused",
      "BETUFU_DB_PASSWORD=unused",
      "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
      "BETUFU_OIDC_CLIENT_ID=betufu-enrollment-limiter-it",
      "BETUFU_OIDC_CLIENT_SECRET=unused"
    })
@ActiveProfiles("integrated")
@Testcontainers
@Import(EnrollmentAttemptLimiterIT.TestConfigurationContext.class)
class EnrollmentAttemptLimiterIT {

  @Container static final GenericContainer<?> REDIS = RedisContainerSupport.REDIS;

  @DynamicPropertySource
  static void redisProperties(DynamicPropertyRegistry registry) {
    RedisContainerSupport.registerProperties(registry);
  }

  @Autowired private EnrollmentAttemptLimiter attemptLimiter;

  @Autowired private StringRedisTemplate redisTemplate;

  @BeforeEach
  void cleanRedis() {
    var connection = redisTemplate.getConnectionFactory().getConnection();
    connection.serverCommands().flushDb();
  }

  @Test
  @DisplayName("D-30: Operações bem-sucedidas desfazem a reserva e não consomem cota de tentativas")
  void successfulOperationsDoNotConsumeAttemptQuota() {
    UUID identityId = UUID.randomUUID();
    String originIp = "192.168.1.100";

    for (int i = 0; i < 10; i++) {
      assertThat(attemptLimiter.tryAcquire(identityId, originIp)).isTrue();
      attemptLimiter.release(identityId, originIp);
    }

    assertThat(attemptLimiter.tryAcquire(identityId, originIp)).isTrue();
  }

  @Test
  @DisplayName(
      "D-30 / D-31: Limite por conta bloqueia após 5 recusas na janela de 15 minutos sem afetar outras contas")
  void accountQuotaEnforcesLimitAfterFiveRejectionsIndependently() {
    UUID identityA = UUID.randomUUID();
    UUID identityB = UUID.randomUUID();
    String originIp = "192.168.1.101";

    for (int i = 0; i < 5; i++) {
      assertThat(attemptLimiter.tryAcquire(identityA, originIp)).isTrue();
      // Rejeição conserva a reserva
    }

    // 6a tentativa da conta A deve ser bloqueada
    assertThat(attemptLimiter.tryAcquire(identityA, originIp)).isFalse();

    // Conta B na mesma origem permanece permitida
    assertThat(attemptLimiter.tryAcquire(identityB, originIp)).isTrue();
  }

  @Test
  @DisplayName("D-30: Limite por origem bloqueia requisições excedentes mesmo com contas distintas")
  void originQuotaEnforcesLimitAcrossMultipleAccounts() {
    String originIp = "192.168.1.102";
    String differentOriginIp = "192.168.1.103";

    // Registra 50 recusas na mesma origem IP com contas distintas
    for (int i = 0; i < 50; i++) {
      UUID randomIdentity = UUID.randomUUID();
      assertThat(attemptLimiter.tryAcquire(randomIdentity, originIp)).isTrue();
      // Rejeição conserva a reserva
    }

    // Próxima conta na mesma origem bloqueada
    UUID freshIdentitySameOrigin = UUID.randomUUID();
    assertThat(attemptLimiter.tryAcquire(freshIdentitySameOrigin, originIp)).isFalse();

    // Origem diferente com nova conta permanece permitida
    UUID freshIdentityOtherOrigin = UUID.randomUUID();
    assertThat(attemptLimiter.tryAcquire(freshIdentityOtherOrigin, differentOriginIp)).isTrue();
  }

  @Test
  @DisplayName(
      "D-30: Chaves de limite por conta e por origem possuem expiração TTL configurada (15 min e 1 hora)")
  void limiterKeysHaveAppropriateTtlInRedis() {
    UUID identityId = UUID.randomUUID();
    String originIp = "192.168.1.104";

    assertThat(attemptLimiter.tryAcquire(identityId, originIp)).isTrue();

    String accountKey = "betufu:verification:limiter:account:" + identityId;
    String originKey = "betufu:verification:limiter:origin:" + originIp;

    // Ambas as chaves devem estar presentes
    assertThat(redisTemplate.hasKey(accountKey)).isTrue();
    assertThat(redisTemplate.hasKey(originKey)).isTrue();

    Long accountTtl = redisTemplate.getExpire(accountKey);
    Long originTtl = redisTemplate.getExpire(originKey);

    // Conta: 15 minutos = 900 segundos (tolerância curta: entre 840s e 900s)
    assertThat(accountTtl).isNotNull().isGreaterThanOrEqualTo(840L).isLessThanOrEqualTo(900L);

    // Origem: 1 hora = 3600 segundos (tolerância curta: entre 3540s e 3600s)
    assertThat(originTtl).isNotNull().isGreaterThanOrEqualTo(3540L).isLessThanOrEqualTo(3600L);
  }

  @Test
  @DisplayName(
      "Fail-closed: sem StringRedisTemplate disponível no ObjectProvider, tryAcquire retorna false e release não lança")
  void failsClosedWhenRedisTemplateIsUnavailable() {
    ObjectProvider<StringRedisTemplate> emptyProvider =
        new ObjectProvider<>() {
          @Override
          public StringRedisTemplate getObject() {
            return null;
          }

          @Override
          public StringRedisTemplate getObject(Object... args) {
            return null;
          }

          @Override
          public StringRedisTemplate getIfAvailable() {
            return null;
          }

          @Override
          public StringRedisTemplate getIfUnique() {
            return null;
          }
        };

    EnrollmentAttemptLimiter limiterWithoutRedis = new EnrollmentAttemptLimiter(emptyProvider);

    UUID identityId = UUID.randomUUID();
    String originIp = "192.168.1.106";

    // tryAcquire deve falhar fechado (retornar false)
    assertThat(limiterWithoutRedis.tryAcquire(identityId, originIp)).isFalse();
    assertThat(limiterWithoutRedis.tryAcquire(null, null)).isFalse();

    // release não deve lançar exceção
    assertDoesNotThrow(() -> limiterWithoutRedis.release(identityId, originIp));
    assertDoesNotThrow(() -> limiterWithoutRedis.release(null, null));

    // Também fail-closed com provider nulo
    EnrollmentAttemptLimiter limiterWithNullProvider = new EnrollmentAttemptLimiter(null);
    assertThat(limiterWithNullProvider.tryAcquire(identityId, originIp)).isFalse();
    assertDoesNotThrow(() -> limiterWithNullProvider.release(identityId, originIp));
  }

  @Test
  @DisplayName("Fail-closed: identidade ou origem nula/em branco falha fechado")
  void failsClosedWhenIdentityOrOriginIsInvalid() {
    UUID identityId = UUID.randomUUID();
    String originIp = "192.168.1.105";

    assertThat(attemptLimiter.tryAcquire(null, originIp)).isFalse();
    assertThat(attemptLimiter.tryAcquire(identityId, null)).isFalse();
    assertThat(attemptLimiter.tryAcquire(identityId, "   ")).isFalse();

    assertDoesNotThrow(() -> attemptLimiter.release(null, originIp));
    assertDoesNotThrow(() -> attemptLimiter.release(identityId, null));
    assertDoesNotThrow(() -> attemptLimiter.release(identityId, "   "));
  }

  @Test
  @DisplayName(
      "Fail-closed: contador não numérico, negativo ou chave sem TTL válido no Redis nega reserva")
  void failsClosedWhenCounterIsNonNumericOrNegativeOrMissingTtl() {
    UUID identityA = UUID.randomUUID();
    UUID identityB = UUID.randomUUID();
    UUID identityC = UUID.randomUUID();
    UUID identityD = UUID.randomUUID();
    String validOrigin = "192.168.1.108";
    String corruptedOrigin = "192.168.1.109";

    String keyA = "betufu:verification:limiter:account:" + identityA;
    String keyB = "betufu:verification:limiter:account:" + identityB;
    String keyC = "betufu:verification:limiter:account:" + identityC;

    // 1. Contador de conta não numérico
    redisTemplate.opsForValue().set(keyA, "not-a-number", Duration.ofMinutes(15));
    assertThat(attemptLimiter.tryAcquire(identityA, validOrigin)).isFalse();

    // 2. Contador de conta negativo
    redisTemplate.opsForValue().set(keyB, "-5", Duration.ofMinutes(15));
    assertThat(attemptLimiter.tryAcquire(identityB, validOrigin)).isFalse();

    // 3. Chave de conta existente sem TTL (persistida sem expiração)
    redisTemplate.opsForValue().set(keyC, "2");
    redisTemplate.persist(keyC);
    assertThat(attemptLimiter.tryAcquire(identityC, validOrigin)).isFalse();

    // 4. Contador de origem não numérico
    String originKey = "betufu:verification:limiter:origin:" + corruptedOrigin;
    redisTemplate.opsForValue().set(originKey, "corrupt", Duration.ofHours(1));
    assertThat(attemptLimiter.tryAcquire(identityD, corruptedOrigin)).isFalse();
  }

  @Test
  @DisplayName(
      "Concorrência / Discriminante: 20 requisições simultâneas reservam atomicamente sem permitir estouro de cota")
  void concurrentRequestsAtomicallyEnforceAccountQuotaDiscriminant() throws Exception {
    UUID identityId = UUID.randomUUID();
    String originIp = "192.168.1.107";

    // Consome 4 tentativas previamente
    for (int i = 0; i < 4; i++) {
      assertThat(attemptLimiter.tryAcquire(identityId, originIp)).isTrue();
    }

    // Resta exatamente 1 tentativa permitida para a conta
    int threadCount = 20;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger rejectCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
      executor.submit(
          () -> {
            try {
              startLatch.await();
              boolean acquired = attemptLimiter.tryAcquire(identityId, originIp);
              if (acquired) {
                successCount.incrementAndGet();
              } else {
                rejectCount.incrementAndGet();
              }
            } catch (InterruptedException ignored) {
              Thread.currentThread().interrupt();
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startLatch.countDown();
    boolean finished = doneLatch.await(10, TimeUnit.SECONDS);
    executor.shutdown();

    assertThat(finished).isTrue();
    // Exatamente 1 thread deve ter conseguido a 5a reserva, e 19 foram rejeitadas
    assertThat(successCount.get()).isEqualTo(1);
    assertThat(rejectCount.get()).isEqualTo(19);

    // Tentativa subsequente é rejeitada
    assertThat(attemptLimiter.tryAcquire(identityId, originIp)).isFalse();

    // O valor final no Redis deve ser exatamente 5
    String accountKey = "betufu:verification:limiter:account:" + identityId;
    String val = redisTemplate.opsForValue().get(accountKey);
    assertThat(val).isEqualTo("5");
  }

  @Test
  @DisplayName(
      "D-30 / D-31: Limitador jamais expõe saldo de tentativas, contagem ou tamanho de janela")
  void limiterDoesNotExposeCountsOrWindowSize() {
    UUID identityId = UUID.randomUUID();
    String originIp = "192.168.1.105";

    for (int i = 0; i < 5; i++) {
      attemptLimiter.tryAcquire(identityId, originIp);
    }

    boolean allowed = attemptLimiter.tryAcquire(identityId, originIp);
    assertThat(allowed).isFalse();

    // Prova que toString() do limiter não expõe contadores ou janelas
    assertThat(attemptLimiter.toString())
        .doesNotContain("remaining")
        .doesNotContain("attemptsLeft")
        .doesNotContain("windowSize");
  }

  @TestConfiguration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class TestConfigurationContext {

    @Bean
    StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
      return new StringRedisTemplate(connectionFactory);
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("keycloak")
              .clientId("betufu-enrollment-limiter-it")
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
              .clientName("BETUFU Enrollment Limiter IT")
              .build();
      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
