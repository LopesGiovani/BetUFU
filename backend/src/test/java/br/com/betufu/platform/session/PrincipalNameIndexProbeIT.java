package br.com.betufu.platform.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
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
import org.springframework.session.FindByIndexNameSessionRepository;
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
 * diretamente as chaves e hashes gravados no banco, comprovando que o índice de principal existe sob
 * a configuração vigente de {@code repository-type: indexed} e {@link SelectiveTouchSessionRepository}.
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
    private static final String EXPIRES_KEY_PREFIX = "betufu:session:sessions:expires:";
    private static final String PRINCIPAL_KEY_PREFIX = "betufu:session:sessions:principal:";
    private static final String INDEX_KEY_PREFIX = "betufu:session:index:";
    private static final String PROBE_SUBJECT = "probe-subject-user-123";
    private static final String OTHER_SUBJECT = "probe-other-subject-user-456";
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
            connection.serverCommands().setConfig("notify-keyspace-events", "KEA");
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
    @DisplayName("A-12: Medir se sessão autenticada real grava índice de principal no Redis sob repository-type=indexed")
    void probeAuthenticatedSessionWritesSecurityContextHashAndPrincipalIndex() throws Exception {
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

        // 4. Prova de isolamento: cria sessão de outra identidade
        SelectiveTouchSessionRepository.SelectiveSession otherSession =
                createAuthenticatedSession(OTHER_SUBJECT);
        String otherSessionId = otherSession.getId();

        // 5. Inspeção de chaves sob o namespace betufu:session:* e ausência de chaves fora dele
        Set<String> allRedisKeys = redis.keys("*");
        Set<String> sessionNamespaceKeys = redis.keys("betufu:session:*");
        assertThat(allRedisKeys)
                .as("No keys must exist outside the betufu:session namespace in Redis")
                .isEqualTo(sessionNamespaceKeys);

        int totalSessionKeys = sessionNamespaceKeys == null ? 0 : sessionNamespaceKeys.size();
        assertThat(totalSessionKeys)
                .as("Total session namespace keys in Redis (3 session hashes + 3 session expires + 2 reverse principals + 2 principal indexes)")
                .isEqualTo(10);

        boolean authenticatedKeyPresent = Boolean.TRUE.equals(redis.hasKey(sessionKey));
        assertThat(authenticatedKeyPresent)
                .as("Authenticated session key must exist under betufu:session:sessions:<session-id>")
                .isTrue();

        boolean authenticatedExpiresKeyPresent = Boolean.TRUE.equals(redis.hasKey(expiredSessionKey(sessionId)));
        assertThat(authenticatedExpiresKeyPresent)
                .as("Authenticated session expires key must exist under betufu:session:sessions:expires:<session-id>")
                .isTrue();

        boolean authenticatedReverseKeyPresent = Boolean.TRUE.equals(redis.hasKey(reversePrincipalKey(sessionId)));
        assertThat(authenticatedReverseKeyPresent)
                .as("Authenticated session reverse principal key must exist under betufu:session:sessions:principal:<session-id>")
                .isTrue();

        boolean anonKeyPresent = Boolean.TRUE.equals(redis.hasKey(sessionKey(anonSession.getId())));
        assertThat(anonKeyPresent)
                .as("Anonymous session key must exist under betufu:session:sessions:<session-id>")
                .isTrue();

        boolean anonExpiresKeyPresent = Boolean.TRUE.equals(redis.hasKey(expiredSessionKey(anonSession.getId())));
        assertThat(anonExpiresKeyPresent)
                .as("Anonymous session expires key must exist under betufu:session:sessions:expires:<session-id>")
                .isTrue();

        boolean anonReverseKeyPresent = Boolean.TRUE.equals(redis.hasKey(reversePrincipalKey(anonSession.getId())));
        assertThat(anonReverseKeyPresent)
                .as("Anonymous session reverse principal key must not exist")
                .isFalse();

        boolean otherKeyPresent = Boolean.TRUE.equals(redis.hasKey(sessionKey(otherSessionId)));
        assertThat(otherKeyPresent)
                .as("Other session key must exist under betufu:session:sessions:<session-id>")
                .isTrue();

        boolean otherExpiresKeyPresent = Boolean.TRUE.equals(redis.hasKey(expiredSessionKey(otherSessionId)));
        assertThat(otherExpiresKeyPresent)
                .as("Other session expires key must exist under betufu:session:sessions:expires:<session-id>")
                .isTrue();

        boolean otherReverseKeyPresent = Boolean.TRUE.equals(redis.hasKey(reversePrincipalKey(otherSessionId)));
        assertThat(otherReverseKeyPresent)
                .as("Other session reverse principal key must exist under betufu:session:sessions:principal:<session-id>")
                .isTrue();

        // 6. Detecção e validação de chaves de índice de principal do Spring Session
        String probeIndexKey = principalIndexKey(PROBE_SUBJECT);
        String otherIndexKey = principalIndexKey(OTHER_SUBJECT);

        Set<String> explicitIndexKeys = redis.keys("betufu:session:index:*");
        long explicitIndexKeyCount = explicitIndexKeys == null ? 0 : explicitIndexKeys.size();
        assertThat(explicitIndexKeyCount)
                .as("Count of betufu:session:index:* keys in Redis")
                .isEqualTo(2);

        assertThat(explicitIndexKeys)
                .as("Index keys must match expected principal index keys")
                .containsExactlyInAnyOrder(probeIndexKey, otherIndexKey);

        Long probeIndexSize = redis.opsForSet().size(probeIndexKey);
        assertThat(probeIndexSize)
                .as("Principal index set for probe subject must contain exactly 1 member")
                .isEqualTo(1L);

        Long otherIndexSize = redis.opsForSet().size(otherIndexKey);
        assertThat(otherIndexSize)
                .as("Principal index set for other subject must contain exactly 1 member")
                .isEqualTo(1L);

        // 7. Validação da consulta por principal via repositório
        assertThat(repository.findByPrincipalName(PROBE_SUBJECT))
                .as("Repository lookup by probe principal name must return exactly probe session")
                .hasSize(1)
                .containsKey(sessionId)
                .doesNotContainKey(anonSession.getId())
                .doesNotContainKey(otherSessionId);

        assertThat(repository.findByPrincipalName(OTHER_SUBJECT))
                .as("Repository lookup by other principal name must return exactly other session")
                .hasSize(1)
                .containsKey(otherSessionId)
                .doesNotContainKey(sessionId)
                .doesNotContainKey(anonSession.getId());

        // 8. Verificação de formato sanitizado de todas as chaves no namespace
        boolean allKeysMatchSanitizedPrefixes = sessionNamespaceKeys != null
                && sessionNamespaceKeys.stream().allMatch(
                        k -> k.startsWith(SESSION_KEY_PREFIX) || k.startsWith(INDEX_KEY_PREFIX));
        assertThat(allKeysMatchSanitizedPrefixes)
                .as("All keys in namespace must match betufu:session:sessions:* or betufu:session:index:*")
                .isTrue();
    }

    @Test
    @DisplayName("A-12: Footprint em runtime sob repository-type=indexed grava chave de sessão, chave de expiração e índice de principal sem estruturas legadas")
    void probeRuntimeFootprintUnderIndexedRepositoryAndSelectiveTouchWrapper() throws Exception {
        SelectiveTouchSessionRepository.SelectiveSession session = createAuthenticatedSession(PROBE_SUBJECT);
        String sessionId = session.getId();
        Cookie sessionCookie = sessionCookie(sessionId);
        Cookie xsrfCookie = materializeXsrfWithoutRenewing(sessionCookie);

        mockMvc.perform(put(CURRENT_IDENTITY_PATH)
                        .cookie(sessionCookie, xsrfCookie)
                        .header(XSRF_HEADER_NAME, xsrfCookie.getValue()))
                .andExpect(status().isNoContent());

        // Confirma por contagens sanitizadas a ausência de estruturas legadas de expiração no runtime vigente
        Set<String> expirationKeys = redis.keys("*expirations*");
        long expirationKeyCount = expirationKeys == null ? 0 : expirationKeys.size();
        assertThat(expirationKeyCount)
                .as("Count of legacy expiration keys in Redis under current runtime")
                .isZero();

        Set<String> indexKeys = redis.keys("betufu:session:index:*");
        long indexKeyCount = indexKeys == null ? 0 : indexKeys.size();
        assertThat(indexKeyCount)
                .as("Count of principal index keys in Redis under current runtime")
                .isEqualTo(1);

        String expectedIndexKey = principalIndexKey(PROBE_SUBJECT);
        assertThat(indexKeys)
                .as("Index keys in Redis must contain the expected principal index key")
                .containsExactly(expectedIndexKey);

        Long indexMemberCount = redis.opsForSet().size(expectedIndexKey);
        assertThat(indexMemberCount)
                .as("Principal index set in Redis must contain exactly 1 member")
                .isEqualTo(1L);

        assertThat(repository.findByPrincipalName(PROBE_SUBJECT))
                .as("Repository lookup by probe principal name must return exactly probe session")
                .hasSize(1)
                .containsKey(sessionId);

        // O runtime observa a presença da chave da sessão sob o prefixo sanitizado e nenhuma chave fora do namespace
        boolean sessionKeyPresent = Boolean.TRUE.equals(redis.hasKey(sessionKey(sessionId)));
        assertThat(sessionKeyPresent)
                .as("Session key exists under betufu:session:sessions:<session-id>")
                .isTrue();

        boolean expiresKeyPresent = Boolean.TRUE.equals(redis.hasKey(expiredSessionKey(sessionId)));
        assertThat(expiresKeyPresent)
                .as("Session expires key exists under betufu:session:sessions:expires:<session-id>")
                .isTrue();

        boolean reverseKeyPresent = Boolean.TRUE.equals(redis.hasKey(reversePrincipalKey(sessionId)));
        assertThat(reverseKeyPresent)
                .as("Session reverse principal key exists under betufu:session:sessions:principal:<session-id>")
                .isTrue();

        Set<String> allKeys = redis.keys("*");
        Set<String> sessionNamespaceKeys = redis.keys("betufu:session:*");
        assertThat(allKeys)
                .as("No keys must exist outside betufu:session namespace")
                .isEqualTo(sessionNamespaceKeys);
        assertThat(sessionNamespaceKeys)
                .as("Exactly 4 keys exist in namespace: session hash, session expires key, reverse principal key and principal index set")
                .hasSize(4);
    }

    @Test
    @DisplayName("A-12: Expiração natural via chave sessions:expires limpa automaticamente o índice de principal e o hash da sessão via keyspace notifications sem consulta prévia")
    void naturalSessionExpirationCleansUpPrincipalIndexAndSessionHashViaKeyspaceNotifications() throws Exception {
        // 1. Cria sessão autenticada para o probe subject
        SelectiveTouchSessionRepository.SelectiveSession targetSession =
                createAuthenticatedSession(PROBE_SUBJECT);
        String targetSessionId = targetSession.getId();

        // 2. Cria sessão autenticada para outra identidade (isolamento)
        SelectiveTouchSessionRepository.SelectiveSession otherSession =
                createAuthenticatedSession(OTHER_SUBJECT);
        String otherSessionId = otherSession.getId();

        String targetSessionKey = sessionKey(targetSessionId);
        String targetExpiredKey = expiredSessionKey(targetSessionId);
        String targetReverseKey = reversePrincipalKey(targetSessionId);
        String targetIndexKey = principalIndexKey(PROBE_SUBJECT);

        String otherSessionKey = sessionKey(otherSessionId);
        String otherExpiredKey = expiredSessionKey(otherSessionId);
        String otherReverseKey = reversePrincipalKey(otherSessionId);
        String otherIndexKey = principalIndexKey(OTHER_SUBJECT);

        // Precondição: Ambas as sessões, suas sombras, reversas e seus índices existem no Redis com cardinalidade exatamente 1
        assertThat(redis.hasKey(targetSessionKey)).isTrue();
        assertThat(redis.hasKey(targetExpiredKey)).isTrue();
        assertThat(redis.hasKey(targetReverseKey)).isTrue();
        assertThat(redis.hasKey(targetIndexKey)).isTrue();
        assertThat(redis.opsForSet().size(targetIndexKey)).isEqualTo(1L);

        assertThat(redis.hasKey(otherSessionKey)).isTrue();
        assertThat(redis.hasKey(otherExpiredKey)).isTrue();
        assertThat(redis.hasKey(otherReverseKey)).isTrue();
        assertThat(redis.hasKey(otherIndexKey)).isTrue();
        assertThat(redis.opsForSet().size(otherIndexKey)).isEqualTo(1L);

        // 3. Encurta o TTL da chave expires e do hash da sessão alvo para expiração rápida (mantendo margem na reversa para o listener processar)
        redis.expire(targetExpiredKey, Duration.ofMillis(50));
        redis.expire(targetSessionKey, Duration.ofMillis(50));

        // 4. Aguarda de forma limitada e assertiva a expiração e limpeza em background pelo listener
        // CRUCIAL: NÃO chamar repository.findByPrincipalName ou repository.findById antes da asserção!
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        boolean cleanedUp = false;
        while (System.nanoTime() < deadline) {
            Boolean sessionKeyExists = redis.hasKey(targetSessionKey);
            Boolean reverseKeyExists = redis.hasKey(targetReverseKey);
            Boolean indexKeyExists = redis.hasKey(targetIndexKey);
            Long indexSize = redis.opsForSet().size(targetIndexKey);
            boolean indexCleaned = Boolean.FALSE.equals(indexKeyExists) || Long.valueOf(0L).equals(indexSize);
            if (Boolean.FALSE.equals(sessionKeyExists) && Boolean.FALSE.equals(reverseKeyExists) && indexCleaned) {
                cleanedUp = true;
                break;
            }
            Thread.sleep(25);
        }

        assertThat(cleanedUp)
                .as("SelectiveTouchSessionRepository.onMessage must clean up session hash, reverse key and principal index on key expiration")
                .isTrue();

        // Asserções diretas no Redis sem tocar no repositório:
        assertThat(redis.hasKey(targetSessionKey))
                .as("Target session hash must be deleted by expiration or listener")
                .isFalse();
        assertThat(redis.hasKey(targetExpiredKey))
                .as("Target session expires key must be deleted by expiration")
                .isFalse();
        assertThat(redis.hasKey(targetReverseKey))
                .as("Target reverse principal key must be deleted by the expiration listener")
                .isFalse();
        boolean targetIndexCleaned = Boolean.FALSE.equals(redis.hasKey(targetIndexKey))
                || Long.valueOf(0L).equals(redis.opsForSet().size(targetIndexKey));
        assertThat(targetIndexCleaned)
                .as("Target principal index key must disappear or have cardinality 0")
                .isTrue();

        // 5. Confirma isolamento da outra identidade:
        assertThat(redis.hasKey(otherSessionKey))
                .as("Other session key must remain intact in Redis")
                .isTrue();
        assertThat(redis.hasKey(otherExpiredKey))
                .as("Other session expires key must remain intact in Redis")
                .isTrue();
        assertThat(redis.hasKey(otherReverseKey))
                .as("Other session reverse principal key must remain intact in Redis")
                .isTrue();
        assertThat(redis.hasKey(otherIndexKey))
                .as("Other principal index key must continue to exist in Redis")
                .isTrue();
        assertThat(redis.opsForSet().size(otherIndexKey))
                .as("Other principal index set must still contain exactly 1 member")
                .isEqualTo(1L);
        assertThat(repository.findByPrincipalName(OTHER_SUBJECT))
                .as("Other session must still be discoverable by principal name")
                .containsKey(otherSessionId);
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

    private static String expiredSessionKey(String sessionId) {
        return EXPIRES_KEY_PREFIX + sessionId;
    }

    private static String reversePrincipalKey(String sessionId) {
        return PRINCIPAL_KEY_PREFIX + sessionId;
    }

    private static String principalIndexKey(String principalName) {
        return INDEX_KEY_PREFIX
                + FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME
                + ":"
                + principalName;
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
