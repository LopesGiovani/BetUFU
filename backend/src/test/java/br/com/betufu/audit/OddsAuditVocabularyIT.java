package br.com.betufu.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OddsAuditVocabularyIT {

  @Test
  void keepsOddsActionsInTheirHistoricalMigration() throws Exception {
    Set<String> oddsActions =
        Set.of(
            AuditEntry.ACTION_ODDS_MARKET_VIEWED,
            AuditEntry.ACTION_ODDS_MARKET_CREATED,
            AuditEntry.ACTION_ODDS_MARKET_LIFECYCLE_CHANGED,
            AuditEntry.ACTION_ODDS_CALCULATION_RECORDED,
            AuditEntry.ACTION_ODDS_PUBLICATION_RECORDED,
            AuditEntry.ACTION_ODDS_OVERRIDE_PROPOSED,
            AuditEntry.ACTION_ODDS_OVERRIDE_APPROVED,
            AuditEntry.ACTION_ODDS_OVERRIDE_REJECTED);
    assertThat(AuditEntry.ALLOWED_ACTIONS).containsAll(oddsActions);
    assertThatThrownBy(
            () ->
                new AuditEntry(
                    null,
                    null,
                    null,
                    "ODDS_ACTION_NOT_APPROVED",
                    AuditEntry.OUTCOME_PERMITTED,
                    null,
                    null,
                    null,
                    null,
                    null))
        .isInstanceOf(IllegalArgumentException.class);

    String migration =
        Files.readString(
            Path.of("src/main/resources/db/migration/V26__extend_odds_governance_audit.sql"),
            StandardCharsets.UTF_8);
    for (String action : oddsActions) {
      assertThat(migration).as("V26 must preserve action %s", action).contains("'" + action + "'");
    }
  }
}
