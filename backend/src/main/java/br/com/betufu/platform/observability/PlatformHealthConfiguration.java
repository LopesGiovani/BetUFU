package br.com.betufu.platform.observability;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures platform-level health indicators and guards against leaking infrastructure topology,
 * connection strings, or internal details in health status representations.
 */
@Configuration(proxyBeanMethods = false)
public class PlatformHealthConfiguration {

    private static final String PLATFORM_NAME = "BETUFU";

    @Bean
    public HealthIndicator platformHealthIndicator() {
        return () -> Health.up()
                .withDetail("platform", PLATFORM_NAME)
                .build();
    }
}
