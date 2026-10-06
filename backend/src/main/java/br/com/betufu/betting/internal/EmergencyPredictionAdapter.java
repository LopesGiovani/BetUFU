package br.com.betufu.betting.internal;

import br.com.betufu.betting.PredictionEmergencyPausePort;
import br.com.betufu.odds.OddsForPredictionPort;
import br.com.betufu.platform.operations.EmergencyPausePort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Adapts operations pause facts and exposes only the public market lookup needed by betting. */
@Component
@Profile("runtime | integrated")
public class EmergencyPredictionAdapter implements PredictionEmergencyPausePort {
  private final EmergencyPausePort pauses;
  private final OddsForPredictionPort odds;

  public EmergencyPredictionAdapter(EmergencyPausePort pauses, OddsForPredictionPort odds) {
    this.pauses = pauses;
    this.odds = odds;
  }

  @Override
  public State lock(TargetKind kind, UUID targetId) {
    var state = pauses.lockState(toOperationKind(kind), targetId);
    return new State(state.paused(), state.currentPauseId());
  }

  @Override
  public State current(TargetKind kind, UUID targetId) {
    var state = pauses.state(toOperationKind(kind), targetId);
    return new State(state.paused(), state.currentPauseId());
  }

  @Override
  public Optional<Market> findMarket(UUID marketId) {
    try {
      var snapshot = odds.getSnapshot(marketId);
      return Optional.of(new Market(snapshot.marketId(), snapshot.eventId(), true));
    } catch (IllegalStateException unavailable) {
      return Optional.empty();
    }
  }

  private EmergencyPausePort.Kind toOperationKind(TargetKind kind) {
    return switch (kind) {
      case ACCOUNT -> EmergencyPausePort.Kind.ACCOUNT;
      case EVENT -> EmergencyPausePort.Kind.EVENT;
      case MARKET -> EmergencyPausePort.Kind.MARKET;
    };
  }
}
