package br.com.betufu.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.betufu.ledger.CreditCorrectionModels.Kind;
import br.com.betufu.ledger.CreditCorrectionModels.Reason;
import br.com.betufu.ledger.CreditCorrectionModels.Status;
import br.com.betufu.platform.operations.CreditCorrectionEvidenceLookup.Type;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class CreditCorrectionEvidenceIT extends CreditCorrectionFixture {
  @Test
  void missingForeignOrWrongAmountEvidenceCannotCreateProposal() {
    UUID validEntry = evidence(Kind.CREDIT, "25.00");
    assertThatThrownBy(
            () ->
                proposeWithEvidence(
                    Kind.CREDIT,
                    Reason.PROVEN_TECHNICAL_FAILURE,
                    "25.00",
                    null,
                    null,
                    "missing-reference"))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(
            () ->
                proposeWithEvidence(
                    Kind.CREDIT,
                    Reason.PROVEN_TECHNICAL_FAILURE,
                    "25.00",
                    UUID.randomUUID(),
                    null,
                    "unknown-reference"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                proposeWithEvidence(
                    Kind.CREDIT,
                    Reason.PROVEN_TECHNICAL_FAILURE,
                    "26.00",
                    validEntry,
                    null,
                    "wrong-amount"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(corrections.listForOwner(owner)).isEmpty();
  }

  @Test
  void foreignOwnerAndWrongDirectionFailBeforePersistence() {
    UUID otherOwner = identity();
    var paid = ledger.payout(otherOwner, new BigDecimal("20.00"), UUID.randomUUID());
    assertThatThrownBy(
            () ->
                proposeWithEvidence(
                    Kind.RECOVERY,
                    Reason.PROVEN_TECHNICAL_FAILURE,
                    "20.00",
                    paid.entryId(),
                    null,
                    "foreign-owner"))
        .isInstanceOf(IllegalArgumentException.class);
    UUID ownCredit = evidence(Kind.RECOVERY, "20.00");
    assertThatThrownBy(
            () ->
                proposeWithEvidence(
                    Kind.CREDIT,
                    Reason.PROVEN_TECHNICAL_FAILURE,
                    "20.00",
                    ownCredit,
                    null,
                    "wrong-direction"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(corrections.listForOwner(owner)).isEmpty();
  }

  @Test
  void enrollmentCannotReplaceMonetaryEvidenceAndMustBelongToOwner() {
    UUID evidenceId = evidence(Kind.CREDIT, "30.00");
    UUID otherOwner = identity();
    UUID foreignEnrollment = enrollment(otherOwner);
    assertThatThrownBy(
            () ->
                proposeWithEvidence(
                    Kind.CREDIT,
                    Reason.ELIGIBILITY_CORRECTION,
                    "30.00",
                    evidenceId,
                    null,
                    "missing-enrollment"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                proposeWithEvidence(
                    Kind.CREDIT,
                    Reason.ELIGIBILITY_CORRECTION,
                    "30.00",
                    evidenceId,
                    foreignEnrollment,
                    "foreign-enrollment"))
        .isInstanceOf(IllegalArgumentException.class);
    UUID ownEnrollment = enrollment(owner);
    var proposed =
        proposeWithEvidence(
            Kind.CREDIT,
            Reason.ELIGIBILITY_CORRECTION,
            "30.00",
            evidenceId,
            ownEnrollment,
            "valid-enrollment");
    assertThat(proposed.status()).isEqualTo(Status.PROPOSED);
    assertThat(proposed.toString()).doesNotContain(ownEnrollment.toString());
  }

  @Test
  void distinctKeysCannotApproveTheSameMonetaryFactTwice() {
    UUID evidenceId = evidence(Kind.CREDIT, "20.00");
    var first =
        proposeWithEvidence(
            Kind.CREDIT,
            Reason.PROVEN_TECHNICAL_FAILURE,
            "20.00",
            evidenceId,
            null,
            "first-evidence");
    var second =
        proposeWithEvidence(
            Kind.CREDIT,
            Reason.PROVEN_TECHNICAL_FAILURE,
            "20.00",
            evidenceId,
            null,
            "second-evidence");
    corrections.approve(checker, first.caseId());
    assertThatThrownBy(() -> corrections.approve(checker, second.caseId()))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("already has an approved correction");
    assertThat(ledger.getBalance(owner).balance()).isEqualByComparingTo("20.00");
  }

  @Test
  void differentReferenceTypesCannotApproveTheSameOriginalEntryTwice() {
    UUID debitEntry = evidence(Kind.CREDIT, "20.00");
    var first =
        proposeWithEvidence(
            Kind.CREDIT, Reason.PROVEN_TECHNICAL_FAILURE, "20.00", debitEntry, null, "first-entry");
    corrections.approve(checker, first.caseId());
    UUID secondCase = UUID.randomUUID();
    jdbc.update(
        """
            INSERT INTO public.credit_correction_case
              (case_id, identity_id, kind, origin, reason, amount, evidence_type,
               evidence_id, original_entry_id, proposed_by_identity_id, description,
               calculation, reference_code, idempotency_key)
            VALUES (?, ?, 'CREDIT', 'ADMINISTRATIVE', 'PROVEN_TECHNICAL_FAILURE',
                    20.00, 'PREDICTION', ?, ?, ?, 'verified', '20.00', ?, ?)
            """,
        secondCase,
        owner,
        UUID.randomUUID(),
        debitEntry,
        maker,
        "CC-" + secondCase,
        "cross-type:" + secondCase);
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO public.credit_correction_decision (decision_id, case_id, checker_identity_id, decision) VALUES (?, ?, ?, 'APPROVED')",
                    UUID.randomUUID(),
                    secondCase,
                    checker))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("already has an approved correction");
  }

  private CreditCorrectionModels.View proposeWithEvidence(
      Kind kind, Reason reason, String amount, UUID evidenceId, UUID enrollmentId, String key) {
    return corrections.propose(
        maker,
        owner,
        kind,
        reason,
        new BigDecimal(amount),
        Type.LEDGER_ENTRY,
        evidenceId,
        enrollmentId,
        null,
        "verified correction",
        amount + " from referenced fact",
        key);
  }

  private UUID enrollment(UUID identityId) {
    UUID enrollmentId = UUID.randomUUID();
    byte[] digest = new byte[32];
    UUID random = UUID.randomUUID();
    java.nio.ByteBuffer.wrap(digest)
        .putLong(random.getMostSignificantBits())
        .putLong(random.getLeastSignificantBits());
    jdbc.update(
        "INSERT INTO public.ufu_enrollment (enrollment_id, identity_id, matricula_hmac, hmac_version, status) VALUES (?, ?, ?, 1, 'ATIVO')",
        enrollmentId,
        identityId,
        digest);
    return enrollmentId;
  }
}
