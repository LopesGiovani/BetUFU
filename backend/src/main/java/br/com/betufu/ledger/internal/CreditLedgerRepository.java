package br.com.betufu.ledger.internal;

import br.com.betufu.ledger.CreditLedgerPort.EntryType;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** JDBC persistence boundary for the authoritative, append-only credit ledger. */
@Repository
@Profile("runtime | integrated")
public class CreditLedgerRepository {

  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate transactions;

  public CreditLedgerRepository(JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  public CreditLedgerRepository(JdbcTemplate jdbcTemplate) {
    this(jdbcTemplate, new org.springframework.jdbc.datasource.DataSourceTransactionManager(
        Objects.requireNonNull(jdbcTemplate.getDataSource())));
  }

  public <T> T inTransaction(java.util.function.Supplier<T> action) {
    return transactions.execute(status -> action.get());
  }

  public void ensureAccount(UUID identityId) {
    jdbcTemplate.update(
        "INSERT INTO public.credit_account (identity_id, balance) VALUES (?, 0.00) ON CONFLICT (identity_id) DO NOTHING",
        identityId);
  }

  public BigDecimal lockBalance(UUID identityId) {
    return jdbcTemplate.queryForObject(
        "SELECT balance FROM public.credit_account WHERE identity_id = ? FOR UPDATE",
        BigDecimal.class,
        identityId);
  }

  public Optional<CreditLedgerEntry> findByIdempotencyKey(String key) {
    return jdbcTemplate.query(
        "SELECT entry_id, identity_id, entry_type, direction, amount, source_type, source_id, idempotency_key, created_at FROM public.credit_ledger_entry WHERE idempotency_key = ?",
        rs ->
            rs.next()
                ? Optional.of(
                    new CreditLedgerEntry(
                        rs.getObject("entry_id", UUID.class),
                        rs.getObject("identity_id", UUID.class),
                        EntryType.valueOf(rs.getString("entry_type")),
                        rs.getString("direction"),
                        rs.getBigDecimal("amount"),
                        rs.getString("source_type"),
                        rs.getObject("source_id", UUID.class),
                        rs.getString("idempotency_key"),
                        rs.getTimestamp("created_at").toInstant()))
                : Optional.empty(),
        key);
  }

  public CreditLedgerEntry insertEntry(
      UUID identityId,
      EntryType entryType,
      String direction,
      BigDecimal amount,
      String sourceType,
      UUID sourceId,
      String idempotencyKey) {
    UUID entryId = UUID.randomUUID();
    try {
      jdbcTemplate.update(
          "INSERT INTO public.credit_ledger_entry (entry_id, identity_id, entry_type, direction, amount, source_type, source_id, idempotency_key) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
          entryId,
          identityId,
          entryType.name(),
          direction,
          amount,
          sourceType,
          sourceId,
          idempotencyKey);
    } catch (DuplicateKeyException ex) {
      return findByIdempotencyKey(idempotencyKey).orElseThrow(() -> ex);
    }
    return findByIdempotencyKey(idempotencyKey).orElseThrow();
  }

  public void updateBalance(UUID identityId, BigDecimal balance) {
    jdbcTemplate.update(
        "UPDATE public.credit_account SET balance = ?, updated_at = CURRENT_TIMESTAMP(6) WHERE identity_id = ?",
        balance,
        identityId);
  }

  public record CreditLedgerEntry(
      UUID entryId,
      UUID identityId,
      EntryType entryType,
      String direction,
      BigDecimal amount,
      String sourceType,
      UUID sourceId,
      String idempotencyKey,
      Instant createdAt) {}
}
