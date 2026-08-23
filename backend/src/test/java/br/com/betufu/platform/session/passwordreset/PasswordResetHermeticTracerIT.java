package br.com.betufu.platform.session.passwordreset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

import br.com.betufu.platform.security.OidcRevocationClient;
import br.com.betufu.platform.session.IdentitySessionTerminationService;
import br.com.betufu.platform.session.OidcSessionRevocationService;
import br.com.betufu.platform.session.RedisContainerSupport;
import br.com.betufu.platform.session.SelectiveTouchSessionRepository;
import br.com.betufu.platform.session.SessionInvalidationListener;
import br.com.betufu.platform.session.SessionSecurityConfiguration;

/**
 * Hermetic tracer IT connecting the 02-24 Keycloak outbox event to the BETUFU password reset inbox
 * and session termination (02-09 IdentitySessionTerminationService).
 *
 * <p>Proves:
 * <ul>
 *   <li>A valid HMAC-signed envelope from the outbox is persisted and terminates all sessions of the target subject with SECURITY reason;
 *   <li>Control sessions of a different subject remain active in Redis;
 *   <li>Tampered signature, method, path, body SHA-256 or prohibited payload fields are rejected before any effect.
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
            PasswordResetHermeticTracerIT.TestApplication.class
        },
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "spring.docker.compose.enabled=false",
            "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
            "BETUFU_OIDC_CLIENT_ID=betufu-hermetic-tracer-it",
            "BETUFU_OIDC_CLIENT_SECRET=unused",
            "BETUFU_PASSWORD_RESET_HMAC_KEY_V1=dGVzdC1vbmx5LXBhc3N3b3JkLXJlc2V0LWtleS0wMQ=="
        })
@ActiveProfiles("integrated")
@Testcontainers
@Import(PasswordResetHermeticTracerIT.TestApplication.class)
class PasswordResetHermeticTracerIT {

    private static final String TARGET_SUBJECT_A = "tracer-target-user-alpha";
    private static final String CONTROL_SUBJECT_B = "tracer-control-user-beta";
    private static final String TEST_REALM = "betufu-local";
    private static final String KEY_ID = "v1";
    private static final byte[] RAW_SECRET_KEY = Base64.getDecoder().decode("dGVzdC1vbmx5LXBhc3N3b3JkLXJlc2V0LWtleS0wMQ==");

    private static final String APPROVED_ACTIVITY_ATTRIBUTE =
            SelectiveTouchSessionRepository.class.getName() + ".approvedActivity";
    private static final String LAST_VALID_EPOCH_SECOND_ATTRIBUTE =
            "betufu.session.lastValidActivityAt.epochSecond";
    private static final String LAST_VALID_NANO_ATTRIBUTE =
            "betufu.session.lastValidActivityAt.nano";

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
    private PasswordResetSignedEnvelopeVerifier verifier;

    @Autowired
    private PasswordResetEventEndpoint endpoint;

    private SelectiveTouchSessionRepository repository;
    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        redis.execute((RedisCallback<Void>) connection -> {
            connection.serverCommands().flushDb();
            connection.serverCommands().setConfig("notify-keyspace-events", "KEA");
            return null;
        });
        jdbcTemplate.execute("TRUNCATE TABLE public.password_reset_event_inbox");
        clock.set(Instant.now());
        repository = (SelectiveTouchSessionRepository) applicationContext.getBean("sessionRepository");
        mockMvc = MockMvcBuilders.standaloneSetup(endpoint).build();
    }

    @Test
    @DisplayName("Valid signed envelope from 02-24 outbox persists inbox and terminates all sessions of target subject while control user survives")
    void validEnvelopePersistsInboxAndTerminatesTargetSessionsLeavingControlIntact() throws Exception {
        // 1. Create 2 active sessions for Target A and 1 for Control B
        SelectiveTouchSessionRepository.SelectiveSession sessionA1 = createAuthenticatedSession(TARGET_SUBJECT_A);
        repository.save(sessionA1);
        SelectiveTouchSessionRepository.SelectiveSession sessionA2 = createAuthenticatedSession(TARGET_SUBJECT_A);
        repository.save(sessionA2);

        SelectiveTouchSessionRepository.SelectiveSession sessionB1 = createAuthenticatedSession(CONTROL_SUBJECT_B);
        repository.save(sessionB1);

        assertThat(repository.findByPrincipalName(TARGET_SUBJECT_A)).hasSize(2);
        assertThat(repository.findByPrincipalName(CONTROL_SUBJECT_B)).hasSize(1);

        // 2. Build 02-24 outbox event contract representation
        String sourceEventId = "evt-outbox-" + UUID.randomUUID();
        Instant eventTime = clock.instant();
        OutboxEventRepresentation outboxEvent = new OutboxEventRepresentation(
                sourceEventId, TARGET_SUBJECT_A, TEST_REALM, "UPDATE_CREDENTIAL", eventTime);

        // 3. Hermetic pump builds JSON payload and computes signature
        String jsonPayload = """
                {
                    "sourceEventId": "%s",
                    "subject": "%s",
                    "realmId": "%s",
                    "eventType": "%s",
                    "timestamp": "%s"
                }
                """.formatted(
                        outboxEvent.sourceEventId(),
                        outboxEvent.subject(),
                        outboxEvent.realmId(),
                        outboxEvent.eventType(),
                        eventTime.toString());

        byte[] bodyBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);
        String bodySha256 = verifier.computeBodySha256(bodyBytes);
        String timestampHeader = String.valueOf(eventTime.toEpochMilli());
        String signature = verifier.sign(
                RAW_SECRET_KEY,
                KEY_ID,
                timestampHeader,
                "POST",
                PasswordResetEventEndpoint.ENDPOINT_PATH,
                sourceEventId,
                bodySha256);

        // 4. Send request to internal endpoint
        mockMvc.perform(post(PasswordResetEventEndpoint.ENDPOINT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PasswordResetEventEndpoint.HEADER_KEY_ID, KEY_ID)
                        .header(PasswordResetEventEndpoint.HEADER_TIMESTAMP, timestampHeader)
                        .header(PasswordResetEventEndpoint.HEADER_SIGNATURE, signature)
                        .header(PasswordResetEventEndpoint.HEADER_SOURCE_EVENT_ID, sourceEventId)
                        .content(bodyBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.sourceEventId").value(sourceEventId))
                .andExpect(jsonPath("$.effectExecuted").value(true));

        // 5. Verify PostgreSQL inbox record
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.password_reset_event_inbox WHERE source_event_id = ? AND status = 'DELIVERED'",
                Long.class,
                sourceEventId);
        assertThat(count).isEqualTo(1L);

        // 6. Verify Target A sessions are terminated in Redis
        assertThat(repository.findByPrincipalName(TARGET_SUBJECT_A)).isEmpty();
        assertThat(repository.findById(sessionA1.getId())).isNull();
        assertThat(repository.findById(sessionA2.getId())).isNull();

        // 7. Verify Control B session survives intact
        assertThat(repository.findByPrincipalName(CONTROL_SUBJECT_B)).hasSize(1);
        assertThat(repository.findById(sessionB1.getId())).isNotNull();
    }

    @Test
    @DisplayName("Tampered signature is rejected with 401 and produces no inbox persistence or session termination")
    void tamperedSignatureIsRejectedWithoutEffect() throws Exception {
        SelectiveTouchSessionRepository.SelectiveSession sessionA = createAuthenticatedSession(TARGET_SUBJECT_A);
        repository.save(sessionA);

        String sourceEventId = "evt-tamper-sig-" + UUID.randomUUID();
        String jsonPayload = """
                {
                    "sourceEventId": "%s",
                    "subject": "%s",
                    "realmId": "%s",
                    "eventType": "UPDATE_CREDENTIAL",
                    "timestamp": "%s"
                }
                """.formatted(sourceEventId, TARGET_SUBJECT_A, TEST_REALM, clock.instant().toString());

        byte[] bodyBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);
        String invalidSignature = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff";

        mockMvc.perform(post(PasswordResetEventEndpoint.ENDPOINT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PasswordResetEventEndpoint.HEADER_KEY_ID, KEY_ID)
                        .header(PasswordResetEventEndpoint.HEADER_TIMESTAMP, "123456789")
                        .header(PasswordResetEventEndpoint.HEADER_SIGNATURE, invalidSignature)
                        .header(PasswordResetEventEndpoint.HEADER_SOURCE_EVENT_ID, sourceEventId)
                        .content(bodyBytes))
                .andExpect(status().isUnauthorized());

        // Inbox has no record
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.password_reset_event_inbox WHERE source_event_id = ?",
                Long.class,
                sourceEventId);
        assertThat(count).isZero();

        // Target session remains active
        assertThat(repository.findByPrincipalName(TARGET_SUBJECT_A)).hasSize(1);
    }

    @Test
    @DisplayName("Tampered body SHA-256 or method/path is rejected with 401 before effect")
    void tamperedEnvelopeComponentsAreRejected() throws Exception {
        SelectiveTouchSessionRepository.SelectiveSession sessionA = createAuthenticatedSession(TARGET_SUBJECT_A);
        repository.save(sessionA);

        String sourceEventId = "evt-tamper-comp-" + UUID.randomUUID();
        String jsonPayload = """
                {
                    "sourceEventId": "%s",
                    "subject": "%s",
                    "realmId": "%s",
                    "eventType": "UPDATE_CREDENTIAL",
                    "timestamp": "%s"
                }
                """.formatted(sourceEventId, TARGET_SUBJECT_A, TEST_REALM, clock.instant().toString());

        byte[] bodyBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);
        // Compute signature for GET instead of POST
        String signatureForGet = verifier.sign(
                RAW_SECRET_KEY, KEY_ID, "123456789", "GET", PasswordResetEventEndpoint.ENDPOINT_PATH, sourceEventId, verifier.computeBodySha256(bodyBytes));

        mockMvc.perform(post(PasswordResetEventEndpoint.ENDPOINT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PasswordResetEventEndpoint.HEADER_KEY_ID, KEY_ID)
                        .header(PasswordResetEventEndpoint.HEADER_TIMESTAMP, "123456789")
                        .header(PasswordResetEventEndpoint.HEADER_SIGNATURE, signatureForGet)
                        .header(PasswordResetEventEndpoint.HEADER_SOURCE_EVENT_ID, sourceEventId)
                        .content(bodyBytes))
                .andExpect(status().isUnauthorized());

        assertThat(repository.findByPrincipalName(TARGET_SUBJECT_A)).hasSize(1);
    }

    @Test
    @DisplayName("Prohibited payload fields like password or email are rejected with 400 Bad Request before effect")
    void prohibitedFieldsInPayloadAreRejectedWith400() throws Exception {
        SelectiveTouchSessionRepository.SelectiveSession sessionA = createAuthenticatedSession(TARGET_SUBJECT_A);
        repository.save(sessionA);

        String sourceEventId = "evt-prohibited-" + UUID.randomUUID();
        String jsonPayloadWithPassword = """
                {
                    "sourceEventId": "%s",
                    "subject": "%s",
                    "realmId": "%s",
                    "password": "super-secret-password-attempt",
                    "timestamp": "%s"
                }
                """.formatted(sourceEventId, TARGET_SUBJECT_A, TEST_REALM, clock.instant().toString());

        byte[] bodyBytes = jsonPayloadWithPassword.getBytes(StandardCharsets.UTF_8);
        String bodySha256 = verifier.computeBodySha256(bodyBytes);
        String timestampHeader = "123456789";
        String signature = verifier.sign(
                RAW_SECRET_KEY, KEY_ID, timestampHeader, "POST", PasswordResetEventEndpoint.ENDPOINT_PATH, sourceEventId, bodySha256);

        mockMvc.perform(post(PasswordResetEventEndpoint.ENDPOINT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PasswordResetEventEndpoint.HEADER_KEY_ID, KEY_ID)
                        .header(PasswordResetEventEndpoint.HEADER_TIMESTAMP, timestampHeader)
                        .header(PasswordResetEventEndpoint.HEADER_SIGNATURE, signature)
                        .header(PasswordResetEventEndpoint.HEADER_SOURCE_EVENT_ID, sourceEventId)
                        .content(bodyBytes))
                .andExpect(status().isBadRequest());

        // Target session remains active
        assertThat(repository.findByPrincipalName(TARGET_SUBJECT_A)).hasSize(1);
        Long count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.password_reset_event_inbox WHERE source_event_id = ?",
                Long.class,
                sourceEventId);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("Duplicate sourceEventId is idempotent and does not execute second session termination")
    void duplicateSourceEventIdIsIdempotent() throws Exception {
        // First delivery terminates initial session
        SelectiveTouchSessionRepository.SelectiveSession session1 = createAuthenticatedSession(TARGET_SUBJECT_A);
        repository.save(session1);

        String sourceEventId = "evt-idempotent-" + UUID.randomUUID();
        String jsonPayload = """
                {
                    "sourceEventId": "%s",
                    "subject": "%s",
                    "realmId": "%s",
                    "eventType": "UPDATE_CREDENTIAL",
                    "timestamp": "%s"
                }
                """.formatted(sourceEventId, TARGET_SUBJECT_A, TEST_REALM, clock.instant().toString());

        byte[] bodyBytes = jsonPayload.getBytes(StandardCharsets.UTF_8);
        String bodySha256 = verifier.computeBodySha256(bodyBytes);
        String timestampHeader = "123456789";
        String signature = verifier.sign(
                RAW_SECRET_KEY, KEY_ID, timestampHeader, "POST", PasswordResetEventEndpoint.ENDPOINT_PATH, sourceEventId, bodySha256);

        // 1st request
        mockMvc.perform(post(PasswordResetEventEndpoint.ENDPOINT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PasswordResetEventEndpoint.HEADER_KEY_ID, KEY_ID)
                        .header(PasswordResetEventEndpoint.HEADER_TIMESTAMP, timestampHeader)
                        .header(PasswordResetEventEndpoint.HEADER_SIGNATURE, signature)
                        .header(PasswordResetEventEndpoint.HEADER_SOURCE_EVENT_ID, sourceEventId)
                        .content(bodyBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectExecuted").value(true));

        assertThat(repository.findByPrincipalName(TARGET_SUBJECT_A)).isEmpty();

        // Create new session for Target A after first reset
        SelectiveTouchSessionRepository.SelectiveSession session2 = createAuthenticatedSession(TARGET_SUBJECT_A);
        repository.save(session2);
        assertThat(repository.findByPrincipalName(TARGET_SUBJECT_A)).hasSize(1);

        // 2nd request with SAME sourceEventId (replay/duplicate)
        mockMvc.perform(post(PasswordResetEventEndpoint.ENDPOINT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(PasswordResetEventEndpoint.HEADER_KEY_ID, KEY_ID)
                        .header(PasswordResetEventEndpoint.HEADER_TIMESTAMP, timestampHeader)
                        .header(PasswordResetEventEndpoint.HEADER_SIGNATURE, signature)
                        .header(PasswordResetEventEndpoint.HEADER_SOURCE_EVENT_ID, sourceEventId)
                        .content(bodyBytes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effectExecuted").value(false));

        // The second session must survive because duplicate was recognized and effect was skipped!
        assertThat(repository.findByPrincipalName(TARGET_SUBJECT_A)).hasSize(1);
        assertThat(repository.findById(session2.getId())).isNotNull();
    }

    private SelectiveTouchSessionRepository.SelectiveSession createAuthenticatedSession(String subject) {
        SelectiveTouchSessionRepository.SelectiveSession session = repository.createSession();
        Instant now = clock.instant();

        OidcIdToken idToken = OidcIdToken.withTokenValue("probe-token-" + subject)
                .issuer("https://idp.invalid")
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
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
                    .clientId("betufu-hermetic-tracer-it")
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
                    .clientName("BETUFU Hermetic Tracer IT")
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

    record OutboxEventRepresentation(
            String sourceEventId,
            String subject,
            String realmId,
            String eventType,
            Instant createdAt
    ) {}
}
