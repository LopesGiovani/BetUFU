package br.com.betufu.odds.internal.model;

import br.com.betufu.sports.ConfirmedSportsResultForOdds;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable, clock-free input for one deterministic odds calculation. */
public record OddsPricingInput(
    UUID eventId,
    UUID marketVersionId,
    UUID homeTeamId,
    UUID awayTeamId,
    List<ConfirmedSportsResultForOdds> historicalResults,
    boolean regulationAllowsDraw,
    BigDecimal margin) {

  public OddsPricingInput {
    require(eventId, "eventId");
    require(marketVersionId, "marketVersionId");
    require(homeTeamId, "homeTeamId");
    require(awayTeamId, "awayTeamId");
    if (homeTeamId.equals(awayTeamId)) {
      throw new IllegalArgumentException("homeTeamId and awayTeamId must differ");
    }
    historicalResults = historicalResults == null ? List.of() : List.copyOf(historicalResults);
    margin = margin == null ? OddsPricingKernel.DEFAULT_MARGIN : margin;
    if (margin.signum() < 0 || margin.compareTo(BigDecimal.ONE) >= 0) {
      throw new IllegalArgumentException("margin must be in [0, 1)");
    }
  }

  public OddsPricingInput(
      UUID eventId,
      UUID marketVersionId,
      UUID homeTeamId,
      UUID awayTeamId,
      List<ConfirmedSportsResultForOdds> historicalResults,
      boolean regulationAllowsDraw) {
    this(
        eventId,
        marketVersionId,
        homeTeamId,
        awayTeamId,
        historicalResults,
        regulationAllowsDraw,
        OddsPricingKernel.DEFAULT_MARGIN);
  }

  private static void require(Object value, String name) {
    Objects.requireNonNull(value, name + " must not be null");
  }
}
