package br.com.betufu.platform.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.Filter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import br.com.betufu.platform.security.SecurityConfiguration;

/**
 * Medição empírica do índice de principal do Spring Session em uma sessão autenticada real (A-12).
 *
 * <p>Esta prova sobe o contexto de segurança e sessão da BETUFU com Redis real para inspecionar
 * diretamente as chaves e hashes gravados no banco, respondendo se o índice de principal existe sob
 * a configuração vigente de {@code repository-type: default} e {@link SelectiveTouchSessionRepository}.
 */
@SpringBootTest(
        classes = {
            SecurityConfiguration.class,
            SessionSecurityConfiguration.class,
            PrincipalNameIndexProbeIT.TestApplication.class,
            PrincipalNameIndexProbeIT.SessionProbeController.class
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
            "BETUFU_OIDC_CLIENT_ID=betufu-redis-probe-it",
            "BETUFU_OIDC_CLIENT_SECRET=unused"
        })
@ActiveProfiles("integrated")
@Testcontainers
class PrincipalNameIndexProbeIT {

    private static final String CURRENT_IDENTITY_PATH = "/api/v1/tracer/identity/current";
    private static final String SESSION_COOKIE_NAME = "SESSION";
    private static final String XSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String XSRF_HEADER_NAME = "X-XSRF-TOKEN";
    private static final String SESSION_KEY_PREFIX = "betufu:session:sessions:";
    private static final String PROBE_SUBJECT = "probe-subject-user-123";
    private static final String PROBE_ISSUER = "https://idp.invalid";

    @Container
    static final GenericContainer<?> REDIS = RedisContainerSupport.REDIS;

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        RedisContainerSupport.registerProperties(registry);
    }

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private MutableTestClock clock;

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
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .addFilters(springSessionRepositoryFilter, springSecurityFilterChain)
                .build();
    }

    @Test
    @DisplayName("A-12: Medir se sessão autenticada real grava índice de principal no Redis sob repository-type=default")
    void probeAuthenticatedSessionWritesSecurityContextHashAndNoPrincipalIndex() throws Exception {
        // 1. Constrói uma sessão autenticada de verdade com SecurityContext contendo DefaultOidcUser
        SelectiveTouchSessionRepository.SelectiveSession authenticatedSession =
                createAuthenticatedSession(PROBE_SUBJECT);
        String sessionId = authenticatedSession.getId();
        Cookie sessionCookie = sessionCookie(sessionId);
        Cookie xsrfCookie = materializeXsrfWithoutRenewing(sessionCookie);

        // Executa uma requisição autenticada aprovada para exercitar toda a cadeia de filtros e persistência
        mockMvc.perform(put(CURRENT_IDENTITY_PATH)
                        .cookie(sessionCookie, xsrfCookie)
                        .header(XSRF_HEADER_NAME, xsrfCookie.getValue()))
                .andExpect(status().isNoContent());

        // 2. Rejeição de prova vazia: confirma booleano de que o hash da sessão contém SPRING_SECURITY_CONTEXT no Redis
        String sessionKey = sessionKey(sessionId);
        boolean hasSecurityContext = Boolean.TRUE.equals(
                redis.opsForHash().hasKey(sessionKey, "sessionAttr:SPRING_SECURITY_CONTEXT"));
        assertThat(hasSecurityContext)
                .as("Authenticated session in Redis must contain SPRING_SECURITY_CONTEXT")
                .isTrue();

        boolean hasCreationTime = Boolean.TRUE.equals(
                redis.opsForHash().hasKey(sessionKey, "creationTime"));
        assertThat(hasCreationTime).isTrue();

        boolean hasLastAccessedTime = Boolean.TRUE.equals(
                redis.opsForHash().hasKey(sessionKey, "lastAccessedTime"));
        assertThat(hasLastAccessedTime).isTrue();

        boolean hasMaxInactiveInterval = Boolean.TRUE.equals(
                redis.opsForHash().hasKey(sessionKey, "maxInactiveInterval"));
        assertThat(hasMaxInactiveInterval).isTrue();

        boolean hasLastValidActivity = Boolean.TRUE.equals(
                redis.opsForHash().hasKey(sessionKey, "sessionAttr:betufu.session.lastValidActivityAt.epochSecond"));
        assertThat(hasLastValidActivity).isTrue();

        boolean hasAcr = Boolean.TRUE.equals(
                redis.opsForHash().hasKey(sessionKey, "sessionAttr:betufu.session.authentication.acr"));
        assertThat(hasAcr).isTrue();

        // 3. Prova discriminante: sessão anônima não deve possuir SPRING_SECURITY_CONTEXT
        SelectiveTouchSessionRepository.SelectiveSession anonSession = repository.createSession();
        repository.save(anonSession);
        boolean anonHasSecurityContext = Boolean.TRUE.equals(
                redis.opsForHash().hasKey(sessionKey(anonSession.getId()), "sessionAttr:SPRING_SECURITY_CONTEXT"));
        assertThat(anonHasSecurityContext)
                .as("Anonymous session in Redis must not contain SPRING_SECURITY_CONTEXT")
                .isFalse();

        // 4. Inspeção de chaves sob o namespace betufu:session:*
        Set<String> sessionNamespaceKeys = redis.keys("betufu:session:*");
        int totalSessionKeys = sessionNamespaceKeys == null ? 0 : sessionNamespaceKeys.size();
        assertThat(totalSessionKeys)
                .as("Total session namespace keys in Redis")
                .isEqualTo(2);

        boolean authenticatedKeyPresent = Boolean.TRUE.equals(redis.hasKey(sessionKey));
        assertThat(authenticatedKeyPresent)
                .as("Authenticated session key must exist under betufu:session:sessions:<session-id>")
                .isTrue();

        boolean anonKeyPresent = Boolean.TRUE.equals(redis.hasKey(sessionKey(anonSession.getId())));
        assertThat(anonKeyPresent)
                .as("Anonymous session key must exist under betufu:session:sessions:<session-id>")
                .isTrue();

        // 5. Detecção de chave de índice de principal do Spring Session por contagens
        Set<String> indexKeys = redis.keys("*index*");
        long indexKeyCount = indexKeys == null ? 0 : indexKeys.size();
        assertThat(indexKeyCount)
                .as("Count of index keys in Redis")
                .isZero();

        Set<String> principalKeys = redis.keys("*principal*");
        long principalKeyCount = principalKeys == null ? 0 : principalKeys.size();
        assertThat(principalKeyCount)
                .as("Count of principal keys in Redis")
                .isZero();

        Set<String> explicitIndexKeys = redis.keys("betufu:session:index:*");
        long explicitIndexKeyCount = explicitIndexKeys == null ? 0 : explicitIndexKeys.size();
        assertThat(explicitIndexKeyCount)
                .as("Count of betufu:session:index:* keys in Redis")
                .isZero();

        // 6. Verificação de formato sanitizado de todas as chaves no namespace
        boolean allKeysMatchSanitizedPrefix = sessionNamespaceKeys != null
                && sessionNamespaceKeys.stream().allMatch(k -> k.startsWith(SESSION_KEY_PREFIX));
        assertThat(allKeysMatchSanitizedPrefix)
                .as("All keys in namespace must match betufu:session:sessions:<session-id>")
                .isTrue();
    }

    @Test
    @DisplayName("A-12: Footprint em runtime não discrimina isoladamente se a ausência de estruturas auxiliares decorre do repositório default, do wrapper, ou de ambos")
    void probeRuntimeFootprintUnderDefaultRepositoryAndSelectiveTouchWrapperDoesNotDiscriminateIndividualCause() throws Exception {
        SelectiveTouchSessionRepository.SelectiveSession session = createAuthenticatedSession(PROBE_SUBJECT);
        String sessionId = session.getId();
        Cookie sessionCookie = sessionCookie(sessionId);
        Cookie xsrfCookie = materializeXsrfWithoutRenewing(sessionCookie);

        mockMvc.perform(put(CURRENT_IDENTITY_PATH)
                        .cookie(sessionCookie, xsrfCookie)
                        .header(XSRF_HEADER_NAME, xsrfCookie.getValue()))
                .andExpect(status().isNoContent());

        // Confirma por contagens sanitizadas a ausência de estruturas auxiliares no runtime vigente
        Set<String> expirationKeys = redis.keys("*expirations*");
        long expirationKeyCount = expirationKeys == null ? 0 : expirationKeys.size();
        assertThat(expirationKeyCount)
                .as("Count of expiration keys in Redis under current runtime")
                .isZero();

        Set<String> indexKeys = redis.keys("betufu:session:index:*");
        long indexKeyCount = indexKeys == null ? 0 : indexKeys.size();
        assertThat(indexKeyCount)
                .as("Count of principal index keys in Redis under current runtime")
                .isZero();

        // O runtime observa apenas a presença da chave da sessão sob o prefixo sanitizado
        boolean sessionKeyPresent = Boolean.TRUE.equals(redis.hasKey(sessionKey(sessionId)));
        assertThat(sessionKeyPresent)
                .as("Session key exists under betufu:session:sessions:<session-id>")
                .isTrue();
    }

    private SelectiveTouchSessionRepository.SelectiveSession createAuthenticatedSession(String subject) {
        SelectiveTouchSessionRepository.SelectiveSession session = repository.createSession();
        Instant now = clock.instant();

        OidcIdToken idToken = OidcIdToken.withTokenValue("probe-token-value")
                .issuer(PROBE_ISSUER)
                .subject(subject)
                .claim("preferred_username", "probe-user")
                .claim("acr", "urn:betufu:acr:mfa")
                .claim("amr", List.of("pwd", "otp"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();

        DefaultOidcUser oidcUser = new DefaultOidcUser(
                AuthorityUtils.createAuthorityList("ROLE_USER"),
                idToken,
                IdTokenClaimNames.SUB);

        boolean nameMatchesSubject = Objects.equals(oidcUser.getName(), subject);
        assertThat(nameMatchesSubject)
                .as("OidcUser name must match subject claim")
                .isTrue();

        OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
                oidcUser,
                oidcUser.getAuthorities(),
                "keycloak");

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authentication);

        session.setAttribute("SPRING_SECURITY_CONTEXT", securityContext);
        session.setAttribute(
                SelectiveTouchSessionRepository.LAST_VALID_EPOCH_SECOND_ATTRIBUTE,
                now.getEpochSecond());
        session.setAttribute(
                SelectiveTouchSessionRepository.LAST_VALID_NANO_ATTRIBUTE,
                now.getNano());
        session.setAttribute(
                SelectiveTouchSessionRepository.AUTHENTICATION_ACR_ATTRIBUTE,
                "urn:betufu:acr:mfa");
        session.setAttribute(
                SelectiveTouchSessionRepository.AUTHENTICATION_AMR_COUNT_ATTRIBUTE,
                2);
        session.setAttribute(
                SelectiveTouchSessionRepository.AUTHENTICATION_AMR_PREFIX + 0,
                "pwd");
        session.setAttribute(
                SelectiveTouchSessionRepository.AUTHENTICATION_AMR_PREFIX + 1,
                "otp");
        session.setAttribute(
                SelectiveTouchSessionRepository.APPROVED_ACTIVITY_ATTRIBUTE,
                now);

        repository.save(session);
        return session;
    }

    private Cookie materializeXsrfWithoutRenewing(Cookie sessionCookie)
            throws Exception {
        MvcResult result = mockMvc.perform(get("/login").cookie(sessionCookie)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        Cookie xsrfCookie = result.getResponse().getCookie(XSRF_COOKIE_NAME);
        assertThat(xsrfCookie).as("real XSRF cookie from the login page").isNotNull();
        boolean hasXsrfValue = xsrfCookie != null && !xsrfCookie.getValue().isBlank();
        assertThat(hasXsrfValue).as("XSRF cookie must have non-blank value").isTrue();
        return xsrfCookie;
    }

    private static String sessionKey(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }

    private static Cookie sessionCookie(String sessionId) {
        String encoded = Base64.getEncoder().encodeToString(
                sessionId.getBytes(StandardCharsets.UTF_8));
        return new Cookie(SESSION_COOKIE_NAME, encoded);
    }

    @RestController
    static class SessionProbeController {

        @PutMapping(CURRENT_IDENTITY_PATH)
        ResponseEntity<Void> probe(HttpSession session) {
            Integer mutations = (Integer) session.getAttribute("betufu.it.mutations");
            session.setAttribute("betufu.it.mutations", mutations == null ? 1 : mutations + 1);
            return ResponseEntity.noContent().build();
        }

        @GetMapping("/login")
        ResponseEntity<Void> login() {
            return ResponseEntity.ok().build();
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
                    .clientId("betufu-redis-probe-it")
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
                    .clientName("BETUFU Redis Probe IT")
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
