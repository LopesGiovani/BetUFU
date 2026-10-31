package br.com.betufu.privacy;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Producers own their storage and expose only the restricted minimum needed by privacy. */
public interface PrivacyRestrictedRecordsPort {
  enum DataClass {
    COMMON_MESSAGES,
    MODERATION_EVIDENCE,
    SANCTION_PROGRESSION,
    ATHLETE_WITHOUT_ACCOUNT,
    ENROLLMENT,
    LEDGER,
    SETTLEMENT,
    RIGHTS,
    IDENTITY,
    AUDIT,
    LOGS,
    BACKUPS
  }

  record Subject(UUID identityId, UUID athleteId) {
    public Subject {
      if ((identityId == null) == (athleteId == null))
        throw new IllegalArgumentException("Exactly one verified subject is required");
    }
  }

  record RestrictedRecord(String reference, Map<String, String> fields) {
    public RestrictedRecord {
      fields = Map.copyOf(fields);
    }
  }

  Set<DataClass> supportedClasses();

  List<RestrictedRecord> export(Subject subject, DataClass dataClass);

  default RestrictedRecord redactForServing(DataClass dataClass, RestrictedRecord record) {
    return record;
  }

  interface Chat extends PrivacyRestrictedRecordsPort {
    record Target(DataClass dataClass, UUID recordId, Instant start, boolean blocked) {}

    boolean rulesDismissed(UUID identityId);

    List<Target> retentionTargets();

    void lockRetentionTargets();

    void suppressServing(DataClass dataClass, UUID messageId, long cursor);

    void servingChanged(UUID messageId);
  }
}
