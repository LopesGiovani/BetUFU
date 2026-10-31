package br.com.betufu.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.ledger.CreditLedgerPort;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

class Phase7CertificationIT extends ChatSanctionFixture {
  @Autowired CreditLedgerPort credits;

  @Test
  void sanctionAndReviewPreservePopulatedFinancialFactsAndAppendOnlyHistory() throws Exception {
    credits.ensureInitialGrant(author);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM credit_ledger_entry", Integer.class))
        .isPositive();
    String before = financialSnapshot();
    UUID occurrence = occurrence();
    var confirmed = confirm(occurrence, "CHAT_SUSPENSION_24H", UUID.randomUUID());
    assertThat(confirmed.statusCode()).isEqualTo(201);
    assertThat(post(ownerSession, null, UUID.randomUUID(), "suspended").statusCode())
        .isEqualTo(403);
    UUID sanctionId = UUID.fromString(json.readTree(confirmed.body()).get("sanctionId").asText());
    var review = review(sanctionId, UUID.randomUUID());
    assertThat(review.statusCode()).isEqualTo(201);
    assertThat(financialSnapshot()).isEqualTo(before);
    for (String table :
        java.util.List.of(
            "chat_message",
            "chat_report",
            "chat_moderation_sanction_fact",
            "chat_moderation_review_fact")) {
      assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class)).isPositive();
      immutable(() -> jdbc.update("DELETE FROM " + table));
      String column = table.equals("chat_message") ? "body" : "reason";
      immutable(() -> jdbc.update("UPDATE " + table + " SET " + column + "=" + column));
    }
    assertThat(financialSnapshot()).isEqualTo(before);
  }

  private static void immutable(Runnable change) {
    assertThatThrownBy(change::run)
        .isInstanceOf(DataAccessException.class)
        .satisfies(
            error ->
                assertThat(
                        ((java.sql.SQLException)
                                ((DataAccessException) error).getMostSpecificCause())
                            .getSQLState())
                    .isEqualTo("P0001"));
  }
}
