package br.com.betufu.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AuditVocabularyIT {
  @Test
  void correctionActionsRemainInTheClosedVocabulary() throws Exception {
    assertThat(AuditEntry.ALLOWED_ACTIONS)
        .containsAll(
            Set.of(
                AuditEntry.ACTION_CREDIT_CORRECTION_PROPOSED,
                AuditEntry.ACTION_CREDIT_CORRECTION_APPROVED,
                AuditEntry.ACTION_CREDIT_CORRECTION_REJECTED,
                AuditEntry.ACTION_CREDIT_CORRECTION_SETTLEMENT,
                AuditEntry.ACTION_CREDIT_CORRECTION_RECOVERED));
    assertThatThrownBy(
            () ->
                AuditEntry.builder()
                    .action("CREDIT_BONUS")
                    .outcome(AuditEntry.OUTCOME_PERMITTED)
                    .build())
        .isInstanceOf(IllegalArgumentException.class);
    String migration =
        Files.readString(
            Path.of("src/main/resources/db/migration/V35__create_credit_correction_cases.sql"),
            StandardCharsets.UTF_8);
    for (String action : AuditEntry.ALLOWED_ACTIONS) {
      assertThat(migration).as("V35 must preserve action %s", action).contains("'" + action + "'");
    }
  }
}
