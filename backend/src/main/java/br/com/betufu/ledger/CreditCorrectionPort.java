package br.com.betufu.ledger;

import br.com.betufu.ledger.CreditCorrectionModels.Kind;
import br.com.betufu.ledger.CreditCorrectionModels.Reason;
import br.com.betufu.ledger.CreditCorrectionModels.View;
import br.com.betufu.platform.operations.CreditCorrectionEvidenceLookup.Type;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Administrative maker-checker commands and owner-scoped, sanitized reads. */
public interface CreditCorrectionPort {
  View propose(
      UUID makerId,
      UUID identityId,
      Kind kind,
      Reason reason,
      BigDecimal amount,
      Type evidenceType,
      UUID evidenceId,
      UUID enrollmentId,
      UUID supersedesCaseId,
      String description,
      String calculation,
      String idempotencyKey);

  View approve(UUID checkerId, UUID caseId);

  View reject(UUID checkerId, UUID caseId);

  View findForOwner(UUID identityId, UUID caseId);

  List<View> listForOwner(UUID identityId);

  boolean hasPending(UUID identityId);
}
