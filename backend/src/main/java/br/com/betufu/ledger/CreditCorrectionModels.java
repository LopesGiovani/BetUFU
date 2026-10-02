package br.com.betufu.ledger;

import br.com.betufu.platform.operations.CreditCorrectionEvidenceLookup.Type;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Sanitized correction facts; operational actor identities stay in persistence only. */
public final class CreditCorrectionModels {
  private CreditCorrectionModels() {}

  public enum Kind {
    CREDIT,
    RECOVERY
  }

  public enum Reason {
    SETTLEMENT_CORRECTION,
    DUPLICATE_OR_MISSING_ENTRY,
    PROVEN_TECHNICAL_FAILURE,
    ELIGIBILITY_CORRECTION
  }

  public enum Status {
    PROPOSED,
    REJECTED,
    APPROVED,
    PENDING,
    RECOVERED
  }

  public record View(
      UUID caseId,
      String referenceCode,
      Kind kind,
      Reason reason,
      Type evidenceType,
      UUID evidenceId,
      UUID originalEntryId,
      BigDecimal amount,
      BigDecimal recovered,
      BigDecimal pending,
      Status status,
      Instant createdAt) {
    public View {
      Objects.requireNonNull(caseId);
      Objects.requireNonNull(referenceCode);
      Objects.requireNonNull(kind);
      Objects.requireNonNull(reason);
      Objects.requireNonNull(amount);
      Objects.requireNonNull(recovered);
      Objects.requireNonNull(pending);
      Objects.requireNonNull(status);
      Objects.requireNonNull(createdAt);
    }
  }
}
