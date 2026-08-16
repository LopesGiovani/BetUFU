package br.com.betufu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.cycle_a.CycleClassA;
import br.com.betufu.cycle_b.CycleClassB;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@AnalyzeClasses(
        packages = "br.com.betufu",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleDependencyRulesTest {

    @ArchTest
    public static final ArchRule NO_DISTRIBUTED_OR_STREAMING_STACKS = ArchRuleDefinition.noClasses()
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.apache.kafka..",
                    "io.fabric8.kubernetes..",
                    "io.kubernetes..",
                    "org.springframework.cloud..",
                    "com.netflix..",
                    "io.grpc..");

    @ArchTest
    public static final ArchRule NO_CROSS_MODULE_ACCESS_TO_IDENTITY_INTERNAL = ArchRuleDefinition.noClasses()
            .that().resideOutsideOfPackage("br.com.betufu.identity..")
            .should().dependOnClassesThat().resideInAPackage("br.com.betufu.identity.internal..");

    @ArchTest
    public static final ArchRule NO_MODULE_CYCLES = SlicesRuleDefinition.slices()
            .matching("br.com.betufu.(*)..")
            .should().beFreeOfCycles();

    @Test
    @DisplayName("Prova de discriminação: regra NO_CROSS_MODULE_ACCESS_TO_IDENTITY_INTERNAL falha se classe externa acessar identity.internal")
    void verifiesDiscriminationAgainstCrossModuleInternalAccess() {
        JavaClasses violatorClasses = new ClassFileImporter()
                .importClasses(SyntheticExternalViolator.class);

        assertThatThrownBy(() -> NO_CROSS_MODULE_ACCESS_TO_IDENTITY_INTERNAL.check(violatorClasses))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("was violated")
                .hasMessageContaining("LocalIdentityService");
    }

    @Test
    @DisplayName("Prova de discriminação: regra NO_MODULE_CYCLES falha diante de dependências circulares entre módulos de primeiro nível")
    void verifiesDiscriminationAgainstModuleCycles() {
        JavaClasses cyclicClasses = new ClassFileImporter()
                .importClasses(CycleClassA.class, CycleClassB.class);

        assertThatThrownBy(() -> NO_MODULE_CYCLES.check(cyclicClasses))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("was violated")
                .hasMessageContaining("Cycle detected");
    }

    /**
     * Fixture sintética fora do pacote 'br.com.betufu.identity' que referencia 'identity.internal'
     * para demonstrar a detecção e falha da regra de encapsulamento do ArchUnit.
     */
    static class SyntheticExternalViolator {
        @SuppressWarnings("unused")
        private br.com.betufu.identity.internal.LocalIdentityService unauthorizedService;
    }
}
