package br.com.betufu.settlement.internal;

import br.com.betufu.platform.operations.CreditCorrectionEvidenceLookup;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("runtime | integrated")
public class SettlementCorrectionEvidenceAdapter implements CreditCorrectionEvidenceLookup {
  private final JdbcTemplate jdbc;

  public SettlementCorrectionEvidenceAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Type type() {
    return Type.SETTLEMENT;
  }

  @Override
  public Optional<Fact> find(UUID evidenceId) {
    return jdbc.query(
        """
            SELECT identity_id, amount FROM public.prediction_settlement
            WHERE settlement_id = ? AND state IN ('WON', 'REFUNDED') AND amount > 0.00
            """,
        rs ->
            rs.next()
                ? Optional.of(
                    new Fact(
                        rs.getObject("identity_id", UUID.class),
                        rs.getBigDecimal("amount"),
                        InverseEffect.CREDIT,
                        null))
                : Optional.empty(),
        evidenceId);
  }
}
