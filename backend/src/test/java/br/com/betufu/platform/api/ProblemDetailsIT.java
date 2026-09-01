package br.com.betufu.platform.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

/**
 * Integration test for the RFC 9457 Problem Details contract — Plan 01-12, Task 1.
 *
 * <p>Uses a permissive security config and a probe controller to exercise error paths in isolation
 * from authentication and CSRF. The probe endpoints are scoped under {@code /api/v1/error-probe/}
 * so they coexist with the real API without conflict.
 *
 * <p>Each test covers a specific criterion from the plan and is paired with a mutation that must
 * turn it red when the handler is removed or degraded.
 */
@SpringBootTest(
    classes = {
      ProblemDetailsIT.TestApplication.class,
      ProblemDetailsHandler.class,
      ProblemDetailsIT.ErrorProbeController.class
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
class ProblemDetailsIT {

  private static final String VALIDATE_PATH = "/api/v1/error-probe/validate";
  private static final String ECHO_PATH = "/api/v1/error-probe/echo";
  private static final String EXPLODE_PATH = "/api/v1/error-probe/explode";
  private static final String EMPTY_ERROR_PATH = "/api/v1/error-probe/empty-error";
  private static final String CONTROL_CHARS_PATH = "/api/v1/error-probe/control-chars";
  private static final String DATA_INTEGRITY_PATH = "/api/v1/error-probe/data-integrity";

  @Autowired private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
  }

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  // ── C1 + C2 + C4 + C5: validation error with code, action, sorted fieldErrors ──

  @Test
  void validationErrorProducesCodeAndSortedFieldErrors() throws Exception {
    mockMvc
        .perform(
            post(VALIDATE_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"email\":\"not-an-email\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(400))
        .andExpect(jsonPath("$.title").value("Erro de validação"))
        .andExpect(jsonPath("$.detail").value("Um ou mais campos estão inválidos."))
        .andExpect(jsonPath("$.code").value("BETUFU-400-VALIDATION"))
        .andExpect(jsonPath("$.action").value("Corrija os campos indicados e tente novamente."))
        .andExpect(jsonPath("$.fieldErrors").isArray())
        // Sorted alphabetically: email before name
        .andExpect(jsonPath("$.fieldErrors[0].field").value("email"))
        .andExpect(jsonPath("$.fieldErrors[0].message").value("Endereço de e-mail inválido"))
        .andExpect(jsonPath("$.fieldErrors[1].field").value("name"))
        .andExpect(jsonPath("$.fieldErrors[1].message").value("O nome não pode ficar vazio"));
  }

  // ── C1 + C2: method not allowed produces stable code ───────────────────────

  @Test
  void methodNotAllowedProducesStableCode() throws Exception {
    mockMvc
        .perform(put(ECHO_PATH))
        .andExpect(status().isMethodNotAllowed())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(405))
        .andExpect(jsonPath("$.code").value("BETUFU-405-METHOD"))
        .andExpect(jsonPath("$.title").value("Método não permitido"))
        .andExpect(jsonPath("$.action").value("Verifique o método HTTP e tente novamente."));
  }

  // ── C6: unexpected exception never leaks internal details ──────────────────

  @Test
  void unexpectedExceptionNeverLeaksInternalDetails() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get(EXPLODE_PATH))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.code").value("BETUFU-500-INTERNAL"))
            .andExpect(jsonPath("$.title").value("Erro interno"))
            .andExpect(jsonPath("$.action").value("Tente novamente mais tarde."))
            .andReturn();

    String body = result.getResponse().getContentAsString();
    assertThat(body)
        .doesNotContain("internal detail")
        .doesNotContain("stack follows")
        .doesNotContain("RuntimeException")
        .doesNotContain("java.lang")
        .doesNotContain("org.springframework")
        .doesNotContain("stackTrace");
  }

  // ── C7: exception without message still produces non-empty detail ──────────

  @Test
  void exceptionWithoutMessageStillProducesNonEmptyDetail() throws Exception {
    mockMvc
        .perform(get(EMPTY_ERROR_PATH))
        .andExpect(status().isInternalServerError())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.detail").isNotEmpty())
        .andExpect(jsonPath("$.code").value("BETUFU-500-INTERNAL"));
  }

  // ── C8: control characters never reach response body ───────────────────────

  @Test
  void controlCharactersNeverReachResponseBody() throws Exception {
    MvcResult result =
        mockMvc
            .perform(get(CONTROL_CHARS_PATH))
            .andExpect(status().isInternalServerError())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    // The exception message contains \u0000, \r, "leaked", "secret"
    // None of these should appear in the response because the handler
    // uses stable Portuguese text, not the exception message.
    assertThat(body).doesNotContain("\u0000").doesNotContain("leaked").doesNotContain("secret");
  }

  // ── C3 (partial): correlationId from MDC appears in response ───────────────

  @Test
  void correlationIdPresentWhenMdcPopulated() throws Exception {
    MDC.put("correlationId", "test-corr-id-42");

    mockMvc
        .perform(get(EXPLODE_PATH))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.correlationId").value("test-corr-id-42"));
  }

  @Test
  void noCorrelationIdWhenMdcEmpty() throws Exception {
    mockMvc
        .perform(get(EXPLODE_PATH))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.correlationId").doesNotExist());
  }

  @Test
  void validationErrorNeverLeaksRejectedValueInResponseBody() throws Exception {
    String sensitiveInvalidEmail = "sensitive-secret-token-without-at-sign";

    MvcResult result =
        mockMvc
            .perform(
                post(VALIDATE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"name\":\"valid-name\",\"email\":\"" + sensitiveInvalidEmail + "\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("BETUFU-400-VALIDATION"))
            .andExpect(jsonPath("$.fieldErrors").isArray())
            .andReturn();

    assertThat(result.getResponse().getContentAsString()).doesNotContain(sensitiveInvalidEmail);
  }

  @Test
  void unsupportedMediaTypeProducesStableCode() throws Exception {
    mockMvc
        .perform(post(VALIDATE_PATH).contentType(MediaType.APPLICATION_XML).content("<probe/>"))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.status").value(415))
        .andExpect(jsonPath("$.code").value("BETUFU-415-MEDIA_TYPE"))
        .andExpect(jsonPath("$.title").value("Formato não aceito"))
        .andExpect(jsonPath("$.action").value("Verifique o cabeçalho Content-Type da requisição."));
  }

  @Test
  void apiErrorCodeEnumSatisfiesStabilityContract() {
    for (ApiErrorCode errorCode : ApiErrorCode.values()) {
      assertThat(errorCode.code()).startsWith("BETUFU-").isNotBlank();
      assertThat(errorCode.title()).isNotBlank();
      assertThat(errorCode.detail()).isNotBlank();
      assertThat(errorCode.action()).isNotBlank();
    }

    assertThat(ApiErrorCode.VALIDATION_FAILED.code()).isEqualTo("BETUFU-400-VALIDATION");
    assertThat(ApiErrorCode.METHOD_NOT_ALLOWED.code()).isEqualTo("BETUFU-405-METHOD");
    assertThat(ApiErrorCode.NOT_ACCEPTABLE.code()).isEqualTo("BETUFU-406-NOT_ACCEPTABLE");
    assertThat(ApiErrorCode.UNSUPPORTED_MEDIA_TYPE.code()).isEqualTo("BETUFU-415-MEDIA_TYPE");
    assertThat(ApiErrorCode.INTERNAL_ERROR.code()).isEqualTo("BETUFU-500-INTERNAL");

    assertThat(ApiErrorCode.fromStatus(400)).isEqualTo(ApiErrorCode.VALIDATION_FAILED);
    assertThat(ApiErrorCode.fromStatus(401)).isEqualTo(ApiErrorCode.UNAUTHORIZED);
    assertThat(ApiErrorCode.fromStatus(403)).isEqualTo(ApiErrorCode.FORBIDDEN);
    assertThat(ApiErrorCode.fromStatus(405)).isEqualTo(ApiErrorCode.METHOD_NOT_ALLOWED);
    assertThat(ApiErrorCode.fromStatus(406)).isEqualTo(ApiErrorCode.NOT_ACCEPTABLE);
    assertThat(ApiErrorCode.fromStatus(415)).isEqualTo(ApiErrorCode.UNSUPPORTED_MEDIA_TYPE);
    assertThat(ApiErrorCode.fromStatus(422)).isEqualTo(ApiErrorCode.NICKNAME_COOLDOWN_ACTIVE);
    assertThat(ApiErrorCode.fromStatus(429)).isEqualTo(ApiErrorCode.TOO_MANY_ATTEMPTS);
    assertThat(ApiErrorCode.fromStatus(500)).isEqualTo(ApiErrorCode.INTERNAL_ERROR);
    assertThat(ApiErrorCode.fromStatus(999)).isEqualTo(ApiErrorCode.INTERNAL_ERROR);
  }

  @Test
  void dataIntegrityExceptionNeverLeaksRawMessageOrDatabaseDetails() throws Exception {
    Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    rootLogger.addAppender(listAppender);

    try {
      MvcResult result =
          mockMvc
              .perform(get(DATA_INTEGRITY_PATH))
              .andExpect(status().isInternalServerError())
              .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
              .andExpect(jsonPath("$.status").value(500))
              .andExpect(jsonPath("$.code").value("BETUFU-500-INTERNAL"))
              .andExpect(jsonPath("$.title").value("Erro interno"))
              .andExpect(jsonPath("$.detail").value("Ocorreu um erro inesperado."))
              .andExpect(jsonPath("$.action").value("Tente novamente mais tarde."))
              .andReturn();

      String body = result.getResponse().getContentAsString();
      String capturedLogs =
          listAppender.list.stream()
              .map(
                  event ->
                      event.getFormattedMessage()
                          + " "
                          + (event.getThrowableProxy() == null
                              ? ""
                              : event.getThrowableProxy().getMessage()))
              .collect(Collectors.joining("\n"));

      assertThat(body)
          .doesNotContain("matricula_hmac")
          .doesNotContain("sensitive-raw-column-value")
          .doesNotContain("betufu_profile")
          .doesNotContain("uq_betufu_profile_identity")
          .doesNotContain("DataIntegrityViolationException")
          .doesNotContain("constraint");
      assertThat(capturedLogs)
          .doesNotContain("matricula_hmac")
          .doesNotContain("sensitive-raw-column-value")
          .doesNotContain("betufu_profile")
          .doesNotContain("uq_betufu_profile_identity")
          .doesNotContain("constraint");
    } finally {
      rootLogger.detachAppender(listAppender);
      listAppender.stop();
    }
  }

  @Test
  void validActivityFilterAllowlistMatchesOnlyApprovedMutations() {
    br.com.betufu.platform.session.ValidActivityFilter filter =
        new br.com.betufu.platform.session.ValidActivityFilter(
            org.mockito.Mockito.mock(br.com.betufu.platform.session.SessionLifecycleService.class));

    assertThat(matchesAllowlist(filter, "PUT", "/api/v1/tracer/identity/current")).isTrue();
    assertThat(matchesAllowlist(filter, "PUT", "/api/v1/profile/me")).isTrue();

    assertThat(matchesAllowlist(filter, "POST", "/api/v1/profile/me")).isFalse();
    assertThat(matchesAllowlist(filter, "GET", "/api/v1/profile/me")).isFalse();
    assertThat(matchesAllowlist(filter, "GET", "/api/v1/session")).isFalse();
    assertThat(matchesAllowlist(filter, "POST", "/api/v1/tracer/identity/current")).isFalse();
    assertThat(matchesAllowlist(filter, "DELETE", "/api/v1/profile/me")).isFalse();
    assertThat(matchesAllowlist(filter, "GET", "/actuator/health/liveness")).isFalse();
    assertThat(matchesAllowlist(filter, "PUT", "/api/v1/other")).isFalse();
  }

  private boolean matchesAllowlist(
      br.com.betufu.platform.session.ValidActivityFilter filter, String method, String path) {
    Boolean shouldNotFilter =
        ReflectionTestUtils.invokeMethod(
            filter, "shouldNotFilter", new MockHttpServletRequest(method, path));
    return Boolean.FALSE.equals(shouldNotFilter);
  }

  // ── Probe controller ──────────────────────────────────────────────────────

  @RestController
  static class ErrorProbeController {

    @PostMapping(path = VALIDATE_PATH, consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<Void> validate(@Valid @RequestBody ProbePayload payload) {
      return ResponseEntity.noContent().build();
    }

    @GetMapping(ECHO_PATH)
    ResponseEntity<String> echo() {
      return ResponseEntity.ok("ok");
    }

    @GetMapping(EXPLODE_PATH)
    ResponseEntity<Void> explode() {
      throw new RuntimeException("internal detail: stack follows");
    }

    @GetMapping(EMPTY_ERROR_PATH)
    ResponseEntity<Void> emptyError() {
      throw new RuntimeException();
    }

    @GetMapping(CONTROL_CHARS_PATH)
    ResponseEntity<Void> controlChars() {
      throw new RuntimeException("leaked\u0000secret\rinternal\ndetail");
    }

    @GetMapping(DATA_INTEGRITY_PATH)
    ResponseEntity<Void> dataIntegrity() {
      throw new DataIntegrityViolationException(
          "Key (matricula_hmac)=(sensitive-raw-column-value) already exists in table betufu_profile constraint uq_betufu_profile_identity");
    }
  }

  record ProbePayload(
      @NotBlank(message = "O nome não pode ficar vazio") String name,
      @Email(message = "Endereço de e-mail inválido") String email) {}

  // ── Test configuration ────────────────────────────────────────────────────

  @TestConfiguration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class TestApplication {

    /**
     * Permissive security chain — all requests allowed, CSRF disabled. This isolates the
     * error-handling tests from authentication concerns.
     */
    @Bean
    SecurityFilterChain permissiveChain(HttpSecurity http) throws Exception {
      http.authorizeHttpRequests(a -> a.anyRequest().permitAll()).csrf(c -> c.disable());
      return http.build();
    }

    /**
     * Stub OIDC registration to satisfy the OAuth2 client auto-configuration. No real IdP is
     * contacted.
     */
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
