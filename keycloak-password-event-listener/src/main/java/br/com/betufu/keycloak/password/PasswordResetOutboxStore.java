package br.com.betufu.keycloak.password;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.keycloak.connections.jpa.JpaConnectionProvider;
import org.keycloak.models.KeycloakSession;

public class PasswordResetOutboxStore {

    private final EntityManager entityManager;

    public PasswordResetOutboxStore(EntityManager entityManager) {
        this.entityManager = Objects.requireNonNull(entityManager, "entityManager must not be null");
    }

    public PasswordResetOutboxStore(KeycloakSession session) {
        Objects.requireNonNull(session, "session must not be null");
        JpaConnectionProvider jpaProvider = session.getProvider(JpaConnectionProvider.class);
        if (jpaProvider == null) {
            throw new IllegalStateException("JpaConnectionProvider is not available in KeycloakSession");
        }
        this.entityManager = jpaProvider.getEntityManager();
    }

    public PasswordResetOutboxEntity persistPending(
            String sourceEventId,
            String subject,
            String realmId,
            String eventType,
            Instant createdAt) {
        Objects.requireNonNull(sourceEventId, "sourceEventId must not be null");
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(realmId, "realmId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");

        Optional<PasswordResetOutboxEntity> existing = findBySourceEventId(sourceEventId);
        if (existing.isPresent()) {
            return existing.get();
        }

        PasswordResetOutboxEntity entity = new PasswordResetOutboxEntity(
                sourceEventId,
                subject,
                realmId,
                eventType,
                createdAt);
        entityManager.persist(entity);
        return entity;
    }

    public Optional<PasswordResetOutboxEntity> findBySourceEventId(String sourceEventId) {
        Objects.requireNonNull(sourceEventId, "sourceEventId must not be null");
        TypedQuery<PasswordResetOutboxEntity> query = entityManager.createNamedQuery(
                "findPasswordResetOutboxBySourceEventId", PasswordResetOutboxEntity.class);
        query.setParameter("sourceEventId", sourceEventId);
        List<PasswordResetOutboxEntity> results = query.getResultList();
        if (results.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(results.get(0));
    }

    public List<PasswordResetOutboxEntity> findPending(int maxResults) {
        TypedQuery<PasswordResetOutboxEntity> query = entityManager.createNamedQuery(
                "findPendingPasswordResetOutbox", PasswordResetOutboxEntity.class);
        if (maxResults > 0) {
            query.setMaxResults(maxResults);
        }
        return query.getResultList();
    }

    public List<PasswordResetOutboxEntity> findEligibleForClaim(Instant now, Duration leaseDuration, int maxResults) {
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");
        Instant leaseCutoff = now.minus(leaseDuration);
        TypedQuery<PasswordResetOutboxEntity> query = entityManager.createNamedQuery(
                "findEligibleForClaim", PasswordResetOutboxEntity.class);
        query.setParameter("now", now);
        query.setParameter("leaseCutoff", leaseCutoff);
        if (maxResults > 0) {
            query.setMaxResults(maxResults);
        }
        return query.getResultList();
    }

    public boolean claim(String id, String instanceId, Instant now, Duration leaseDuration) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(instanceId, "instanceId must not be null");
        Objects.requireNonNull(now, "now must not be null");
        Objects.requireNonNull(leaseDuration, "leaseDuration must not be null");

        Instant leaseCutoff = now.minus(leaseDuration);
        int updated = entityManager.createQuery(
                "UPDATE PasswordResetOutboxEntity o " +
                "SET o.status = :claimedStatus, o.claimedAt = :now, o.claimInstanceId = :instanceId, o.updatedAt = :now " +
                "WHERE o.id = :id AND ((o.status = :pendingStatus AND (o.nextAttemptAt IS NULL OR o.nextAttemptAt <= :now)) OR (o.status = :claimedStatus AND o.claimedAt < :leaseCutoff))")
                .setParameter("claimedStatus", PasswordResetOutboxEntity.STATUS_CLAIMED)
                .setParameter("pendingStatus", PasswordResetOutboxEntity.STATUS_PENDING)
                .setParameter("now", now)
                .setParameter("instanceId", instanceId)
                .setParameter("leaseCutoff", leaseCutoff)
                .setParameter("id", id)
                .executeUpdate();
        return updated > 0;
    }

    public boolean markDelivered(String id, Instant deliveredAt) {
        Objects.requireNonNull(id, "id must not be null");
        Instant timestamp = deliveredAt != null ? deliveredAt : Instant.now();
        PasswordResetOutboxEntity entity = entityManager.find(PasswordResetOutboxEntity.class, id);
        if (entity == null) {
            return false;
        }
        entity.setStatus(PasswordResetOutboxEntity.STATUS_DELIVERED);
        entity.setDeliveredAt(timestamp);
        entity.setClaimInstanceId(null);
        entity.setLastError(null);
        entity.setNextAttemptAt(null);
        return true;
    }

    public boolean recordFailure(String id, String errorMessage, Instant now, Instant nextAttemptAt, int maxAttempts) {
        Objects.requireNonNull(id, "id must not be null");
        Instant timestamp = now != null ? now : Instant.now();
        PasswordResetOutboxEntity entity = entityManager.find(PasswordResetOutboxEntity.class, id);
        if (entity == null) {
            return false;
        }
        entity.incrementAttemptCount();
        entity.setLastError(errorMessage);
        entity.setClaimInstanceId(null);

        if (entity.getAttemptCount() >= maxAttempts) {
            entity.setStatus(PasswordResetOutboxEntity.STATUS_FAILED);
            entity.setNextAttemptAt(null);
        } else {
            entity.setStatus(PasswordResetOutboxEntity.STATUS_PENDING);
            entity.setNextAttemptAt(nextAttemptAt);
        }
        return true;
    }

    public boolean recordFailure(String id, String errorMessage, Instant now, int maxAttempts) {
        return recordFailure(id, errorMessage, now, null, maxAttempts);
    }

    public List<PasswordResetOutboxEntity> findFailed(int maxResults) {
        TypedQuery<PasswordResetOutboxEntity> query = entityManager.createNamedQuery(
                "findFailedPasswordResetOutbox", PasswordResetOutboxEntity.class);
        if (maxResults > 0) {
            query.setMaxResults(maxResults);
        }
        return query.getResultList();
    }

    public boolean replayFailed(String sourceEventId, Instant now) {
        Objects.requireNonNull(sourceEventId, "sourceEventId must not be null");
        Optional<PasswordResetOutboxEntity> optional = findBySourceEventId(sourceEventId);
        if (optional.isEmpty()) {
            return false;
        }
        PasswordResetOutboxEntity entity = optional.get();
        if (!PasswordResetOutboxEntity.STATUS_FAILED.equals(entity.getStatus())) {
            return false;
        }
        entity.setStatus(PasswordResetOutboxEntity.STATUS_PENDING);
        entity.setClaimInstanceId(null);
        entity.setClaimedAt(null);
        entity.setLastError(null);
        entity.setNextAttemptAt(null);
        entity.resetAttemptCount();
        return true;
    }

    public int deleteDeliveredOlderThan(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff must not be null");
        return entityManager.createQuery(
                "DELETE FROM PasswordResetOutboxEntity o WHERE o.status = :deliveredStatus AND o.deliveredAt < :cutoff")
                .setParameter("deliveredStatus", PasswordResetOutboxEntity.STATUS_DELIVERED)
                .setParameter("cutoff", cutoff)
                .executeUpdate();
    }

    public EntityManager getEntityManager() {
        return entityManager;
    }
}
