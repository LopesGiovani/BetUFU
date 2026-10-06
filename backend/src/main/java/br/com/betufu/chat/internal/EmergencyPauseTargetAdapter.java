package br.com.betufu.chat.internal;

import br.com.betufu.platform.operations.EmergencyPausePort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component("chatEmergencyPauseTargetAdapter")
@Profile("runtime | integrated")
public class EmergencyPauseTargetAdapter implements EmergencyPausePort.TargetLookup {
  private final JdbcTemplate jdbc;

  public EmergencyPauseTargetAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public EmergencyPausePort.Kind kind() {
    return EmergencyPausePort.Kind.CHANNEL;
  }

  @Override
  public Optional<EmergencyPausePort.Target> find(UUID id) {
    return jdbc
        .query(
            "SELECT event_id FROM chat_channel WHERE channel_id=?",
            (rs, row) -> new EmergencyPausePort.Target(rs.getObject(1, UUID.class)),
            id)
        .stream()
        .findFirst();
  }
}
