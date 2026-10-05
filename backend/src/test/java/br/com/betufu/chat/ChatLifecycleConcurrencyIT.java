package br.com.betufu.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

class ChatLifecycleConcurrencyIT extends ChatLifecycleFixture {
  @Test
  void concurrentExpiredHttpWritesCreateOneCloseAndNoMessages() throws Exception {
    UUID event = event(true);
    Instant at = clock.instant();
    result(event, at, 2, 1);
    clock.current = at.plusSeconds(7200);
    var first = session("reader");
    var second = session("reader");
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                gate.await();
                return post(first, event, UUID.randomUUID(), "one").statusCode();
              });
      var b =
          pool.submit(
              () -> {
                gate.await();
                return post(second, event, UUID.randomUUID(), "two").statusCode();
              });
      gate.countDown();
      assertThat(a.get(15, TimeUnit.SECONDS)).isEqualTo(409);
      assertThat(b.get(15, TimeUnit.SECONDS)).isEqualTo(409);
    }
    assertThat(count("chat_event_lifecycle_close")).isEqualTo(1);
    assertThat(count("chat_message")).isZero();
    assertThat(count("chat_write_receipt")).isZero();
  }

  @Test
  void clockIsRecheckedAfterActualDatabaseLockWait() throws Exception {
    blockedWrite(true);
  }

  @Test
  void eventLockWaitCannotBackdateAcceptanceAndBypassRateLimit() throws Exception {
    blockedWrite(false);
  }

  private void blockedWrite(boolean expired) throws Exception {
    UUID event = event(true);
    channel(event);
    Instant at = clock.instant();
    result(event, at, 2, 1);
    clock.current = at.plusSeconds(expired ? 7199 : 1);
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var account = session("reader");
    try (var pool = Executors.newFixedThreadPool(2)) {
      var holder =
          pool.submit(
              () ->
                  new TransactionTemplate(transactions)
                      .execute(
                          status -> {
                            jdbc.queryForObject(
                                "SELECT event_id FROM sports_event WHERE event_id=? FOR UPDATE",
                                UUID.class,
                                event);
                            locked.countDown();
                            try {
                              assertThat(release.await(15, TimeUnit.SECONDS)).isTrue();
                            } catch (InterruptedException e) {
                              throw new RuntimeException(e);
                            }
                            return null;
                          }));
      assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
      var sending =
          pool.submit(() -> post(account, event, UUID.randomUUID(), "waited").statusCode());
      long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      boolean waiting = false;
      while (System.nanoTime() < until) {
        jdbc.execute("SELECT pg_stat_clear_snapshot()");
        waiting =
            jdbc.queryForObject(
                "SELECT count(*)>0 FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE '%sports_event%'",
                Boolean.class);
        if (waiting) break;
        Thread.sleep(20);
      }
      assertThat(waiting).isTrue();
      clock.current = at.plusSeconds(expired ? 7200 : 10);
      release.countDown();
      holder.get(10, TimeUnit.SECONDS);
      assertThat(sending.get(10, TimeUnit.SECONDS)).isEqualTo(expired ? 409 : 201);
      if (!expired) {
        assertThat(post(account, event, UUID.randomUUID(), "too soon").statusCode()).isEqualTo(429);
        assertThat(
                jdbc.queryForObject("SELECT posted_at FROM chat_message", java.sql.Timestamp.class)
                    .toInstant())
            .isEqualTo(clock.instant());
      }
    } finally {
      release.countDown();
    }
    assertThat(count("chat_message")).isEqualTo(expired ? 0 : 1);
    assertThat(count("chat_event_lifecycle_close")).isEqualTo(expired ? 1 : 0);
  }
}
