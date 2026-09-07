package br.com.betufu.odds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.odds.internal.market.MarketState;
import br.com.betufu.odds.internal.market.MarketTransition;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OddsContractArchitectureTest {

  private static final ArchRule ODDS_MUST_NOT_IMPORT_SPORTS_INTERNAL =
      ArchRuleDefinition.noClasses()
          .that()
          .resideInAnyPackage("br.com.betufu.odds..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("br.com.betufu.sports.internal..")
          .because("odds consumes only the public sports contract");

  @Test
  void oddsDoesNotImportSportsInternal() {
    JavaClasses classes =
        new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages("br.com.betufu");
    ODDS_MUST_NOT_IMPORT_SPORTS_INTERNAL.check(classes);
  }

  @Test
  void transitionTableRejectsUnlistedJumps() {
    assertThat(MarketTransition.apply(MarketState.DRAFT, MarketTransition.Action.PUBLISH_MARKET))
        .isEqualTo(MarketState.OPEN);
    assertThatThrownBy(
            () -> MarketTransition.apply(MarketState.CLOSED, MarketTransition.Action.PUBLISH_MARKET))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("FORBIDDEN_TRANSITION");
  }

  @Test
  void closeDeadlineIsEnforcedWithoutWritingState() {
    Instant deadline = Instant.parse("2026-10-10T14:00:00Z");
    assertThatThrownBy(
            () ->
                MarketTransition.apply(
                    MarketState.OPEN,
                    MarketTransition.Action.CLOSE_DEADLINE,
                    deadline.minusSeconds(1),
                    deadline))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("CLOSE_DEADLINE_NOT_REACHED");
    assertThat(
            MarketTransition.apply(
                MarketState.OPEN, MarketTransition.Action.CLOSE_DEADLINE, deadline, deadline))
        .isEqualTo(MarketState.CLOSED);
  }
}
