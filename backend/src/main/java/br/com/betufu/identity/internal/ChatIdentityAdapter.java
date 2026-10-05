package br.com.betufu.identity.internal;

import br.com.betufu.chat.ChatIdentityPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("runtime | integrated")
public class ChatIdentityAdapter implements ChatIdentityPort {
  private final JdbcTemplate jdbc;

  public ChatIdentityAdapter(ObjectProvider<JdbcTemplate> jdbc) {
    this.jdbc = jdbc.getIfAvailable();
  }

  @Override
  public Optional<Participant> findParticipant(UUID identityId) {
    if (jdbc == null)
      throw new IllegalStateException("Chat participant lookup requires JdbcTemplate");
    return jdbc.query(
        "SELECT identity_id, nickname FROM public.betufu_profile WHERE identity_id = ?",
        rs ->
            rs.next()
                ? Optional.of(new Participant(rs.getObject(1, UUID.class), rs.getString(2)))
                : Optional.empty(),
        identityId);
  }
}
