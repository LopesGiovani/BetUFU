package br.com.betufu.odds.internal;

import br.com.betufu.odds.internal.publicview.OddsPublicViewService;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.platform.operations.EmergencyPausePort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component("oddsEmergencyPauseTargetAdapter")
@Profile("runtime | integrated")
public class EmergencyPauseTargetAdapter implements EmergencyPausePort.TargetLookup {
  private final OddsPublicViewService views;

  public EmergencyPauseTargetAdapter(OddsPublicViewService views) {
    this.views = views;
  }

  @Override
  public EmergencyPausePort.Kind kind() {
    return EmergencyPausePort.Kind.MARKET;
  }

  @Override
  public Optional<EmergencyPausePort.Target> find(UUID id) {
    try {
      var view = views.get(id);
      return view.state().name().equals("DRAFT")
          ? Optional.empty()
          : Optional.of(new EmergencyPausePort.Target(view.eventId()));
    } catch (ApiProblemException error) {
      if (error.status() == HttpStatus.NOT_FOUND) return Optional.empty();
      throw error;
    }
  }
}
