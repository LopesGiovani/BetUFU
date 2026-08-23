package br.com.betufu.platform.session;

import java.util.Objects;

import br.com.betufu.audit.AuditEntry;
import br.com.betufu.audit.AuditRecorder;
import br.com.betufu.platform.session.events.OperationalRoleChanged;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Profile("runtime | integrated")
public class RoleChangeSessionTerminationListener {

    private final IdentitySessionTerminationService terminationService;
    private final AuditRecorder auditRecorder;

    public RoleChangeSessionTerminationListener(
            IdentitySessionTerminationService terminationService) {
        this(terminationService, null);
    }

    @Autowired
    public RoleChangeSessionTerminationListener(
            IdentitySessionTerminationService terminationService,
            AuditRecorder auditRecorder) {
        this.terminationService = Objects.requireNonNull(
                terminationService, "terminationService must not be null");
        this.auditRecorder = auditRecorder;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOperationalRoleChanged(OperationalRoleChanged event) {
        if (event == null || event.targetSubject() == null || event.targetSubject().isBlank()) {
            return;
        }

        IdentitySessionTerminationService.TerminationSummary summary =
                terminationService.terminateAllSessionsFor(
                        event.targetSubject(),
                        SessionTerminationReason.ADMINISTRATIVE);

        if (auditRecorder != null && !summary.isFullyRevoked()) {
            String action = "CONCEDIDO".equals(event.action())
                    ? AuditEntry.ACTION_PAPEL_CONCEDIDO
                    : AuditEntry.ACTION_PAPEL_REVOGADO;

            auditRecorder.record(AuditEntry.builder()
                    .actorIdentityId(event.actorIdentityId())
                    .targetIdentityId(event.targetIdentityId())
                    .action(action)
                    .outcome(AuditEntry.OUTCOME_BLOCKED)
                    .reason("Encerramento de sessão pós-commit incompleto: total=" + summary.totalTerminated()
                            + "; unrevoked=" + summary.unrevokedOutcomes().size()
                            + "; failures=" + summary.revocationFailures().size())
                    .correlationId(MDC.get("correlationId"))
                    .build());
        }
    }
}
