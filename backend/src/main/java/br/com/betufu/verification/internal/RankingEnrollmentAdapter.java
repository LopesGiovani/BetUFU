package br.com.betufu.verification.internal;

import br.com.betufu.ranking.RankingEnrollmentSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("runtime | integrated")
public class RankingEnrollmentAdapter implements RankingEnrollmentSource {
  private final JdbcTemplate jdbc;

  public RankingEnrollmentAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean wasActiveAt(UUID identityId, Instant acceptedAt) {
    // A released row retains its original validity interval in created_at/updated_at.
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            """
                SELECT EXISTS (
                  SELECT 1 FROM public.ufu_enrollment
                  WHERE identity_id = ? AND created_at <= ?
                    AND (status = 'ATIVO' OR updated_at > ?)
                )
                """,
            Boolean.class,
            identityId,
            Timestamp.from(acceptedAt),
            Timestamp.from(acceptedAt)));
  }

  @Override
  public boolean isActiveNow(UUID identityId) {
    return Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM public.ufu_enrollment WHERE identity_id = ? AND status = 'ATIVO')",
            Boolean.class,
            identityId));
  }
}
