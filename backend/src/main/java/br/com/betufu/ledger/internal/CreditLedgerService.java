package br.com.betufu.ledger.internal;

import br.com.betufu.ledger.CreditBalance;
import br.com.betufu.ledger.CreditLedgerPort;
import br.com.betufu.ledger.CreditLedgerPort.EntryType;
import br.com.betufu.ledger.InsufficientCreditException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Transactional application service for grants and opaque debit/compensation facts. */
@Service
@Profile("runtime | integrated")
public class CreditLedgerService implements CreditLedgerPort {

  private static final String INITIAL_GRANT_PREFIX = "credit:initial-grant:";
  private final CreditLedgerRepository repository;

  public CreditLedgerService(CreditLedgerRepository repository) {
    this.repository = Objects.requireNonNull(repository, "repository must not be null");
  }

  @Override
  public CreditBalance getBalance(UUID identityId) {
    requireIdentity(identityId);
    return repository.inTransaction(
        () -> {
          repository.ensureAccount(identityId);
          return balance(identityId, repository.lockBalance(identityId));
        });
  }

  @Override
  public CreditBalance ensureInitialGrant(UUID identityId) {
    requireIdentity(identityId);
    return repository.inTransaction(
        () -> {
          repository.ensureAccount(identityId);
          BigDecimal current = repository.lockBalance(identityId);
          String key = INITIAL_GRANT_PREFIX + identityId;
          Optional<CreditLedgerRepository.CreditLedgerEntry> existing =
              repository.findByIdempotencyKey(key);
          if (existing.isEmpty()) {
            repository.insertEntry(
                identityId,
                EntryType.GRANT_INITIAL,
                "CREDIT",
                INITIAL_GRANT,
                "SYSTEM",
                identityId,
                key);
            current = normalize(current.add(INITIAL_GRANT));
            repository.updateBalance(identityId, current);
          }
          return balance(identityId, current);
        });
  }

  @Override
  public LedgerOperation debit(UUID identityId, BigDecimal amount, UUID sourceId) {
    return debit(identityId, amount, SourceType.PREDICTION.name(), sourceId);
  }

  @Override
  public LedgerOperation debit(
      UUID identityId, BigDecimal amount, String sourceType, UUID sourceId) {
    return mutate(identityId, amount, EntryType.DEBIT, "DEBIT", sourceType, sourceId);
  }

  @Override
  public LedgerOperation compensate(UUID identityId, BigDecimal amount, UUID sourceId) {
    return compensate(identityId, amount, SourceType.COMPENSATION.name(), sourceId);
  }

  @Override
  public LedgerOperation compensate(
      UUID identityId, BigDecimal amount, String sourceType, UUID sourceId) {
    return mutate(identityId, amount, EntryType.COMPENSATION, "CREDIT", sourceType, sourceId);
  }

  private LedgerOperation mutate(
      UUID identityId,
      BigDecimal amount,
      EntryType entryType,
      String direction,
      String sourceType,
      UUID sourceId) {
    requireIdentity(identityId);
    BigDecimal normalized = normalizeAmount(amount);
    SourceType source = parseSource(sourceType, entryType);
    if (sourceId == null) {
      throw new IllegalArgumentException("sourceId must not be null");
    }
    String key = "credit:" + entryType.name().toLowerCase() + ":" + sourceId;
    return repository.inTransaction(
        () -> {
          repository.ensureAccount(identityId);
          BigDecimal current = repository.lockBalance(identityId);
          Optional<CreditLedgerRepository.CreditLedgerEntry> existing =
              repository.findByIdempotencyKey(key);
          if (existing.isPresent()) {
            CreditLedgerRepository.CreditLedgerEntry entry = existing.get();
            if (!entry.identityId().equals(identityId)
                || entry.amount().compareTo(normalized) != 0
                || !entry.sourceType().equals(source.name())) {
              throw new IllegalStateException(
                  "ledger idempotency key was reused with a different payload");
            }
            return new LedgerOperation(entry.entryId(), balance(identityId, current), true);
          }
          if (entryType == EntryType.DEBIT && current.compareTo(normalized) < 0) {
            throw new InsufficientCreditException();
          }
          BigDecimal next =
              entryType == EntryType.DEBIT
                  ? normalize(current.subtract(normalized))
                  : normalize(current.add(normalized));
          CreditLedgerRepository.CreditLedgerEntry entry =
              repository.insertEntry(
                  identityId, entryType, direction, normalized, source.name(), sourceId, key);
          repository.updateBalance(identityId, next);
          return new LedgerOperation(entry.entryId(), balance(identityId, next), false);
        });
  }

  private static SourceType parseSource(String sourceType, EntryType entryType) {
    if (sourceType == null) throw new IllegalArgumentException("sourceType must not be null");
    SourceType source;
    try {
      source = SourceType.valueOf(sourceType.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("unsupported ledger source type", ex);
    }
    if ((entryType == EntryType.DEBIT && source != SourceType.PREDICTION)
        || (entryType == EntryType.COMPENSATION && source != SourceType.COMPENSATION)) {
      throw new IllegalArgumentException("source type is not permitted for ledger operation");
    }
    return source;
  }

  private static BigDecimal normalizeAmount(BigDecimal value) {
    Objects.requireNonNull(value, "amount must not be null");
    if (value.scale() > 2)
      throw new IllegalArgumentException("credit amount scale must be at most 2");
    if (value.signum() <= 0) throw new IllegalArgumentException("credit amount must be positive");
    return value.setScale(2, RoundingMode.UNNECESSARY);
  }

  private static BigDecimal normalize(BigDecimal value) {
    return value.setScale(2, RoundingMode.HALF_UP);
  }

  private static CreditBalance balance(UUID identityId, BigDecimal value) {
    return new CreditBalance(identityId, normalize(value));
  }

  private static void requireIdentity(UUID identityId) {
    Objects.requireNonNull(identityId, "identityId must not be null");
  }
}
