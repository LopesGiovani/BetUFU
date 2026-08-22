package br.com.betufu.keycloak.password;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
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

    public EntityManager getEntityManager() {
        return entityManager;
    }
}
