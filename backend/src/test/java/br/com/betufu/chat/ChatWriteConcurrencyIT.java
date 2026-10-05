package br.com.betufu.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class ChatWriteConcurrencyIT extends ChatWriteFixture {
  @Test
  void enrollmentReleaseOrSessionDeletionWhileWaitingForLockRefusesWrite() throws Exception {
    for (boolean revokeSession : new boolean[] {true, false}) {
      var account = session("reader");
      try (var pool = Executors.newSingleThreadExecutor()) {
        var result =
            new java.util.concurrent.atomic.AtomicReference<
                java.util.concurrent.Future<java.net.http.HttpResponse<String>>>();
        new org.springframework.transaction.support.TransactionTemplate(transactions)
            .executeWithoutResult(
                status -> {
                  jdbc.queryForList(
                      "SELECT enrollment_id FROM ufu_enrollment WHERE identity_id=? AND status='ATIVO' FOR UPDATE",
                      author);
                  result.set(
                      pool.submit(() -> post(account, null, UUID.randomUUID(), "must refuse")));
                  long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                  boolean blocked = false;
                  while (System.nanoTime() < deadline) {
                    jdbc.execute("SELECT pg_stat_clear_snapshot()");
                    blocked =
                        Boolean.TRUE.equals(
                            jdbc.queryForObject(
                                "SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE '%ufu_enrollment%')",
                                Boolean.class));
                    if (blocked) break;
                    try {
                      Thread.sleep(20);
                    } catch (InterruptedException interrupted) {
                      throw new IllegalStateException(interrupted);
                    }
                  }
                  assertThat(blocked).isTrue();
                  if (revokeSession) sessions.deleteById(account.getId());
                  else
                    jdbc.update(
                        "UPDATE ufu_enrollment SET status='LIBERADO' WHERE identity_id=?", author);
                });
        assertThat(result.get().get(10, TimeUnit.SECONDS).statusCode())
            .isEqualTo(revokeSession ? 401 : 403);
        if (revokeSession) assertThat(sessions.findById(account.getId())).isNull();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_write_receipt", Integer.class))
            .isZero();
      }
    }
  }

  @Test
  void simultaneousSameKeyProducesOneFactAndIdenticalAcknowledgements() throws Exception {
    var account = session("reader");
    UUID key = UUID.randomUUID();
    try (var pool = Executors.newFixedThreadPool(6)) {
      var start = new CountDownLatch(1);
      var calls = new ArrayList<java.util.concurrent.Future<java.net.http.HttpResponse<String>>>();
      for (int i = 0; i < 6; i++)
        calls.add(
            pool.submit(
                () -> {
                  start.await();
                  return post(account, null, key, "one fact");
                }));
      start.countDown();
      var bodies = new java.util.HashSet<String>();
      for (var call : calls) {
        var response = call.get(10, TimeUnit.SECONDS);
        assertThat(response.statusCode()).isEqualTo(201);
        bodies.add(response.body());
      }
      assertThat(bodies).hasSize(1);
      assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_message", Integer.class))
          .isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_write_receipt", Integer.class))
          .isEqualTo(1);
    }
  }

  @Test
  void concurrentDifferentChannelsAndKeysShareOneActorLimiter() throws Exception {
    var account = session("reader");
    var secondDevice = session("reader");
    UUID event = event(true);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var start = new CountDownLatch(1);
      var a =
          pool.submit(
              () -> {
                start.await();
                return post(account, null, UUID.randomUUID(), "global");
              });
      var b =
          pool.submit(
              () -> {
                start.await();
                return post(secondDevice, event, UUID.randomUUID(), "event");
              });
      start.countDown();
      assertThat(
              java.util.List.of(
                  a.get(10, TimeUnit.SECONDS).statusCode(),
                  b.get(10, TimeUnit.SECONDS).statusCode()))
          .containsExactlyInAnyOrder(201, 429);
      assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_message", Integer.class))
          .isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_write_receipt", Integer.class))
          .isEqualTo(1);
    }
  }
}
