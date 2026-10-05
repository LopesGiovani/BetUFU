package br.com.betufu.chat;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.betufu.chat.internal.ChatModerationModels;
import br.com.betufu.chat.internal.ChatSanctionService;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class ChatSanctionConcurrencyIT extends ChatSanctionFixture {
  @Autowired ChatSanctionService sanctionsService;

  @Test
  void waitingWriteSeesCommittedSanctionAndRollbackLeavesNoPunishment() throws Exception {
    UUID occurrence = occurrence();
    CountDownLatch inserted = new CountDownLatch(1), finish = new CountDownLatch(1);
    var confirmation =
        CompletableFuture.runAsync(
            () ->
                new TransactionTemplate(transactions)
                    .executeWithoutResult(
                        tx -> {
                          sanctionsService.confirm(
                              "https://idp.invalid",
                              "sanction-admin",
                              adminSession.getId(),
                              occurrence,
                              new ChatModerationModels.SanctionCommand(
                                  UUID.randomUUID(),
                                  "confirmed violation",
                                  ChatModerationModels.SanctionKind.CHAT_SUSPENSION_24H));
                          inserted.countDown();
                          try {
                            if (!finish.await(10, TimeUnit.SECONDS))
                              throw new AssertionError("confirmation release timed out");
                          } catch (InterruptedException error) {
                            throw new RuntimeException(error);
                          }
                        }));
    assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();
    var writing =
        CompletableFuture.supplyAsync(
            () -> {
              try {
                return post(ownerSession, null, UUID.randomUUID(), "concurrent attempt")
                    .statusCode();
              } catch (Exception error) {
                throw new RuntimeException(error);
              }
            });
    try {
      awaitSanctionLock();
    } finally {
      finish.countDown();
    }
    confirmation.get(10, TimeUnit.SECONDS);
    assertThat(writing.get(10, TimeUnit.SECONDS)).isEqualTo(403);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_write_receipt", Integer.class))
        .isZero();
  }

  @Test
  void rolledBackConfirmationDoesNotCreateSanctionOrConsumeProgression() throws Exception {
    UUID occurrence = occurrence();
    new TransactionTemplate(transactions)
        .executeWithoutResult(
            tx -> {
              sanctionsService.confirm(
                  "https://idp.invalid",
                  "sanction-admin",
                  adminSession.getId(),
                  occurrence,
                  new ChatModerationModels.SanctionCommand(
                      UUID.randomUUID(),
                      "confirmed violation",
                      ChatModerationModels.SanctionKind.CHAT_SUSPENSION_24H));
              tx.setRollbackOnly();
            });
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chat_moderation_sanction_fact", Integer.class))
        .isZero();
    assertThat(
            post(ownerSession, null, UUID.randomUUID(), "rollback preserved writing").statusCode())
        .isEqualTo(201);
    assertThat(confirm(occurrence, "CHAT_SUSPENSION_24H", UUID.randomUUID()).statusCode())
        .isEqualTo(201);
  }

  @Test
  void waitingConfirmationDeniesDeletedSession() throws Exception {
    UUID occurrence = occurrence();
    jdbc.update(
        "INSERT INTO chat_write_limiter(author_identity_id) VALUES (?) ON CONFLICT DO NOTHING",
        author);
    try (var connection = POSTGRESQL.createConnection("")) {
      connection.setAutoCommit(false);
      try (var statement =
          connection.prepareStatement(
              "SELECT author_identity_id FROM chat_write_limiter WHERE author_identity_id=? FOR UPDATE")) {
        statement.setObject(1, author);
        statement.executeQuery();
      }
      var waiting =
          CompletableFuture.supplyAsync(() -> uncheckedConfirm(occurrence, "CHAT_SUSPENSION_24H"));
      awaitSanctionLock();
      sessions.deleteById(adminSession.getId());
      connection.commit();
      assertThat(waiting.get(10, TimeUnit.SECONDS)).isEqualTo(401);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chat_moderation_sanction_fact", Integer.class))
        .isZero();
  }

  @Test
  void simultaneousConfirmationsForOneOccurrenceProduceOnePunishment() throws Exception {
    UUID occurrence = occurrence();
    var first =
        CompletableFuture.supplyAsync(() -> uncheckedConfirm(occurrence, "CHAT_SUSPENSION_24H"));
    var second =
        CompletableFuture.supplyAsync(() -> uncheckedConfirm(occurrence, "CHAT_SUSPENSION_24H"));
    assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
        .containsExactlyInAnyOrder(201, 409);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chat_moderation_sanction_fact", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void distinctOccurrencesCannotRaceIntoTheSameProgressionStep() throws Exception {
    UUID a = occurrence(), b = occurrence();
    var first = CompletableFuture.supplyAsync(() -> uncheckedConfirm(a, "CHAT_SUSPENSION_24H"));
    var second = CompletableFuture.supplyAsync(() -> uncheckedConfirm(b, "CHAT_SUSPENSION_24H"));
    var results =
        java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
    assertThat(results).containsExactlyInAnyOrder(201, 409);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chat_moderation_sanction_fact", Integer.class))
        .isEqualTo(1);
  }

  @Test
  void waitingConfirmationRevalidatesRevokedAuthorityAndSession() throws Exception {
    UUID occurrence = occurrence();
    jdbc.update(
        "INSERT INTO chat_write_limiter(author_identity_id) VALUES (?) ON CONFLICT DO NOTHING",
        author);
    try (var connection = POSTGRESQL.createConnection("")) {
      connection.setAutoCommit(false);
      try (var statement =
          connection.prepareStatement(
              "SELECT author_identity_id FROM chat_write_limiter WHERE author_identity_id=? FOR UPDATE")) {
        statement.setObject(1, author);
        statement.executeQuery();
      }
      var waiting =
          CompletableFuture.supplyAsync(() -> uncheckedConfirm(occurrence, "CHAT_SUSPENSION_24H"));
      awaitSanctionLock();
      jdbc.update(
          "UPDATE operational_role_grant SET status='REVOGADO',revoked_at=CURRENT_TIMESTAMP WHERE identity_id=?",
          admin);
      connection.commit();
      assertThat(waiting.get(10, TimeUnit.SECONDS)).isEqualTo(403);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM chat_moderation_sanction_fact", Integer.class))
        .isZero();
  }

  private int uncheckedConfirm(UUID occurrence, String kind) {
    try {
      return confirm(occurrence, kind, UUID.randomUUID()).statusCode();
    } catch (Exception error) {
      throw new RuntimeException(error);
    }
  }

  private void awaitSanctionLock() throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      jdbc.execute("SELECT pg_stat_clear_snapshot()");
      if (Boolean.TRUE.equals(
          jdbc.queryForObject(
              "SELECT EXISTS(SELECT FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE '%chat_write_limiter%')",
              Boolean.class))) return;
      Thread.sleep(10);
    }
    throw new AssertionError("No real actor-row lock wait observed");
  }
}
