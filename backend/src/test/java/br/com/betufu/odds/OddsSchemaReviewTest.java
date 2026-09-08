package br.com.betufu.odds;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OddsSchemaReviewTest {

  @Test
  void migrationMatchesApprovedCandidateBoundaries() throws Exception {
    String migration =
        Files.readString(
            Path.of("src/main/resources/db/migration/V25__create_odds_market_lineage.sql"),
            StandardCharsets.UTF_8);
    String candidate =
        Files.readString(Path.of("../scripts/odds-schema-candidate.json"), StandardCharsets.UTF_8);

    assertThat(migration)
        .contains(
            "CREATE TABLE public.odds_market",
            "CREATE TABLE public.odds_market_version",
            "CREATE TABLE public.odds_calculation",
            "CREATE TABLE public.odds_publication",
            "CREATE TABLE public.odds_lifecycle",
            "CREATE TABLE public.odds_override",
            "CREATE TABLE public.odds_audit_event",
            "CREATE TABLE public.odds_idempotency_command",
            "prevent_odds_history_modification");
    assertThat(candidate).contains("\"ledger\"", "\"settlement\"", "\"PostgreSQL\"", "\"Flyway\"");
    assertThat(migration.toLowerCase())
        .doesNotContain("create table public.ledger", "create table public.settlement");
  }
}
