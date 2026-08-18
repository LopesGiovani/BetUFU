package br.com.betufu.platform.diagnostic;

import br.com.betufu.platform.persistence.PostgresAuthorityProbe;
import br.com.betufu.platform.persistence.PrivateObjectStorageProbe;
import br.com.betufu.platform.persistence.RedisAuxiliaryProbe;
import java.util.List;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "management.endpoints.web.exposure.include=health",
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
@Import(DiagnosticDegradationIT.TestOidcConfiguration.class)
class DiagnosticDegradationIT {

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
    private DiagnosticService diagnosticService;

    @Test
    @DisplayName("D-04 & P2: Falha em PostgreSQL torna plataforma UNAVAILABLE mas preserva os outros cinco componentes")
    void postgresFailureProducesUnavailableResponsePreservingOtherComponents() {
        PostgresAuthorityProbe failingPostgres = mock(PostgresAuthorityProbe.class);
        when(failingPostgres.checkAuthority())
                .thenThrow(new RuntimeException("Connection refused to 10.0.0.12:5432 with password=super_secret_db_pass"));

        RedisAuxiliaryProbe healthyRedis = mock(RedisAuxiliaryProbe.class);
        when(healthyRedis.checkAuxiliaryStatus())
                .thenReturn(new RedisAuxiliaryProbe.RedisAuxiliaryStatus(true, "PONG"));

        PrivateObjectStorageProbe healthyStorage = mock(PrivateObjectStorageProbe.class);
        when(healthyStorage.checkStorageStatus())
                .thenReturn(new PrivateObjectStorageProbe.PrivateObjectStorageStatus(true, "betufu-private", true));

        ClientRegistrationRepository mockKeycloakRepo = mock(ClientRegistrationRepository.class);
        when(mockKeycloakRepo.findByRegistrationId("keycloak"))
                .thenReturn(mock(ClientRegistration.class));

        try (PlatformDependencyProbes probes = new PlatformDependencyProbes(
                failingPostgres,
                healthyRedis,
                healthyStorage,
                mockKeycloakRepo
        )) {
            List<DiagnosticComponent> components = probes.probeAll();
            DiagnosticResponse response = diagnosticService.aggregate(components);

            // Overall status is UNAVAILABLE because PostgreSQL is the authoritative data source (01-10)
            assertThat(response.status()).isEqualTo(DiagnosticStatus.UNAVAILABLE);
            assertThat(response.components()).hasSize(6);

            // PostgreSQL is UNAVAILABLE and contains NO leaked topology or secrets
            DiagnosticComponent pgComponent = response.components().stream()
                    .filter(c -> DiagnosticComponent.POSTGRESQL.equals(c.name()))
                    .findFirst()
                    .orElseThrow();
            assertThat(pgComponent.status()).isEqualTo(DiagnosticStatus.UNAVAILABLE);
            assertThat(pgComponent.description())
                    .doesNotContain("10.0.0.12")
                    .doesNotContain("5432")
                    .doesNotContain("super_secret_db_pass")
                    .doesNotContain("RuntimeException");

            // The remaining 5 components are healthy (AVAILABLE)
            long availableCount = response.components().stream()
                    .filter(c -> c.status() == DiagnosticStatus.AVAILABLE)
                    .count();
            assertThat(availableCount).isEqualTo(5);
        }
    }

    @Test
    @DisplayName("D-04 & P2: Falha em Redis degrada a plataforma para DEGRADED e degrada a Sessão BFF")
    void redisFailureProducesDegradedResponseAndDegradesBffSession() {
        PostgresAuthorityProbe healthyPostgres = mock(PostgresAuthorityProbe.class);
        when(healthyPostgres.checkAuthority())
                .thenReturn(new PostgresAuthorityProbe.PostgresAuthorityStatus(true, 18, 1));

        RedisAuxiliaryProbe failingRedis = mock(RedisAuxiliaryProbe.class);
        when(failingRedis.checkAuxiliaryStatus())
                .thenThrow(new RuntimeException("Redis connection timed out to 127.0.0.1:6379 with auth_token=secret_redis_token"));

        PrivateObjectStorageProbe healthyStorage = mock(PrivateObjectStorageProbe.class);
        when(healthyStorage.checkStorageStatus())
                .thenReturn(new PrivateObjectStorageProbe.PrivateObjectStorageStatus(true, "betufu-private", true));

        ClientRegistrationRepository mockKeycloakRepo = mock(ClientRegistrationRepository.class);
        when(mockKeycloakRepo.findByRegistrationId("keycloak"))
                .thenReturn(mock(ClientRegistration.class));

        try (PlatformDependencyProbes probes = new PlatformDependencyProbes(
                healthyPostgres,
                failingRedis,
                healthyStorage,
                mockKeycloakRepo
        )) {
            List<DiagnosticComponent> components = probes.probeAll();
            DiagnosticResponse response = diagnosticService.aggregate(components);

            // Auxiliary failure only degrades the platform
            assertThat(response.status()).isEqualTo(DiagnosticStatus.DEGRADED);

            DiagnosticComponent redisComp = response.components().stream()
                    .filter(c -> DiagnosticComponent.REDIS.equals(c.name()))
                    .findFirst()
                    .orElseThrow();
            assertThat(redisComp.status()).isEqualTo(DiagnosticStatus.UNAVAILABLE);
            assertThat(redisComp.description())
                    .doesNotContain("127.0.0.1")
                    .doesNotContain("6379")
                    .doesNotContain("secret_redis_token");

            // BFF Session is also degraded due to Redis dependency (P2)
            DiagnosticComponent sessionComp = response.components().stream()
                    .filter(c -> DiagnosticComponent.BFF_SESSION.equals(c.name()))
                    .findFirst()
                    .orElseThrow();
            assertThat(sessionComp.status()).isEqualTo(DiagnosticStatus.DEGRADED);
            assertThat(sessionComp.description()).contains("degradada");
        }
    }

    @Test
    @DisplayName("D-04 & P1: Falha em Keycloak e Storage produz status geral UNAVAILABLE")
    void keycloakAndStorageFailuresProduceUnavailableOverallStatus() {
        PostgresAuthorityProbe healthyPostgres = mock(PostgresAuthorityProbe.class);
        when(healthyPostgres.checkAuthority())
                .thenReturn(new PostgresAuthorityProbe.PostgresAuthorityStatus(true, 18, 1));

        RedisAuxiliaryProbe healthyRedis = mock(RedisAuxiliaryProbe.class);
        when(healthyRedis.checkAuxiliaryStatus())
                .thenReturn(new RedisAuxiliaryProbe.RedisAuxiliaryStatus(true, "PONG"));

        PrivateObjectStorageProbe failingStorage = mock(PrivateObjectStorageProbe.class);
        when(failingStorage.checkStorageStatus())
                .thenThrow(new RuntimeException("S3 bucket connection failed on endpoint https://s3.internal.lan:8333"));

        ClientRegistrationRepository failingKeycloakRepo = mock(ClientRegistrationRepository.class);
        when(failingKeycloakRepo.findByRegistrationId("keycloak"))
                .thenThrow(new RuntimeException("Keycloak discovery failed on http://auth.internal.lan:8080"));

        try (PlatformDependencyProbes probes = new PlatformDependencyProbes(
                healthyPostgres,
                healthyRedis,
                failingStorage,
                failingKeycloakRepo
        )) {
            List<DiagnosticComponent> components = probes.probeAll();
            DiagnosticResponse response = diagnosticService.aggregate(components);

            // With Keycloak (identity) UNAVAILABLE and Storage UNAVAILABLE, overall status is UNAVAILABLE
            assertThat(response.status()).isEqualTo(DiagnosticStatus.UNAVAILABLE);
            assertThat(response.summary()).contains("Plataforma indisponível");

            long unavailableCount = response.components().stream()
                    .filter(c -> c.status() == DiagnosticStatus.UNAVAILABLE)
                    .count();
            assertThat(unavailableCount).isEqualTo(2);
        }
    }
}
