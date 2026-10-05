package br.com.betufu.chat;

import java.time.Instant;
import java.util.UUID;

/** Future lifecycle/sanction/pause producers implement this transaction-bound write check. */
public interface ChatWriteGuard {
  void requireAllowed(UUID actorId, UUID channelId, UUID eventId, Instant now);
}
