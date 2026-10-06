package br.com.betufu.sports.internal.privacy;

import br.com.betufu.privacy.PrivacySubjectPort;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Produces only the existence projection needed to verify an athlete without an account. */
@Component
@Profile("runtime | integrated")
public class SportsPrivacyAdapter implements PrivacySubjectPort.Sports {
  private final JdbcTemplate jdbc;

  public SportsPrivacyAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean existsAthlete(UUID athleteId) {
    if (athleteId == null) return false;
    return Boolean.TRUE.equals(
        jdbc.query(
            "SELECT EXISTS (SELECT 1 FROM public.sports_athlete WHERE athlete_id = ?)",
            rs -> rs.next() && rs.getBoolean(1),
            athleteId));
  }
}
