package br.com.betufu.privacy.internal;

import br.com.betufu.platform.api.ApiErrorCode;
import br.com.betufu.platform.api.ApiProblemException;
import br.com.betufu.privacy.PrivacyRestrictedRecordsPort;
import br.com.betufu.privacy.PrivacyRestrictedRecordsPort.DataClass;
import br.com.betufu.privacy.PrivacySubjectPort;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("runtime | integrated")
public class PrivacyRetentionService {
  public record Entry(
      DataClass dataClass, UUID recordId, Instant start, Instant deadline, String state) {}

  public record Inventory(Instant evaluatedAt, String digest, List<Entry> entries) {
    public Inventory {
      entries = List.copyOf(entries);
    }
  }

  private final PrivacyRestrictedRecordsPort.Chat chat;
  private final PrivacyRetentionRegistry registry;
  private final PrivacySubjectPort.Identity identities;
  private final JdbcTemplate jdbc;
  private final Clock clock;

  public PrivacyRetentionService(
      PrivacyRestrictedRecordsPort.Chat chat,
      PrivacyRetentionRegistry registry,
      PrivacySubjectPort.Identity identities,
      JdbcTemplate jdbc,
      Clock clock) {
    this.chat = chat;
    this.registry = registry;
    this.identities = identities;
    this.jdbc = jdbc;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Inventory dryRun(UUID administrator) {
    requireAdmin(administrator);
    Instant now = clock.instant();
    List<Entry> entries = new ArrayList<>();
    for (var target : chat.retentionTargets()) {
      Integer days = PrivacyRetentionRegistry.days(target.dataClass());
      Instant deadline =
          days == null || target.start() == null
              ? null
              : target.start().plus(days, ChronoUnit.DAYS);
      String state =
          days == null
              ? "POLICY_BLOCKED"
              : registry.suppressed(target.dataClass(), target.recordId())
                  ? "SUPPRESSED"
                  : target.blocked()
                          || registry.held(target.dataClass(), target.recordId())
                          || deadline == null
                      ? "HELD"
                      : now.isBefore(deadline) ? "RETAINED" : "ELIGIBLE";
      entries.add(
          new Entry(target.dataClass(), target.recordId(), target.start(), deadline, state));
    }
    // Pending classes remain explicit even when they have no disposable targets.
    for (DataClass type : DataClass.values())
      if (PrivacyRetentionRegistry.days(type) == null)
        entries.add(new Entry(type, null, null, null, "POLICY_BLOCKED"));
    entries.sort(
        Comparator.comparing((Entry e) -> e.dataClass().name())
            .thenComparing(e -> String.valueOf(e.recordId())));
    String canonical =
        PrivacyRetentionRegistry.POLICY
            + "\n"
            + entries.stream().map(Entry::toString).reduce("", (a, b) -> a + b + "\n");
    return new Inventory(now, PrivacyRetentionRegistry.sha256(canonical), entries);
  }

  /**
   * Operator-only entry point. Runtime has no retention-role membership; no scheduler invokes this.
   */
  @Transactional
  public Inventory apply(
      UUID administrator, String approvedDigest, List<UUID> messageIds, DataClass type) {
    requireAdmin(administrator);
    if (PrivacyRetentionRegistry.days(type) == null
        || messageIds == null
        || messageIds.isEmpty()
        || messageIds.stream().anyMatch(java.util.Objects::isNull)
        || messageIds.stream().distinct().count() != messageIds.size()) throw conflict();
    if (!Boolean.TRUE.equals(
        jdbc.queryForObject(
            "SELECT CASE WHEN EXISTS(SELECT FROM pg_roles WHERE rolname='betufu_privacy_retention') THEN pg_has_role(current_user,'betufu_privacy_retention','MEMBER') ELSE false END",
            Boolean.class)))
      throw new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
    if (!org.springframework.transaction.support.TransactionSynchronizationManager
        .isActualTransactionActive()) throw conflict();
    registry.lock();
    chat.lockRetentionTargets();
    requireAdmin(administrator);
    Inventory current = dryRun(administrator);
    if (!current.digest().equals(approvedDigest)) throw conflict();
    for (UUID id : messageIds)
      if (current.entries().stream()
          .noneMatch(
              e ->
                  e.dataClass() == type && id.equals(e.recordId()) && e.state().equals("ELIGIBLE")))
        throw conflict();
    for (UUID id : messageIds) {
      registry.record(type, id, administrator, current.digest());
      chat.suppressServing(type, id, registry.cursor(type, id));
      chat.servingChanged(id);
    }
    return dryRun(administrator);
  }

  @Transactional(readOnly = true)
  public List<java.util.Map<String, Object>> currentRegister(UUID administrator) {
    requireAdmin(administrator);
    return registry.currentRegister();
  }

  private void requireAdmin(UUID actor) {
    if (actor == null || !identities.isGeneralAdministrator(actor))
      throw new ApiProblemException(HttpStatus.FORBIDDEN, ApiErrorCode.FORBIDDEN);
  }

  private static ApiProblemException conflict() {
    return new ApiProblemException(HttpStatus.CONFLICT, ApiErrorCode.VALIDATION_FAILED);
  }
}
