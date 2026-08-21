package br.com.betufu.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.session.SessionRepository;

import br.com.betufu.platform.session.SelectiveTouchSessionRepository;
import br.com.betufu.platform.session.SessionSecurityConfiguration;

class EnvironmentConfigurationTest {

    private static final List<String> ENVIRONMENT_PROFILES = List.of("dev", "homolog", "prod");
    private static final String JDBC_URL = "jdbc:postgresql://db.invalid:5432/betufu_test";
    private static final String DATABASE_USER = "betufu_test";
    private static final String DATABASE_PASSWORD = "test-only-password";
    private static final String REDIS_HOST = "redis.invalid";
    private static final int REDIS_PORT = 6380;
    private static final String REDIS_PASSWORD = "test-only-redis-password";
    private static final String REDIS_NAMESPACE = "betufu:session";
    private static final String SERVICE_CONNECTION_JDBC_URL =
            "jdbc:postgresql://service-connection.invalid:5432/integrated";
    private static final String SERVICE_CONNECTION_DATABASE_USER = "service_connection_user";
    private static final String SERVICE_CONNECTION_DATABASE_PASSWORD = "service-connection-password";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues(
                    "BETUFU_OIDC_ISSUER_URI=https://idp.invalid/realms/betufu",
                    "BETUFU_OIDC_CLIENT_ID=betufu-test",
                    "BETUFU_OIDC_CLIENT_SECRET=unused",
                    "BETUFU_DB_JDBC_URL=" + JDBC_URL,
                    "BETUFU_DB_USER=" + DATABASE_USER,
                    "BETUFU_DB_PASSWORD=" + DATABASE_PASSWORD,
                    "BETUFU_REDIS_HOST=" + REDIS_HOST,
                    "BETUFU_REDIS_PORT=" + REDIS_PORT,
                    "BETUFU_REDIS_PASSWORD=" + REDIS_PASSWORD);

    @Test
    void loadsDevWithOnlyLoopbackProxies() {
        runProfile(
                "dev",
                "https://external-origin.invalid",
                "http://external-backend.invalid",
                "10.10.0.0/16",
                environment -> {
                    assertThat(environment.getProperty("betufu.web.public-origin"))
                            .isEqualTo("http://localhost:4200");
                    assertThat(environment.getProperty("betufu.web.backend-origin"))
                            .isEqualTo("http://127.0.0.1:8080");
                    assertThat(environment.getProperty("server.tomcat.remoteip.internal-proxies"))
                            .isEqualTo("127.0.0.1/32,::1/128");
                    assertThat(environment.getProperty("spring.autoconfigure.exclude")).isNull();
                });
    }

    @Test
    void loadsHomologWithExternalValues() {
        runExternalProfile(
                "homolog",
                "https://homolog.betufu.invalid",
                "http://backend-homolog.betufu.invalid:8080",
                "10.20.0.0/16");
    }

    @Test
    void loadsProdWithExternalValues() {
        runExternalProfile(
                "prod",
                "https://betufu.invalid",
                "http://backend-prod.betufu.invalid:8080",
                "10.30.0.0/16");
    }

    @Test
    void keepsSharedEnvironmentValuesExternalAndDefinesNoDiagnosticFlag() throws IOException {
        for (String profile : List.of("homolog", "prod")) {
            String content = new ClassPathResource("application-" + profile + ".yaml")
                    .getContentAsString(StandardCharsets.UTF_8);

            assertThat(content)
                    .contains("${BETUFU_PUBLIC_ORIGIN}")
                    .contains("${BETUFU_BACKEND_ORIGIN}")
                    .contains("${BETUFU_TRUSTED_PROXIES}")
                    .doesNotContain("${BETUFU_PUBLIC_ORIGIN:")
                    .doesNotContain("${BETUFU_BACKEND_ORIGIN:")
                    .doesNotContain("${BETUFU_TRUSTED_PROXIES:")
                    .doesNotContainIgnoringCase("diagnostic.enabled");
        }
    }

    @Test
    void requiresExactlyThreeDatasourcePlaceholdersWithoutDefaults() throws IOException {
        String content = new ClassPathResource("application.yaml")
                .getContentAsString(StandardCharsets.UTF_8)
                .replace("\r\n", "\n");

        assertThat(countOccurrences(content, "${BETUFU_DB_JDBC_URL}")).isEqualTo(1);
        assertThat(countOccurrences(content, "${BETUFU_DB_USER}")).isEqualTo(1);
        assertThat(countOccurrences(content, "${BETUFU_DB_PASSWORD}")).isEqualTo(1);
        assertThat(countOccurrences(content, "${BETUFU_DB_")).isEqualTo(3);
        assertThat(content)
                .doesNotContain("${BETUFU_DB_JDBC_URL:")
                .doesNotContain("${BETUFU_DB_USER:")
                .doesNotContain("${BETUFU_DB_PASSWORD:")
                .doesNotContain("jdbc:postgresql://127.0.0.1:5432/betufu_local")
                .doesNotContain("betufu-local-postgres-app");
    }

    @Test
    void requiresExactlyThreeRedisPlaceholdersWithoutDefaultsOrLegacyStoreType() throws IOException {
        String content = new ClassPathResource("application.yaml")
                .getContentAsString(StandardCharsets.UTF_8)
                .replace("\r\n", "\n");

        assertThat(countOccurrences(content, "${BETUFU_REDIS_HOST}")).isEqualTo(1);
        assertThat(countOccurrences(content, "${BETUFU_REDIS_PORT}")).isEqualTo(1);
        assertThat(countOccurrences(content, "${BETUFU_REDIS_PASSWORD}")).isEqualTo(1);
        assertThat(countOccurrences(content, "${BETUFU_REDIS_")).isEqualTo(3);
        assertThat(content)
                .doesNotContain("${BETUFU_REDIS_HOST:")
                .doesNotContain("${BETUFU_REDIS_PORT:")
                .doesNotContain("${BETUFU_REDIS_PASSWORD:")
                .doesNotContain("betufu-local-redis")
                .doesNotContain("spring.session.store-type");
    }

    @Test
    void letsServiceConnectionJdbcConnectionDetailsTakePrecedenceOverIntegratedDatasourceProperties() {
        contextRunner
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class))
                .withBean(JdbcConnectionDetails.class, EnvironmentConfigurationTest::serviceConnectionJdbcDetails)
                .withPropertyValues("spring.profiles.active=integrated")
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();

                    ConfigurableEnvironment environment = context.getEnvironment();
                    assertThat(environment.getActiveProfiles()).contains("integrated");
                    assertThat(environment.getProperty("spring.datasource.url")).isEqualTo(JDBC_URL);
                    assertThat(environment.getProperty("spring.datasource.username")).isEqualTo(DATABASE_USER);
                    assertThat(environment.getProperty("spring.datasource.password")).isEqualTo(DATABASE_PASSWORD);

                    assertThat(context).hasSingleBean(DataSource.class);
                    DataSource dataSource = context.getBean(DataSource.class);
                    assertThat(dataSource).isInstanceOf(HikariDataSource.class);

                    HikariDataSource hikariDataSource = (HikariDataSource) dataSource;
                    assertThat(hikariDataSource.getJdbcUrl())
                            .isEqualTo(SERVICE_CONNECTION_JDBC_URL)
                            .isNotEqualTo(environment.getProperty("spring.datasource.url"));
                    assertThat(hikariDataSource.getUsername())
                            .isEqualTo(SERVICE_CONNECTION_DATABASE_USER)
                            .isNotEqualTo(environment.getProperty("spring.datasource.username"));
                    assertThat(hikariDataSource.getPassword())
                            .isEqualTo(SERVICE_CONNECTION_DATABASE_PASSWORD)
                            .isNotEqualTo(environment.getProperty("spring.datasource.password"));
                });
    }

    @Test
    void rejectsEachMissingDatasourceVariableWithoutFallback() {
        Map.of(
                        "BETUFU_DB_JDBC_URL", "spring.datasource.url",
                        "BETUFU_DB_USER", "spring.datasource.username",
                        "BETUFU_DB_PASSWORD", "spring.datasource.password")
                .forEach((missingVariable, propertyName) -> new ApplicationContextRunner()
                        .withInitializer(new ConfigDataApplicationContextInitializer())
                        .withPropertyValues(requiredRuntimeConfigurationExcept(missingVariable))
                        .run(context -> {
                            assertThat(context.getStartupFailure()).isNull();
                            assertThatThrownBy(() -> context.getEnvironment().getRequiredProperty(propertyName))
                                    .as("variável obrigatória ausente: %s", missingVariable)
                                    .hasMessageContaining(missingVariable)
                                    .hasMessageNotContaining("test-only-password");
                        }));
    }

    @Test
    void rejectsEachMissingRedisVariableWithoutFallback() {
        Map.of(
                        "BETUFU_REDIS_HOST", "spring.data.redis.host",
                        "BETUFU_REDIS_PORT", "spring.data.redis.port",
                        "BETUFU_REDIS_PASSWORD", "spring.data.redis.password")
                .forEach((missingVariable, propertyName) -> new ApplicationContextRunner()
                        .withInitializer(new ConfigDataApplicationContextInitializer())
                        .withPropertyValues(requiredRuntimeConfigurationExcept(missingVariable))
                        .run(context -> {
                            assertThat(context.getStartupFailure()).isNull();
                            assertThatThrownBy(() -> context.getEnvironment().getRequiredProperty(propertyName))
                                    .as("variável obrigatória ausente: %s", missingVariable)
                                    .hasMessageContaining(missingVariable)
                                    .hasMessageNotContaining(REDIS_PASSWORD);
                        }));
    }

    @Test
    void keepsLocalDatasourceAndRedisContractInDiscardableEnvExample()
            throws IOException, URISyntaxException {
        List<String> lines = Files.readAllLines(resolveEnvExample(), StandardCharsets.UTF_8);

        assertThat(lines.stream()
                .filter("BETUFU_DB_JDBC_URL=jdbc:postgresql://127.0.0.1:5432/betufu_local"::equals)
                        .count())
                .isEqualTo(1);
        assertThat(lines.stream().filter("BETUFU_DB_USER=betufu_app"::equals).count()).isEqualTo(1);
        assertThat(lines.stream().filter("POSTGRES_USER=postgres"::equals).count()).isEqualTo(1);
        assertThat(lines.stream().anyMatch(line -> line.startsWith("SPRING_DATASOURCE_"))).isFalse();
        assertThat(lines.stream().filter("BETUFU_REDIS_HOST=127.0.0.1"::equals).count()).isEqualTo(1);
        assertThat(lines.stream().filter("BETUFU_REDIS_PORT=6379"::equals).count()).isEqualTo(1);
        assertThat(lines.stream().filter("BETUFU_REDIS_PASSWORD=betufu-local-redis"::equals).count())
                .isEqualTo(1);
        assertThat(lines.stream().anyMatch(line -> line.startsWith("SPRING_DATA_REDIS_"))).isFalse();
    }

    @Test
    void devCreatesRedisSessionRepositoryWithoutOpeningAConnection() {
        redisSessionContextRunner()
                .withPropertyValues("spring.profiles.active=dev")
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();
                    assertThat(context).hasSingleBean(SessionRepository.class);
                    assertThat(context.getBean(SessionRepository.class))
                            .isInstanceOf(SelectiveTouchSessionRepository.class);
                    assertThat(context.getEnvironment().getProperty("spring.autoconfigure.exclude")).isNull();
                    assertRedisSessionContract(context.getEnvironment());
                    verifyNoInteractions(context.getBean(RedisConnectionFactory.class));
                });
    }

    private void runExternalProfile(
            String profile,
            String publicOrigin,
            String backendOrigin,
            String trustedProxies) {
        runProfile(profile, publicOrigin, backendOrigin, trustedProxies, environment -> {
            assertThat(environment.getProperty("betufu.web.public-origin")).isEqualTo(publicOrigin);
            assertThat(environment.getProperty("betufu.web.backend-origin")).isEqualTo(backendOrigin);
            assertThat(environment.getProperty("server.tomcat.remoteip.internal-proxies"))
                    .isEqualTo(trustedProxies)
                    .isNotBlank()
                    .doesNotContain("*");
            assertThat(environment.getProperty("spring.autoconfigure.exclude")).isNull();
        });
    }

    private void runProfile(
            String profile,
            String publicOrigin,
            String backendOrigin,
            String trustedProxies,
            EnvironmentAssertions environmentAssertions) {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=" + profile,
                        "BETUFU_PUBLIC_ORIGIN=" + publicOrigin,
                        "BETUFU_BACKEND_ORIGIN=" + backendOrigin,
                        "BETUFU_TRUSTED_PROXIES=" + trustedProxies)
                .run(context -> {
                    assertThat(context.getStartupFailure()).isNull();

                    ConfigurableEnvironment environment = context.getEnvironment();
                    assertThat(environment.getActiveProfiles()).contains(profile, "runtime");
                    assertOnlySelectedEnvironmentSourceIsLoaded(environment, profile);
                    assertSharedRuntimeContract(environment);
                    environmentAssertions.accept(environment);
                });
    }

    private static void assertOnlySelectedEnvironmentSourceIsLoaded(
            ConfigurableEnvironment environment, String selectedProfile) {
        List<String> propertySourceNames = StreamSupport.stream(
                        environment.getPropertySources().spliterator(), false)
                .map(propertySource -> propertySource.getName())
                .toList();

        assertThat(propertySourceNames)
                .anyMatch(name -> name.contains("application-" + selectedProfile + ".yaml"));

        ENVIRONMENT_PROFILES.stream()
                .filter(profile -> !profile.equals(selectedProfile))
                .forEach(profile -> assertThat(propertySourceNames)
                        .noneMatch(name -> name.contains("application-" + profile + ".yaml")));
    }

    private static void assertSharedRuntimeContract(ConfigurableEnvironment environment) {
        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo(JDBC_URL);
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo(DATABASE_USER);
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo(DATABASE_PASSWORD);
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.flyway.enabled", Boolean.class)).isTrue();
        assertRedisSessionContract(environment);
        assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health");
        assertThat(environment.getProperty("server.forward-headers-strategy"))
                .isEqualToIgnoringCase("native");
        assertThat(environment.containsProperty("betufu.diagnostic.enabled")).isFalse();
    }

    private static void assertRedisSessionContract(ConfigurableEnvironment environment) {
        assertThat(environment.getProperty("spring.session.timeout")).isEqualTo("8h");
        assertThat(environment.getProperty("spring.session.data.redis.namespace"))
                .isEqualTo(REDIS_NAMESPACE);
        assertThat(environment.getProperty("spring.session.data.redis.repository-type"))
                .isEqualTo("indexed");
        assertThat(environment.getProperty("spring.session.data.redis.flush-mode"))
                .isEqualTo("on-save");
        assertThat(environment.getProperty("spring.session.data.redis.save-mode"))
                .isEqualTo("on-set-attribute");
        assertThat(environment.getProperty("spring.data.redis.host")).isEqualTo(REDIS_HOST);
        assertThat(environment.getProperty("spring.data.redis.port", Integer.class))
                .isEqualTo(REDIS_PORT);
        assertThat(environment.getProperty("spring.data.redis.password")).isEqualTo(REDIS_PASSWORD);
    }

    private static String[] requiredRuntimeConfigurationExcept(String missingVariable) {
        return StreamSupport.stream(Map.of(
                                "BETUFU_OIDC_ISSUER_URI", "https://idp.invalid/realms/betufu",
                                "BETUFU_OIDC_CLIENT_ID", "betufu-test",
                                "BETUFU_OIDC_CLIENT_SECRET", "unused",
                                "spring.profiles.active", "runtime",
                                "BETUFU_DB_JDBC_URL", JDBC_URL,
                                "BETUFU_DB_USER", DATABASE_USER,
                                "BETUFU_DB_PASSWORD", DATABASE_PASSWORD,
                                "BETUFU_REDIS_HOST", REDIS_HOST,
                                "BETUFU_REDIS_PORT", Integer.toString(REDIS_PORT),
                                "BETUFU_REDIS_PASSWORD", REDIS_PASSWORD)
                        .entrySet()
                        .spliterator(), false)
                .filter(entry -> !entry.getKey().equals(missingVariable))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .toArray(String[]::new);
    }

    private static JdbcConnectionDetails serviceConnectionJdbcDetails() {
        return new JdbcConnectionDetails() {
            @Override
            public String getUsername() {
                return SERVICE_CONNECTION_DATABASE_USER;
            }

            @Override
            public String getPassword() {
                return SERVICE_CONNECTION_DATABASE_PASSWORD;
            }

            @Override
            public String getJdbcUrl() {
                return SERVICE_CONNECTION_JDBC_URL;
            }

            @Override
            public String getDriverClassName() {
                return "org.postgresql.Driver";
            }
        };
    }

    private static WebApplicationContextRunner redisSessionContextRunner() {
        return new WebApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(RedisSessionTestConfiguration.class)
                .withPropertyValues(
                        "BETUFU_OIDC_ISSUER_URI=https://idp.invalid/realms/betufu",
                        "BETUFU_OIDC_CLIENT_ID=betufu-test",
                        "BETUFU_OIDC_CLIENT_SECRET=unused",
                        "BETUFU_DB_JDBC_URL=" + JDBC_URL,
                        "BETUFU_DB_USER=" + DATABASE_USER,
                        "BETUFU_DB_PASSWORD=" + DATABASE_PASSWORD,
                        "BETUFU_REDIS_HOST=" + REDIS_HOST,
                        "BETUFU_REDIS_PORT=" + REDIS_PORT,
                        "BETUFU_REDIS_PASSWORD=" + REDIS_PASSWORD,
                        "spring.docker.compose.enabled=false",
                        "spring.session.data.redis.configure-action=none");
    }

    private static int countOccurrences(String source, String needle) {
        return source.split(java.util.regex.Pattern.quote(needle), -1).length - 1;
    }

    private static Path resolveEnvExample() throws URISyntaxException {
        Path testClasses = Path.of(EnvironmentConfigurationTest.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        Path backendDirectory = testClasses.getParent().getParent();
        return backendDirectory.resolveSibling("infra").resolve(".env.example");
    }

    @Configuration(proxyBeanMethods = false)
    @Import(SessionSecurityConfiguration.class)
    @EnableAutoConfiguration(excludeName = {
        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
        "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
        "org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientAutoConfiguration"
    })
    static class RedisSessionTestConfiguration {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return mock(RedisConnectionFactory.class);
        }

        @Bean
        static BeanPostProcessor redisMessageListenerContainerLifecycleController() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessBeforeInitialization(Object bean, String beanName) {
                    if (bean instanceof RedisMessageListenerContainer container) {
                        container.setAutoStartup(false);
                    }
                    return bean;
                }
            };
        }
    }

    @FunctionalInterface
    private interface EnvironmentAssertions {

        void accept(ConfigurableEnvironment environment);
    }
}
