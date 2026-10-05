package br.com.betufu.chat;

import java.util.Optional;
import java.util.UUID;

/** Consumer-owned identity resolution and transaction-bound enrollment authorization. */
public interface ChatEligibilityPort {
  Optional<UUID> findActor(String issuer, String subject);

  /** Locks the producer's active enrollment through the caller's transaction. */
  boolean lockActiveEnrollment(UUID actorId);
}
