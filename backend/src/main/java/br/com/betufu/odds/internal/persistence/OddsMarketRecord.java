package br.com.betufu.odds.internal.persistence;

import java.time.Instant;
import java.util.UUID;

/** Stable market identity and its projected current head. Historical versions remain separate. */
public record OddsMarketRecord(
    UUID marketId,
    UUID eventId,
    String marketType,
    UUID currentVersionId,
    long lockVersion,
    Instant createdAt,
    Instant updatedAt) {}
