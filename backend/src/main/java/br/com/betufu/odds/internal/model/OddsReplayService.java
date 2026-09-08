package br.com.betufu.odds.internal.model;

import br.com.betufu.sports.ConfirmedSportsResultForOdds;
import br.com.betufu.sports.SportsEventForOdds;
import br.com.betufu.sports.SportsResultForOddsPort;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Replays and reproduces deterministic odds calculations as of any historical timestamp.
 *
 * <p>Preserves previous calculations without mutating or deleting prior facts when corrections or
 * administrative walkovers occur in the event stream.
 */
public final class OddsReplayService {

  private final OddsInputFilter filter;
  private final OddsPricingKernel kernel;

  public OddsReplayService() {
    this(new OddsInputFilter(), new OddsPricingKernel());
  }

  public OddsReplayService(OddsInputFilter filter, OddsPricingKernel kernel) {
    this.filter = Objects.requireNonNull(filter, "filter must not be null");
    this.kernel = Objects.requireNonNull(kernel, "kernel must not be null");
  }

  /** Replays calculation from an existing OddsPricingInput filtered as of the specified cutoff. */
  public OddsPricingOutput replay(OddsPricingInput input, Instant asOfCutoff) {
    Objects.requireNonNull(input, "input must not be null");
    Objects.requireNonNull(asOfCutoff, "asOfCutoff must not be null");
    List<ConfirmedSportsResultForOdds> filtered =
        filter.filterAdmissibleResults(input.historicalResults(), asOfCutoff);
    OddsPricingInput filteredInput =
        new OddsPricingInput(
            input.eventId(),
            input.marketVersionId(),
            input.homeTeamId(),
            input.awayTeamId(),
            filtered,
            input.regulationAllowsDraw(),
            input.margin());
    return kernel.calculate(filteredInput);
  }

  /** Replays calculation from raw historical facts with custom margin. */
  public OddsPricingOutput replay(
      UUID eventId,
      UUID marketVersionId,
      UUID homeTeamId,
      UUID awayTeamId,
      List<ConfirmedSportsResultForOdds> allHistoricalFacts,
      Instant asOfCutoff,
      Instant scheduledAt,
      boolean regulationAllowsDraw,
      BigDecimal margin) {
    List<ConfirmedSportsResultForOdds> filtered =
        filter.filterAdmissibleResults(allHistoricalFacts, asOfCutoff, scheduledAt);
    OddsPricingInput input =
        new OddsPricingInput(
            eventId,
            marketVersionId,
            homeTeamId,
            awayTeamId,
            filtered,
            regulationAllowsDraw,
            margin);
    return kernel.calculate(input);
  }

  /** Replays calculation from raw historical facts with default margin. */
  public OddsPricingOutput replay(
      UUID eventId,
      UUID marketVersionId,
      UUID homeTeamId,
      UUID awayTeamId,
      List<ConfirmedSportsResultForOdds> allHistoricalFacts,
      Instant asOfCutoff,
      Instant scheduledAt,
      boolean regulationAllowsDraw) {
    return replay(
        eventId,
        marketVersionId,
        homeTeamId,
        awayTeamId,
        allHistoricalFacts,
        asOfCutoff,
        scheduledAt,
        regulationAllowsDraw,
        OddsPricingKernel.DEFAULT_MARGIN);
  }

  /** Replays calculation and returns a snapshot with full lineage metadata. */
  public ReplaySnapshot replayWithLineage(
      UUID eventId,
      UUID marketVersionId,
      UUID homeTeamId,
      UUID awayTeamId,
      List<ConfirmedSportsResultForOdds> allHistoricalFacts,
      Instant asOfCutoff,
      Instant scheduledAt,
      boolean regulationAllowsDraw,
      BigDecimal margin) {
    List<ConfirmedSportsResultForOdds> filtered =
        filter.filterAdmissibleResults(allHistoricalFacts, asOfCutoff, scheduledAt);
    OddsPricingInput input =
        new OddsPricingInput(
            eventId,
            marketVersionId,
            homeTeamId,
            awayTeamId,
            filtered,
            regulationAllowsDraw,
            margin);
    OddsPricingOutput output = kernel.calculate(input);
    return new ReplaySnapshot(
        output,
        asOfCutoff,
        latestConfirmedVersion(filtered),
        latestKnownAt(filtered),
        filtered.size(),
        filtered);
  }

  /** Replays calculation resolving the target event and facts via the public sports port. */
  public OddsPricingOutput replayFromPort(
      SportsResultForOddsPort sports,
      UUID eventId,
      UUID marketVersionId,
      boolean regulationAllowsDraw,
      Instant asOfCutoff,
      BigDecimal margin) {
    Objects.requireNonNull(sports, "sports must not be null");
    Objects.requireNonNull(eventId, "eventId must not be null");
    Objects.requireNonNull(marketVersionId, "marketVersionId must not be null");
    Objects.requireNonNull(asOfCutoff, "asOfCutoff must not be null");
    SportsEventForOdds event =
        sports
            .getEventForOdds(eventId)
            .orElseThrow(
                () -> new IllegalArgumentException("event not found for odds: " + eventId));
    List<ConfirmedSportsResultForOdds> rawHistory =
        sports.findAdmissibleResultsAsOf(event.competitionId(), event.editionId(), asOfCutoff);
    List<ConfirmedSportsResultForOdds> filtered =
        filter.filterAdmissibleResults(rawHistory, asOfCutoff, event.scheduledAt());
    OddsPricingInput input =
        new OddsPricingInput(
            event.eventId(),
            marketVersionId,
            event.slots().get(0).teamId(),
            event.slots().get(1).teamId(),
            filtered,
            regulationAllowsDraw,
            margin);
    return kernel.calculate(input);
  }

  /** Replays calculation resolving via the public sports port with default margin. */
  public OddsPricingOutput replayFromPort(
      SportsResultForOddsPort sports,
      UUID eventId,
      UUID marketVersionId,
      boolean regulationAllowsDraw,
      Instant asOfCutoff) {
    return replayFromPort(
        sports,
        eventId,
        marketVersionId,
        regulationAllowsDraw,
        asOfCutoff,
        OddsPricingKernel.DEFAULT_MARGIN);
  }

  /** Identifies the latest confirmed version ID in the given historical results. */
  public static UUID latestConfirmedVersion(List<ConfirmedSportsResultForOdds> results) {
    if (results == null || results.isEmpty()) {
      return null;
    }
    return results.stream()
        .filter(result -> result.resultKind().feedsRating())
        .max(
            Comparator.comparing(ConfirmedSportsResultForOdds::knownAt)
                .thenComparing(ConfirmedSportsResultForOdds::versionNo)
                .thenComparing(ConfirmedSportsResultForOdds::versionId))
        .map(ConfirmedSportsResultForOdds::versionId)
        .orElse(null);
  }

  /** Identifies the latest knownAt instant in the given historical results. */
  public static Instant latestKnownAt(List<ConfirmedSportsResultForOdds> results) {
    if (results == null || results.isEmpty()) {
      return null;
    }
    return results.stream()
        .map(ConfirmedSportsResultForOdds::knownAt)
        .max(Instant::compareTo)
        .orElse(null);
  }

  /** Immutable snapshot of a replayed odds calculation with lineage metadata. */
  public record ReplaySnapshot(
      OddsPricingOutput output,
      Instant asOfCutoff,
      UUID latestConfirmedVersionId,
      Instant latestKnownAt,
      int admissibleResultsCount,
      List<ConfirmedSportsResultForOdds> filteredHistory) {

    public ReplaySnapshot {
      Objects.requireNonNull(output, "output must not be null");
      Objects.requireNonNull(asOfCutoff, "asOfCutoff must not be null");
      filteredHistory = filteredHistory == null ? List.of() : List.copyOf(filteredHistory);
    }
  }
}
