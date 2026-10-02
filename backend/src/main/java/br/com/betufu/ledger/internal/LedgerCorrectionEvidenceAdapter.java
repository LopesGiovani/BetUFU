package br.com.betufu.ledger.internal;

import br.com.betufu.platform.operations.CreditCorrectionEvidenceLookup;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("runtime | integrated")
public class LedgerCorrectionEvidenceAdapter implements CreditCorrectionEvidenceLookup {
  private final JdbcTemplate jdbc;

  public LedgerCorrectionEvidenceAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Type type() {
    return Type.LEDGER_ENTRY;
  }

  @Override
  public Optional<Fact> find(UUID evidenceId) {
    return jdbc.query(
        "SELECT identity_id, amount, direction FROM public.credit_ledger_entry WHERE entry_id = ?",
        rs ->
            rs.next()
                ? Optional.of(
                    new Fact(
                        rs.getObject("identity_id", UUID.class),
                        rs.getBigDecimal("amount"),
                        "DEBIT".equals(rs.getString("direction"))
                            ? InverseEffect.CREDIT
                            : InverseEffect.RECOVERY,
                        evidenceId))
                : Optional.empty(),
        evidenceId);
  }
}
