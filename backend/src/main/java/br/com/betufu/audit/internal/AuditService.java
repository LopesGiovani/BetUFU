package br.com.betufu.audit.internal;

import br.com.betufu.audit.AuditEntry;
import br.com.betufu.audit.AuditRecorder;
import java.util.Objects;
import org.slf4j.MDC;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("runtime | integrated")
public class AuditService implements AuditRecorder {

  private static final String MDC_CORRELATION_ID_KEY = "correlationId";

  private final AuditEventRepository auditEventRepository;

  public AuditService(AuditEventRepository auditEventRepository) {
    this.auditEventRepository =
        Objects.requireNonNull(auditEventRepository, "auditEventRepository must not be null");
  }

  @Override
  @Transactional
  public void record(AuditEntry entry) {
    Objects.requireNonNull(entry, "entry must not be null");

    String correlationId = entry.correlationId();
    if (correlationId == null || correlationId.isBlank()) {
      correlationId = MDC.get(MDC_CORRELATION_ID_KEY);
    }

    auditEventRepository.insertIfAbsent(
        entry.actorIdentityId(),
        entry.actorRole(),
        entry.targetIdentityId(),
        entry.action(),
        entry.outcome(),
        entry.previousValue(),
        entry.newValue(),
        entry.reason(),
        correlationId,
        entry.idempotencyKey());
  }
}
