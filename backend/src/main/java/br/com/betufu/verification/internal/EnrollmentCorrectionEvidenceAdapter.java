package br.com.betufu.verification.internal;

import br.com.betufu.platform.operations.CreditCorrectionEnrollmentLookup;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("runtime | integrated")
public class EnrollmentCorrectionEvidenceAdapter implements CreditCorrectionEnrollmentLookup {
  private final JdbcTemplate jdbc;

  public EnrollmentCorrectionEvidenceAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean belongsTo(UUID enrollmentId, UUID identityId) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM public.ufu_enrollment WHERE enrollment_id = ? AND identity_id = ?",
            Integer.class,
            enrollmentId,
            identityId);
    return count != null && count == 1;
  }
}
