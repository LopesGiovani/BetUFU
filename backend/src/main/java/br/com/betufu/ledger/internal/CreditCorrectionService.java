package br.com.betufu.ledger.internal;

import br.com.betufu.audit.AuditEntry;
import br.com.betufu.audit.AuditRecorder;
import br.com.betufu.ledger.CreditCorrectionModels.Kind;
import br.com.betufu.ledger.CreditCorrectionModels.Reason;
import br.com.betufu.ledger.CreditCorrectionModels.Status;
import br.com.betufu.ledger.CreditCorrectionModels.View;
import br.com.betufu.ledger.CreditCorrectionPort;
import br.com.betufu.ledger.CreditLedgerPort;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.operations.CreditCorrectionEnrollmentLookup;
import br.com.betufu.platform.operations.CreditCorrectionEvidenceLookup;
import br.com.betufu.platform.operations.CreditCorrectionEvidenceLookup.Fact;
import br.com.betufu.platform.operations.CreditCorrectionEvidenceLookup.Type;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Profile("runtime | integrated")
public class CreditCorrectionService implements CreditCorrectionPort {
  private final CreditCorrectionRepository repository;
  private final CreditLedgerPort ledger;
  private final OperationalIdentityLookup identities;
  private final AuditRecorder audit;
  private final Map<Type, CreditCorrectionEvidenceLookup> evidenceLookups;
  private final CreditCorrectionEnrollmentLookup contextLookup;
  private final TransactionTemplate transactions;

  public CreditCorrectionService(
      CreditCorrectionRepository repository,
      CreditLedgerPort ledger,
      OperationalIdentityLookup identities,
      AuditRecorder audit,
      List<CreditCorrectionEvidenceLookup> evidenceLookups,
      CreditCorrectionEnrollmentLookup contextLookup,
      PlatformTransactionManager manager) {
    this.repository = Objects.requireNonNull(repository);
    this.ledger = Objects.requireNonNull(ledger);
    this.identities = Objects.requireNonNull(identities);
    this.audit = Objects.requireNonNull(audit);
    EnumMap<Type, CreditCorrectionEvidenceLookup> byType = new EnumMap<>(Type.class);
    for (CreditCorrectionEvidenceLookup lookup : Objects.requireNonNull(evidenceLookups)) {
      if (byType.putIfAbsent(lookup.type(), lookup) != null) {
        throw new IllegalArgumentException("duplicate correction evidence lookup");
      }
    }
    if (byType.size() != Type.values().length) {
      throw new IllegalArgumentException("correction evidence lookups are incomplete");
    }
    this.evidenceLookups = Map.copyOf(byType);
    this.contextLookup = Objects.requireNonNull(contextLookup);
    this.transactions = new TransactionTemplate(Objects.requireNonNull(manager));
  }

  @Override
  public View propose(
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
      String idempotencyKey) {
    requireAdministrator(makerId);
    Objects.requireNonNull(identityId);
    Objects.requireNonNull(kind);
    Objects.requireNonNull(reason);
    Objects.requireNonNull(evidenceType);
    Objects.requireNonNull(evidenceId);
    BigDecimal normalized = amount(amount);
    if ((reason == Reason.ELIGIBILITY_CORRECTION) != (enrollmentId != null)) {
      throw new IllegalArgumentException("eligibility correction requires enrollment context");
    }
    requireText(description, 1000);
    requireText(calculation, 1000);
    requireText(idempotencyKey, 255);
    return transactions.execute(
        ignored -> {
          Fact fact =
              evidenceLookups
                  .get(evidenceType)
                  .find(evidenceId)
                  .orElseThrow(() -> new IllegalArgumentException("correction evidence not found"));
          if (!fact.identityId().equals(identityId) || fact.amount().compareTo(normalized) != 0) {
            throw new IllegalArgumentException(
                "correction evidence owner or amount does not match");
          }
          if (enrollmentId != null && !contextLookup.belongsTo(enrollmentId, identityId)) {
            throw new IllegalArgumentException("enrollment evidence does not belong to owner");
          }
          UUID originalEntryId = fact.originalEntryId();
          if (evidenceType == Type.PREDICTION) {
            originalEntryId =
                repository
                    .predictionDebit(identityId, evidenceId, normalized)
                    .orElseThrow(() -> new IllegalArgumentException("prediction debit not found"));
          }
          if (evidenceType == Type.SETTLEMENT
              && repository.settlementCredit(identityId, evidenceId).isPresent()) {
            throw new IllegalArgumentException("settlement already has a credit entry");
          }
          if (supersedesCaseId != null) {
            CreditCorrectionRepository.Case prior =
                repository.findCase(supersedesCaseId).orElseThrow();
            if (!prior.identityId().equals(identityId)
                || !isEffective(prior)
                || prior.kind() == kind
                || prior.amount().compareTo(normalized) != 0
                || prior.evidenceType() != evidenceType
                || !evidenceId.equals(prior.evidenceId())
                || (prior.kind() == Kind.RECOVERY
                    && repository.recovered(prior.caseId()).compareTo(prior.amount()) != 0)) {
              throw new IllegalArgumentException(
                  "superseded correction is not effective for owner");
            }
          } else if (!fact.inverseEffect().name().equals(kind.name())) {
            throw new IllegalArgumentException("correction direction does not match evidence");
          }
          CreditCorrectionRepository.Case requested =
              new CreditCorrectionRepository.Case(
                  UUID.randomUUID(),
                  identityId,
                  kind,
                  "ADMINISTRATIVE",
                  reason,
                  normalized,
                  evidenceType,
                  evidenceId,
                  enrollmentId,
                  originalEntryId,
                  null,
                  null,
                  supersedesCaseId,
                  makerId,
                  description,
                  calculation,
                  "CC-" + UUID.randomUUID(),
                  idempotencyKey,
                  Instant.now());
          CreditCorrectionRepository.Case saved = repository.insertCase(requested);
          if (!sameProposal(saved, requested)) {
            throw new IllegalStateException("correction key was reused with different data");
          }
          if (saved.caseId().equals(requested.caseId())) {
            record(makerId, saved, AuditEntry.ACTION_CREDIT_CORRECTION_PROPOSED);
          }
          return view(saved);
        });
  }

  @Override
  public View approve(UUID checkerId, UUID caseId) {
    return decide(checkerId, caseId, "APPROVED");
  }

  @Override
  public View reject(UUID checkerId, UUID caseId) {
    return decide(checkerId, caseId, "REJECTED");
  }

  private View decide(UUID checkerId, UUID caseId, String decision) {
    requireAdministrator(checkerId);
    Objects.requireNonNull(caseId);
    return transactions.execute(
        ignored -> {
          CreditCorrectionRepository.Case correction = repository.findCase(caseId).orElseThrow();
          if (!"ADMINISTRATIVE".equals(correction.origin())
              || checkerId.equals(correction.proposedById())) {
            throw new SecurityException("a distinct general administrator must decide correction");
          }
          CreditCorrectionRepository.Decision requested =
              new CreditCorrectionRepository.Decision(
                  UUID.randomUUID(), caseId, checkerId, decision, Instant.now());
          CreditCorrectionRepository.Decision stored = repository.insertDecision(requested);
          if (!stored.checkerId().equals(checkerId) || !stored.decision().equals(decision)) {
            throw new IllegalStateException("correction already has a different decision");
          }
          if (stored.decisionId().equals(requested.decisionId())) {
            if ("APPROVED".equals(decision)) {
              if (correction.kind() == Kind.CREDIT) {
                ledger.correctionCredit(
                    correction.identityId(), correction.amount(), correction.caseId());
              } else {
                ledger.recoverPending(correction.identityId());
              }
            }
            record(
                checkerId,
                correction,
                "APPROVED".equals(decision)
                    ? AuditEntry.ACTION_CREDIT_CORRECTION_APPROVED
                    : AuditEntry.ACTION_CREDIT_CORRECTION_REJECTED);
          }
          return view(correction);
        });
  }

  public void recoverSettlement(
      UUID identityId, UUID previousSettlementId, UUID replacementSettlementId, BigDecimal amount) {
    Objects.requireNonNull(identityId);
    Objects.requireNonNull(previousSettlementId);
    Objects.requireNonNull(replacementSettlementId);
    BigDecimal normalized = amount(amount);
    transactions.executeWithoutResult(
        ignored -> {
          CreditCorrectionRepository.SettlementCredit original =
              repository.settlementCredit(identityId, previousSettlementId).orElseThrow();
          if (original.amount().compareTo(normalized) != 0
              || !repository.replacementMatches(
                  identityId, previousSettlementId, replacementSettlementId)) {
            throw new IllegalStateException("settlement correction does not match prior credit");
          }
          CreditCorrectionRepository.Case requested =
              new CreditCorrectionRepository.Case(
                  UUID.randomUUID(),
                  identityId,
                  Kind.RECOVERY,
                  "SETTLEMENT",
                  Reason.SETTLEMENT_CORRECTION,
                  normalized,
                  null,
                  null,
                  null,
                  original.entryId(),
                  previousSettlementId,
                  replacementSettlementId,
                  null,
                  null,
                  null,
                  null,
                  "CC-" + replacementSettlementId,
                  "settlement-correction:" + replacementSettlementId,
                  Instant.now());
          CreditCorrectionRepository.Case saved = repository.insertCase(requested);
          if (!saved.identityId().equals(identityId)
              || !saved.previousSettlementId().equals(previousSettlementId)
              || saved.amount().compareTo(normalized) != 0) {
            throw new IllegalStateException("settlement correction key was reused");
          }
          if (saved.caseId().equals(requested.caseId())) {
            ledger.recoverPending(identityId);
            record(null, saved, AuditEntry.ACTION_CREDIT_CORRECTION_SETTLEMENT);
          }
        });
  }

  @Override
  public View findForOwner(UUID identityId, UUID caseId) {
    Objects.requireNonNull(identityId);
    Objects.requireNonNull(caseId);
    CreditCorrectionRepository.Case correction = repository.findCase(caseId).orElseThrow();
    if (!correction.identityId().equals(identityId)) throw new java.util.NoSuchElementException();
    return view(correction);
  }

  @Override
  public List<View> listForOwner(UUID identityId) {
    Objects.requireNonNull(identityId);
    return repository.listForOwner(identityId).stream().map(this::view).toList();
  }

  @Override
  public boolean hasPending(UUID identityId) {
    Objects.requireNonNull(identityId);
    return repository.hasPending(identityId);
  }

  private View view(CreditCorrectionRepository.Case correction) {
    CreditCorrectionRepository.Decision decision =
        repository.findDecision(correction.caseId()).orElse(null);
    BigDecimal recovered = repository.recovered(correction.caseId());
    BigDecimal pending =
        correction.kind() == Kind.RECOVERY
                && ("SETTLEMENT".equals(correction.origin())
                    || (decision != null && "APPROVED".equals(decision.decision())))
            ? correction.amount().subtract(recovered)
            : BigDecimal.ZERO.setScale(2);
    Status status =
        decision == null && "ADMINISTRATIVE".equals(correction.origin())
            ? Status.PROPOSED
            : decision != null && "REJECTED".equals(decision.decision())
                ? Status.REJECTED
                : correction.kind() == Kind.CREDIT
                    ? Status.APPROVED
                    : pending.signum() > 0 ? Status.PENDING : Status.RECOVERED;
    return new View(
        correction.caseId(),
        correction.referenceCode(),
        correction.kind(),
        correction.reason(),
        correction.evidenceType(),
        correction.evidenceId(),
        correction.originalEntryId(),
        correction.amount(),
        recovered,
        pending,
        status,
        correction.createdAt());
  }

  private boolean isEffective(CreditCorrectionRepository.Case correction) {
    return "SETTLEMENT".equals(correction.origin())
        || repository
            .findDecision(correction.caseId())
            .map(decision -> "APPROVED".equals(decision.decision()))
            .orElse(false);
  }

  private void requireAdministrator(UUID actorId) {
    if (actorId == null || !identities.hasGlobalRole(actorId, "ADMINISTRADOR_GERAL")) {
      throw new SecurityException("general administrator role is required for credit correction");
    }
  }

  private void record(UUID actorId, CreditCorrectionRepository.Case correction, String action) {
    audit.record(
        AuditEntry.builder()
            .actorIdentityId(actorId)
            .actorRole(actorId == null ? "SYSTEM" : "ADMINISTRADOR_GERAL")
            .targetIdentityId(correction.identityId())
            .action(action)
            .outcome(AuditEntry.OUTCOME_PERMITTED)
            .reason(correction.reason().name())
            .correlationId(correction.referenceCode())
            .idempotencyKey(correction.caseId() + ":" + action)
            .build());
  }

  private static boolean sameProposal(
      CreditCorrectionRepository.Case stored, CreditCorrectionRepository.Case proposed) {
    return stored.identityId().equals(proposed.identityId())
        && stored.kind() == proposed.kind()
        && stored.reason() == proposed.reason()
        && stored.amount().compareTo(proposed.amount()) == 0
        && stored.evidenceType() == proposed.evidenceType()
        && Objects.equals(stored.evidenceId(), proposed.evidenceId())
        && Objects.equals(stored.contextReferenceId(), proposed.contextReferenceId())
        && Objects.equals(stored.originalEntryId(), proposed.originalEntryId())
        && Objects.equals(stored.supersedesCaseId(), proposed.supersedesCaseId())
        && stored.proposedById().equals(proposed.proposedById())
        && stored.description().equals(proposed.description())
        && stored.calculation().equals(proposed.calculation());
  }

  private static BigDecimal amount(BigDecimal value) {
    BigDecimal normalized = Objects.requireNonNull(value).setScale(2, RoundingMode.HALF_UP);
    if (normalized.signum() <= 0) throw new IllegalArgumentException("amount must be positive");
    return normalized;
  }

  private static void requireText(String value, int maximumLength) {
    if (value == null || value.isBlank() || value.length() > maximumLength) {
      throw new IllegalArgumentException("required correction text is invalid");
    }
  }
}
