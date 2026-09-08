package br.com.betufu.odds.internal.persistence;

import java.time.Instant;
import java.util.UUID;

/** Maker-checker decision fact. Proposer and approver must be different identities. */
public record OddsOverrideRecord(
    UUID overrideId,
    UUID marketId,
    UUID calculationId,
    UUID proposerId,
    UUID approverId,
    String reason,
    String decision,
    Instant createdAt) {}
