package br.com.betufu.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.ledger.CreditCorrectionModels.Kind;
import br.com.betufu.ledger.CreditCorrectionModels.Reason;
import br.com.betufu.ledger.CreditCorrectionModels.Status;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CreditCorrectionSecurityIT extends CreditCorrectionFixture {
  @Test
  void makerCannotApproveAndNonAdministratorCannotProposeOrDecide() {
    var proposed = propose(Kind.CREDIT, "100.00", "maker-checker");
    assertThatThrownBy(() -> corrections.approve(maker, proposed.caseId()))
        .isInstanceOf(SecurityException.class);
    assertThatThrownBy(() -> corrections.approve(owner, proposed.caseId()))
        .isInstanceOf(SecurityException.class);
    assertThatThrownBy(
            () ->
                corrections.propose(
                    owner,
                    owner,
                    Kind.CREDIT,
                    Reason.PROVEN_TECHNICAL_FAILURE,
                    new BigDecimal("100.00"),
                    null,
                    null,
                    null,
                    null,
                    "invalid actor",
                    "100.00 calculation",
                    "not-admin"))
        .isInstanceOf(SecurityException.class);
    assertThat(ledger.getBalance(owner).balance()).isEqualByComparingTo("0.00");
    assertThat(corrections.findForOwner(owner, proposed.caseId()).status())
        .isEqualTo(Status.PROPOSED);
  }

  @Test
  void ownerReadsNeverRevealOtherOwnersOrOperationalActors() {
    var proposed = propose(Kind.RECOVERY, "10.00", "private-case");
    assertThatThrownBy(() -> corrections.findForOwner(maker, proposed.caseId()))
        .isInstanceOf(java.util.NoSuchElementException.class);
    assertThat(corrections.listForOwner(maker)).isEmpty();
    assertThat(corrections.listForOwner(owner)).hasSize(1);
    assertThat(corrections.findForOwner(owner, proposed.caseId()).toString())
        .doesNotContain(maker.toString(), checker.toString());
  }

  @Test
  void rejectionAndClosedReasonVocabularyCannotMintCredits() {
    var proposed = propose(Kind.CREDIT, "100.00", "rejected-case");
    assertThat(corrections.reject(checker, proposed.caseId()).status()).isEqualTo(Status.REJECTED);
    assertThatThrownBy(() -> corrections.approve(checker, proposed.caseId()))
        .isInstanceOf(IllegalStateException.class);
    assertThat(ledger.getBalance(owner).balance()).isEqualByComparingTo("0.00");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO public.credit_correction_case (case_id, identity_id, kind, origin, reason, amount, proposed_by_identity_id, description, calculation, reference_code, idempotency_key) VALUES (?, ?, 'CREDIT', 'ADMINISTRATIVE', 'BONUS', 10.00, ?, 'bonus', '10.00', ?, ?)",
                    UUID.randomUUID(),
                    owner,
                    maker,
                    "CC-invalid",
                    "invalid-reason"))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  }

  @Test
  void databaseRejectsSelfApprovalAndHistoricalMutation() {
    var proposed = propose(Kind.RECOVERY, "10.00", "database-checker");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO public.credit_correction_decision (decision_id, case_id, checker_identity_id, decision) VALUES (?, ?, ?, 'APPROVED')",
                    UUID.randomUUID(),
                    proposed.caseId(),
                    maker))
        .hasMessageContaining("distinct checker");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "DELETE FROM public.credit_correction_case WHERE case_id = ?",
                    proposed.caseId()))
        .hasMessageContaining("immutable");
  }
}
