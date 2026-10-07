package br.com.betufu.sports.internal.privacy;

import br.com.betufu.privacy.PrivacySubjectPort;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Produces only the existence projection needed to verify an athlete without an account. */
@Component
@Profile("runtime | integrated")
public class SportsPrivacyAdapter
    implements PrivacySubjectPort.Sports, br.com.betufu.privacy.PrivacyRestrictedRecordsPort {
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

  @Override
  public java.util.Set<DataClass> supportedClasses() {
    return java.util.Set.of(DataClass.ATHLETE_WITHOUT_ACCOUNT);
  }

  @Override
  public java.util.List<RestrictedRecord> export(Subject subject, DataClass dataClass) {
    if (dataClass != DataClass.ATHLETE_WITHOUT_ACCOUNT)
      throw new IllegalArgumentException("Unsupported class");
    return jdbc.query(
        "SELECT a.athlete_id,a.name,a.status FROM sports_athlete a WHERE a.athlete_id=? OR EXISTS(SELECT FROM sports_athlete_link_proposal p WHERE p.athlete_id=a.athlete_id AND p.identity_id=? AND p.status='ACEITA') ORDER BY a.athlete_id",
        (rs, row) ->
            new RestrictedRecord(
                rs.getString(1),
                java.util.Map.of("name", rs.getString(2), "status", rs.getString(3))),
        subject.athleteId(),
        subject.identityId());
  }
}
