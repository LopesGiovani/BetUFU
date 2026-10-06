package br.com.betufu.sports.internal;

import br.com.betufu.platform.operations.EmergencyPausePort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("sportsEmergencyPauseTargetAdapter")
@Profile("runtime | integrated")
public class EmergencyPauseTargetAdapter implements EmergencyPausePort.TargetLookup {
  private final JdbcTemplate jdbc;

  public EmergencyPauseTargetAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public EmergencyPausePort.Kind kind() {
    return EmergencyPausePort.Kind.EVENT;
  }

  @Override
  public Optional<EmergencyPausePort.Target> find(UUID id) {
    return jdbc
        .query(
            "SELECT event_id FROM sports_event e WHERE event_id=? AND status<>'RASCUNHO' AND EXISTS(SELECT FROM sports_event_publication p WHERE p.event_id=e.event_id)",
            (rs, row) -> new EmergencyPausePort.Target(null),
            id)
        .stream()
        .findFirst();
  }
}
