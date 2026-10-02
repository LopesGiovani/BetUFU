package br.com.betufu.betting.internal;

import br.com.betufu.platform.operations.CreditCorrectionEvidenceLookup;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("runtime | integrated")
public class PredictionCorrectionEvidenceAdapter implements CreditCorrectionEvidenceLookup {
  private final JdbcTemplate jdbc;

  public PredictionCorrectionEvidenceAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Type type() {
    return Type.PREDICTION;
  }

  @Override
  public Optional<Fact> find(UUID evidenceId) {
    return jdbc.query(
        "SELECT identity_id, stake FROM public.prediction WHERE prediction_id = ?",
        rs ->
            rs.next()
                ? Optional.of(
                    new Fact(
                        rs.getObject("identity_id", UUID.class),
                        rs.getBigDecimal("stake"),
                        InverseEffect.CREDIT,
                        null))
                : Optional.empty(),
        evidenceId);
  }
}
