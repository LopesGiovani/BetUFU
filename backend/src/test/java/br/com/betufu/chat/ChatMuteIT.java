package br.com.betufu.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.chat.internal.ChatMuteService;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.session.Session;
import org.springframework.transaction.support.TransactionTemplate;

class ChatMuteIT extends ChatCommunityFixture {
  @Autowired ChatMuteService mutes;

  @Test
  void rollbackDoesNotPersistThePreferenceOrNotifyAnotherDevice() throws Exception {
    UUID target = participant("target");
    Session actor = session("reader");
    try (Wire wire = connected(actor)) {
      wire.subscribe("/chat/global", "0");
      wire.message();
      new TransactionTemplate(transactions)
          .executeWithoutResult(
              status -> {
                mutes.change("https://idp.invalid", "reader", actor.getId(), target, 0, true);
                status.setRollbackOnly();
              });
      assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_mute", Integer.class)).isZero();
      assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_mute_account", Integer.class))
          .isZero();
      assertThat(wire.frames.poll(200, TimeUnit.MILLISECONDS)).isNull();
      assertThat(mute(actor, target, 0).statusCode()).isEqualTo(200);
      assertThat(wire.message()).contains("resyncRequired");
    }
  }

  @Test
  void revocationWhileWaitingForAccountLockDoesNotChangePreferences() throws Exception {
    UUID target = participant("target");
    Session actor = session("reader");
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
      var pending = CompletableFuture.supplyAsync(() -> uncheckedMute(actor, target));
      awaitAccountLock();
      sessions.deleteById(actor.getId());
      connection.commit();
      assertThat(pending.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(401);
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_mute", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT version FROM chat_mute_account", Long.class)).isZero();
  }

  @Test
  void accountWidePreferencePersistsAcrossDevicesWithoutChangingMessages() throws Exception {
    UUID target = participant("target");
    Session first = session("reader"), second = session("reader");
    var initial = command(first, "GET", "/me/mutes", null, null, true, ORIGIN);
    assertThat(initial.statusCode()).isEqualTo(200);
    assertThat(json.readTree(initial.body()).get("version").asLong()).isZero();
    var saved = mute(first, target, 0);
    assertThat(saved.statusCode()).isEqualTo(200);
    assertThat(saved.headers().firstValue("ETag")).contains("\"1\"");
    assertThat(saved.headers().firstValue("Cache-Control")).contains("private, no-store");
    assertThat(json.readTree(saved.body()).size()).isEqualTo(2);
    assertThat(saved.body())
        .contains(target.toString())
        .doesNotContain("reader", "target", author.toString());
    assertThat(command(second, "GET", "/me/mutes/" + target, null, null, true, ORIGIN).body())
        .isEqualTo(saved.body());
    assertThat(mute(second, target, 1).body()).isEqualTo(saved.body());
    assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_mute", Integer.class)).isEqualTo(1);
    var removed = command(second, "DELETE", "/me/mutes/" + target, null, "1", true, ORIGIN);
    assertThat(removed.statusCode()).isEqualTo(200);
    assertThat(json.readTree(removed.body()).get("version").asLong()).isEqualTo(2);
    assertThat(command(first, "GET", "/me/mutes", null, null, true, ORIGIN).body())
        .isEqualTo(removed.body());
  }

  @Test
  void concurrentDevicesRejectStaleVersionWithoutOverwritingTheWinner() throws Exception {
    UUID targetA = participant("targetA"), targetB = participant("targetB");
    Session first = session("reader"), second = session("reader");
    var a = CompletableFuture.supplyAsync(() -> uncheckedMute(first, targetA));
    var b = CompletableFuture.supplyAsync(() -> uncheckedMute(second, targetB));
    assertThat(java.util.List.of(a.get().statusCode(), b.get().statusCode()))
        .containsExactlyInAnyOrder(200, 409);
    assertThat(
            jdbc.queryForObject(
                "SELECT version FROM chat_mute_account WHERE owner_identity_id=?",
                Long.class,
                author))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_mute", Integer.class)).isEqualTo(1);
    assertThat(
            command(first, "DELETE", "/me/mutes/" + targetA, null, "0", true, ORIGIN).statusCode())
        .isEqualTo(409);
  }

  @Test
  void ownershipSessionValidationAndPublicParticipantBoundaryAreEnforced() throws Exception {
    UUID target = participant("target");
    Session owner = session("reader"), other = session("target");
    assertThat(mute(null, target, 0).statusCode()).isEqualTo(401);
    assertThat(command(owner, "GET", "/me/mutes/invalid", null, null, true, ORIGIN).statusCode())
        .isEqualTo(400);
    assertThat(mute(owner, UUID.randomUUID(), 0).statusCode()).isEqualTo(404);
    assertThat(mute(owner, author, 0).statusCode()).isEqualTo(400);
    assertThat(
            command(
                    owner,
                    "PUT",
                    "/me/mutes/" + target,
                    "{\"expectedVersion\":0,\"ownerId\":\"spoof\"}",
                    null,
                    true,
                    ORIGIN)
                .statusCode())
        .isEqualTo(400);
    for (String invalid : java.util.List.of("-1", "0.5", "\"0\"", "null"))
      assertThat(
              command(
                      owner,
                      "PUT",
                      "/me/mutes/" + target,
                      "{\"expectedVersion\":" + invalid + "}",
                      null,
                      true,
                      ORIGIN)
                  .statusCode())
          .isEqualTo(400);
    assertThat(
            command(
                    owner,
                    "PUT",
                    "/me/mutes/" + target,
                    "{\"expectedVersion\":0}",
                    null,
                    false,
                    ORIGIN)
                .statusCode())
        .isEqualTo(403);
    assertThat(
            command(
                    owner,
                    "PUT",
                    "/me/mutes/" + target,
                    "{\"expectedVersion\":0}",
                    null,
                    true,
                    "https://evil.invalid")
                .statusCode())
        .isEqualTo(403);
    assertThat(mute(owner, target, 0).statusCode()).isEqualTo(200);
    assertThat(command(other, "GET", "/me/mutes", null, null, true, ORIGIN).body())
        .doesNotContain(target.toString());
    assertThat(
            command(other, "GET", "/accounts/" + author + "/mutes", null, null, true, ORIGIN)
                .statusCode())
        .isEqualTo(404);
    sessions.deleteById(owner.getId());
    assertThat(command(owner, "GET", "/me/mutes", null, null, true, ORIGIN).statusCode())
        .isEqualTo(401);
  }

  @Test
  void preferenceNotificationsReachOnlyOwnerSessionsAcrossChannels() throws Exception {
    UUID target = participant("target");
    Session first = session("reader"), second = session("reader");
    try (Wire a = connected(first);
        Wire b = connected(second);
        Wire other = connected(session("target"));
        Wire visitor = connected(null)) {
      a.subscribe("/chat/global", "0");
      a.message();
      b.subscribe("/chat/events/" + event(true), "0");
      b.message();
      other.subscribe("/chat/global", "0");
      other.message();
      visitor.subscribe("/chat/events/" + event(true), "0");
      visitor.message();
      assertThat(mute(first, target, 0).statusCode()).isEqualTo(200);
      assertThat(a.message())
          .contains("resyncRequired")
          .doesNotContain(target.toString(), "mute", "reader");
      assertThat(b.message())
          .contains("resyncRequired")
          .doesNotContain(target.toString(), "mute", "reader");
      assertThat(other.frames.poll(200, TimeUnit.MILLISECONDS)).isNull();
      assertThat(visitor.frames.poll(200, TimeUnit.MILLISECONDS)).isNull();
      assertThat(mute(first, target, 0).statusCode()).isEqualTo(409);
      assertThat(a.frames.poll(200, TimeUnit.MILLISECONDS)).isNull();
      assertThat(b.frames.poll(200, TimeUnit.MILLISECONDS)).isNull();
    }
  }

  @Test
  void runtimeCanEditOnlyThePreferenceProjectionAndForeignKeysRejectSpoofing() throws Exception {
    UUID target = participant("target");
    var runtime = runtime();
    runtime.update("INSERT INTO chat_mute_account(owner_identity_id) VALUES (?)", author);
    runtime.update(
        "INSERT INTO chat_mute(owner_identity_id,participant_id) VALUES (?,?)", author, target);
    runtime.update(
        "UPDATE chat_mute_account SET version=version+1 WHERE owner_identity_id=?", author);
    assertThatThrownBy(
            () ->
                runtime.update(
                    "INSERT INTO chat_mute(owner_identity_id,participant_id) VALUES (?,?)",
                    author,
                    author))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThatThrownBy(
            () ->
                runtime.update(
                    "INSERT INTO chat_mute(owner_identity_id,participant_id) VALUES (?,?)",
                    author,
                    UUID.randomUUID()))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
    assertThat(runtime.update("DELETE FROM chat_mute WHERE owner_identity_id=?", author))
        .isEqualTo(1);
  }

  private java.net.http.HttpResponse<String> mute(Session actor, UUID target, long version)
      throws Exception {
    return command(
        actor,
        "PUT",
        "/me/mutes/" + target,
        "{\"expectedVersion\":" + version + "}",
        null,
        true,
        ORIGIN);
  }

  private java.net.http.HttpResponse<String> uncheckedMute(Session actor, UUID target) {
    try {
      return mute(actor, target, 0);
    } catch (Exception error) {
      throw new RuntimeException(error);
    }
  }
}
