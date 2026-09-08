package br.com.betufu.odds.internal.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Immutable model output with the exact input snapshot and sports result lineage. */
public record OddsCalculationRecord(
    UUID calculationId,
    UUID marketVersionId,
    String algorithmVersion,
    String configurationDigest,
    String inputSnapshot,
    UUID confirmedVersionId,
    BigDecimal fairProbability,
    BigDecimal confidence,
    Instant createdAt,
    Instant knownAt) {}
