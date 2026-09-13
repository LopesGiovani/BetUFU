package br.com.betufu.odds.internal.persistence;

import java.math.BigDecimal;
import java.util.UUID;

/** One immutable outcome price belonging to a complete odds publication. */
public record OddsPublicationSelectionRecord(
    UUID publicationId, int selectionNo, String selectionKey, BigDecimal publishedOdds) {}
