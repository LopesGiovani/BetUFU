package br.com.betufu.odds.internal.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** JDBC boundary for odds lineage facts. Historical rows are never updated in application code. */
@Repository
@ConditionalOnBean(JdbcTemplate.class)
public class OddsMarketRepository {

  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate transactions;

  @Autowired
  public OddsMarketRepository(
      JdbcTemplate jdbcTemplate, PlatformTransactionManager transactionManager) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager));
  }

  public OddsMarketRepository(JdbcTemplate jdbcTemplate) {
    this(
        jdbcTemplate,
        new DataSourceTransactionManager(Objects.requireNonNull(jdbcTemplate.getDataSource())));
  }

  public Optional<OddsMarketRecord> findMarket(UUID marketId) {
    if (marketId == null) return Optional.empty();
    return jdbcTemplate.query(
        "SELECT market_id, event_id, market_type, current_version_id, lock_version, created_at, updated_at FROM public.odds_market WHERE market_id = ?",
        rs ->
            rs.next()
                ? Optional.of(
                    new OddsMarketRecord(
                        rs.getObject("market_id", UUID.class),
                        rs.getObject("event_id", UUID.class),
                        rs.getString("market_type"),
                        rs.getObject("current_version_id", UUID.class),
                        rs.getLong("lock_version"),
                        instant(rs.getTimestamp("created_at")),
                        instant(rs.getTimestamp("updated_at"))))
                : Optional.empty(),
        marketId);
  }

  public OddsMarketRecord saveMarket(OddsMarketRecord record) {
    Objects.requireNonNull(record, "record must not be null");
    return transactions.execute(
        status -> {
          jdbcTemplate.update(
              "INSERT INTO public.odds_market (market_id, event_id, market_type, current_version_id, lock_version, created_at, updated_at) VALUES (?, ?, ?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP(6)), COALESCE(?, CURRENT_TIMESTAMP(6))) ON CONFLICT (market_id) DO NOTHING",
              record.marketId(),
              record.eventId(),
              record.marketType(),
              null,
              record.lockVersion(),
              record.createdAt() == null ? null : Timestamp.from(record.createdAt()),
              record.updatedAt() == null ? null : Timestamp.from(record.updatedAt()));
          if (record.currentVersionId() != null) {
            jdbcTemplate.update(
                "UPDATE public.odds_market SET current_version_id = ? WHERE market_id = ?",
                record.currentVersionId(),
                record.marketId());
          }
          return findMarket(record.marketId()).orElseThrow();
        });
  }

  public OddsMarketRecord saveMarket(
      OddsMarketRecord record, UUID actorId, String idempotencyKey, String payloadDigest) {
    return idempotent(
        "MARKET",
        actorId,
        idempotencyKey,
        payloadDigest,
        new IdempotentOperation<>(record.marketId(), () -> saveMarket(record), this::findMarket));
  }

  public boolean advanceMarketHead(UUID marketId, UUID versionId, long expectedLockVersion) {
    Objects.requireNonNull(marketId, "marketId must not be null");
    return transactions.execute(
        status -> {
          jdbcTemplate.queryForObject(
              "SELECT market_id FROM public.odds_market WHERE market_id = ? FOR UPDATE",
              UUID.class,
              marketId);
          return jdbcTemplate.update(
                  "UPDATE public.odds_market SET current_version_id = ?, lock_version = lock_version + 1, updated_at = CURRENT_TIMESTAMP(6) WHERE market_id = ? AND lock_version = ?",
                  versionId,
                  marketId,
                  expectedLockVersion)
              == 1;
        });
  }

  public Optional<OddsCalculationRecord> findCalculation(UUID calculationId) {
    if (calculationId == null) return Optional.empty();
    return jdbcTemplate.query(
        "SELECT calculation_id, market_version_id, algorithm_version, configuration_digest, input_snapshot::text, confirmed_version_id, fair_probability, confidence, created_at, known_at FROM public.odds_calculation WHERE calculation_id = ?",
        rs ->
            rs.next()
                ? Optional.of(
                    new OddsCalculationRecord(
                        rs.getObject("calculation_id", UUID.class),
                        rs.getObject("market_version_id", UUID.class),
                        rs.getString("algorithm_version"),
                        rs.getString("configuration_digest"),
                        rs.getString("input_snapshot"),
                        rs.getObject("confirmed_version_id", UUID.class),
                        rs.getBigDecimal("fair_probability"),
                        rs.getBigDecimal("confidence"),
                        instant(rs.getTimestamp("created_at")),
                        instant(rs.getTimestamp("known_at"))))
                : Optional.empty(),
        calculationId);
  }

  public OddsCalculationRecord saveCalculation(OddsCalculationRecord record) {
    Objects.requireNonNull(record, "record must not be null");
    return transactions.execute(
        status -> {
          jdbcTemplate.update(
              "INSERT INTO public.odds_calculation (calculation_id, market_version_id, algorithm_version, configuration_digest, input_snapshot, confirmed_version_id, fair_probability, confidence, created_at, known_at) VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP(6)), COALESCE(?, CURRENT_TIMESTAMP(6)))",
              record.calculationId(),
              record.marketVersionId(),
              record.algorithmVersion(),
              record.configurationDigest(),
              record.inputSnapshot(),
              record.confirmedVersionId(),
              record.fairProbability(),
              record.confidence(),
              record.createdAt() == null ? null : Timestamp.from(record.createdAt()),
              record.knownAt() == null ? null : Timestamp.from(record.knownAt()));
          return findCalculation(record.calculationId()).orElseThrow();
        });
  }

  public OddsCalculationRecord saveCalculation(
      OddsCalculationRecord record, UUID actorId, String idempotencyKey, String payloadDigest) {
    return idempotent(
        "CALCULATION",
        actorId,
        idempotencyKey,
        payloadDigest,
        new IdempotentOperation<>(
            record.calculationId(), () -> saveCalculation(record), this::findCalculation));
  }

  public Optional<OddsPublicationRecord> findPublication(UUID publicationId) {
    if (publicationId == null) return Optional.empty();
    return jdbcTemplate.query(
        "SELECT publication_id, calculation_id, margin, rounding_mode, published_odds, supersedes_publication_id, base_lock_version, published_at FROM public.odds_publication WHERE publication_id = ?",
        rs ->
            rs.next()
                ? Optional.of(
                    new OddsPublicationRecord(
                        rs.getObject("publication_id", UUID.class),
                        rs.getObject("calculation_id", UUID.class),
                        rs.getBigDecimal("margin"),
                        rs.getString("rounding_mode"),
                        rs.getBigDecimal("published_odds"),
                        rs.getObject("supersedes_publication_id", UUID.class),
                        rs.getLong("base_lock_version"),
                        instant(rs.getTimestamp("published_at"))))
                : Optional.empty(),
        publicationId);
  }

  public OddsPublicationRecord savePublication(OddsPublicationRecord record) {
    return transactions.execute(
        status -> {
          jdbcTemplate.update(
              "INSERT INTO public.odds_publication (publication_id, calculation_id, margin, rounding_mode, published_odds, supersedes_publication_id, base_lock_version, published_at) VALUES (?, ?, ?, ?, ?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP(6)))",
              record.publicationId(),
              record.calculationId(),
              record.margin(),
              record.roundingMode(),
              record.publishedOdds(),
              record.supersedesPublicationId(),
              record.baseLockVersion(),
              record.publishedAt() == null ? null : Timestamp.from(record.publishedAt()));
          return findPublication(record.publicationId()).orElseThrow();
        });
  }

  public OddsPublicationRecord savePublication(
      OddsPublicationRecord record, UUID actorId, String idempotencyKey, String payloadDigest) {
    return idempotent(
        "PUBLICATION",
        actorId,
        idempotencyKey,
        payloadDigest,
        new IdempotentOperation<>(
            record.publicationId(), () -> savePublication(record), this::findPublication));
  }

  public Optional<OddsLifecycleRecord> findLifecycle(UUID lifecycleFactId) {
    if (lifecycleFactId == null) return Optional.empty();
    return jdbcTemplate.query(
        "SELECT lifecycle_fact_id, market_id, from_state, to_state, reason, actor_id, occurred_at FROM public.odds_lifecycle WHERE lifecycle_fact_id = ?",
        rs ->
            rs.next()
                ? Optional.of(
                    new OddsLifecycleRecord(
                        rs.getObject("lifecycle_fact_id", UUID.class),
                        rs.getObject("market_id", UUID.class),
                        rs.getString("from_state"),
                        rs.getString("to_state"),
                        rs.getString("reason"),
                        rs.getObject("actor_id", UUID.class),
                        instant(rs.getTimestamp("occurred_at"))))
                : Optional.empty(),
        lifecycleFactId);
  }

  public OddsLifecycleRecord saveLifecycle(OddsLifecycleRecord record) {
    return transactions.execute(
        status -> {
          jdbcTemplate.update(
              "INSERT INTO public.odds_lifecycle (lifecycle_fact_id, market_id, from_state, to_state, reason, actor_id, occurred_at) VALUES (?, ?, ?, ?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP(6)))",
              record.lifecycleFactId(),
              record.marketId(),
              record.fromState(),
              record.toState(),
              record.reason(),
              record.actorId(),
              record.occurredAt() == null ? null : Timestamp.from(record.occurredAt()));
          return findLifecycle(record.lifecycleFactId()).orElseThrow();
        });
  }

  public OddsLifecycleRecord saveLifecycle(
      OddsLifecycleRecord record, UUID actorId, String idempotencyKey, String payloadDigest) {
    return idempotent(
        "LIFECYCLE",
        actorId,
        idempotencyKey,
        payloadDigest,
        new IdempotentOperation<>(
            record.lifecycleFactId(), () -> saveLifecycle(record), this::findLifecycle));
  }

  public Optional<OddsOverrideRecord> findOverride(UUID overrideId) {
    if (overrideId == null) return Optional.empty();
    return jdbcTemplate.query(
        "SELECT override_id, market_id, calculation_id, proposer_id, approver_id, reason, decision, created_at FROM public.odds_override WHERE override_id = ?",
        rs ->
            rs.next()
                ? Optional.of(
                    new OddsOverrideRecord(
                        rs.getObject("override_id", UUID.class),
                        rs.getObject("market_id", UUID.class),
                        rs.getObject("calculation_id", UUID.class),
                        rs.getObject("proposer_id", UUID.class),
                        rs.getObject("approver_id", UUID.class),
                        rs.getString("reason"),
                        rs.getString("decision"),
                        instant(rs.getTimestamp("created_at"))))
                : Optional.empty(),
        overrideId);
  }

  public OddsOverrideRecord saveOverride(OddsOverrideRecord record) {
    return transactions.execute(
        status -> {
          jdbcTemplate.update(
              "INSERT INTO public.odds_override (override_id, market_id, calculation_id, proposer_id, approver_id, reason, decision, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, COALESCE(?, CURRENT_TIMESTAMP(6)))",
              record.overrideId(),
              record.marketId(),
              record.calculationId(),
              record.proposerId(),
              record.approverId(),
              record.reason(),
              record.decision(),
              record.createdAt() == null ? null : Timestamp.from(record.createdAt()));
          return findOverride(record.overrideId()).orElseThrow();
        });
  }

  public OddsOverrideRecord saveOverride(
      OddsOverrideRecord record, UUID actorId, String idempotencyKey, String payloadDigest) {
    return idempotent(
        "OVERRIDE",
        actorId,
        idempotencyKey,
        payloadDigest,
        new IdempotentOperation<>(
            record.overrideId(), () -> saveOverride(record), this::findOverride));
  }

  private <T> T idempotent(
      String commandType,
      UUID actorId,
      String idempotencyKey,
      String payloadDigest,
      IdempotentOperation<T> operation) {
    Objects.requireNonNull(actorId, "actorId must not be null");
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("idempotencyKey must not be blank");
    }
    if (payloadDigest == null || payloadDigest.isBlank()) {
      throw new IllegalArgumentException("payloadDigest must not be blank");
    }
    return transactions.execute(
        status -> {
          jdbcTemplate.query(
              "SELECT pg_advisory_xact_lock(hashtextextended(?::text, 0))",
              rs -> null,
              actorId + ":" + commandType + ":" + idempotencyKey);
          Optional<IdempotencyReceipt> existing =
              jdbcTemplate.query(
                  "SELECT payload_digest, result_reference FROM public.odds_idempotency_command WHERE actor_id = ? AND command_type = ? AND idempotency_key = ? FOR UPDATE",
                  rs ->
                      rs.next()
                          ? Optional.of(
                              new IdempotencyReceipt(
                                  rs.getString("payload_digest"),
                                  rs.getObject("result_reference", UUID.class)))
                          : Optional.empty(),
                  actorId,
                  commandType,
                  idempotencyKey);
          if (existing.isPresent()) {
            IdempotencyReceipt receipt = existing.get();
            if (!receipt.payloadDigest().equals(payloadDigest)) {
              throw new IllegalStateException(
                  "idempotency key was reused with a different payload");
            }
            return operation
                .find()
                .apply(receipt.resultReference())
                .orElseThrow(
                    () ->
                        new IllegalStateException("idempotency receipt references a missing fact"));
          }
          T value = operation.create().get();
          jdbcTemplate.update(
              "INSERT INTO public.odds_idempotency_command (actor_id, command_type, idempotency_key, payload_digest, result_reference) VALUES (?, ?, ?, ?, ?)",
              actorId,
              commandType,
              idempotencyKey,
              payloadDigest,
              operation.resultReference());
          return value;
        });
  }

  private record IdempotencyReceipt(String payloadDigest, UUID resultReference) {}

  private record IdempotentOperation<T>(
      UUID resultReference, Supplier<T> create, Function<UUID, Optional<T>> find) {}

  private static Instant instant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }
}
