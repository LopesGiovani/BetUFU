package br.com.betufu.chat.internal;

import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Producers call this within their transaction; rolled-back facts never invalidate a reader. */
@Service
@Profile("runtime | integrated")
public class ChatPushService {
  private final CopyOnWriteArrayList<Consumer<Change>> listeners = new CopyOnWriteArrayList<>();

  public void changed(UUID eventId) {
    afterCommit(new Change(eventId, null, null));
  }

  public void changedForAccount(String issuer, String subject) {
    if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank())
      throw new IllegalArgumentException("Account change requires an issuer and subject");
    afterCommit(new Change(null, issuer, subject));
  }

  void listen(Consumer<Change> listener) {
    listeners.add(listener);
  }

  private void afterCommit(Change change) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || !TransactionSynchronizationManager.isSynchronizationActive())
      throw new IllegalStateException("Chat notification requires a domain transaction");
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCommit() {
            listeners.forEach(listener -> listener.accept(change));
          }
        });
  }

  record Change(UUID eventId, String issuer, String subject) {}
}
