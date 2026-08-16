package br.com.betufu.platform.persistence;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Política de persistência e integridade de schema da BETUFU (DATA-01, DATA-02).
 *
 * Imposições obrigatórias:
 * 1. spring.jpa.hibernate.ddl-auto deve ser estritamente "validate". Rejeita ausência, "none", "update", "create".
 * 2. spring.flyway.enabled não pode ser false.
 * 3. spring.flyway.clean-disabled não pode ser false.
 * 4. spring.flyway.clean-on-validation-error não pode ser true.
 * 5. spring.flyway.baseline-on-migrate não pode ser true.
 * 6. spring.flyway.locations deve conter "classpath:db/migration" quando configurado.
 */
@Component
@Profile("runtime | integrated")
public class FlywayPolicy implements InitializingBean {

    private final Environment environment;

    public FlywayPolicy(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void afterPropertiesSet() {
        validateJpaDdlAuto();
        validateFlywayEnabled();
        validateFlywayCleanAndBaseline();
        validateFlywayLocations();
    }

    public void validateJpaDdlAuto() {
        String ddlAuto = environment.getProperty("spring.jpa.hibernate.ddl-auto");
        if (ddlAuto == null || ddlAuto.isBlank() || !ddlAuto.equalsIgnoreCase("validate")) {
            throw new IllegalStateException(
                    "Violação de política de persistência: JPA ddl-auto deve ser estritamente 'validate', mas foi configurado como: "
                            + (ddlAuto == null ? "<ausente>" : ddlAuto));
        }
    }

    public void validateFlywayEnabled() {
        String flywayEnabled = environment.getProperty("spring.flyway.enabled", "true");
        if ("false".equalsIgnoreCase(flywayEnabled)) {
            throw new IllegalStateException(
                    "Violação de política de persistência: Flyway deve permanecer habilitado como fonte autoritativa de schema.");
        }
    }

    public void validateFlywayCleanAndBaseline() {
        String cleanDisabled = environment.getProperty("spring.flyway.clean-disabled", "true");
        if ("false".equalsIgnoreCase(cleanDisabled)) {
            throw new IllegalStateException(
                    "Violação de política de persistência: spring.flyway.clean-disabled não pode ser false.");
        }

        String cleanOnValidationError = environment.getProperty("spring.flyway.clean-on-validation-error", "false");
        if ("true".equalsIgnoreCase(cleanOnValidationError)) {
            throw new IllegalStateException(
                    "Violação de política de persistência: spring.flyway.clean-on-validation-error não pode ser true.");
        }

        String baselineOnMigrate = environment.getProperty("spring.flyway.baseline-on-migrate", "false");
        if ("true".equalsIgnoreCase(baselineOnMigrate)) {
            throw new IllegalStateException(
                    "Violação de política de persistência: spring.flyway.baseline-on-migrate não pode ser true.");
        }
    }

    public void validateFlywayLocations() {
        String locations = environment.getProperty("spring.flyway.locations");
        if (locations != null && !locations.isBlank() && !locations.contains("classpath:db/migration")) {
            throw new IllegalStateException(
                    "Violação de política de persistência: spring.flyway.locations deve conter 'classpath:db/migration', mas foi configurado como: "
                            + locations);
        }
    }
}
