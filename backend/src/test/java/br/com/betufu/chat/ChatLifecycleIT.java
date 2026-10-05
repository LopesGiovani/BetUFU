package br.com.betufu.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.chat.internal.ChatLifecycleService;
import br.com.betufu.ledger.CreditLedgerPort;
import br.com.betufu.settlement.SettlementProcessPort;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class ChatLifecycleIT extends ChatLifecycleFixture {
  @Test
  void exactTwoHourBoundaryCommitsPermanentCloseThroughRealHttpTransaction() throws Exception {
    UUID event = event(true);
    Instant start = clock.instant();
    result(event, start, 2, 1);
    var account = session("reader");
    clock.current = start.plusSeconds(7200).minusMillis(1);
    UUID key = UUID.randomUUID();
    var accepted = post(account, event, key, "before");
    assertThat(accepted.statusCode()).isEqualTo(201);
    clock.advanceMillis(1);
    assertThat(post(account, event, UUID.randomUUID(), "at").statusCode()).isEqualTo(409);
    assertThat(count("chat_event_lifecycle_close")).isEqualTo(1);
    assertThat(post(account, event, key, "before").body()).isEqualTo(accepted.body());
    clock.advanceSeconds(3);
    assertThat(post(account, event, UUID.randomUUID(), "after").statusCode()).isEqualTo(409);
    assertThat(post(account, null, UUID.randomUUID(), "global").statusCode()).isEqualTo(201);
    assertThat(count("chat_message")).isEqualTo(2);
    assertThat(count("chat_event_lifecycle_close")).isEqualTo(1);
  }

  @Test
  void cancellationWithoutScoreStartsTheConfirmedWindow() throws Exception {
    UUID event = event(true);
    Instant at = clock.instant();
    administrative(event, at, "EVENT_CANCELLED");
    assertThat(events.findDefinitiveMilestone(event).orElseThrow().sourceVersionId()).isNull();
    clock.current = at.plusSeconds(7199);
    var account = session("reader");
    assertThat(post(account, event, UUID.randomUUID(), "before cancellation deadline").statusCode())
        .isEqualTo(201);
    clock.current = at.plusSeconds(7200);
    assertThat(post(account, event, UUID.randomUUID(), "expired").statusCode()).isEqualTo(409);
    assertThat(
            jdbc.queryForObject(
                "SELECT milestone_kind FROM chat_event_lifecycle_fact", String.class))
        .isEqualTo("CANCELLATION_CONFIRMED");
  }

  @Test
  void noOutcomeKeepsChatOpenAndDoesNotInventAMilestone() throws Exception {
    var account = session("reader");
    assertThat(post(account, event(true), UUID.randomUUID(), "not finished").statusCode())
        .isEqualTo(201);
    assertThat(count("chat_event_lifecycle_fact")).isZero();
  }

  @Test
  void timelyCorrectionExtendsEvenWhenNoWriteObservedTheOldDeadline() throws Exception {
    UUID event = event(true);
    Instant start = clock.instant();
    result(event, start, 2, 1);
    result(event, start.plusSeconds(3600), 1, 2);
    clock.current = start.plusSeconds(9000);
    assertThat(post(session("reader"), event, UUID.randomUUID(), "extended").statusCode())
        .isEqualTo(201);
    assertThat(deadline(event)).isEqualTo(start.plusSeconds(10800));
    assertThat(count("chat_event_lifecycle_fact")).isEqualTo(2);
  }

  @Test
  void lateCorrectionNeverReopensEvenWithoutPreviousProjection() throws Exception {
    UUID event = event(true);
    Instant start = clock.instant();
    result(event, start, 2, 1);
    result(event, start.plusSeconds(7200), 1, 2);
    clock.current = start.plusSeconds(7201);
    var account = session("reader");
    assertThat(post(account, event, UUID.randomUUID(), "late").statusCode()).isEqualTo(409);
    jdbc.update("DELETE FROM chat_event_lifecycle_state WHERE event_id=?", event);
    result(event, start.plusSeconds(7250), 3, 1);
    clock.current = start.plusSeconds(7251);
    assertThat(post(account, event, UUID.randomUUID(), "restore").statusCode()).isEqualTo(409);
    assertThat(
            jdbc.queryForObject(
                "SELECT state FROM chat_event_lifecycle_state WHERE event_id=?",
                String.class,
                event))
        .isEqualTo("CLOSED");
    assertThat(count("chat_event_lifecycle_close")).isEqualTo(1);
  }

  @Test
  void partialFailedAndMissingMarketProcessingCannotStartMilestone() throws Exception {
    UUID event = event(true);
    Instant at = clock.instant();
    UUID fact = result(event, at, 2, 1);
    prediction(event);
    prediction(event);
    UUID auth = processor.authorize(event, author, "pending");
    UUID caseId = caseFor(event, fact, auth);
    for (String state : java.util.List.of("PENDING", "FAILED")) {
      jdbc.update(
          "UPDATE settlement_processing SET status=?,total_units=2,completed_units=1 WHERE case_id=?",
          state,
          caseId);
      assertThat(settlements.findCompletion(event, fact).orElseThrow().complete()).isFalse();
      assertThat(post(session("reader"), event, UUID.randomUUID(), "still open").statusCode())
          .isEqualTo(201);
      clock.advanceSeconds(3);
    }
    jdbc.update(
        "UPDATE settlement_processing SET status='COMPLETE',total_units=1,completed_units=1 WHERE case_id=?",
        caseId);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO settlement_chat_completion_fact(case_id,completed_at) VALUES (?,?)",
                    caseId,
                    Timestamp.from(at)))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(settlements.findCompletion(event, fact).orElseThrow().complete()).isFalse();
    assertThat(count("chat_event_lifecycle_fact")).isZero();
  }

  @Test
  void completionTimestampIsImmutableAndAllMarketsMustComplete() throws Exception {
    UUID event = event(true);
    Instant confirmed = clock.instant().minusSeconds(7200);
    UUID fact = result(event, confirmed, 2, 1);
    prediction(event);
    prediction(event);
    UUID caseId = processor.process(processor.authorize(event, author, "complete"));
    var completion = settlements.findCompletion(event, fact).orElseThrow();
    assertThat(completion.complete()).isTrue();
    assertThat(completion.completedAt()).isAfter(confirmed);
    assertThatThrownBy(
            () -> jdbc.execute("UPDATE settlement_chat_completion_fact SET completed_at=now()"))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(() -> jdbc.execute("DELETE FROM settlement_chat_completion_fact"))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    jdbc.update(
        "UPDATE settlement_processing SET updated_at=updated_at+INTERVAL '8 hours' WHERE case_id=?",
        caseId);
    assertThat(settlements.findCompletion(event, fact).orElseThrow().completedAt())
        .isEqualTo(completion.completedAt());
    clock.current = completion.completedAt().plusSeconds(7199);
    var account = session("reader");
    assertThat(post(account, event, UUID.randomUUID(), "before").statusCode()).isEqualTo(201);
    clock.current = completion.completedAt().plusSeconds(7200);
    assertThat(post(account, event, UUID.randomUUID(), "at").statusCode()).isEqualTo(409);
    assertThat(count("prediction_settlement")).isEqualTo(2);
  }

  @Test
  void pendingFinancialCompensationBlocksCorrectedMilestoneUntilRecovered() throws Exception {
    UUID event = event(true);
    UUID first = result(event, clock.instant(), 2, 1);
    UUID prediction = prediction(event);
    processor.process(processor.authorize(event, author, "first"));
    var original = settlements.findCompletion(event, first).orElseThrow();
    ledger.debit(
        author,
        ledger.getBalance(author).balance().subtract(new BigDecimal("10.00")),
        UUID.randomUUID());
    UUID corrected = result(event, clock.instant().plusMillis(1), 1, 2);
    processor.process(processor.authorize(event, author, "correction"));
    assertThat(settlements.findCompletion(event, corrected).orElseThrow().complete()).isFalse();
    lifecycle.requireWritable(event, clock.instant());
    assertThat(count("chat_event_lifecycle_fact")).isEqualTo(1);
    ledger.compensate(author, new BigDecimal("106.00"), UUID.randomUUID());
    var recovered = settlements.findCompletion(event, corrected).orElseThrow();
    assertThat(recovered.complete()).isTrue();
    assertThat(recovered.completedAt()).isAfterOrEqualTo(original.completedAt());
    clock.current = recovered.completedAt().plusSeconds(7199);
    assertThat(post(session("reader"), event, UUID.randomUUID(), "recovered").statusCode())
        .isEqualTo(201);
    assertThat(deadline(event)).isEqualTo(recovered.completedAt().plusSeconds(7200));
    assertThat(prediction).isNotNull();
  }

  @Test
  void cancelledOutcomeWithPendingCompensationWaitsForRecovery() throws Exception {
    UUID event = event(true);
    Instant at = clock.instant();
    result(event, at, 2, 1);
    prediction(event);
    processor.process(processor.authorize(event, author, "initial result"));
    ledger.debit(
        author,
        ledger.getBalance(author).balance().subtract(new BigDecimal("10.00")),
        UUID.randomUUID());
    clock.current = at.plusSeconds(3600);
    UUID cancellation = administrative(event, clock.instant(), "EVENT_CANCELLED");
    processor.process(processor.authorize(event, author, "cancelled"));
    assertThat(settlements.findCompletion(event, cancellation).orElseThrow().complete()).isFalse();
    lifecycle.requireWritable(event, clock.instant());
    assertThat(count("chat_event_lifecycle_fact")).isEqualTo(1);
    ledger.compensate(author, new BigDecimal("106.00"), UUID.randomUUID());
    var completion = settlements.findCompletion(event, cancellation).orElseThrow();
    assertThat(completion.complete()).isTrue();
    clock.current = completion.completedAt().plusSeconds(7199);
    var account = session("reader");
    assertThat(post(account, event, UUID.randomUUID(), "after recovery").statusCode())
        .isEqualTo(201);
    assertThat(deadline(event)).isEqualTo(completion.completedAt().plusSeconds(7200));
    clock.current = completion.completedAt().plusSeconds(7200);
    assertThat(post(account, event, UUID.randomUUID(), "expired cancellation").statusCode())
        .isEqualTo(409);
    assertThat(count("chat_event_lifecycle_close")).isEqualTo(1);
  }

  @Test
  void expiredOriginalDeadlineClosesWhileCorrectionIsStillPending() throws Exception {
    UUID event = event(true);
    UUID first = result(event, clock.instant(), 2, 1);
    prediction(event);
    processor.process(processor.authorize(event, author, "initial"));
    Instant at = settlements.findCompletion(event, first).orElseThrow().completedAt();
    result(event, at.plusSeconds(3600), 1, 2);
    clock.current = at.plusSeconds(7200);
    assertThat(post(session("reader"), event, UUID.randomUUID(), "pending correction").statusCode())
        .isEqualTo(409);
    assertThat(count("chat_event_lifecycle_close")).isEqualTo(1);
  }

  @Test
  void historyTriggersRejectOwnerMutationAndRuntimeHasNoMutationGrant() throws Exception {
    UUID event = event(true);
    Instant at = clock.instant();
    result(event, at, 2, 1);
    clock.current = at.plusSeconds(7200);
    assertThat(post(session("reader"), event, UUID.randomUUID(), "close").statusCode())
        .isEqualTo(409);
    for (String table :
        java.util.List.of("chat_event_lifecycle_fact", "chat_event_lifecycle_close")) {
      assertThatThrownBy(() -> jdbc.execute("UPDATE " + table + " SET event_id=event_id"))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
      assertThatThrownBy(() -> jdbc.execute("DELETE FROM " + table))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    try (var db =
        new org.testcontainers.postgresql.PostgreSQLContainer(
            org.testcontainers.utility.DockerImageName.parse("postgres:18.4-trixie"))) {
      db.start();
      var ownerSource =
          new org.springframework.jdbc.datasource.DriverManagerDataSource(
              db.getJdbcUrl(), db.getUsername(), db.getPassword());
      var owner = new org.springframework.jdbc.core.JdbcTemplate(ownerSource);
      owner.execute("CREATE ROLE lifecycle_runtime LOGIN PASSWORD 'test-only'");
      owner.execute(
          "ALTER DATABASE "
              + db.getDatabaseName()
              + " SET betufu.runtime_role TO 'lifecycle_runtime'");
      owner.execute("GRANT USAGE ON SCHEMA public TO lifecycle_runtime");
      owner.execute(
          "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT,INSERT,UPDATE,DELETE ON TABLES TO lifecycle_runtime");
      org.flywaydb.core.Flyway.configure().dataSource(ownerSource).load().migrate();
      var runtime =
          new org.springframework.jdbc.core.JdbcTemplate(
              new org.springframework.jdbc.datasource.DriverManagerDataSource(
                  db.getJdbcUrl(), "lifecycle_runtime", "test-only"));
      assertThat(
              runtime.queryForObject(
                  "SELECT rolsuper FROM pg_roles WHERE rolname=current_user", Boolean.class))
          .isFalse();
      for (String table :
          java.util.List.of(
              "chat_event_lifecycle_fact",
              "chat_event_lifecycle_close",
              "settlement_chat_completion_fact")) {
        assertThat(
                runtime.queryForObject(
                    "SELECT has_table_privilege(current_user,?,'INSERT')", Boolean.class, table))
            .isTrue();
        for (String privilege : java.util.List.of("UPDATE", "DELETE", "TRUNCATE"))
          assertThat(
                  runtime.queryForObject(
                      "SELECT has_table_privilege(current_user,?,?)",
                      Boolean.class,
                      table,
                      privilege))
              .isFalse();
      }
    }
  }
}

abstract class ChatLifecycleFixture extends ChatWriteFixture {
  @Autowired ChatLifecycleService lifecycle;
  @Autowired ChatEventPort events;
  @Autowired ChatSettlementPort settlements;
  @Autowired SettlementProcessPort processor;
  @Autowired CreditLedgerPort ledger;

  int count(String table) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
  }

  Instant deadline(UUID event) {
    return jdbc.queryForObject(
            "SELECT deadline_at FROM chat_event_lifecycle_state WHERE event_id=?",
            Timestamp.class,
            event)
        .toInstant();
  }

  UUID result(UUID event, Instant at, int home, int away) {
    return outcome(event, at, "SPORTING_RESULT", home, away);
  }

  UUID administrative(UUID event, Instant at, String kind) {
    return outcome(event, at, kind, 0, 0);
  }

  private UUID outcome(UUID event, Instant at, String kind, int home, int away) {
    return new TransactionTemplate(transactions)
        .execute(
            ignored -> {
              var scope =
                  jdbc.queryForMap(
                      "SELECT competition_id,edition_id,confirmed_version_id FROM sports_event WHERE event_id=? FOR UPDATE",
                      event);
              int version =
                  jdbc.queryForObject(
                      "SELECT coalesce(max(version_no),0)+1 FROM sports_outcome_fact WHERE event_id=?",
                      Integer.class,
                      event);
              UUID previous =
                  jdbc.query(
                      "SELECT fact_id FROM sports_outcome_head WHERE event_id=?",
                      rs -> rs.next() ? rs.getObject(1, UUID.class) : null,
                      event);
              UUID resultVersion = null;
              if ("SPORTING_RESULT".equals(kind)) {
                String status = version == 1 ? "CONFIRMADO" : "CORRIGIDO";
                resultVersion =
                    jdbc.queryForObject(
                        "INSERT INTO sports_result_version(event_id,competition_id,edition_id,version_no,status,source_reference,reason,recorded_by_identity_id,confirmed_by_identity_id,confirmed_at,supersedes_version_id,base_lock_version) VALUES (?,?,?,?,?,'https://fixture.invalid','correction',?,?,?, ?,0) RETURNING version_id",
                        UUID.class,
                        event,
                        scope.get("competition_id"),
                        scope.get("edition_id"),
                        version,
                        status,
                        author,
                        author,
                        Timestamp.from(at),
                        scope.get("confirmed_version_id"));
                jdbc.update(
                    "INSERT INTO sports_result_version_slot(version_id,slot_no,score,display_label) VALUES (?,1,?,'Home'),(?,2,?,'Away')",
                    resultVersion,
                    home,
                    resultVersion,
                    away);
                jdbc.update(
                    "UPDATE sports_event SET confirmed_version_id=?,status='PUBLICADO' WHERE event_id=?",
                    resultVersion,
                    event);
              } else if ("EVENT_CANCELLED".equals(kind))
                jdbc.update("UPDATE sports_event SET status='CANCELADO' WHERE event_id=?", event);
              UUID fact =
                  jdbc.queryForObject(
                      "INSERT INTO sports_outcome_fact(event_id,competition_id,edition_id,version_no,outcome_kind,source_reference,reason,recorded_by_identity_id,sport_time,known_at,supersedes_fact_id) VALUES (?,?,?,?,?,'https://fixture.invalid','confirmed',?,?,?,?) RETURNING fact_id",
                      UUID.class,
                      event,
                      scope.get("competition_id"),
                      scope.get("edition_id"),
                      version,
                      kind,
                      author,
                      Timestamp.from(at),
                      Timestamp.from(at),
                      previous);
              if (resultVersion != null)
                jdbc.update(
                    "INSERT INTO sports_outcome_sporting_payload(fact_id,event_id,confirmed_version_id,confirmed_version_status,confirmed_at) VALUES (?,?,?,?,?)",
                    fact,
                    event,
                    resultVersion,
                    version == 1 ? "CONFIRMADO" : "CORRIGIDO",
                    Timestamp.from(at));
              else
                jdbc.update(
                    "INSERT INTO sports_outcome_administrative_payload(fact_id,event_id,administrative_reason) VALUES (?,?,'confirmed')",
                    fact,
                    event);
              jdbc.update(
                  "INSERT INTO sports_outcome_head(event_id,fact_id) VALUES (?,?) ON CONFLICT(event_id) DO UPDATE SET fact_id=excluded.fact_id",
                  event,
                  fact);
              return fact;
            });
  }

  UUID prediction(UUID event) {
    jdbc.update(
        "INSERT INTO operational_role_grant(identity_id,role_name,status) VALUES (?,'ADMINISTRADOR_GERAL','VIGENTE') ON CONFLICT DO NOTHING",
        author);
    UUID market = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO odds_market(market_id,event_id,market_type) VALUES (?,?,?)",
        market,
        event,
        "H2H-" + market);
    UUID prediction = UUID.randomUUID();
    ledger.ensureInitialGrant(author);
    ledger.debit(author, new BigDecimal("50.00"), prediction);
    jdbc.update(
        "INSERT INTO prediction(prediction_id,identity_id,market_id,market_version_id,publication_id,rule_version,selection,stake,accepted_odds,potential_return,net_profit,close_deadline,idempotency_key,payload_digest) VALUES (?,?,?,?,?,'test','HOME',50.00,2.1200000000,106.00,56.00,now(),?,?)",
        prediction,
        author,
        market,
        UUID.randomUUID(),
        UUID.randomUUID(),
        prediction.toString(),
        prediction.toString());
    return prediction;
  }

  UUID caseFor(UUID event, UUID fact, UUID auth) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO settlement_case(case_id,authorization_id,event_id,outcome_fact_id,processing_version,rule_version) VALUES (?,?,?,?,1,'test')",
        id,
        auth,
        event,
        fact);
    jdbc.update("INSERT INTO settlement_processing(case_id,total_units) VALUES (?,2)", id);
    return id;
  }
}
