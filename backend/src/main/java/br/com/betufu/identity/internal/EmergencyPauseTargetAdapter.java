package br.com.betufu.identity.internal;

import br.com.betufu.platform.operations.EmergencyPausePort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("identityEmergencyPauseTargetAdapter")
@Profile("runtime | integrated")
public class EmergencyPauseTargetAdapter implements EmergencyPausePort.TargetLookup {
  private final JdbcTemplate jdbc;

  public EmergencyPauseTargetAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public EmergencyPausePort.Kind kind() {
    return EmergencyPausePort.Kind.ACCOUNT;
  }

  @Override
  public Optional<EmergencyPausePort.Target> find(UUID id) {
    return jdbc
        .query(
            "SELECT identity_id FROM local_identity WHERE identity_id=?",
            (rs, row) -> new EmergencyPausePort.Target(null),
            id)
        .stream()
        .findFirst();
  }
}
