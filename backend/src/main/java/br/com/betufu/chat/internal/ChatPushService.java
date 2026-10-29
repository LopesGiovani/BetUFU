package br.com.betufu.chat.internal;

import br.com.betufu.platform.operations.EmergencyPausePort;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Producers call this within their transaction; rolled-back facts never invalidate a reader. */
@Service
@Profile("runtime | integrated")
public class ChatPushService {
  private final CopyOnWriteArrayList<Consumer<Change>> listeners = new CopyOnWriteArrayList<>();

  public void changed(UUID eventId) {
    afterCommit(new Change(eventId, null, null, null));
  }

  public void changedForAccount(String issuer, String subject) {
    if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank())
      throw new IllegalArgumentException("Account change requires an issuer and subject");
    afterCommit(new Change(null, issuer, subject, null));
  }

  /** Same minimal resync hint, confined to the account; no preference list goes on the wire. */
  public void preferencesChanged(String issuer, String subject) {
    changedForAccount(issuer, subject);
  }

  @EventListener
  public void availabilityChanged(EmergencyPausePort.Changed change) {
    if (change.kind() == EmergencyPausePort.Kind.ACCOUNT) {
      afterCommit(new Change(null, null, null, change.targetId()));
    } else {
      changed(
          change.kind() == EmergencyPausePort.Kind.EVENT
              ? change.targetId()
              : change.relatedEventId());
    }
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

  record Change(UUID eventId, String issuer, String subject, UUID accountId) {}
}
