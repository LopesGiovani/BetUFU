package br.com.betufu.odds.internal.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Immutable published price; a correction is represented by a new publication row. */
public record OddsPublicationRecord(
    UUID publicationId,
    UUID calculationId,
    BigDecimal margin,
    String roundingMode,
    BigDecimal publishedOdds,
    UUID supersedesPublicationId,
    long baseLockVersion,
    Instant publishedAt) {}
