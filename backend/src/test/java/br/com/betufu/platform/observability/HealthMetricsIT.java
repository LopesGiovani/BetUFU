package br.com.betufu.platform.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "management.endpoints.web.exposure.include=health,env,configprops,beans,metrics,heapdump,info",
            "management.endpoint.health.probes.enabled=true",
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
@Import(HealthMetricsIT.TestOidcConfiguration.class)
class HealthMetricsIT {

    @TestConfiguration
    static class TestOidcConfiguration {

        @Bean
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration
                    .withRegistrationId("keycloak")
                    .clientId("betufu-platform-spa")
                    .clientSecret("test-secret")
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                    .scope("openid")
                    .authorizationUri("http://localhost:8081/realms/betufu/protocol/openid-connect/auth")
                    .tokenUri("http://localhost:8081/realms/betufu/protocol/openid-connect/token")
                    .userInfoUri("http://localhost:8081/realms/betufu/protocol/openid-connect/userinfo")
                    .userNameAttributeName(IdTokenClaimNames.SUB)
                    .jwkSetUri("http://localhost:8081/realms/betufu/protocol/openid-connect/certs")
                    .issuerUri("http://localhost:8081/realms/betufu")
                    .build();

            return new InMemoryClientRegistrationRepository(registration);
        }
    }

    @Autowired
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Autowired
    private org.springframework.boot.health.contributor.HealthIndicator platformHealthIndicator;

    @Test
    @DisplayName("Platform health indicator returns UP and contains no sensitive details")
    void platformHealthIndicatorIsSafe() {
        org.springframework.boot.health.contributor.Health health = platformHealthIndicator.health();
        assertThat(health.getStatus().getCode()).isEqualTo("UP");
        assertThat(health.getDetails())
                .containsKey("platform")
                .doesNotContainKey("password")
                .doesNotContainKey("secret")
                .doesNotContainKey("token");
        assertThat(health.getDetails().get("platform")).isEqualTo("BETUFU");
    }

    @Test
    @DisplayName("Liveness endpoint is public, returns UP, and does not leak infrastructure details")
    void livenessEndpointIsPublicAndSafe() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("jdbc"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("redis"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("keycloak"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("password"))));
    }

    @Test
    @DisplayName("Raw health and sensitive actuator endpoints are protected by security authorization")
    void sensitiveActuatorEndpointsAreProtected() throws Exception {
        // Raw /actuator/health is protected and denied with 401 Problem Details for anonymous access
        mockMvc.perform(get("/actuator/health").header("Accept", "application/json"))
                .andExpect(status().isUnauthorized());

        // env endpoint is blocked by security authorization
        mockMvc.perform(get("/actuator/env").header("Accept", "application/json"))
                .andExpect(status().isUnauthorized());

        // configprops endpoint is blocked by security authorization
        mockMvc.perform(get("/actuator/configprops").header("Accept", "application/json"))
                .andExpect(status().isUnauthorized());

        // heapdump endpoint is blocked by security authorization
        mockMvc.perform(get("/actuator/heapdump").header("Accept", "application/json"))
                .andExpect(status().isUnauthorized());

        // beans endpoint is blocked by security authorization
        mockMvc.perform(get("/actuator/beans").header("Accept", "application/json"))
                .andExpect(status().isUnauthorized());

        // metrics endpoint is blocked by security authorization
        mockMvc.perform(get("/actuator/metrics").header("Accept", "application/json"))
                .andExpect(status().isUnauthorized());
    }

    // ── SafeMetricsConfiguration & MeterFilter ─────────────────────────────────

    @Test
    @DisplayName("MeterFilter strips sensitive and high-cardinality tags from MeterRegistry")
    void meterFilterStripsHighCardinalityAndSensitiveTags() {
        String meterName = "betufu.test.operation.filtered";
        meterRegistry.counter(meterName, Tags.of(
                Tag.of("userId", "user-12345"),
                Tag.of("correlationId", "corr-67890"),
                Tag.of("email", "test@ufu.br"),
                Tag.of("sub", "keycloak-sub-uuid"),
                Tag.of("password", "supersecret"),
                Tag.of("operation", "audit_query")
        )).increment();

        Counter counter = meterRegistry.find(meterName).counter();
        assertThat(counter).isNotNull();

        // Safe low-cardinality tag is preserved
        assertThat(counter.getId().getTag("operation")).isEqualTo("audit_query");

        // High-cardinality / sensitive tags are completely stripped
        assertThat(counter.getId().getTag("userId")).isNull();
        assertThat(counter.getId().getTag("correlationId")).isNull();
        assertThat(counter.getId().getTag("email")).isNull();
        assertThat(counter.getId().getTag("sub")).isNull();
        assertThat(counter.getId().getTag("password")).isNull();
    }

    @Test
    @DisplayName("MeterFilter sanitizes tag values with LogSanitizer patterns")
    void meterFilterSanitizesTagValues() {
        String meterName = "betufu.test.sanitized.metric";
        meterRegistry.counter(meterName, Tags.of(
                Tag.of("client_info", "Authorization: Bearer eyJhbGciOiJSUzI1NiJ9.sig\u0000injected")
        )).increment();

        Counter counter = meterRegistry.find(meterName).counter();
        assertThat(counter).isNotNull();

        String tagVal = counter.getId().getTag("client_info");
        assertThat(tagVal)
                .contains("[REDACTED]")
                .doesNotContain("eyJhbGciOiJSUzI1NiJ9")
                .doesNotContain("\u0000");
    }

    @Test
    @DisplayName("MeterFilter caps URI tag cardinality for http.server.requests at maximum 100 unique values")
    void meterFilterCapsHttpUriCardinality() {
        for (int i = 1; i <= 105; i++) {
            meterRegistry.counter("http.server.requests", Tags.of("uri", "/api/v1/test-cap/" + i)).increment();
        }

        java.util.Collection<Counter> counters = meterRegistry.find("http.server.requests").counters();
        assertThat(counters).hasSize(100);
    }
}
