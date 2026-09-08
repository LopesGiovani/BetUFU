package br.com.betufu.odds.internal.override;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Verifiable correction attempt backed by an immutable odds calculation. */
public record OddsCorrectionAttempt(
    UUID calculationId,
    UUID marketVersionId,
    BigDecimal previousOdds,
    BigDecimal proposedOdds,
    String evidenceReference,
    Instant recordedAt) {

  public OddsCorrectionAttempt {
    Objects.requireNonNull(calculationId, "calculationId must not be null");
    Objects.requireNonNull(marketVersionId, "marketVersionId must not be null");
    Objects.requireNonNull(previousOdds, "previousOdds must not be null");
    Objects.requireNonNull(proposedOdds, "proposedOdds must not be null");
    if (previousOdds.signum() <= 0 || proposedOdds.signum() <= 0) {
      throw new IllegalArgumentException("odds must be positive");
    }
    if (evidenceReference == null || evidenceReference.isBlank()) {
      throw new IllegalArgumentException("evidenceReference must not be blank");
    }
    Objects.requireNonNull(recordedAt, "recordedAt must not be null");
  }

  public BigDecimal delta() {
    return proposedOdds.subtract(previousOdds);
  }
}
