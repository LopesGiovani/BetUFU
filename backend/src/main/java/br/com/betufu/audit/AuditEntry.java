package br.com.betufu.audit;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record AuditEntry(
        UUID actorIdentityId,
        String actorRole,
        UUID targetIdentityId,
        String action,
        String outcome,
        String previousValue,
        String newValue,
        String reason,
        String correlationId,
        String idempotencyKey) {

    public static final String OUTCOME_PERMITTED = "PERMITIDO";
    public static final String OUTCOME_BLOCKED = "BLOQUEADO";

    public static final String ACTION_PAPEL_CONCEDIDO = "PAPEL_CONCEDIDO";
    public static final String ACTION_PAPEL_REVOGADO = "PAPEL_REVOGADO";
    public static final String ACTION_AUTOCONCESSAO_PAPEL = "AUTOCONCESSAO_PAPEL";
    public static final String ACTION_ULTIMO_ADMINISTRADOR_GERAL = "ULTIMO_ADMINISTRADOR_GERAL";
    public static final String ACTION_REVOGACAO_ULTIMO_ADMINISTRADOR = "REVOGACAO_ULTIMO_ADMINISTRADOR";
    public static final String ACTION_REGISTRO_MATRICULA = "REGISTRO_MATRICULA";
    public static final String ACTION_CORRECAO_MATRICULA = "CORRECAO_MATRICULA";
    public static final String ACTION_LOGIN_EFETUADO = "LOGIN_EFETUADO";
    public static final String ACTION_CONSULTA_OPERACIONAL = "CONSULTA_OPERACIONAL";
    public static final String ACTION_SEMENTE_ADMINISTRADOR_INICIAL = "SEMENTE_ADMINISTRADOR_INICIAL";
    public static final String ACTION_REPROCESSAMENTO_RESET_SENHA = "REPROCESSAMENTO_RESET_SENHA";
    public static final String ACTION_VINCULO_ATLETA_DECIDIDO = "VINCULO_ATLETA_DECIDIDO";
    public static final String ACTION_REVISAO_VINCULO_ATLETA_CRIADA = "REVISAO_VINCULO_ATLETA_CRIADA";

    public static final Set<String> ALLOWED_ACTIONS = Set.of(
            ACTION_PAPEL_CONCEDIDO,
            ACTION_PAPEL_REVOGADO,
            ACTION_AUTOCONCESSAO_PAPEL,
            ACTION_ULTIMO_ADMINISTRADOR_GERAL,
            ACTION_REVOGACAO_ULTIMO_ADMINISTRADOR,
            ACTION_REGISTRO_MATRICULA,
            ACTION_CORRECAO_MATRICULA,
            ACTION_LOGIN_EFETUADO,
            ACTION_CONSULTA_OPERACIONAL,
            ACTION_SEMENTE_ADMINISTRADOR_INICIAL,
            ACTION_REPROCESSAMENTO_RESET_SENHA,
            ACTION_VINCULO_ATLETA_DECIDIDO,
            ACTION_REVISAO_VINCULO_ATLETA_CRIADA);

    public AuditEntry {
        Objects.requireNonNull(action, "action must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (action.isBlank()) {
            throw new IllegalArgumentException("action must not be blank");
        }
        if (!ALLOWED_ACTIONS.contains(action)) {
            throw new IllegalArgumentException("action is not recognized in closed vocabulary: " + action);
        }
        if (!OUTCOME_PERMITTED.equals(outcome) && !OUTCOME_BLOCKED.equals(outcome)) {
            throw new IllegalArgumentException("outcome must be PERMITIDO or BLOQUEADO");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID actorIdentityId;
        private String actorRole;
        private UUID targetIdentityId;
        private String action;
        private String outcome;
        private String previousValue;
        private String newValue;
        private String reason;
        private String correlationId;
        private String idempotencyKey;

        public Builder actorIdentityId(UUID actorIdentityId) {
            this.actorIdentityId = actorIdentityId;
            return this;
        }

        public Builder actorRole(String actorRole) {
            this.actorRole = actorRole;
            return this;
        }

        public Builder targetIdentityId(UUID targetIdentityId) {
            this.targetIdentityId = targetIdentityId;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder outcome(String outcome) {
            this.outcome = outcome;
            return this;
        }

        public Builder previousValue(String previousValue) {
            this.previousValue = previousValue;
            return this;
        }

        public Builder newValue(String newValue) {
            this.newValue = newValue;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder correlationId(String correlationId) {
            this.correlationId = correlationId;
            return this;
        }

        public Builder idempotencyKey(String idempotencyKey) {
            this.idempotencyKey = idempotencyKey;
            return this;
        }

        public AuditEntry build() {
            return new AuditEntry(
                    actorIdentityId,
                    actorRole,
                    targetIdentityId,
                    action,
                    outcome,
                    previousValue,
                    newValue,
                    reason,
                    correlationId,
                    idempotencyKey);
        }
    }
}
