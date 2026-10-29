package br.com.betufu.betting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.betufu.betting.internal.PredictionController;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/** Availability is a public snapshot and never acts as the write authorization guard. */
class PredictionAvailabilityIT {
  @Test
  @SuppressWarnings("unchecked")
  void pausedMarketProducesMinimalPublicSnapshot() {
    UUID marketId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID revision = UUID.randomUUID();
    var pauses = mock(PredictionEmergencyPausePort.class);
    when(pauses.findMarket(marketId))
        .thenReturn(Optional.of(new PredictionEmergencyPausePort.Market(marketId, eventId, true)));
    when(pauses.current(PredictionEmergencyPausePort.TargetKind.MARKET, marketId))
        .thenReturn(new PredictionEmergencyPausePort.State(true, revision));
    when(pauses.current(PredictionEmergencyPausePort.TargetKind.EVENT, eventId))
        .thenReturn(new PredictionEmergencyPausePort.State(false, null));
    ObjectProvider<PredictionEmergencyPausePort> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(pauses);

    var response =
        new PredictionController(null, null, null, provider).availability(null, marketId);
    var body = (PredictionController.Availability) response.getBody();
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(body.state()).isEqualTo("PAUSED");
    assertThat(body.predictionsPaused()).isTrue();
    assertThat(body.revision()).isEqualTo(revision);
  }

  @Test
  @SuppressWarnings("unchecked")
  void pausedEventUsesThePublishedEventIdAndNeverExposesPauseDetails() {
    UUID marketId = UUID.randomUUID();
    UUID eventId = UUID.randomUUID();
    UUID revision = UUID.randomUUID();
    var pauses = mock(PredictionEmergencyPausePort.class);
    when(pauses.findMarket(marketId))
        .thenReturn(Optional.of(new PredictionEmergencyPausePort.Market(marketId, eventId, true)));
    when(pauses.current(PredictionEmergencyPausePort.TargetKind.MARKET, marketId))
        .thenReturn(new PredictionEmergencyPausePort.State(false, null));
    when(pauses.current(PredictionEmergencyPausePort.TargetKind.EVENT, eventId))
        .thenReturn(new PredictionEmergencyPausePort.State(true, revision));
    ObjectProvider<PredictionEmergencyPausePort> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(pauses);

    var response =
        new PredictionController(null, null, null, provider).availability(null, marketId);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody())
        .isEqualTo(new PredictionController.Availability("PAUSED", true, revision));
    org.mockito.Mockito.verify(pauses)
        .current(PredictionEmergencyPausePort.TargetKind.EVENT, eventId);
  }
}
