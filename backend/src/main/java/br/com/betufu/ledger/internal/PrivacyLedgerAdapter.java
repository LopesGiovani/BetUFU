package br.com.betufu.ledger.internal;

import br.com.betufu.privacy.PrivacyRestrictedRecordsPort;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("runtime | integrated")
public class PrivacyLedgerAdapter implements PrivacyRestrictedRecordsPort {
  private final JdbcTemplate jdbc;

  public PrivacyLedgerAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Set<DataClass> supportedClasses() {
    return Set.of(DataClass.LEDGER);
  }

  @Override
  public List<RestrictedRecord> export(Subject subject, DataClass dataClass) {
    if (dataClass != DataClass.LEDGER) throw new IllegalArgumentException("Unsupported class");
    if (subject.identityId() == null) return List.of();
    return jdbc.query(
        "SELECT entry_id,entry_type,direction,amount,created_at FROM credit_ledger_entry WHERE identity_id=? ORDER BY created_at,entry_id",
        (rs, row) ->
            new RestrictedRecord(
                rs.getString(1),
                Map.of(
                    "kind",
                    rs.getString(2),
                    "direction",
                    rs.getString(3),
                    "amount",
                    rs.getBigDecimal(4).toPlainString(),
                    "createdAt",
                    rs.getTimestamp(5).toInstant().toString())),
        subject.identityId());
  }
}
