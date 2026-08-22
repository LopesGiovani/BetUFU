package br.com.betufu.keycloak.password;

import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Access(AccessType.FIELD)
@Table(
        name = "betufu_password_reset_outbox",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_betufu_password_reset_outbox_source_event",
                    columnNames = {"source_event_id"})
        })
@NamedQueries({
    @NamedQuery(
            name = "findPasswordResetOutboxBySourceEventId",
            query = "SELECT o FROM PasswordResetOutboxEntity o WHERE o.sourceEventId = :sourceEventId"),
    @NamedQuery(
            name = "findPendingPasswordResetOutbox",
            query = "SELECT o FROM PasswordResetOutboxEntity o WHERE o.status = 'PENDING' ORDER BY o.createdAt ASC")
})
public class PasswordResetOutboxEntity {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CLAIMED = "CLAIMED";
    public static final String STATUS_DELIVERED = "DELIVERED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "source_event_id", nullable = false, updatable = false, length = 255)
    private String sourceEventId;

    @Column(name = "subject", nullable = false, updatable = false, length = 255)
    private String subject;

    @Column(name = "realm_id", nullable = false, updatable = false, length = 255)
    private String realmId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 100)
    private String eventType;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "claim_instance_id", length = 255)
    private String claimInstanceId;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "last_error", length = 1024)
    private String lastError;

    protected PasswordResetOutboxEntity() {}

    public PasswordResetOutboxEntity(
            String sourceEventId,
            String subject,
            String realmId,
            String eventType,
            Instant createdAt) {
        this.id = UUID.randomUUID().toString();
        this.sourceEventId = Objects.requireNonNull(sourceEventId, "sourceEventId must not be null");
        this.subject = Objects.requireNonNull(subject, "subject must not be null");
        this.realmId = Objects.requireNonNull(realmId, "realmId must not be null");
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.status = STATUS_PENDING;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = this.createdAt;
        this.attemptCount = 0;
    }

    public String getId() {
        return id;
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

    public void setStatus(String status) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.updatedAt = Instant.now();
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

    public void incrementAttemptCount() {
        this.attemptCount++;
        this.updatedAt = Instant.now();
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public void setClaimedAt(Instant claimedAt) {
        this.claimedAt = claimedAt;
    }

    public String getClaimInstanceId() {
        return claimInstanceId;
    }

    public void setClaimInstanceId(String claimInstanceId) {
        this.claimInstanceId = claimInstanceId;
    }

    public Instant getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(Instant deliveredAt) {
        this.deliveredAt = deliveredAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PasswordResetOutboxEntity that = (PasswordResetOutboxEntity) o;
        return Objects.equals(sourceEventId, that.sourceEventId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceEventId);
    }
}
