package br.com.betufu.odds.internal.market;

import br.com.betufu.odds.internal.model.OddsPricingInput;
import br.com.betufu.odds.internal.model.OddsPricingKernel;
import br.com.betufu.odds.internal.model.OddsPricingOutput;
import br.com.betufu.odds.internal.persistence.OddsCalculationRecord;
import br.com.betufu.odds.internal.persistence.OddsMarketRepository;
import br.com.betufu.sports.ConfirmedSportsResultForOdds;
import br.com.betufu.sports.SportsEventForOdds;
import br.com.betufu.sports.SportsResultForOddsPort;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Orchestrates the public sports port, pure kernel and append-only odds calculation record. */
@Service
@Profile("runtime | integrated")
public class OddsCalculationApplicationService {

  private final SportsResultForOddsPort sports;
  private final OddsMarketRepository repository;
  private final OddsPricingKernel kernel;

  public OddsCalculationApplicationService(
      SportsResultForOddsPort sports, OddsMarketRepository repository) {
    this(sports, repository, new OddsPricingKernel());
  }

  OddsCalculationApplicationService(
      SportsResultForOddsPort sports, OddsMarketRepository repository, OddsPricingKernel kernel) {
    this.sports = Objects.requireNonNull(sports, "sports must not be null");
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
    this.kernel = Objects.requireNonNull(kernel, "kernel must not be null");
  }

  /** Calculates from an already assembled immutable input and persists one calculation fact. */
  public OddsCalculationRecord calculate(OddsPricingInput input) {
    OddsPricingOutput output = kernel.calculate(input);
    OddsCalculationRecord record =
        new OddsCalculationRecord(
            UUID.randomUUID(),
            input.marketVersionId(),
            output.algorithmVersion(),
            output.configurationDigest(),
            output.inputSnapshot(),
            latestConfirmedVersion(input.historicalResults()),
            output.fairProbability(),
            output.confidence(),
            null,
            latestKnownAt(input.historicalResults()));
    return repository.saveCalculation(record);
  }

  /**
   * Resolves the event and historical sports facts through the public boundary before calculating.
   */
  public OddsCalculationRecord calculate(
      UUID marketVersionId, UUID eventId, boolean regulationAllowsDraw, Instant asOfCutoff) {
    Objects.requireNonNull(marketVersionId, "marketVersionId must not be null");
    Objects.requireNonNull(eventId, "eventId must not be null");
    Objects.requireNonNull(asOfCutoff, "asOfCutoff must not be null");
    SportsEventForOdds event = sports.getEventForOdds(eventId).orElseThrow();
    List<ConfirmedSportsResultForOdds> history =
        sports
            .findAdmissibleResultsAsOf(event.competitionId(), event.editionId(), asOfCutoff)
            .stream()
            .filter(result -> result.sportTime().isBefore(event.scheduledAt()))
            .filter(result -> result.knownAt().compareTo(asOfCutoff) <= 0)
            .filter(result -> result.eventId() != null)
            .toList();
    return calculate(
        new OddsPricingInput(
            event.eventId(),
            marketVersionId,
            event.slots().get(0).teamId(),
            event.slots().get(1).teamId(),
            history,
            regulationAllowsDraw));
  }

  public OddsPricingOutput price(OddsPricingInput input) {
    return kernel.calculate(input);
  }

  private static UUID latestConfirmedVersion(List<ConfirmedSportsResultForOdds> results) {
    return results.stream()
        .filter(result -> result.resultKind().feedsRating())
        .max(java.util.Comparator.comparing(ConfirmedSportsResultForOdds::knownAt))
        .map(ConfirmedSportsResultForOdds::versionId)
        .orElse(null);
  }

  private static Instant latestKnownAt(List<ConfirmedSportsResultForOdds> results) {
    return results.stream()
        .map(ConfirmedSportsResultForOdds::knownAt)
        .max(Instant::compareTo)
        .orElse(null);
  }
}
