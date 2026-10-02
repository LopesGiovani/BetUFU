package br.com.betufu.identity.internal;

import br.com.betufu.ranking.RankingNicknameSource;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("runtime | integrated")
public class RankingNicknameAdapter implements RankingNicknameSource {
  private final JdbcTemplate jdbc;

  @Autowired
  public RankingNicknameAdapter(ObjectProvider<JdbcTemplate> jdbcProvider) {
    this.jdbc = jdbcProvider.getIfAvailable();
  }

  public RankingNicknameAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<String> findNickname(UUID identityId) {
    if (jdbc == null) {
      throw new IllegalStateException("Ranking nickname lookup requires JdbcTemplate");
    }
    return jdbc.query(
        "SELECT nickname FROM public.betufu_profile WHERE identity_id = ?",
        rs -> rs.next() ? Optional.of(rs.getString(1)) : Optional.empty(),
        identityId);
  }
}
