package br.com.betufu.ledger.internal;

import br.com.betufu.audit.AuditEntry;
import br.com.betufu.audit.AuditRecorder;
import br.com.betufu.ledger.CreditLedgerPort.EntryType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Applies approved recovery cases oldest first while the caller holds the credit-account lock. */
@Service
@Profile("runtime | integrated")
public class CreditAllocationService {
  private final CreditCorrectionRepository corrections;
  private final CreditLedgerRepository ledger;
  private final AuditRecorder audit;

  public CreditAllocationService(
      CreditCorrectionRepository corrections, CreditLedgerRepository ledger) {
    this(corrections, ledger, entry -> {});
  }

  @Autowired
  public CreditAllocationService(
      CreditCorrectionRepository corrections, CreditLedgerRepository ledger, AuditRecorder audit) {
    this.corrections = Objects.requireNonNull(corrections);
    this.ledger = Objects.requireNonNull(ledger);
    this.audit = Objects.requireNonNull(audit);
  }

  public BigDecimal allocate(UUID identityId, BigDecimal available, UUID sourceEntryId) {
    BigDecimal remainingBalance = available;
    for (CreditCorrectionRepository.Pending pending : corrections.pending(identityId)) {
      if (remainingBalance.signum() == 0) break;
      BigDecimal amount = remainingBalance.min(pending.remaining());
      String sourceKey =
          sourceEntryId == null ? "BALANCE:" + pending.caseId() : "CREDIT:" + sourceEntryId;
      UUID allocationId =
          UUID.nameUUIDFromBytes(
              ("correction:allocation:" + pending.caseId() + ":" + sourceKey)
                  .getBytes(StandardCharsets.UTF_8));
      CreditLedgerRepository.CreditLedgerEntry recovery =
          ledger.insertEntry(
              identityId,
              EntryType.CORRECTION_RECOVERY,
              "DEBIT",
              amount,
              "CORRECTION",
              allocationId,
              "credit:correction_recovery:" + allocationId);
      if (!recovery.identityId().equals(identityId)
          || recovery.entryType() != EntryType.CORRECTION_RECOVERY
          || recovery.amount().compareTo(amount) != 0) {
        throw new IllegalStateException("correction recovery key was reused with different data");
      }
      corrections.insertAllocation(
          allocationId, pending.caseId(), sourceEntryId, sourceKey, recovery.entryId(), amount);
      audit.record(
          AuditEntry.builder()
              .actorRole("SYSTEM")
              .targetIdentityId(identityId)
              .action(AuditEntry.ACTION_CREDIT_CORRECTION_RECOVERED)
              .outcome(AuditEntry.OUTCOME_PERMITTED)
              .reason("FIFO_RECOVERY")
              .correlationId(pending.caseId().toString())
              .idempotencyKey("credit-correction-allocation:" + allocationId)
              .build());
      remainingBalance = remainingBalance.subtract(amount);
    }
    return remainingBalance;
  }

  public boolean hasPending(UUID identityId) {
    return corrections.hasPending(identityId);
  }
}
