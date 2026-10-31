package br.com.betufu.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

@Import(Phase7ConcurrencyIT.RuntimeTransactions.class)
class Phase7ConcurrencyIT extends ChatLifecycleFixture {
  private DataSource original;
  private DataSourceTransactionManager manager;

  @BeforeEach
  void ordinaryRuntimeConnection() {
    original = jdbc.getDataSource();
    manager = (DataSourceTransactionManager) transactions;
    jdbc.execute(
        "DO $$ BEGIN IF NOT EXISTS(SELECT FROM pg_roles WHERE rolname='phase7_runtime') THEN CREATE ROLE phase7_runtime LOGIN PASSWORD 'phase7-test-only' NOSUPERUSER NOCREATEDB NOCREATEROLE; END IF; END $$");
    jdbc.execute("GRANT USAGE ON SCHEMA public TO phase7_runtime");
    jdbc.execute(
        "GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO phase7_runtime");
    jdbc.execute("GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA public TO phase7_runtime");
    var source =
        new DriverManagerDataSource(POSTGRESQL.getJdbcUrl(), "phase7_runtime", "phase7-test-only");
    jdbc.setDataSource(source);
    manager.setDataSource(source);
    assertThat(jdbc.queryForObject("SELECT current_user", String.class))
        .isEqualTo("phase7_runtime");
    assertThat(
            jdbc.queryForObject(
                "SELECT rolsuper FROM pg_roles WHERE rolname=current_user", Boolean.class))
        .isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT tableowner=current_user FROM pg_tables WHERE schemaname='public' AND tablename='chat_message'",
                Boolean.class))
        .isFalse();
  }

  @AfterEach
  void restoreFixtureConnection() {
    if (original != null) {
      jdbc.setDataSource(original);
      if (manager != null) manager.setDataSource(original);
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class RuntimeTransactions {
    @Bean
    @Primary
    DataSourceTransactionManager transactionManager(DataSource source) {
      return new DataSourceTransactionManager(source);
    }
  }

  @Test
  void runtimeConcurrentReplayProducesOneReceiptAndSharedLimiter() throws Exception {
    var account = session("reader");
    UUID key = UUID.randomUUID();
    var start = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                start.await();
                return post(account, null, key, "final replay");
              });
      var b =
          pool.submit(
              () -> {
                start.await();
                return post(account, null, key, "final replay");
              });
      start.countDown();
      var first = a.get(15, TimeUnit.SECONDS);
      var second = b.get(15, TimeUnit.SECONDS);
      assertThat(List.of(first.statusCode(), second.statusCode())).containsOnly(201);
      assertThat(first.body()).isEqualTo(second.body());
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_message", Integer.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_write_receipt", Integer.class))
        .isEqualTo(1);
    assertThat(post(account, event(true), UUID.randomUUID(), "other channel").statusCode())
        .isEqualTo(429);
  }

  @Test
  void committedChannelPauseWinsAgainstWaitingRuntimeWrite() throws Exception {
    var account = session("reader");
    UUID admin =
        jdbc.queryForObject(
            "INSERT INTO local_identity(issuer,subject) VALUES ('https://idp.invalid','phase7-pause-admin') RETURNING identity_id",
            UUID.class);
    jdbc.update(
        "INSERT INTO emergency_pause_target(target_kind,target_id) VALUES ('CHANNEL',?)", global);
    try (var pool = Executors.newSingleThreadExecutor()) {
      var writing =
          new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<Integer>>();
      new TransactionTemplate(transactions)
          .executeWithoutResult(
              tx -> {
                jdbc.queryForList(
                    "SELECT * FROM emergency_pause_target WHERE target_kind='CHANNEL' AND target_id=? FOR UPDATE",
                    global);
                writing.set(
                    pool.submit(
                        () ->
                            post(account, null, UUID.randomUUID(), "waiting pause").statusCode()));
                long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                boolean blocked = false;
                while (System.nanoTime() < until) {
                  jdbc.execute("SELECT pg_stat_clear_snapshot()");
                  blocked =
                      Boolean.TRUE.equals(
                          jdbc.queryForObject(
                              "SELECT EXISTS(SELECT FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE '%emergency_pause_target%')",
                              Boolean.class));
                  if (blocked) break;
                  try {
                    Thread.sleep(10);
                  } catch (InterruptedException error) {
                    throw new IllegalStateException(error);
                  }
                }
                assertThat(blocked).isTrue();
                UUID fact =
                    jdbc.queryForObject(
                        "INSERT INTO emergency_pause_fact(target_kind,target_id,actor_identity_id,action,reason,client_command_id,recorded_at) VALUES ('CHANNEL',?,?,'PAUSE','isolated concurrency',?,now()) RETURNING pause_fact_id",
                        UUID.class,
                        global,
                        admin,
                        UUID.randomUUID());
                jdbc.update(
                    "UPDATE emergency_pause_target SET paused=true,current_pause_id=? WHERE target_kind='CHANNEL' AND target_id=?",
                    fact,
                    global);
              });
      assertThat(writing.get().get(15, TimeUnit.SECONDS)).isEqualTo(403);
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_message", Integer.class)).isZero();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_write_receipt", Integer.class))
        .isZero();
  }

  @Test
  void runtimeExpiredWritesCloseOnceAndNeverCreateAcceptedFacts() throws Exception {
    UUID event = event(true);
    var at = clock.instant();
    result(event, at, 2, 1);
    clock.current = at.plusSeconds(7200);
    var account = session("reader");
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(() -> post(account, event, UUID.randomUUID(), "expired one").statusCode());
      var b =
          pool.submit(() -> post(account, event, UUID.randomUUID(), "expired two").statusCode());
      assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS)))
          .containsOnly(409);
    }
    assertThat(
            jdbc.queryForObject("SELECT count(*) FROM chat_event_lifecycle_close", Integer.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM chat_write_receipt", Integer.class))
        .isZero();
  }
}
