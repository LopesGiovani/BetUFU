package br.com.betufu.verification.internal;

import br.com.betufu.privacy.PrivacyRestrictedRecordsPort;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("runtime | integrated")
public class PrivacyEnrollmentAdapter implements PrivacyRestrictedRecordsPort {
  private final JdbcTemplate jdbc;

  public PrivacyEnrollmentAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Set<DataClass> supportedClasses() {
    return Set.of(DataClass.ENROLLMENT);
  }

  @Override
  public List<RestrictedRecord> export(Subject subject, DataClass dataClass) {
    if (dataClass != DataClass.ENROLLMENT) throw new IllegalArgumentException("Unsupported class");
    if (subject.identityId() == null) return List.of();
    return jdbc.query(
        "SELECT enrollment_id,status,created_at FROM ufu_enrollment WHERE identity_id=? ORDER BY created_at,enrollment_id",
        (rs, row) ->
            new RestrictedRecord(
                rs.getString(1),
                Map.of(
                    "status",
                    rs.getString(2),
                    "createdAt",
                    rs.getTimestamp(3).toInstant().toString())),
        subject.identityId());
  }
}
