package br.com.betufu.platform.session.events;

import java.util.Objects;
import java.util.UUID;

/**
 * Domain event published when an operational role is granted or revoked.
 *
 * <p>Consumed post-commit by the platform module to terminate all active sessions of the target
 * identity without creating a platform -&gt; identity module dependency cycle.
 *
 * @param targetIdentityId the public identifier of the affected identity
 * @param targetSubject the subject / principal name of the affected identity used for session termination
 * @param role the operational role name that was granted or revoked ("ADMINISTRADOR_GERAL", "ADMINISTRADOR_COMPETICAO", "MODERADOR")
 * @param action the action performed ("CONCEDIDO" or "REVOGADO")
 * @param actorIdentityId the public identifier of the actor who performed the change (optional)
 */
public record OperationalRoleChanged(
        UUID targetIdentityId,
        String targetSubject,
        String role,
        String action,
        UUID actorIdentityId) {

    public OperationalRoleChanged {
        Objects.requireNonNull(targetIdentityId, "targetIdentityId must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(action, "action must not be null");
    }

    public OperationalRoleChanged(
            UUID targetIdentityId,
            String targetSubject,
            String role,
            String action) {
        this(targetIdentityId, targetSubject, role, action, null);
    }
}
