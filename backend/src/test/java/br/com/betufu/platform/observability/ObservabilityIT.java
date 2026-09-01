package br.com.betufu.platform.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

/**
 * Integration test for request correlation, MDC isolation, and log sanitization — Plan 01-12, Task
 * 2.
 *
 * <p>Exercises every {@code [EDGE OPS-03]} criterion using a permissive security config and a probe
 * controller, in isolation from authentication and infrastructure.
 *
 * <p>Each test is paired with a mutation that must turn it red when the corresponding
 * filter/sanitizer behaviour is removed or degraded.
 */
@SpringBootTest(
    classes = {
      ObservabilityIT.TestApplication.class,
      CorrelationIdFilter.class,
      ObservabilityIT.ObsProbeController.class
    },
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.autoconfigure.exclude="
          + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
          + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
          + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
          + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration,"
          + "org.springframework.boot.session.autoconfigure.SessionAutoConfiguration,"
          + "org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration"
    })
class ObservabilityIT {

  private static final String ECHO_PATH = "/api/v1/obs-probe/echo";

  @Autowired private WebApplicationContext context;

  @Autowired private CorrelationIdFilter correlationIdFilter;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).addFilters(correlationIdFilter).build();
  }

  // ── EDGE OPS-03/ordering: valid header is preserved ───────────────────────

  @Test
  void validCorrelationIdIsPreserved() throws Exception {
    String clientId = "client-corr-" + UUID.randomUUID();

    mockMvc
        .perform(get(ECHO_PATH).header(CorrelationIdFilter.HEADER_NAME, clientId))
        .andExpect(status().isOk())
        .andExpect(header().string(CorrelationIdFilter.HEADER_NAME, clientId));
  }

  // ── EDGE OPS-03/empty: missing header generates new ID ────────────────────

  @Test
  void missingHeaderGeneratesNewId() throws Exception {
    MvcResult result = mockMvc.perform(get(ECHO_PATH)).andExpect(status().isOk()).andReturn();

    String correlationId = result.getResponse().getHeader(CorrelationIdFilter.HEADER_NAME);
    assertThat(correlationId).isNotNull().isNotEmpty();
    // Must be a valid UUID (the filter generates UUID.randomUUID().toString())
    assertThat(UUID.fromString(correlationId)).isNotNull();
  }

  // ── EDGE OPS-03/empty: empty header generates new ID ──────────────────────

  @Test
  void emptyHeaderGeneratesNewId() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get(ECHO_PATH).header(CorrelationIdFilter.HEADER_NAME, ""))
            .andExpect(status().isOk())
            .andReturn();

    String correlationId = result.getResponse().getHeader(CorrelationIdFilter.HEADER_NAME);
    assertThat(correlationId).isNotEmpty();
    // Generated ID should be a UUID, not the empty string
    assertThat(UUID.fromString(correlationId)).isNotNull();
  }

  // ── EDGE OPS-03/encoding: invalid Unicode header is rejected ──────────────

  @Test
  void invalidUnicodeHeaderIsRejected() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get(ECHO_PATH).header(CorrelationIdFilter.HEADER_NAME, "inject\u0000evil\rlog"))
            .andExpect(status().isOk())
            .andReturn();

    String correlationId = result.getResponse().getHeader(CorrelationIdFilter.HEADER_NAME);
    assertThat(correlationId)
        .doesNotContain("\u0000")
        .doesNotContain("\r")
        .doesNotContain("inject")
        .doesNotContain("evil");
    // Should be a fresh UUID
    assertThat(UUID.fromString(correlationId)).isNotNull();
  }

  // ── EDGE OPS-03/encoding: over-long header is rejected ────────────────────

  @Test
  void overLongHeaderIsRejected() throws Exception {
    String longValue = "a".repeat(65); // exceeds MAX_LENGTH of 64

    MvcResult result =
        mockMvc
            .perform(get(ECHO_PATH).header(CorrelationIdFilter.HEADER_NAME, longValue))
            .andExpect(status().isOk())
            .andReturn();

    String correlationId = result.getResponse().getHeader(CorrelationIdFilter.HEADER_NAME);
    assertThat(correlationId).isNotEqualTo(longValue);
    assertThat(UUID.fromString(correlationId)).isNotNull();
  }

  // ── EDGE OPS-03/adjacency: sequential requests do not share MDC ───────────

  @Test
  void sequentialRequestsDoNotShareMdc() throws Exception {
    MvcResult first = mockMvc.perform(get(ECHO_PATH)).andExpect(status().isOk()).andReturn();
    String firstId = first.getResponse().getHeader(CorrelationIdFilter.HEADER_NAME);

    // After the request completes, the thread's MDC must be clean
    assertThat(org.slf4j.MDC.get(CorrelationIdFilter.MDC_KEY))
        .as("MDC must be cleared after request completion")
        .isNull();

    MvcResult second = mockMvc.perform(get(ECHO_PATH)).andExpect(status().isOk()).andReturn();
    String secondId = second.getResponse().getHeader(CorrelationIdFilter.HEADER_NAME);

    assertThat(org.slf4j.MDC.get(CorrelationIdFilter.MDC_KEY))
        .as("MDC must be cleared after request completion")
        .isNull();

    // Each request gets its own generated UUID — they must differ
    assertThat(firstId).isNotEqualTo(secondId);
    // Both should be valid UUIDs
    assertThat(UUID.fromString(firstId)).isNotNull();
    assertThat(UUID.fromString(secondId)).isNotNull();
  }

  // ── EDGE OPS-03/concurrency: concurrent requests have distinct IDs ────────

  @Test
  void concurrentRequestsHaveDistinctIds() throws Exception {
    int threadCount = 8;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(threadCount);
    Set<String> observedIds = ConcurrentHashMap.newKeySet();
    List<Exception> errors = new ArrayList<>();

    for (int i = 0; i < threadCount; i++) {
      executor.submit(
          () -> {
            try {
              startLatch.await(5, TimeUnit.SECONDS);
              MvcResult result = mockMvc.perform(get(ECHO_PATH)).andReturn();
              String id = result.getResponse().getHeader(CorrelationIdFilter.HEADER_NAME);
              if (id != null) {
                observedIds.add(id);
              }
            } catch (Exception e) {
              synchronized (errors) {
                errors.add(e);
              }
            } finally {
              doneLatch.countDown();
            }
          });
    }

    startLatch.countDown(); // release all threads simultaneously
    assertThat(doneLatch.await(10, TimeUnit.SECONDS))
        .as("all threads should finish within timeout")
        .isTrue();
    executor.shutdown();

    assertThat(errors).as("no thread should throw").isEmpty();
    assertThat(observedIds)
        .as("each concurrent request must produce a distinct correlation ID")
        .hasSize(threadCount);
  }

  // ── OPS-03 base: sensitive headers do not appear in captured logs ──────────

  @Test
  void sensitiveHeadersDoNotAppearInLogs() throws Exception {
    Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    rootLogger.addAppender(listAppender);

    try {
      mockMvc
          .perform(
              get(ECHO_PATH)
                  .header("Authorization", "Bearer eyJhbGciOiJSUzI1NiJ9.payload.sig")
                  .header("Cookie", "SESSION=abc123secret"))
          .andExpect(status().isOk());

      for (ILoggingEvent event : listAppender.list) {
        String message = event.getFormattedMessage();
        assertThat(message)
            .as("log message must not contain raw JWT")
            .doesNotContain("eyJhbGciOiJSUzI1NiJ9")
            .as("log message must not contain raw cookie value")
            .doesNotContain("abc123secret")
            .as("log message must not contain raw Bearer token")
            .doesNotContain("Bearer eyJ");
      }
    } finally {
      rootLogger.detachAppender(listAppender);
      listAppender.stop();
    }
  }

  // ── Log pipeline & SanitizingMessageConverter integration ──────────────────

  @Test
  void sanitizingMessageConverterAppliesToLoggingEvents() {
    SanitizingMessageConverter converter = new SanitizingMessageConverter();
    converter.start();

    Logger logger = (Logger) LoggerFactory.getLogger(ObservabilityIT.class);
    ch.qos.logback.classic.spi.LoggingEvent event =
        new ch.qos.logback.classic.spi.LoggingEvent(
            ObservabilityIT.class.getName(),
            logger,
            ch.qos.logback.classic.Level.INFO,
            "User login failed: Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.secret.jwt with password=my_secret_pwd\u0000injected",
            null,
            null);

    String rendered = converter.convert(event);

    assertThat(rendered)
        .contains("[REDACTED]")
        .doesNotContain("my_secret_pwd")
        .doesNotContain("eyJhbGciOiJSUzI1NiJ9")
        .doesNotContain("\u0000");
  }

  // ── SanitizingExtendedThrowableProxyConverter integration ───────────────────

  @Test
  void sanitizingExtendedThrowableProxyConverterAppliesToThrowables() {
    SanitizingExtendedThrowableProxyConverter converter =
        new SanitizingExtendedThrowableProxyConverter();
    converter.start();

    Logger logger = (Logger) LoggerFactory.getLogger(ObservabilityIT.class);
    Throwable errorWithSecret =
        new RuntimeException(
            "Database error with password=my_db_pwd and Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.sig");
    ch.qos.logback.classic.spi.LoggingEvent event =
        new ch.qos.logback.classic.spi.LoggingEvent(
            ObservabilityIT.class.getName(),
            logger,
            ch.qos.logback.classic.Level.ERROR,
            "Unhandled exception occurred",
            errorWithSecret,
            null);

    String rendered = converter.convert(event);

    assertThat(rendered)
        .contains("[REDACTED]")
        .doesNotContain("my_db_pwd")
        .doesNotContain("eyJhbGciOiJSUzI1NiJ9");
  }

  // ── Structured JSON logging encoder verification ─────────────────────────

  @Test
  void sanitizingJsonEncoderStripsSensitiveMessageAndThrowable() {
    ch.qos.logback.classic.LoggerContext context =
        (ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory();
    SanitizingJsonEncoder encoder = new SanitizingJsonEncoder();
    encoder.setContext(context);
    encoder.start();

    Logger logger = (Logger) LoggerFactory.getLogger(ObservabilityIT.class);
    Throwable errorWithSecret =
        new RuntimeException("Underlying exception with Cookie: SESSION=secret_session_123");
    ch.qos.logback.classic.spi.LoggingEvent event =
        new ch.qos.logback.classic.spi.LoggingEvent(
            ObservabilityIT.class.getName(),
            logger,
            ch.qos.logback.classic.Level.ERROR,
            "Operation failed for user: {} with Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.sig with password=leaked_pwd",
            errorWithSecret,
            new Object[] {"password=secret_in_argument"});
    event.setMDCPropertyMap(
        java.util.Map.of(
            "correlationId", "test-corr-json-123", "client_secret", "secret_in_mdc_456"));
    event.addKeyValuePair(
        new org.slf4j.event.KeyValuePair("custom_token", "Bearer eyJhbGciOiJSUzI1NiJ9.kvp.sig"));

    byte[] encodedBytes = encoder.encode(event);
    String jsonOutput = new String(encodedBytes, java.nio.charset.StandardCharsets.UTF_8);

    assertThat(jsonOutput)
        .contains("\"correlationId\":\"test-corr-json-123\"")
        .contains("\"level\":\"ERROR\"")
        .contains("[REDACTED]")
        .contains("[JWT-REDACTED]")
        .doesNotContain("leaked_pwd")
        .doesNotContain("secret_session_123")
        .doesNotContain("secret_in_argument")
        .doesNotContain("secret_in_mdc_456")
        .doesNotContain("eyJhbGciOiJSUzI1NiJ9");
  }

  // ── LogSanitizer unit-level coverage ───────────────────────────────────────

  @Test
  void logSanitizerStripsSensitivePatterns() {
    // Authorization
    assertThat(LogSanitizer.sanitize("Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.p.s"))
        .contains("[REDACTED]")
        .doesNotContain("eyJ");

    // Cookie
    assertThat(LogSanitizer.sanitize("Cookie: SESSION=abc123; user=xyz"))
        .contains("[REDACTED]")
        .doesNotContain("abc123");

    // Standalone JWT
    assertThat(LogSanitizer.sanitize("token is eyJhbGciOiJSUzI1NiJ9.payload.sig"))
        .contains("[JWT-REDACTED]")
        .doesNotContain("eyJ");

    // Password
    assertThat(LogSanitizer.sanitize("password=super_secret_42"))
        .contains("[REDACTED]")
        .doesNotContain("super_secret_42");

    // Control chars
    assertThat(LogSanitizer.sanitize("clean\u0000text\rhere")).isEqualTo("cleantexthere");

    // Null-safe
    assertThat(LogSanitizer.sanitize(null)).isNull();

    // Innocuous text unchanged
    assertThat(LogSanitizer.sanitize("Hello, world!")).isEqualTo("Hello, world!");
  }

  // ── Probe controller ──────────────────────────────────────────────────────

  @RestController
  static class ObsProbeController {

    @GetMapping(ECHO_PATH)
    ResponseEntity<String> echo() {
      return ResponseEntity.ok("ok");
    }
  }

  // ── Test configuration ────────────────────────────────────────────────────

  @TestConfiguration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class TestApplication {

    @Bean
    SecurityFilterChain permissiveChain(HttpSecurity http) throws Exception {
      http.authorizeHttpRequests(a -> a.anyRequest().permitAll()).csrf(c -> c.disable());
      return http.build();
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
      ClientRegistration registration =
          ClientRegistration.withRegistrationId("stub")
              .clientId("stub-client")
              .clientSecret("unused")
              .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
              .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
              .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
              .scope("openid")
              .authorizationUri("https://stub.invalid/authorize")
              .tokenUri("https://stub.invalid/token")
              .jwkSetUri("https://stub.invalid/jwks")
              .issuerUri("https://stub.invalid")
              .userNameAttributeName(IdTokenClaimNames.SUB)
              .build();
      return new InMemoryClientRegistrationRepository(registration);
    }
  }
}
