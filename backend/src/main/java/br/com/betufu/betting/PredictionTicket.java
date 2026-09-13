package br.com.betufu.betting;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Immutable public projection of the user's accepted prediction. */
public record PredictionTicket(
    UUID predictionId,
    UUID marketId,
    UUID marketVersionId,
    UUID publicationId,
    String selection,
    BigDecimal stake,
    BigDecimal acceptedOdds,
    BigDecimal potentialReturn,
    BigDecimal netProfit,
    String ruleVersion,
    Instant closeDeadline,
    String state,
    Instant acceptedAt) {}
