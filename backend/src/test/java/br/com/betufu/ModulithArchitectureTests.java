package br.com.betufu;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithArchitectureTests {

    private static final List<String> EXPECTED_BUSINESS_MODULES = List.of(
            "identity",
            "verification",
            "sports",
            "odds",
            "betting",
            "ledger",
            "settlement",
            "ranking",
            "chat",
            "audit");

    @Test
    @DisplayName("ApplicationModules.verify() valida todas as fronteiras declaradas no grafo de produção")
    void verifiesModulithBoundaries() {
        ApplicationModules modules = ApplicationModules.of(BetufuApplication.class);
        modules.verify();
    }

    @Test
    @DisplayName("Modulith identifica exatamente os dez módulos de negócio mais o módulo técnico platform (11 no total)")
    void verifiesAllTenBusinessModulesAndPlatformArePresent() {
        ApplicationModules modules = ApplicationModules.of(BetufuApplication.class);

        Set<String> detectedModules = modules.stream()
                .map(module -> module.getIdentifier().toString())
                .collect(Collectors.toSet());

        assertThat(detectedModules)
                .containsAll(EXPECTED_BUSINESS_MODULES)
                .contains("platform");

        // Trava estrutural: 10 módulos de capacidade de negócio (ARCH-02) + 1 módulo de suporte técnico (platform)
        assertThat(detectedModules).hasSize(11);
    }
}
