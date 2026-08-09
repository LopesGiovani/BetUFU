package br.com.betufu.platform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.ClassPathResource;

class EnvironmentConfigurationTest {

    private static final List<String> ENVIRONMENT_PROFILES = List.of("dev", "homolog", "prod");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues(
                    "BETUFU_OIDC_ISSUER_URI=https://idp.invalid/realms/betufu",
                    "BETUFU_OIDC_CLIENT_ID=betufu-test",
                    "BETUFU_OIDC_CLIENT_SECRET=unused");

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
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(environment.getProperty("spring.flyway.enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("management.endpoints.web.exposure.include"))
                .isEqualTo("health");
        assertThat(environment.getProperty("server.forward-headers-strategy"))
                .isEqualToIgnoringCase("native");
        assertThat(environment.containsProperty("betufu.diagnostic.enabled")).isFalse();
    }

    @FunctionalInterface
    private interface EnvironmentAssertions {

        void accept(ConfigurableEnvironment environment);
    }
}
