package br.com.betufu.audit.internal;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

@Entity
@Immutable
@Access(AccessType.FIELD)
@Table(name = "audit_event", schema = "public")
public class AuditEvent {

    @Id
    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(
            name = "occurred_at",
            nullable = false,
            updatable = false,
            columnDefinition = "timestamptz(6)")
    private Instant occurredAt;

    @Column(name = "actor_identity_id", updatable = false)
    private UUID actorIdentityId;

    @Column(name = "actor_role", updatable = false, length = 32)
    private String actorRole;

    @Column(name = "target_identity_id", updatable = false)
    private UUID targetIdentityId;

    @Column(name = "action", nullable = false, updatable = false, length = 64)
    private String action;

    @Column(name = "outcome", nullable = false, updatable = false, length = 16)
    private String outcome;

    @Column(name = "previous_value", updatable = false, length = 2048)
    private String previousValue;

    @Column(name = "new_value", updatable = false, length = 2048)
    private String newValue;

    @Column(name = "reason", updatable = false, length = 1024)
    private String reason;

    @Column(name = "correlation_id", updatable = false, length = 64)
    private String correlationId;

    @Column(name = "idempotency_key", updatable = false, length = 128)
    private String idempotencyKey;

    protected AuditEvent() {}

    public UUID getEventId() {
        return eventId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getActorIdentityId() {
        return actorIdentityId;
    }

    public String getActorRole() {
        return actorRole;
    }

    public UUID getTargetIdentityId() {
        return targetIdentityId;
    }

    public String getAction() {
        return action;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getPreviousValue() {
        return previousValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public String getReason() {
        return reason;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
