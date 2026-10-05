package br.com.betufu.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

class ChatPushDeliveryIT extends ChatPushFixture {
  @Test
  void boundedDeliveryQueueClosesSubscriberDuringABurst() throws Exception {
    UUID event = event(true);
    try (Wire wire = connected(null)) {
      wire.subscribe("/chat/events/" + event, "0");
      wire.message();
      new TransactionTemplate(transactions)
          .executeWithoutResult(
              s -> {
                for (int index = 0; index < 4000; index++) push.changed(event);
              });
      assertThat(wire.closed.get(5, TimeUnit.SECONDS)).isEqualTo(4500);
    }
  }

  @Test
  void reversedCommitOrderNeverSuppressesAnOlderAllocatedSequence() throws Exception {
    UUID event = event(true), channel = channel(event);
    var allocated = new java.util.concurrent.CountDownLatch(1);
    var release = new java.util.concurrent.CountDownLatch(1);
    try (Wire wire = connected(null);
        var worker = java.util.concurrent.Executors.newSingleThreadExecutor()) {
      wire.subscribe("/chat/events/" + event, "0");
      wire.message();
      var earlier =
          worker.submit(
              () ->
                  new TransactionTemplate(transactions)
                      .executeWithoutResult(
                          s -> {
                            message(channel, "allocated-first");
                            push.changed(event);
                            allocated.countDown();
                            try {
                              if (!release.await(5, TimeUnit.SECONDS))
                                throw new IllegalStateException("fixture timeout");
                            } catch (InterruptedException interrupted) {
                              throw new IllegalStateException(interrupted);
                            }
                          }));
      assertThat(allocated.await(5, TimeUnit.SECONDS)).isTrue();
      try {
        new TransactionTemplate(transactions)
            .executeWithoutResult(
                s -> {
                  message(channel, "committed-first");
                  push.changed(event);
                });
        String higher = wire.message();
        assertThat(higher).contains("resyncRequired");
        release.countDown();
        earlier.get(5, TimeUnit.SECONDS);
        assertThat(wire.message()).contains("resyncRequired");
        assertThat(
                jdbc.queryForObject(
                    "SELECT count(*) FROM chat_message WHERE channel_id=?", Integer.class, channel))
            .isEqualTo(2);
      } finally {
        release.countDown();
      }
    }
  }

  @Test
  void messageInvalidationIsDeliveredOnlyAfterCommitAndRollbackIsSilent() throws Exception {
    UUID event = event(true), channel = channel(event);
    try (Wire wire = connected(null)) {
      wire.subscribe("/chat/events/" + event, "0");
      wire.message();
      new TransactionTemplate(transactions)
          .executeWithoutResult(
              s -> {
                message(channel, "committed-original");
                push.changed(event);
                try {
                  assertThat(wire.frames.poll(200, TimeUnit.MILLISECONDS)).isNull();
                } catch (InterruptedException e) {
                  throw new IllegalStateException(e);
                }
              });
      assertThat(wire.message()).contains("resyncRequired").doesNotContain("committed-original");
      new TransactionTemplate(transactions)
          .executeWithoutResult(
              s -> {
                message(channel, "rolled-back");
                push.changed(event);
                s.setRollbackOnly();
              });
      assertThat(wire.frames.poll(300, TimeUnit.MILLISECONDS)).isNull();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM chat_message WHERE body='rolled-back'", Integer.class))
          .isZero();
    }
  }

  @Test
  void oldRemovedMessageInvalidatesEvenWhenSequenceDidNotAdvance() throws Exception {
    UUID event = event(true), channel = channel(event), old = message(channel, "private-original");
    long sequence =
        jdbc.queryForObject(
            "SELECT change_cursor FROM chat_message WHERE message_id=?", Long.class, old);
    try (Wire wire = connected(null)) {
      wire.subscribe("/chat/events/" + event, Long.toString(sequence));
      wire.message();
      new TransactionTemplate(transactions)
          .executeWithoutResult(
              s -> {
                jdbc.update(
                    "INSERT INTO chat_message_projection(message_id,state) VALUES (?, 'REMOVED')",
                    old);
                push.changed(event);
              });
      assertThat(wire.message())
          .contains("resyncRequired")
          .doesNotContain("private-original", author.toString());
      assertThat(
              jdbc.queryForObject(
                  "SELECT max(change_cursor) FROM chat_message WHERE channel_id=?",
                  Long.class,
                  channel))
          .isEqualTo(sequence);
    }
  }

  @Test
  void reconnectRequiresFullResyncEvenWithClientAheadOfSequence() throws Exception {
    UUID event = event(true);
    try (Wire wire = connected(null)) {
      wire.subscribe("/chat/events/" + event, Long.toString(Long.MAX_VALUE));
      assertThat(wire.message()).contains("resyncRequired", "true");
    }
  }

  @Test
  void channelsAndAccountChangesAreIsolated() throws Exception {
    UUID event = event(true);
    try (Wire publicWire = connected(null);
        Wire reader = connected(session("reader"));
        Wire other = connected(session("other"))) {
      publicWire.subscribe("/chat/events/" + event, "0");
      publicWire.message();
      reader.subscribe("/chat/global", "0");
      reader.message();
      other.subscribe("/chat/global", "0");
      other.message();
      new TransactionTemplate(transactions)
          .executeWithoutResult(s -> push.changedForAccount("https://idp.invalid", "reader"));
      assertThat(reader.message()).contains("resyncRequired");
      assertThat(other.frames.poll(200, TimeUnit.MILLISECONDS)).isNull();
      assertThat(publicWire.frames.poll(200, TimeUnit.MILLISECONDS)).isNull();
      new TransactionTemplate(transactions).executeWithoutResult(s -> push.changed(event));
      publicWire.message();
      assertThat(reader.frames.poll(200, TimeUnit.MILLISECONDS)).isNull();
    }
  }
}
