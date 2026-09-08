package br.com.betufu.odds.internal.persistence;

import java.time.Instant;
import java.util.UUID;

/** Append-only state transition for an odds market. */
public record OddsLifecycleRecord(
    UUID lifecycleFactId,
    UUID marketId,
    String fromState,
    String toState,
    String reason,
    UUID actorId,
    Instant occurredAt) {}
