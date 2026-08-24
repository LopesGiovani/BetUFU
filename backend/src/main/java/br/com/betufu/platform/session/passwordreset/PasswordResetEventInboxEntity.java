package br.com.betufu.platform.session.passwordreset;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * JPA entity representing a durable inbox record in {@code public.password_reset_event_inbox}.
 *
 * <p>Guarantees idempotency and traceability for password reset events received from the Keycloak outbox.
 */
@Entity
@Access(AccessType.FIELD)
@Table(
        name = "password_reset_event_inbox",
        schema = "public",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_password_reset_event_inbox_source_event",
                columnNames = {"source_event_id"}))
public class PasswordResetEventInboxEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CLAIMED = "CLAIMED";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_PROCESSED = "PROCESSED";

    @Id
    @Column(name = "inbox_id", nullable = false, updatable = false)
    private UUID inboxId;

    @Column(name = "source_event_id", nullable = false, updatable = false, length = 255)
    private String sourceEventId;

    @Column(name = "subject", nullable = false, updatable = false, length = 255)
    private String subject;

    @Column(name = "realm_id", nullable = false, updatable = false, length = 255)
    private String realmId;

    @Column(name = "event_type", updatable = false, length = 100)
    private String eventType;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false,
            columnDefinition = "timestamptz(6)")
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false,
            columnDefinition = "timestamptz(6)")
    private Instant updatedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(
            name = "claimed_at",
            columnDefinition = "timestamptz(6)")
    private Instant claimedAt;

    @Column(name = "claim_instance_id", length = 255)
    private String claimInstanceId;

    @Column(
            name = "delivered_at",
            columnDefinition = "timestamptz(6)")
    private Instant deliveredAt;

    @Column(name = "last_error", length = 1024)
    private String lastError;

    protected PasswordResetEventInboxEntity() {}

    public PasswordResetEventInboxEntity(
            UUID inboxId,
            String sourceEventId,
            String subject,
            String realmId,
            String eventType,
            String status,
            Instant createdAt,
            Instant updatedAt,
            int attemptCount,
            Instant claimedAt,
            String claimInstanceId,
            Instant deliveredAt,
            String lastError) {
        this.inboxId = inboxId;
        this.sourceEventId = sourceEventId;
        this.subject = subject;
        this.realmId = realmId;
        this.eventType = eventType;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.attemptCount = attemptCount;
        this.claimedAt = claimedAt;
        this.claimInstanceId = claimInstanceId;
        this.deliveredAt = deliveredAt;
        this.lastError = lastError;
    }

    public UUID getInboxId() {
        return inboxId;
    }

    public String getSourceEventId() {
        return sourceEventId;
    }

    public String getSubject() {
        return subject;
    }

    public String getRealmId() {
        return realmId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public String getClaimInstanceId() {
        return claimInstanceId;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void markClaimed(String instanceId, Instant claimedAt) {
        this.status = STATUS_CLAIMED;
        this.claimInstanceId = Objects.requireNonNull(instanceId, "instanceId must not be null");
        this.claimedAt = Objects.requireNonNull(claimedAt, "claimedAt must not be null");
        this.attemptCount++;
        this.updatedAt = claimedAt;
    }

    public void markDelivered(Instant deliveredAt) {
        this.status = STATUS_DELIVERED;
        this.deliveredAt = Objects.requireNonNull(deliveredAt, "deliveredAt must not be null");
        this.claimInstanceId = null;
        this.lastError = null;
        this.updatedAt = deliveredAt;
    }

    public void markFailed(String errorSummary, Instant failedAt) {
        this.status = STATUS_FAILED;
        this.lastError = errorSummary;
        this.claimInstanceId = null;
        this.updatedAt = Objects.requireNonNull(failedAt, "failedAt must not be null");
    }

    public void resetForReplay(Instant now) {
        this.status = STATUS_PENDING;
        this.claimInstanceId = null;
        this.claimedAt = null;
        this.lastError = null;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }
}
