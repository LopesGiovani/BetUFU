package br.com.betufu.chat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Consumer-owned view of settlement completion; pending work never counts as complete. */
public interface ChatSettlementPort {
  record Completion(UUID eventId, UUID outcomeFactId, Instant completedAt, boolean complete) {}

  Optional<Completion> findCompletion(UUID eventId, UUID outcomeFactId);

  /** Ledger-owned adapter inspects only recovery cases linked to these replacement units. */
  interface RecoveryPort {
    Recovery inspectRecoveries(List<UUID> replacementSettlementIds);
  }

  record Recovery(boolean pending, Instant completedAt) {}
}
