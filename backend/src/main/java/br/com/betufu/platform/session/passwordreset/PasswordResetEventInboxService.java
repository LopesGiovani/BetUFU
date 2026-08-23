package br.com.betufu.platform.session.passwordreset;

import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.betufu.platform.session.IdentitySessionTerminationService;
import br.com.betufu.platform.session.SessionTerminationReason;

/**
 * Service responsible for idempotent persistence of password reset events into the PostgreSQL
 * inbox and exclusive delegation to {@link IdentitySessionTerminationService} with reason SECURITY.
 *
 * <p>Concurrency is resolved at the PostgreSQL database level using {@code insert-first} semantics
 * with unique constraint arbitration on {@code source_event_id}.
 */
@Service
@Profile("runtime | integrated")
public class PasswordResetEventInboxService {

    public static final String STATUS_PENDING = PasswordResetEventInboxEntity.STATUS_PENDING;
    public static final String STATUS_CLAIMED = PasswordResetEventInboxEntity.STATUS_CLAIMED;
    public static final String STATUS_DELIVERED = PasswordResetEventInboxEntity.STATUS_DELIVERED;
    public static final String STATUS_FAILED = PasswordResetEventInboxEntity.STATUS_FAILED;
    public static final String STATUS_PROCESSED = PasswordResetEventInboxEntity.STATUS_PROCESSED;

    /**
     * Código estável gravado quando o encerramento de sessões falha. Substitui a classe e a
     * mensagem cruas da exceção, que podem carregar dado do titular ou detalhe de infraestrutura.
     */
    public static final String ERROR_CODE_TERMINATION_FAILED = "TERMINATION_FAILED";

    private final PasswordResetEventInboxRepository repository;
    private final IdentitySessionTerminationService sessionTerminationService;

    public PasswordResetEventInboxService(
            PasswordResetEventInboxRepository repository,
            IdentitySessionTerminationService sessionTerminationService) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.sessionTerminationService = Objects.requireNonNull(
                sessionTerminationService, "sessionTerminationService must not be null");
    }

    public record InboxProcessingResult(
            String sourceEventId,
            String status,
            boolean effectExecuted,
            int terminatedSessionCount
    ) {}

    /**
     * Persists the event in the PostgreSQL inbox first via database-level unique constraint arbitration,
     * and if not already recorded, executes the security session termination effect for the subject.
     *
     * <p>If session termination fails, the failure state is preserved in the database for 02-31 recovery.
     */
    @Transactional(noRollbackFor = RuntimeException.class)
    public InboxProcessingResult processEvent(
            String sourceEventId,
            String subject,
            String realmId,
            String eventType) {
        Objects.requireNonNull(sourceEventId, "sourceEventId must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(realmId, "realmId must not be null");

        if (sourceEventId.isBlank()) {
            throw new IllegalArgumentException("sourceEventId must not be blank");
        }
        if (subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        if (realmId.isBlank()) {
            throw new IllegalArgumentException("realmId must not be blank");
        }

        String effectiveEventType = (eventType != null && !eventType.isBlank()) ? eventType : "UPDATE_CREDENTIAL";

        // 1. Insert-first: database unique constraint arbitrates concurrent deliveries
        int inserted = repository.insertIfAbsent(
                sourceEventId,
                subject,
                realmId,
                effectiveEventType,
                STATUS_PENDING);

        if (inserted == 0) {
            // Duplicate or concurrent delivery caught by database unique constraint
            String currentStatus = repository.findBySourceEventId(sourceEventId)
                    .map(PasswordResetEventInboxEntity::getStatus)
                    .orElse(STATUS_PENDING);
            return new InboxProcessingResult(sourceEventId, currentStatus, false, 0);
        }

        // 2. Exclusive winner executes the security termination effect through 02-09 service
        try {
            IdentitySessionTerminationService.TerminationSummary summary =
                    sessionTerminationService.terminateAllSessionsFor(
                            subject, SessionTerminationReason.SECURITY);

            repository.markDelivered(sourceEventId);

            return new InboxProcessingResult(
                    sourceEventId, STATUS_DELIVERED, true, summary.totalTerminated());
        } catch (RuntimeException ex) {
            // Persistimos um código estável, nunca a exceção crua. A mensagem de uma RuntimeException
            // carrega o que quer que a causa tenha colocado nela — host, SQL, caminho, identificador
            // do titular — e a linha do inbox é lida por operador. O detalhe continua disponível pelo
            // rethrow, que preserva a exceção original para o tratamento e o log da borda.
            repository.markFailed(sourceEventId, ERROR_CODE_TERMINATION_FAILED);
            throw ex;
        }
    }
}
