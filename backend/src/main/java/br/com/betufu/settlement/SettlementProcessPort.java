package br.com.betufu.settlement;

import java.util.UUID;

/** Commands for audited authorization and resumable settlement processing. */
public interface SettlementProcessPort {
  UUID authorize(UUID eventId, UUID administratorIdentityId, String idempotencyKey);

  UUID authorize(
      UUID eventId,
      UUID expectedOutcomeFactId,
      UUID expectedConfirmedVersionId,
      UUID administratorIdentityId,
      String idempotencyKey);

  UUID process(UUID authorizationId);
}
