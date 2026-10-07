package br.com.betufu.settlement.internal;

import br.com.betufu.privacy.PrivacyRestrictedRecordsPort;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("runtime | integrated")
public class PrivacySettlementAdapter implements PrivacyRestrictedRecordsPort {
  private final JdbcTemplate jdbc;

  public PrivacySettlementAdapter(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Set<DataClass> supportedClasses() {
    return Set.of(DataClass.SETTLEMENT);
  }

  @Override
  public List<RestrictedRecord> export(Subject subject, DataClass dataClass) {
    if (dataClass != DataClass.SETTLEMENT) throw new IllegalArgumentException("Unsupported class");
    if (subject.identityId() == null) return List.of();
    return jdbc.query(
        "SELECT settlement_id,state,reason,amount,created_at FROM prediction_settlement WHERE identity_id=? ORDER BY created_at,settlement_id",
        (rs, row) ->
            new RestrictedRecord(
                rs.getString(1),
                Map.of(
                    "state",
                    rs.getString(2),
                    "reason",
                    rs.getString(3),
                    "amount",
                    rs.getBigDecimal(4).toPlainString(),
                    "createdAt",
                    rs.getTimestamp(5).toInstant().toString())),
        subject.identityId());
  }
}
