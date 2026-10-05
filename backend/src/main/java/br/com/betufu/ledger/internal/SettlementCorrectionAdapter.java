package br.com.betufu.ledger.internal;

import br.com.betufu.settlement.SettlementCorrectionPort;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("runtime | integrated")
public class SettlementCorrectionAdapter implements SettlementCorrectionPort {
  private final CreditCorrectionService corrections;

  public SettlementCorrectionAdapter(CreditCorrectionService corrections) {
    this.corrections = corrections;
  }

  @Override
  public void recoverPreviousCredit(
      UUID identityId, UUID previousSettlementId, UUID replacementSettlementId, BigDecimal amount) {
    corrections.recoverSettlement(
        identityId, previousSettlementId, replacementSettlementId, amount);
  }

  @Component
  @Profile("runtime | integrated")
  public static class ChatRecoveryAdapter
      implements br.com.betufu.chat.ChatSettlementPort.RecoveryPort {
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    public ChatRecoveryAdapter(org.springframework.jdbc.core.JdbcTemplate jdbc) {
      this.jdbc = jdbc;
    }

    @Override
    public br.com.betufu.chat.ChatSettlementPort.Recovery inspectRecoveries(
        java.util.List<UUID> replacements) {
      if (replacements.isEmpty())
        return new br.com.betufu.chat.ChatSettlementPort.Recovery(false, null);
      String slots = String.join(",", java.util.Collections.nCopies(replacements.size(), "?"));
      return jdbc.queryForObject(
          "SELECT coalesce(bool_or(remaining>0),false) AS pending, max(completed_at) AS completed_at FROM ("
              + "SELECT c.case_id,c.amount-coalesce(sum(a.amount),0) AS remaining,max(a.created_at) AS completed_at "
              + "FROM public.credit_correction_case c LEFT JOIN public.credit_correction_allocation a ON a.case_id=c.case_id "
              + "WHERE c.origin='SETTLEMENT' AND c.replacement_settlement_id IN ("
              + slots
              + ") GROUP BY c.case_id,c.amount) recovery",
          (rs, row) ->
              new br.com.betufu.chat.ChatSettlementPort.Recovery(
                  rs.getBoolean("pending"),
                  rs.getTimestamp("completed_at") == null
                      ? null
                      : rs.getTimestamp("completed_at").toInstant()),
          replacements.toArray());
    }
  }
}
