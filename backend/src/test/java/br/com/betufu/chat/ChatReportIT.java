package br.com.betufu.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.chat.internal.ChatCommunityModels.ReportReason;
import br.com.betufu.chat.internal.ChatReportService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.session.Session;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

class ChatReportIT extends ChatCommunityFixture {
  @Autowired ChatReportService reports;

  @Test
  void reportRollbackAndAcceptedReportsNeverNotifyOrRemovePublicFacts() throws Exception {
    UUID message = message(global, "public original");
    Session actor = session("reader");
    try (Wire wire = connected(actor)) {
      wire.subscribe("/chat/global", "0");
      wire.message();
      new TransactionTemplate(transactions)
          .executeWithoutResult(
              status -> {
                reports.report(
                    "https://idp.invalid",
                    "reader",
                    actor.getId(),
                    message,
                    UUID.randomUUID(),
                    ReportReason.SPAM);
                status.setRollbackOnly();
              });
      assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_report", Integer.class)).isZero();
      assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_report_receipt", Integer.class))
          .isZero();
      assertThat(report(actor, message, UUID.randomUUID(), "SPAM").statusCode()).isEqualTo(201);
      assertThat(wire.frames.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS)).isNull();
      assertThat(
              jdbc.queryForObject(
                  "SELECT body FROM chat_message WHERE message_id=?", String.class, message))
          .isEqualTo("public original");
    }
  }

  @Test
  void revocationWhileWaitingForAccountLockRejectsTheReport() throws Exception {
    Session actor = session("reader");
    UUID message = message(global, "fact");
    jdbc.update("INSERT INTO chat_mute_account(owner_identity_id) VALUES (?)", author);
    try (var connection =
        java.sql.DriverManager.getConnection(
            POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword())) {
      connection.setAutoCommit(false);
      try (var sql =
          connection.prepareStatement(
              "SELECT version FROM chat_mute_account WHERE owner_identity_id=? FOR UPDATE")) {
        sql.setObject(1, author);
        sql.executeQuery().close();
      }
      var pending =
          CompletableFuture.supplyAsync(() -> uncheckedReport(actor, message, UUID.randomUUID()));
      awaitAccountLock();
      sessions.deleteById(actor.getId());
      connection.commit();
      assertThat(pending.get(10, java.util.concurrent.TimeUnit.SECONDS).statusCode())
          .isEqualTo(401);
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_report", Integer.class)).isZero();
  }

  @Test
  void authenticatedInactiveReaderReportsWithoutAcquiringWritePermission() throws Exception {
    Session actor = session("reader");
    UUID message = message(global, "private report fixture");
    assertThat(
            command(
                    actor,
                    "POST",
                    "/global/messages",
                    "{\"text\":\"no\"}",
                    UUID.randomUUID().toString(),
                    true,
                    ORIGIN)
                .statusCode())
        .isEqualTo(403);
    var accepted = report(actor, message, UUID.randomUUID(), "SPAM");
    assertThat(accepted.statusCode()).isEqualTo(201);
    assertThat(accepted.headers().firstValue("Cache-Control")).contains("private, no-store");
    assertThat(json.readTree(accepted.body()).size()).isEqualTo(2);
    assertThat(accepted.body())
        .contains("reportId", "reportedAt")
        .doesNotContain("reader", author.toString(), "fixture", "SPAM");
    assertThat(jdbc.queryForObject("SELECT reporter_identity_id FROM chat_report", UUID.class))
        .isEqualTo(author);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_message", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void actualSessionCsrfOriginAndClosedReasonCatalogAreRequired() throws Exception {
    UUID message = message(global, "fact");
    Session actor = session("reader");
    assertThat(report(null, message, UUID.randomUUID(), "SPAM").statusCode()).isEqualTo(401);
    for (String body :
        List.of(
            "{\"reason\":\"OTHER\"}",
            "{\"reason\":null}",
            "{\"reason\":\"SPAM\",\"actorId\":\"spoof\"}",
            "[]")) {
      assertThat(
              command(
                      actor,
                      "POST",
                      "/messages/" + message + "/reports",
                      body,
                      UUID.randomUUID().toString(),
                      true,
                      ORIGIN)
                  .statusCode())
          .isEqualTo(400);
    }
    assertThat(
            command(
                    actor,
                    "POST",
                    "/messages/" + message + "/reports",
                    "{\"reason\":\"SPAM\"}",
                    "not-a-uuid",
                    true,
                    ORIGIN)
                .statusCode())
        .isEqualTo(400);
    assertThat(
            command(
                    actor,
                    "POST",
                    "/messages/" + message + "/reports",
                    "{\"reason\":\"SPAM\"}",
                    UUID.randomUUID().toString(),
                    false,
                    ORIGIN)
                .statusCode())
        .isEqualTo(403);
    assertThat(
            command(
                    actor,
                    "POST",
                    "/messages/" + message + "/reports",
                    "{\"reason\":\"SPAM\"}",
                    UUID.randomUUID().toString(),
                    true,
                    "https://evil.invalid")
                .statusCode())
        .isEqualTo(403);
    sessions.deleteById(actor.getId());
    assertThat(report(actor, message, UUID.randomUUID(), "SPAM").statusCode()).isEqualTo(401);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_report", Integer.class)).isZero();
  }

  @Test
  void draftMissingAndUnpublishedEventsAreNotReportableButRemovedFactsAre() throws Exception {
    Session actor = session("reader");
    assertThat(report(actor, UUID.randomUUID(), UUID.randomUUID(), "SPAM").statusCode())
        .isEqualTo(404);
    UUID draft = event(false);
    assertThat(
            report(actor, message(channel(draft), "draft"), UUID.randomUUID(), "SPAM").statusCode())
        .isEqualTo(404);
    UUID event = event(true), message = message(channel(event), "event private original");
    jdbc.update(
        "INSERT INTO chat_message_projection(message_id,state) VALUES (?,'REMOVED')", message);
    assertThat(report(actor, message, UUID.randomUUID(), "ASSEDIO").statusCode()).isEqualTo(201);
    jdbc.update("UPDATE sports_event SET status='RASCUNHO' WHERE event_id=?", event);
    assertThat(report(actor, message, UUID.randomUUID(), "ASSEDIO").statusCode()).isEqualTo(404);
  }

  @Test
  void retryNewKeysAndPayloadConflictsPreserveOneCanonicalOccurrence() throws Exception {
    Session actor = session("reader");
    UUID message = message(global, "first"), key = UUID.randomUUID();
    var first = report(actor, message, key, "OFENSA");
    assertThat(first.statusCode()).isEqualTo(201);
    assertThat(report(actor, message, key, "OFENSA").body()).isEqualTo(first.body());
    UUID secondKey = UUID.randomUUID();
    assertThat(report(actor, message, secondKey, "OFENSA").body()).isEqualTo(first.body());
    assertThat(report(actor, message, secondKey, "FRAUDE").statusCode()).isEqualTo(409);
    assertThat(report(actor, message(global, "other"), secondKey, "OFENSA").statusCode())
        .isEqualTo(409);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_report", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_report_receipt", Integer.class))
        .isEqualTo(2);
  }

  @Test
  void concurrentDevicesAndDifferentReportersCannotDuplicatePrivateOccurrences() throws Exception {
    UUID message = message(global, "fact"), key = UUID.randomUUID();
    Session first = session("reader"), second = session("reader");
    var a = CompletableFuture.supplyAsync(() -> uncheckedReport(first, message, key));
    var b = CompletableFuture.supplyAsync(() -> uncheckedReport(second, message, key));
    assertThat(a.get().statusCode()).isEqualTo(201);
    assertThat(b.get().body()).isEqualTo(a.get().body());
    UUID other = participant("other");
    assertThat(report(session("other"), message, key, "SPAM").statusCode()).isEqualTo(201);
    assertThat(jdbc.queryForList("SELECT reporter_identity_id FROM chat_report", UUID.class))
        .containsExactlyInAnyOrder(author, other);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_report_receipt", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chat_message_projection WHERE state='REMOVED'",
                Integer.class))
        .isZero();
  }

  @Test
  void allFiveReasonsAndRuntimePrivilegesEnforceImmutableFacts() throws Exception {
    Session actor = session("reader");
    for (String reason : List.of("DISCURSO_DE_ODIO", "SPAM", "OFENSA", "ASSEDIO", "FRAUDE"))
      assertThat(report(actor, message(global, reason), UUID.randomUUID(), reason).statusCode())
          .isEqualTo(201);
    for (String table : List.of("chat_report", "chat_report_receipt")) {
      assertThatThrownBy(() -> jdbc.execute("DELETE FROM " + table))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
      assertThatThrownBy(
              () ->
                  jdbc.execute(
                      "UPDATE " + table + " SET reporter_identity_id=reporter_identity_id"))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    JdbcTemplate runtime = runtime();
    assertThat(
            runtime.queryForObject(
                "SELECT rolsuper FROM pg_roles WHERE rolname=current_user", Boolean.class))
        .isFalse();
    UUID id =
        runtime.queryForObject(
            "INSERT INTO chat_report(reporter_identity_id,message_id,reason) VALUES (?,?,'SPAM') RETURNING report_id",
            UUID.class,
            author,
            message(global, "runtime"));
    runtime.update(
        "INSERT INTO chat_report_receipt(reporter_identity_id,idempotency_key,report_id) VALUES (?,?,?)",
        author,
        UUID.randomUUID(),
        id);
    for (String table : List.of("chat_report", "chat_report_receipt")) {
      assertThatThrownBy(() -> runtime.execute("TRUNCATE " + table))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
      assertThatThrownBy(() -> runtime.execute("DELETE FROM " + table))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
      assertThatThrownBy(
              () ->
                  runtime.execute(
                      "UPDATE " + table + " SET reporter_identity_id=reporter_identity_id"))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    assertThatThrownBy(
            () ->
                runtime.update(
                    "INSERT INTO chat_report(reporter_identity_id,message_id,reason) VALUES (?,?,'OTHER')",
                    author,
                    message(global, "bad")))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }

  private HttpResponse<String> uncheckedReport(Session actor, UUID message, UUID key) {
    try {
      return report(actor, message, key, "SPAM");
    } catch (Exception error) {
      throw new RuntimeException(error);
    }
  }
}

abstract class ChatCommunityFixture extends ChatPushFixture {
  final ObjectMapper json = new ObjectMapper();

  void awaitAccountLock() throws Exception {
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      if (Boolean.TRUE.equals(
          jdbc.queryForObject(
              "SELECT EXISTS(SELECT FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE '%chat_mute_account%')",
              Boolean.class))) return;
      Thread.sleep(10);
    }
    throw new AssertionError("No real PostgreSQL account lock wait observed");
  }

  UUID participant(String subject) {
    UUID id =
        jdbc.queryForObject(
            "INSERT INTO local_identity(issuer,subject) VALUES ('https://idp.invalid',?) RETURNING identity_id",
            UUID.class,
            subject);
    jdbc.update("INSERT INTO betufu_profile(identity_id,nickname) VALUES (?,?)", id, subject);
    return id;
  }

  HttpResponse<String> report(Session actor, UUID message, UUID key, String reason)
      throws Exception {
    return command(
        actor,
        "POST",
        "/messages/" + message + "/reports",
        "{\"reason\":\"" + reason + "\"}",
        key.toString(),
        true,
        ORIGIN);
  }

  HttpResponse<String> command(
      Session actor,
      String method,
      String path,
      String body,
      String key,
      boolean csrf,
      String origin)
      throws Exception {
    String cookie = "XSRF-TOKEN=" + TOKEN;
    if (actor != null)
      cookie +=
          "; SESSION="
              + Base64.getEncoder().encodeToString(actor.getId().getBytes(StandardCharsets.UTF_8));
    var builder =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/chat" + path))
            .header("Cookie", cookie)
            .header("Origin", origin)
            .header("Content-Type", "application/json");
    if (csrf) builder.header("X-XSRF-TOKEN", TOKEN);
    if (key != null)
      builder.header(
          method.equals("DELETE") ? "If-Match" : "Idempotency-Key",
          method.equals("DELETE") ? "\"" + key + "\"" : key);
    builder.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
    try (HttpClient client = HttpClient.newHttpClient()) {
      return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
  }

  JdbcTemplate runtime() throws Exception {
    jdbc.execute(
        "DO $$ BEGIN IF NOT EXISTS(SELECT FROM pg_roles WHERE rolname='chat_community_runtime') THEN CREATE ROLE chat_community_runtime LOGIN PASSWORD 'community-test-only'; END IF; END $$");
    jdbc.execute("GRANT USAGE ON SCHEMA public TO chat_community_runtime");
    String migration;
    try (var resource =
        getClass().getResourceAsStream("/db/migration/V42__chat_reports_and_mutes.sql")) {
      migration = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
    }
    try (var connection =
            java.sql.DriverManager.getConnection(
                POSTGRESQL.getJdbcUrl(), POSTGRESQL.getUsername(), POSTGRESQL.getPassword());
        var sql = connection.createStatement()) {
      sql.execute("SET betufu.runtime_role='chat_community_runtime'");
      sql.execute(migration.substring(migration.indexOf("DO $$")));
    }
    return new JdbcTemplate(
        new DriverManagerDataSource(
            POSTGRESQL.getJdbcUrl(), "chat_community_runtime", "community-test-only"));
  }
}
