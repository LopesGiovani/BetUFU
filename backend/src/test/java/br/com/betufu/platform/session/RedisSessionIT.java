package br.com.betufu.platform.session;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import br.com.betufu.platform.security.SecurityConfiguration;

@SpringBootTest(
        classes = {
            SecurityConfiguration.class,
            SessionSecurityConfiguration.class,
            RedisSessionIT.TestApplication.class,
            RedisSessionIT.SessionProbeController.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "spring.docker.compose.enabled=false",
            "spring.session.data.redis.configure-action=none",
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                    + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                    + "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
            "BETUFU_DB_JDBC_URL=jdbc:postgresql://database.invalid/betufu",
            "BETUFU_DB_USER=unused",
            "BETUFU_DB_PASSWORD=unused",
            "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
            "BETUFU_OIDC_CLIENT_ID=betufu-redis-session-it",
            "BETUFU_OIDC_CLIENT_SECRET=unused"
        })
@ActiveProfiles("integrated")
@Testcontainers
class RedisSessionIT {

    private static final String CURRENT_IDENTITY_PATH =
            "/api/v1/tracer/identity/current";
    private static final String OAUTH_CALLBACK_PATH = "/login/oauth2/code/keycloak";
    private static final String SESSION_COOKIE_NAME = "SESSION";
    private static final String XSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String XSRF_HEADER_NAME = "X-XSRF-TOKEN";
    private static final String SESSION_KEY_PREFIX = "betufu:session:sessions:";
    private static final String SESSION_FIXATION_PROBE_ATTRIBUTE =
            RedisSessionIT.class.getName() + ".invokeOAuth2SessionStrategy";
    private static final String PRE_AUTHENTICATION_MARKER_ATTRIBUTE =
            "betufu.it.preAuthenticationMarker";
    private static final String REDIS_PASSWORD = "betufu-redis-session-it-only";
    private static final int REDIS_PORT = 6379;
    private static final Duration SHORT_TEST_TTL = Duration.ofMinutes(5);
    private static final Duration TTL_TOLERANCE = Duration.ofSeconds(10);
    private static final String REDIS_IMAGE_REFERENCE =
            "docker.io/library/redis:8.10.0-trixie@sha256:"
                    + "344e3945a0b431c8ff1eecd58c5573538126bd756f02fc7e218ddf1fc2546366";

    private static final DockerImageName REDIS_IMAGE =
            DockerImageName.parse(REDIS_IMAGE_REFERENCE)
                    .asCompatibleSubstituteFor("redis");

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(REDIS_IMAGE)
            .withImagePullPolicy(imageName -> false)
            .withExposedPorts(REDIS_PORT)
            .withCommand("redis-server", "--requirepass", REDIS_PASSWORD)
            .waitingFor(Wait.forListeningPort());

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("BETUFU_REDIS_HOST", REDIS::getHost);
        registry.add("BETUFU_REDIS_PORT", () -> REDIS.getMappedPort(REDIS_PORT));
        registry.add("BETUFU_REDIS_PASSWORD", () -> REDIS_PASSWORD);
    }

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private MutableTestClock clock;

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private CookieCsrfTokenRepository csrfTokenRepository;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private FilterChainProxy springSecurityFilterChain;

    @Autowired
    @Qualifier("springSessionRepositoryFilter")
    private Filter springSessionRepositoryFilter;

    private MockMvc mockMvc;
    private SelectiveTouchSessionRepository repository;

    @BeforeEach
    void prepareRedisAndMockMvc() {
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            return null;
        });
        clock.set(Instant.now());
        repository = (SelectiveTouchSessionRepository)
                applicationContext.getBean("sessionRepository");
        SessionAuthenticationStrategy oauth2SessionStrategy = oauth2SessionStrategy();
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .addFilters(
                        springSessionRepositoryFilter,
                        sessionFixationProbeFilter(oauth2SessionStrategy),
                        springSecurityFilterChain)
                .build();
        assertCanonicalCsrfRepository();
    }

    @AfterEach
    void verifyCanonicalCsrfRepositoryRemainsInstalled() {
        assertCanonicalCsrfRepository();
    }

    @Test
    void bootsThePinnedRedisWithOneDecoratorOneSecurityChainAndOneSessionFilter() {
        assertThat(REDIS.getDockerImageName()).isEqualTo(REDIS_IMAGE_REFERENCE);
        assertThat(REDIS.getMappedPort(REDIS_PORT)).isPositive();

        Map<String, SessionRepository> repositories =
                applicationContext.getBeansOfType(SessionRepository.class);
        assertThat(repositories).hasSize(1);
        assertThat(repositories.get("sessionRepository"))
                .isSameAs(repository)
                .isExactlyInstanceOf(SelectiveTouchSessionRepository.class);
        assertThat(applicationContext.getBeansOfType(SecurityFilterChain.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(SessionRepositoryFilter.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(ValidActivityFilter.class)).hasSize(1);

        FilterRegistrationBean<?> registration = applicationContext.getBean(
                "validActivityFilterRegistration", FilterRegistrationBean.class);
        assertThat(registration.isEnabled()).isFalse();
    }

    @Test
    void keepsTwoWebSessionsIndependentWhenOneRedisKeyExpires() throws Exception {
        SessionHandle sessionA = createSessionThroughApprovedPut();
        SessionHandle sessionB = createSessionThroughApprovedPut();

        assertThat(sessionB.id()).isNotEqualTo(sessionA.id());
        assertThat(repository.findById(sessionA.id())).isNotNull();
        assertThat(repository.findById(sessionB.id())).isNotNull();

        assertThat(redis.expire(sessionKey(sessionA.id()), Duration.ofMillis(75)))
                .isTrue();
        awaitKeyRemoval(sessionKey(sessionA.id()));

        assertThat(repository.findById(sessionA.id())).isNull();
        assertThat(repository.findById(sessionB.id())).isNotNull();
        assertThat(redis.hasKey(sessionKey(sessionB.id()))).isTrue();
    }

    @Test
    void technicalAndRejectedTrafficNeverIncreasesThePersistedDeadline() throws Exception {
        SelectiveTouchSessionRepository.SelectiveSession session = createLifecycleSession();
        Cookie cookie = sessionCookie(session.getId());
        shortenTtl(session.getId());
        Cookie xsrfCookie = materializeXsrfWithoutRenewing(session.getId(), cookie);

        assertThat(assertDoesNotRenew(
                        session.getId(), authenticated(get("/actuator/health/liveness"), cookie))
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(assertDoesNotRenew(
                        session.getId(), authenticated(get("/api/v1/session"), cookie))
                .getResponse().getStatus()).isEqualTo(204);
        assertThat(assertDoesNotRenew(
                        session.getId(), authenticated(get("/api/v1/poll"), cookie))
                .getResponse().getStatus()).isEqualTo(204);
        assertThat(assertDoesNotRenew(
                        session.getId(), authenticated(get("/api/v1/heartbeat"), cookie))
                .getResponse().getStatus()).isEqualTo(204);
        assertThat(assertDoesNotRenew(
                        session.getId(), authenticated(get("/assets/application.js"), cookie))
                .getResponse().getStatus()).isEqualTo(403);
        MvcResult preflight = assertDoesNotRenew(
                session.getId(),
                authenticated(options(CURRENT_IDENTITY_PATH)
                        .header("Origin", "https://spa.invalid")
                        .header("Access-Control-Request-Method", "PUT"), cookie));
        assertThat(preflight.getResponse().getStatus()).isBetween(200, 499);

        MvcResult csrfRejected = assertDoesNotRenew(
                session.getId(), authenticated(put(CURRENT_IDENTITY_PATH), cookie));
        assertThat(csrfRejected.getResponse().getStatus()).isEqualTo(403);

        MvcResult invalidCsrfRejected = assertDoesNotRenew(
                session.getId(),
                authenticatedWithXsrf(
                        put(CURRENT_IDENTITY_PATH), cookie, xsrfCookie, "invalid-xsrf-token"));
        assertThat(invalidCsrfRejected.getResponse().getStatus()).isEqualTo(403);

        MvcResult failed = assertDoesNotRenew(
                session.getId(),
                authenticatedWithXsrf(
                        put(CURRENT_IDENTITY_PATH).header("X-Test-Status", "500"),
                        cookie,
                        xsrfCookie));
        assertThat(failed.getResponse().getStatus()).isEqualTo(500);

        SelectiveTouchSessionRepository.SelectiveSession anonymousSession =
                createLifecycleSession();
        shortenTtl(anonymousSession.getId());
        Cookie anonymousCookie = sessionCookie(anonymousSession.getId());
        Cookie anonymousXsrfCookie = materializeXsrfWithoutRenewing(
                anonymousSession.getId(), anonymousCookie);
        MvcResult unauthenticated = assertDoesNotRenew(
                anonymousSession.getId(),
                withXsrf(
                        put(CURRENT_IDENTITY_PATH),
                        anonymousCookie,
                        anonymousXsrfCookie));
        assertThat(unauthenticated.getResponse().getStatus()).isBetween(300, 399);
    }

    @Test
    void exactAuthenticatedCsrfProtectedPutRenewsOnceForOneObservedInstant()
            throws Exception {
        SelectiveTouchSessionRepository.SelectiveSession session = createLifecycleSession();
        shortenTtl(session.getId());
        long shortenedPttl = pttl(session.getId());
        Cookie cookie = sessionCookie(session.getId());
        Cookie xsrfCookie = materializeXsrfWithoutRenewing(session.getId(), cookie);

        Instant approvedAt = Instant.now().plusSeconds(1);
        clock.set(approvedAt);
        performApprovedPut(cookie, xsrfCookie).andExpect(status().isNoContent());

        long firstRenewedPttl = pttl(session.getId());
        assertThat(firstRenewedPttl).isGreaterThan(shortenedPttl);
        assertPttlMatchesDeadline(firstRenewedPttl, approvedAt.plus(SessionPolicy.IDLE_TIMEOUT));
        assertThat(lastValidActivity(repository.findById(session.getId())))
                .isEqualTo(approvedAt);

        performApprovedPut(cookie, xsrfCookie).andExpect(status().isNoContent());

        long idempotentPttl = pttl(session.getId());
        assertThat(idempotentPttl).isPositive().isLessThanOrEqualTo(firstRenewedPttl);
        assertThat(lastValidActivity(repository.findById(session.getId())))
                .isEqualTo(approvedAt);
    }

    @Test
    void concurrentActivitiesConvergeToMaxEvenWhenAStaleTechnicalSaveFinishesLast()
            throws Exception {
        SelectiveTouchSessionRepository.SelectiveSession created = createLifecycleSession();
        String id = created.getId();
        SelectiveTouchSessionRepository.SelectiveSession lower = repository.findById(id);
        SelectiveTouchSessionRepository.SelectiveSession higher = repository.findById(id);
        SelectiveTouchSessionRepository.SelectiveSession staleTechnical = repository.findById(id);

        Instant lowerInstant = Instant.now().plusSeconds(1);
        Instant higherInstant = lowerInstant.plusSeconds(1);
        lower.setAttribute(
                SelectiveTouchSessionRepository.APPROVED_ACTIVITY_ATTRIBUTE, lowerInstant);
        higher.setAttribute(
                SelectiveTouchSessionRepository.APPROVED_ACTIVITY_ATTRIBUTE, higherInstant);
        staleTechnical.setLastAccessedTime(higherInstant.plusSeconds(30));
        staleTechnical.setAttribute("betufu.it.technicalCompletion", "completed-last");

        saveConcurrently(lower, higher);
        repository.save(staleTechnical);

        SelectiveTouchSessionRepository.SelectiveSession persisted = repository.findById(id);
        assertThat(lastValidActivity(persisted)).isEqualTo(higherInstant);
        assertThat(persisted.<String>getAttribute("betufu.it.technicalCompletion"))
                .isEqualTo("completed-last");
        assertPttlMatchesDeadline(
                pttl(id), higherInstant.plus(SessionPolicy.IDLE_TIMEOUT));
    }

    @Test
    void configuredOAuth2SessionStrategyRotatesThePreAuthenticationId()
            throws Exception {
        SelectiveTouchSessionRepository.SelectiveSession preAuthentication =
                createLifecycleSession();
        Instant originalLastValidActivity = lastValidActivity(preAuthentication);
        preAuthentication.setAttribute(PRE_AUTHENTICATION_MARKER_ATTRIBUTE, "preserved");
        repository.save(preAuthentication);
        String oldId = preAuthentication.getId();
        Cookie preAuthenticationCookie = sessionCookie(oldId);

        // This calls only the SessionAuthenticationStrategy wired into the real OAuth2 login
        // filter. It does not simulate an authorization code, token exchange, OAuth callback
        // authentication or the configured authentication-success handler.
        MvcResult authenticated = mockMvc.perform(get("/actuator/health/liveness")
                        .cookie(preAuthenticationCookie)
                        .requestAttr(SESSION_FIXATION_PROBE_ATTRIBUTE, Boolean.TRUE))
                .andExpect(status().isOk())
                .andReturn();
        Cookie rotatedCookie = authenticated.getResponse().getCookie(SESSION_COOKIE_NAME);
        assertThat(rotatedCookie).as("rotated Spring Session cookie").isNotNull();
        assertThat(rotatedCookie.getValue()).isNotEqualTo(preAuthenticationCookie.getValue());
        String newId = decodeSessionId(rotatedCookie);

        assertThat(newId).isNotEqualTo(oldId);
        assertThat(repository.findById(oldId)).isNull();
        assertThat(redis.hasKey(sessionKey(oldId))).isFalse();
        SelectiveTouchSessionRepository.SelectiveSession rotated = repository.findById(newId);
        assertThat(rotated).isNotNull();
        assertThat(redis.hasKey(sessionKey(newId))).isTrue();
        assertThat(rotated.<String>getAttribute(PRE_AUTHENTICATION_MARKER_ATTRIBUTE))
                .isEqualTo("preserved");
        assertThat(lastValidActivity(rotated)).isEqualTo(originalLastValidActivity);
    }

    @Test
    void storesLifecycleStateAsScalarsWithoutSerializingSessionState() throws Exception {
        SessionHandle handle = createSessionThroughApprovedPut();
        SelectiveTouchSessionRepository.SelectiveSession session =
                repository.findById(handle.id());

        assertThat(session.<Long>getAttribute(
                        SelectiveTouchSessionRepository.LAST_VALID_EPOCH_SECOND_ATTRIBUTE))
                .isInstanceOf(Long.class);
        assertThat(session.<Integer>getAttribute(
                        SelectiveTouchSessionRepository.LAST_VALID_NANO_ATTRIBUTE))
                .isInstanceOf(Integer.class);
        assertThat(session.<String>getAttribute(
                        SelectiveTouchSessionRepository.AUTHENTICATION_ACR_ATTRIBUTE))
                .isEqualTo("urn:betufu:acr:mfa");
        assertThat(session.<Integer>getAttribute(
                        SelectiveTouchSessionRepository.AUTHENTICATION_AMR_COUNT_ATTRIBUTE))
                .isEqualTo(2);
        assertThat(session.<String>getAttribute(
                        SelectiveTouchSessionRepository.AUTHENTICATION_AMR_PREFIX + 0))
                .isEqualTo("pwd");
        assertThat(session.<String>getAttribute(
                        SelectiveTouchSessionRepository.AUTHENTICATION_AMR_PREFIX + 1))
                .isEqualTo("otp");
        assertThat(session.getAttributeNames()).doesNotContain(
                SelectiveTouchSessionRepository.APPROVED_ACTIVITY_ATTRIBUTE);
        for (String attributeName : session.getAttributeNames()) {
            Object attributeValue = session.getAttribute(attributeName);
            assertThat(attributeValue)
                    .isNotInstanceOf(SessionPolicy.SessionState.class);
        }

        // oidcLogin() only injects a test Authentication. This assertion deliberately does not
        // claim a real OAuth callback or hosted-IdP success.
        assertThat(repository.findById(handle.id())).isNotNull();
    }

    private SessionHandle createSessionThroughApprovedPut() throws Exception {
        SelectiveTouchSessionRepository.SelectiveSession session = repository.createSession();
        repository.save(session);
        Cookie sessionCookie = sessionCookie(session.getId());
        Cookie xsrfCookie = materializeXsrfWithoutRenewing(session.getId(), sessionCookie);
        performApprovedPut(sessionCookie, xsrfCookie).andExpect(status().isNoContent());
        return new SessionHandle(sessionCookie, session.getId());
    }

    private SelectiveTouchSessionRepository.SelectiveSession createLifecycleSession() {
        SelectiveTouchSessionRepository.SelectiveSession session = repository.createSession();
        Instant initializedAt = clock.instant();
        session.setAttribute(
                SelectiveTouchSessionRepository.LAST_VALID_EPOCH_SECOND_ATTRIBUTE,
                initializedAt.getEpochSecond());
        session.setAttribute(
                SelectiveTouchSessionRepository.LAST_VALID_NANO_ATTRIBUTE,
                initializedAt.getNano());
        session.setAttribute(
                SelectiveTouchSessionRepository.AUTHENTICATION_AMR_COUNT_ATTRIBUTE, 0);
        session.setAttribute(
                SelectiveTouchSessionRepository.APPROVED_ACTIVITY_ATTRIBUTE, initializedAt);
        repository.save(session);
        return session;
    }

    private org.springframework.test.web.servlet.ResultActions performApprovedPut(
            Cookie cookie, Cookie xsrfCookie) throws Exception {
        return mockMvc.perform(authenticatedWithXsrf(
                put(CURRENT_IDENTITY_PATH), cookie, xsrfCookie));
    }

    private MockHttpServletRequestBuilder authenticated(
            MockHttpServletRequestBuilder request, Cookie cookie) {
        return request.cookie(cookie).with(oidcPrincipal());
    }

    private MockHttpServletRequestBuilder authenticatedWithXsrf(
            MockHttpServletRequestBuilder request,
            Cookie sessionCookie,
            Cookie xsrfCookie) {
        return authenticatedWithXsrf(
                request, sessionCookie, xsrfCookie, xsrfCookie.getValue());
    }

    private MockHttpServletRequestBuilder authenticatedWithXsrf(
            MockHttpServletRequestBuilder request,
            Cookie sessionCookie,
            Cookie xsrfCookie,
            String headerValue) {
        return withXsrf(request, sessionCookie, xsrfCookie, headerValue)
                .with(oidcPrincipal());
    }

    private static MockHttpServletRequestBuilder withXsrf(
            MockHttpServletRequestBuilder request,
            Cookie sessionCookie,
            Cookie xsrfCookie) {
        return withXsrf(request, sessionCookie, xsrfCookie, xsrfCookie.getValue());
    }

    private static MockHttpServletRequestBuilder withXsrf(
            MockHttpServletRequestBuilder request,
            Cookie sessionCookie,
            Cookie xsrfCookie,
            String headerValue) {
        return request
                .cookie(sessionCookie, xsrfCookie)
                .header(XSRF_HEADER_NAME, headerValue);
    }

    private RequestPostProcessor oidcPrincipal() {
        ClientRegistration registration =
                clientRegistrationRepository.findByRegistrationId("keycloak");
        assertThat(registration).isNotNull();
        return oidcLogin()
                .clientRegistration(registration)
                .idToken(token -> token
                        .claim(IdTokenClaimNames.ISS, "https://idp.invalid")
                        .claim(IdTokenClaimNames.SUB, "redis-session-it-user")
                        .claim("acr", "urn:betufu:acr:mfa")
                        .claim("amr", List.of("pwd", "otp")));
    }

    private SessionAuthenticationStrategy oauth2SessionStrategy() {
        List<OAuth2LoginAuthenticationFilter> callbackFilters = springSecurityFilterChain
                .getFilters(OAUTH_CALLBACK_PATH)
                .stream()
                .filter(OAuth2LoginAuthenticationFilter.class::isInstance)
                .map(OAuth2LoginAuthenticationFilter.class::cast)
                .toList();
        assertThat(callbackFilters).hasSize(1);
        OAuth2LoginAuthenticationFilter oauth2LoginFilter = callbackFilters.getFirst();

        Object matcherValue = ReflectionTestUtils.getField(
                oauth2LoginFilter,
                AbstractAuthenticationProcessingFilter.class,
                "requiresAuthenticationRequestMatcher");
        assertThat(matcherValue).isInstanceOf(RequestMatcher.class);
        RequestMatcher callbackMatcher = (RequestMatcher) matcherValue;
        MockHttpServletRequest callbackRequest = new MockHttpServletRequest();
        callbackRequest.setMethod("GET");
        callbackRequest.setRequestURI(OAUTH_CALLBACK_PATH);
        callbackRequest.setServletPath(OAUTH_CALLBACK_PATH);
        assertThat(callbackMatcher.matches(callbackRequest)).isTrue();

        Object strategyValue = ReflectionTestUtils.getField(
                oauth2LoginFilter,
                AbstractAuthenticationProcessingFilter.class,
                "sessionStrategy");
        assertThat(strategyValue).isInstanceOf(SessionAuthenticationStrategy.class);
        return (SessionAuthenticationStrategy) strategyValue;
    }

    private static Filter sessionFixationProbeFilter(
            SessionAuthenticationStrategy oauth2SessionStrategy) {
        return (request, response, filterChain) -> {
            if (!Boolean.TRUE.equals(
                    request.getAttribute(SESSION_FIXATION_PROBE_ATTRIBUTE))) {
                filterChain.doFilter(request, response);
                return;
            }
            request.removeAttribute(SESSION_FIXATION_PROBE_ATTRIBUTE);
            if (!(request instanceof HttpServletRequest httpRequest)
                    || !(response instanceof HttpServletResponse httpResponse)) {
                throw new IllegalStateException("Session fixation probe requires HTTP");
            }
            if (httpRequest.getSession(false) == null) {
                throw new IllegalStateException(
                        "Session fixation probe requires a pre-authentication session");
            }

            TestingAuthenticationToken authentication = new TestingAuthenticationToken(
                    "redis-session-it-user", "not-used", "ROLE_USER");
            oauth2SessionStrategy.onAuthentication(
                    authentication, httpRequest, httpResponse);
            filterChain.doFilter(request, response);
        };
    }

    private MvcResult assertDoesNotRenew(
            String sessionId, MockHttpServletRequestBuilder request) throws Exception {
        long before = pttl(sessionId);
        MvcResult result = mockMvc.perform(request).andReturn();
        long after = pttl(sessionId);
        assertThat(after)
                .as("PTTL after technical or rejected traffic")
                .isPositive()
                .isLessThanOrEqualTo(before);
        return result;
    }

    private Cookie materializeXsrfWithoutRenewing(String sessionId, Cookie sessionCookie)
            throws Exception {
        // The login page is deliberately anonymous: rendering it resolves the real deferred
        // CookieCsrfTokenRepository token without a security-test CSRF postprocessor.
        MvcResult result = assertDoesNotRenew(
                sessionId, get("/login").cookie(sessionCookie));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        Cookie xsrfCookie = result.getResponse().getCookie(XSRF_COOKIE_NAME);
        assertThat(xsrfCookie).as("real XSRF cookie from the login page").isNotNull();
        assertThat(xsrfCookie.getValue()).isNotBlank();
        return xsrfCookie;
    }

    private void assertCanonicalCsrfRepository() {
        Map<String, CookieCsrfTokenRepository> repositories =
                applicationContext.getBeansOfType(CookieCsrfTokenRepository.class);
        assertThat(repositories)
                .as("canonical CookieCsrfTokenRepository bean")
                .hasSize(1)
                .containsValue(csrfTokenRepository);

        List<CsrfFilter> csrfFilters = springSecurityFilterChain
                .getFilters(CURRENT_IDENTITY_PATH)
                .stream()
                .filter(CsrfFilter.class::isInstance)
                .map(CsrfFilter.class::cast)
                .toList();
        assertThat(csrfFilters).as("real CsrfFilter for the protected PUT").hasSize(1);
        Object configuredRepository = ReflectionTestUtils.getField(
                csrfFilters.getFirst(), "tokenRepository");
        assertThat(configuredRepository)
                .as("CsrfFilter must retain the canonical cookie repository")
                .isSameAs(csrfTokenRepository);
    }

    private void shortenTtl(String sessionId) {
        assertThat(redis.expire(sessionKey(sessionId), SHORT_TEST_TTL)).isTrue();
        assertThat(pttl(sessionId)).isPositive().isLessThanOrEqualTo(SHORT_TEST_TTL.toMillis());
    }

    private long pttl(String sessionId) {
        Long value = redis.getExpire(sessionKey(sessionId), MILLISECONDS);
        assertThat(value).as("Redis PTTL for session %s", sessionId).isNotNull();
        return value;
    }

    private static String sessionKey(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }

    private static Cookie sessionCookie(String sessionId) {
        String encoded = Base64.getEncoder().encodeToString(
                sessionId.getBytes(StandardCharsets.UTF_8));
        return new Cookie(SESSION_COOKIE_NAME, encoded);
    }

    private static String decodeSessionId(Cookie cookie) {
        byte[] decoded = Base64.getDecoder().decode(cookie.getValue());
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private void awaitKeyRemoval(String key) throws InterruptedException {
        long deadline = System.nanoTime() + SECONDS.toNanos(3);
        while (Boolean.TRUE.equals(redis.hasKey(key)) && System.nanoTime() < deadline) {
            Thread.sleep(20);
        }
        assertThat(redis.hasKey(key)).isFalse();
    }

    private void saveConcurrently(
            SelectiveTouchSessionRepository.SelectiveSession first,
            SelectiveTouchSessionRepository.SelectiveSession second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> firstSave = executor.submit(() -> saveAfterSignal(first, ready, start));
            Future<?> secondSave = executor.submit(() -> saveAfterSignal(second, ready, start));
            assertThat(ready.await(10, SECONDS)).isTrue();
            start.countDown();
            firstSave.get(10, SECONDS);
            secondSave.get(10, SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
        }
    }

    private Void saveAfterSignal(
            SelectiveTouchSessionRepository.SelectiveSession session,
            CountDownLatch ready,
            CountDownLatch start) {
        try {
            ready.countDown();
            if (!start.await(10, SECONDS)) {
                throw new IllegalStateException("Concurrent Redis save did not start");
            }
            repository.save(session);
            return null;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent Redis save was interrupted", failure);
        }
    }

    private static Instant lastValidActivity(
            SelectiveTouchSessionRepository.SelectiveSession session) {
        Long seconds = session.getAttribute(
                SelectiveTouchSessionRepository.LAST_VALID_EPOCH_SECOND_ATTRIBUTE);
        Integer nanos = session.getAttribute(
                SelectiveTouchSessionRepository.LAST_VALID_NANO_ATTRIBUTE);
        assertThat(seconds).isNotNull();
        assertThat(nanos).isNotNull();
        return Instant.ofEpochSecond(seconds, nanos.longValue());
    }

    private static void assertPttlMatchesDeadline(long pttl, Instant expectedDeadline) {
        long expected = Duration.between(Instant.now(), expectedDeadline).toMillis();
        assertThat(Math.abs(pttl - expected))
                .isLessThanOrEqualTo(TTL_TOLERANCE.toMillis());
    }

    private record SessionHandle(Cookie cookie, String id) {

        private SessionHandle {
            cookie = Objects.requireNonNull(cookie);
            id = Objects.requireNonNull(id);
        }
    }

    @RestController
    static class SessionProbeController {

        @PutMapping(CURRENT_IDENTITY_PATH)
        ResponseEntity<Void> updateCurrentIdentity(
                HttpSession session,
                @RequestHeader(name = "X-Test-Status", required = false) Integer forcedStatus) {
            if (forcedStatus != null) {
                return ResponseEntity.status(forcedStatus).build();
            }
            Integer mutations = (Integer) session.getAttribute("betufu.it.mutations");
            session.setAttribute("betufu.it.mutations", mutations == null ? 1 : mutations + 1);
            return ResponseEntity.noContent().build();
        }

        @GetMapping("/api/v1/session")
        ResponseEntity<Void> session(HttpSession session) {
            return ResponseEntity.noContent().build();
        }

        @GetMapping("/api/v1/poll")
        ResponseEntity<Void> poll(HttpSession session) {
            return ResponseEntity.noContent().build();
        }

        @GetMapping("/api/v1/heartbeat")
        ResponseEntity<Void> heartbeat(HttpSession session) {
            return ResponseEntity.noContent().build();
        }
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
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
                    .clientId("betufu-redis-session-it")
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
                    .clientName("BETUFU Redis Session IT")
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
