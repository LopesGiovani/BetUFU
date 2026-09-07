package br.com.betufu.sports;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SportsOddsBoundaryArchitectureTest {

  private static final ArchRule PUBLIC_SPORTS_CONTRACT_MUST_NOT_USE_INTERNALS =
      ArchRuleDefinition.noClasses()
          .that()
          .resideInAPackage("br.com.betufu.sports")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("br.com.betufu.sports.internal..")
          .because("public sports contract must not depend on sports internals");

  @Test
  void publicSportsContractDoesNotDependOnSportsInternals() {
    JavaClasses classes =
        new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("br.com.betufu");
    PUBLIC_SPORTS_CONTRACT_MUST_NOT_USE_INTERNALS.check(classes);
  }

  @Test
  void eventProjectionIsHeadToHeadOnlyAndDoesNotRequireResultHead() {
    SportsEventForOdds event =
        new SportsEventForOdds(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.parse("2026-10-10T14:00:00Z"),
            "America/Sao_Paulo",
            SportsEventForOdds.EventFormat.HEAD_TO_HEAD,
            List.of(
                new SportsEventForOdds.Slot(1, "A", UUID.randomUUID(), "Team A"),
                new SportsEventForOdds.Slot(2, "B", UUID.randomUUID(), "Team B")));
    assertThat(event.format()).isEqualTo(SportsEventForOdds.EventFormat.HEAD_TO_HEAD);
  }

  @Test
  void administrativeResultCannotCarryScore() {
    UUID id = UUID.randomUUID();
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                new ConfirmedSportsResultForOdds(
                    id,
                    id,
                    id,
                    id,
                    1,
                    ConfirmedSportsResultForOdds.VersionStatus.CONFIRMADO,
                    SportsResultKindForOdds.ADMINISTRATIVE_WALKOVER,
                    List.of(new ConfirmedSportsResultForOdds.Score(1, 1, id, "A")),
                    Instant.parse("2026-10-10T14:30:00Z"),
                    Instant.parse("2026-10-10T14:35:00Z"),
                    Instant.parse("2026-10-10T14:00:00Z"),
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("administrative and lifecycle facts cannot carry scores");
  }
}
