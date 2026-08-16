package br.com.betufu.platform.persistence;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class FlywayPolicyTest {

    private MockEnvironment createValidEnvironment() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("spring.jpa.hibernate.ddl-auto", "validate");
        env.setProperty("spring.flyway.enabled", "true");
        env.setProperty("spring.flyway.clean-disabled", "true");
        env.setProperty("spring.flyway.clean-on-validation-error", "false");
        env.setProperty("spring.flyway.baseline-on-migrate", "false");
        env.setProperty("spring.flyway.locations", "classpath:db/migration");
        return env;
    }

    @Test
    @DisplayName("Aceita configuração de persistência em estrita conformidade com as políticas")
    void acceptsValidPersistenceConfiguration() {
        FlywayPolicy policy = new FlywayPolicy(createValidEnvironment());
        assertThatCode(policy::afterPropertiesSet).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Rejeita JPA ddl-auto ausente, em branco, 'none', 'update' ou 'create'")
    void rejectsInvalidJpaDdlAuto() {
        // Genuinamente ausente (null)
        MockEnvironment nullEnv = new MockEnvironment();
        nullEnv.setProperty("spring.flyway.enabled", "true");
        assertThatThrownBy(new FlywayPolicy(nullEnv)::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JPA ddl-auto deve ser estritamente 'validate'");

        // Em branco ("")
        MockEnvironment blankEnv = createValidEnvironment();
        blankEnv.setProperty("spring.jpa.hibernate.ddl-auto", "   ");
        assertThatThrownBy(new FlywayPolicy(blankEnv)::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JPA ddl-auto deve ser estritamente 'validate'");

        // 'none'
        MockEnvironment noneEnv = createValidEnvironment();
        noneEnv.setProperty("spring.jpa.hibernate.ddl-auto", "none");
        assertThatThrownBy(new FlywayPolicy(noneEnv)::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JPA ddl-auto deve ser estritamente 'validate'");

        // 'update'
        MockEnvironment updateEnv = createValidEnvironment();
        updateEnv.setProperty("spring.jpa.hibernate.ddl-auto", "update");
        assertThatThrownBy(new FlywayPolicy(updateEnv)::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JPA ddl-auto deve ser estritamente 'validate'");

        // 'create'
        MockEnvironment createEnv = createValidEnvironment();
        createEnv.setProperty("spring.jpa.hibernate.ddl-auto", "create");
        assertThatThrownBy(new FlywayPolicy(createEnv)::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JPA ddl-auto deve ser estritamente 'validate'");
    }

    @Test
    @DisplayName("Rejeita desativação do Flyway")
    void rejectsDisabledFlyway() {
        MockEnvironment disabledEnv = createValidEnvironment();
        disabledEnv.setProperty("spring.flyway.enabled", "false");
        assertThatThrownBy(new FlywayPolicy(disabledEnv)::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Flyway deve permanecer habilitado");
    }

    @Test
    @DisplayName("Rejeita clean-disabled=false e clean-on-validation-error=true")
    void rejectsInsecureCleanSettings() {
        MockEnvironment cleanDisabledFalseEnv = createValidEnvironment();
        cleanDisabledFalseEnv.setProperty("spring.flyway.clean-disabled", "false");
        assertThatThrownBy(new FlywayPolicy(cleanDisabledFalseEnv)::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.flyway.clean-disabled não pode ser false");

        MockEnvironment cleanOnErrorTrueEnv = createValidEnvironment();
        cleanOnErrorTrueEnv.setProperty("spring.flyway.clean-on-validation-error", "true");
        assertThatThrownBy(new FlywayPolicy(cleanOnErrorTrueEnv)::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.flyway.clean-on-validation-error não pode ser true");
    }

    @Test
    @DisplayName("Rejeita baseline-on-migrate=true")
    void rejectsBaselineOnMigrateTrue() {
        MockEnvironment baselineEnv = createValidEnvironment();
        baselineEnv.setProperty("spring.flyway.baseline-on-migrate", "true");
        assertThatThrownBy(new FlywayPolicy(baselineEnv)::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.flyway.baseline-on-migrate não pode ser true");
    }

    @Test
    @DisplayName("Rejeita locations divergentes que não apontem para classpath:db/migration")
    void rejectsDivergentFlywayLocations() {
        MockEnvironment divergentLocationEnv = createValidEnvironment();
        divergentLocationEnv.setProperty("spring.flyway.locations", "classpath:db/custom_divergent");
        assertThatThrownBy(new FlywayPolicy(divergentLocationEnv)::afterPropertiesSet)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.flyway.locations deve conter 'classpath:db/migration'");
    }
}
