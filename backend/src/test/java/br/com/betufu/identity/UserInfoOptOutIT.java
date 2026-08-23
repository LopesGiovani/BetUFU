package br.com.betufu.identity;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.RecordComponent;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import br.com.betufu.identity.internal.OidcClaimsProjection;

/**
 * Proves the {@code userinfo_endpoint} OPT-OUT recorded in the phase COVERAGE matrix.
 *
 * <p>The fixture publishes a UserInfo endpoint that is reachable but always fails with HTTP 500,
 * and counts every request it receives. The client registration deliberately requests the
 * {@code profile} scope, which is exactly the condition under which Spring Security's default
 * {@code OidcUserService} WOULD call UserInfo. A passing run therefore proves the opt-out is
 * enforced by the application, not merely implied by the requested scopes.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "spring.docker.compose.enabled=false",
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                    + "org.springframework.boot.data.redis.autoconfigure.DataRedisReactiveAutoConfiguration,"
                    + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration,"
                    + "org.springframework.boot.session.autoconfigure.SessionAutoConfiguration,"
                    + "org.springframework.boot.session.data.redis.autoconfigure.SessionDataRedisAutoConfiguration",
            "BETUFU_OIDC_ISSUER_URI=https://idp.invalid",
            "BETUFU_OIDC_CLIENT_ID=betufu-userinfo-optout-it",
            "BETUFU_OIDC_CLIENT_SECRET=unused"
        })
@ActiveProfiles("integrated")
@Testcontainers
@Import(UserInfoOptOutIT.TestOidcConfiguration.class)
class UserInfoOptOutIT {

    private static final String CURRENT_IDENTITY_PATH = "/api/v1/tracer/identity/current";
    private static final String ISSUER = "https://idp.invalid/realms/betufu";
    private static final String NAME_CLAIM = "fixture_name";
    private static final String POSTGRES_IMAGE_REFERENCE =
            "docker.io/library/postgres:18.4-trixie@sha256:"
                    + "a02db8cac496f15b094798a38254f14d6e00741f709360e5e00bb6668ea31636";

    private static final DockerImageName POSTGRES_IMAGE =
            DockerImageName.parse(POSTGRES_IMAGE_REFERENCE).asCompatibleSubstituteFor("postgres");

    /** Counts every request that reaches the fixture UserInfo endpoint. */
    private static final AtomicInteger USER_INFO_CALLS = new AtomicInteger();

    private static final HttpServer USER_INFO_SERVER = startUserInfoServer();

    private static final String USER_INFO_URI =
            "http://127.0.0.1:" + USER_INFO_SERVER.getAddress().getPort() + "/userinfo";

    @Container
    @ServiceConnection(name = "postgres")
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer(POSTGRES_IMAGE)
            .withDatabaseName("betufu_app")
            .withUsername("betufu_app")
            .withPassword("betufu-test-only")
            .withInitScript("postgres-audit-runtime-role.sql");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private WebApplicationContext applicationContext;

    @Autowired
    @Qualifier("springSecurityFilterChain")
    private Filter springSecurityFilterChain;

    @Autowired
    private ClientRegistrationRepository clientRegistrationRepository;

    @Autowired
    private OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService;

    private MockMvc mockMvc;

    private static HttpServer startUserInfoServer() {
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/userinfo", exchange -> {
                USER_INFO_CALLS.incrementAndGet();
                byte[] body = "{\"error\":\"server_error\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(500, body.length);
                try (OutputStream responseBody = exchange.getResponseBody()) {
                    responseBody.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not start the fixture UserInfo endpoint", failure);
        }
    }

    @AfterAll
    static void stopUserInfoServer() {
        USER_INFO_SERVER.stop(0);
    }

    @BeforeEach
    void prepareTest() {
        USER_INFO_CALLS.set(0);
        jdbcTemplate.execute(
                "TRUNCATE TABLE public.audit_event, public.operational_role_grant, public.betufu_profile, public.ufu_enrollment, public.local_identity CASCADE");
        mockMvc = MockMvcBuilders.webAppContextSetup(applicationContext)
                .addFilters(springSecurityFilterChain)
                .build();
    }

    @Test
    void loginBuildsThePrincipalFromTheIdTokenWithoutCallingUserInfo() {
        ClientRegistration registration =
                clientRegistrationRepository.findByRegistrationId("keycloak");
        assertThat(registration).isNotNull();
        assertThat(registration.getScopes()).contains("profile");
        assertThat(registration.getProviderDetails().getUserInfoEndpoint().getUri())
                .isEqualTo(USER_INFO_URI);

        OidcIdToken idToken = idToken(ISSUER, "optout-subject", "optout@example.invalid");
        OidcUserRequest userRequest = new OidcUserRequest(registration, accessToken(), idToken);

        OidcUser principal = oidcUserService.loadUser(userRequest);

        assertThat(principal.getIdToken().getClaimAsString(IdTokenClaimNames.SUB))
                .isEqualTo("optout-subject");
        assertThat(USER_INFO_CALLS).hasValue(0);

        // Control: the fixture must be able to observe a call, otherwise the assertion above
        // would pass even against a broken counter.
        assertThat(callUserInfoDirectly()).isEqualTo(500);
        assertThat(USER_INFO_CALLS).hasValue(1);
    }

    @Test
    void persistsTheIdentityWhileUserInfoIsUnavailable() throws Exception {
        String subject = "optout-http-subject";

        performCurrentIdentityPut(ISSUER, subject)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", aMapWithSize(4)))
                .andExpect(jsonPath("$.status").value("PERSISTED"))
                .andExpect(jsonPath("$.identityId").value(not(emptyOrNullString())));

        assertThat(rowCount(ISSUER, subject)).isEqualTo(1L);
        assertThat(USER_INFO_CALLS).hasValue(0);
    }

    @Test
    void projectionExposesOnlyTheFiveAllowlistedClaims() throws Exception {
        assertThat(OidcClaimsProjection.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactly("issuer", "subject", "preferredUsername", "acr", "amr");

        MvcResult result = performCurrentIdentityPut(ISSUER, "optout-allowlist-subject")
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body)
                .doesNotContain("email")
                .doesNotContain("optout@example.invalid")
                .doesNotContain("preferred_username")
                .doesNotContain("acr")
                .doesNotContain("amr")
                .doesNotContain("token");
        assertThat(USER_INFO_CALLS).hasValue(0);
    }

    /**
     * Guards the regression that every hand-built fixture hides.
     *
     * <p>{@code OidcIdTokenDecoderFactory} converts {@code iss} to {@link java.net.URL}, so a
     * token from the real login pipeline never carries a {@code String} there. A projection that
     * only accepts {@code String} would answer 401 to every production login while the rest of
     * this suite still passed, because the other tests build the claim by hand.
     */
    @Test
    void acceptsTheIssuerInTheTypeConvertedFormThatRealLoginsProduce() throws Exception {
        String subject = "optout-converted-issuer";
        Object convertedIssuer = OidcIdTokenDecoderFactory.createDefaultClaimTypeConverters()
                .get(IdTokenClaimNames.ISS)
                .convert(ISSUER);

        assertThat(convertedIssuer)
                .as("the real decoder must convert iss away from String")
                .isNotInstanceOf(String.class);

        mockMvc.perform(put(CURRENT_IDENTITY_PATH)
                        .with(oidcPrincipal(convertedIssuer, subject))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(4)));

        // The persisted issuer must be byte-identical to the raw string, or the natural key
        // would stop matching rows written by Plans 01-05 and 01-29.
        assertThat(rowCount(ISSUER, subject)).isEqualTo(1L);
        assertThat(USER_INFO_CALLS).hasValue(0);
    }

    @Test
    void missingOrBlankIdentityClaimsAreRejectedWithoutPersisting() throws Exception {
        assertRejected(null, "valid-subject");
        assertRejected("", "valid-subject");
        assertRejected("   ", "valid-subject");
        assertRejected(ISSUER, null);
        assertRejected(ISSUER, "");
        assertRejected(ISSUER, "   ");

        assertThat(totalRowCount()).isZero();
        assertThat(USER_INFO_CALLS).hasValue(0);
    }

    @Test
    void optionalUnicodeClaimsDoNotDisturbTheExactIdentityComparison() throws Exception {
        String composedSubject = "optout-\u00e9";
        String decomposedSubject = "optout-e\u0301";

        performCurrentIdentityPut(ISSUER, composedSubject).andExpect(status().isOk());
        performCurrentIdentityPut(ISSUER, decomposedSubject).andExpect(status().isOk());
        performCurrentIdentityPut(ISSUER.toUpperCase(), composedSubject)
                .andExpect(status().isOk());

        assertThat(rowCount(ISSUER, composedSubject)).isEqualTo(1L);
        assertThat(rowCount(ISSUER, decomposedSubject)).isEqualTo(1L);
        assertThat(totalRowCount()).isEqualTo(3L);
        assertThat(USER_INFO_CALLS).hasValue(0);
    }

    @Test
    void repeatedAndConcurrentSyncConvergeOnOneIdentityWithoutDomainRole() throws Exception {
        int workers = 8;
        String subject = "optout-concurrent-subject";

        performCurrentIdentityPut(ISSUER, subject).andExpect(status().isOk());
        performCurrentIdentityPut(ISSUER, subject).andExpect(status().isOk());

        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>();

        try {
            for (int worker = 0; worker < workers; worker++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, SECONDS)).isTrue();
                    return performCurrentIdentityPut(ISSUER, subject).andReturn();
                }));
            }

            assertThat(ready.await(10, SECONDS)).isTrue();
            start.countDown();

            List<MvcResult> results = new ArrayList<>();
            for (Future<MvcResult> future : futures) {
                results.add(future.get(20, SECONDS));
            }

            assertThat(results).allSatisfy(result ->
                    assertThat(result.getResponse().getStatus()).isEqualTo(200));
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, SECONDS)).isTrue();
        }

        assertThat(rowCount(ISSUER, subject)).isEqualTo(1L);
        assertThat(totalRowCount()).isEqualTo(1L);
        assertThat(USER_INFO_CALLS).hasValue(0);

        // The Keycloak claims never create a BETUFU domain role: local_identity carries the
        // opaque key only, and no role column or table is written by the sync.
        assertThat(jdbcTemplate.queryForList(
                        """
                        SELECT column_name
                        FROM information_schema.columns
                        WHERE table_schema = 'public'
                          AND table_name = 'local_identity'
                        """,
                        String.class))
                .containsExactlyInAnyOrder(
                        "identity_id", "issuer", "subject", "created_at", "updated_at");
    }

    private int callUserInfoDirectly() {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(USER_INFO_URI)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            return response.statusCode();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failure);
        }
    }

    private ResultActions performCurrentIdentityPut(String issuer, String subject)
            throws Exception {
        return mockMvc.perform(put(CURRENT_IDENTITY_PATH)
                .with(oidcPrincipal(issuer, subject))
                .with(csrf()));
    }

    private void assertRejected(String issuer, String subject) throws Exception {
        performCurrentIdentityPut(issuer, subject)
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(""));
    }

    private RequestPostProcessor oidcPrincipal(Object issuer, String subject) {
        ClientRegistration registration =
                clientRegistrationRepository.findByRegistrationId("keycloak");
        assertThat(registration).isNotNull();

        OidcUser oidcUser = new DefaultOidcUser(
                AuthorityUtils.createAuthorityList("OIDC_USER"),
                idToken(issuer, subject, "optout@example.invalid"),
                NAME_CLAIM);

        return oidcLogin().oidcUser(oidcUser).clientRegistration(registration);
    }

    private static OidcIdToken idToken(Object issuer, String subject, String email) {
        Instant now = Instant.now();
        OidcIdToken.Builder builder = OidcIdToken.withTokenValue(
                        "userinfo-optout-it-" + UUID.randomUUID())
                .issuedAt(now.minusSeconds(60))
                .expiresAt(now.plusSeconds(300))
                .claim(NAME_CLAIM, "userinfo-optout-it-user")
                .claim(StandardClaimNames.PREFERRED_USERNAME, "optout-user")
                .claim("acr", "1")
                .claim("amr", List.of("pwd"))
                .claim("email", email);

        if (issuer != null) {
            builder.claim(IdTokenClaimNames.ISS, issuer);
        }
        if (subject != null) {
            builder.claim(IdTokenClaimNames.SUB, subject);
        }

        return builder.build();
    }

    private static OAuth2AccessToken accessToken() {
        Instant now = Instant.now();
        return new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "userinfo-optout-it-access-token",
                now.minusSeconds(60),
                now.plusSeconds(300));
    }

    private long rowCount(String issuer, String subject) {
        return jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM public.local_identity
                WHERE issuer = ?
                  AND subject = ?
                """,
                Long.class,
                issuer,
                subject);
    }

    private long totalRowCount() {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM public.local_identity", Long.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestOidcConfiguration {

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration.withRegistrationId("keycloak")
                    .clientId("betufu-userinfo-optout-it")
                    .clientSecret("unused")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    // `profile` is requested on purpose: it is the scope that makes Spring
                    // Security's default OidcUserService fetch UserInfo.
                    .scope("openid", "profile")
                    .authorizationUri("https://idp.invalid/authorize")
                    .tokenUri("https://idp.invalid/token")
                    .jwkSetUri("https://idp.invalid/jwks")
                    .userInfoUri(USER_INFO_URI)
                    .issuerUri("https://idp.invalid")
                    .userNameAttributeName(NAME_CLAIM)
                    .clientName("BETUFU UserInfo Opt-Out IT")
                    .build();

            return new InMemoryClientRegistrationRepository(registration);
        }
    }
}
