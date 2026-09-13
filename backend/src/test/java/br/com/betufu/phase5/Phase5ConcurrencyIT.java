package br.com.betufu.phase5;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Phase5ConcurrencyIT {
  @Test
  void migrationContainsPessimisticLedgerInvariants() throws Exception {
    String migration = Files.readString(Path.of("src/main/resources/db/migration/V29__create_virtual_credit_ledger.sql"));
    assertThat(migration).contains("credit_account", "balance >= 0.00", "prevent_credit_ledger_modification");
  }
}
