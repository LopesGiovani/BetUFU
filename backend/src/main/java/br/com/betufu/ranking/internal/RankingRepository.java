package br.com.betufu.ranking.internal;

import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("runtime | integrated")
public class RankingRepository {
  private final JdbcTemplate jdbc;

  public RankingRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  void lockRebuild() {
    jdbc.queryForObject("SELECT pg_advisory_xact_lock(36036)", Object.class);
  }

  void replaceAll(List<Counts> rows) {
    jdbc.update("DELETE FROM public.ranking_projection");
    for (Counts row : rows) {
      jdbc.update(
          "INSERT INTO public.ranking_projection (identity_id, wins, losses) VALUES (?, ?, ?)",
          row.identityId(),
          row.wins(),
          row.losses());
    }
  }

  List<Counts> findAll() {
    return jdbc.query(
        "SELECT identity_id, wins, losses FROM public.ranking_projection ORDER BY identity_id",
        (rs, row) ->
            new Counts(
                rs.getObject("identity_id", UUID.class), rs.getLong("wins"), rs.getLong("losses")));
  }

  public record Counts(UUID identityId, long wins, long losses) {}
}
