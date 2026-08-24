package br.com.betufu.platform.session.passwordreset;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Resposta de consulta e reprocessamento administrativo de eventos de redefinição de senha.
 *
 * <p>Contém apenas metadados seguros de estado e correlação da recuperação, sem expor
 * segredos, tokens, cookies, dados pessoais do titular ou identificadores internos.
 */
public record PasswordResetEventRecoveryResponse(
        String sourceEventId,
        String status,
        boolean effectExecuted,
        int terminatedSessionCount,
        UUID actorIdentityId,
        Instant processedAt,
        String lastError
) {

    public PasswordResetEventRecoveryResponse {
        Objects.requireNonNull(sourceEventId, "sourceEventId must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    public PasswordResetEventRecoveryResponse(
            String sourceEventId,
            String status,
            boolean effectExecuted,
            int terminatedSessionCount,
            UUID actorIdentityId,
            Instant processedAt) {
        this(sourceEventId, status, effectExecuted, terminatedSessionCount, actorIdentityId, processedAt, null);
    }

    public static PasswordResetEventRecoveryResponse fromEntity(PasswordResetEventInboxEntity entity) {
        Objects.requireNonNull(entity, "entity must not be null");
        return new PasswordResetEventRecoveryResponse(
                entity.getSourceEventId(),
                entity.getStatus(),
                PasswordResetEventInboxEntity.STATUS_DELIVERED.equals(entity.getStatus()),
                0,
                null,
                entity.getUpdatedAt() != null ? entity.getUpdatedAt() : entity.getCreatedAt(),
                entity.getLastError()
        );
    }

    public static PasswordResetEventRecoveryResponse fromResult(
            PasswordResetEventInboxService.InboxProcessingResult result,
            UUID actorIdentityId,
            Instant processedAt) {
        Objects.requireNonNull(result, "result must not be null");
        return new PasswordResetEventRecoveryResponse(
                result.sourceEventId(),
                result.status(),
                result.effectExecuted(),
                result.terminatedSessionCount(),
                actorIdentityId,
                processedAt,
                null
        );
    }
}
