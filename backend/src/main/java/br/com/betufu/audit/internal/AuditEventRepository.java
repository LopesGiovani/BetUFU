package br.com.betufu.audit.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface AuditEventRepository extends Repository<AuditEvent, UUID> {

    @Modifying
    @Query(
            value = """
                    INSERT INTO public.audit_event (
                        actor_identity_id,
                        actor_role,
                        target_identity_id,
                        action,
                        outcome,
                        previous_value,
                        new_value,
                        reason,
                        correlation_id,
                        idempotency_key
                    )
                    VALUES (
                        :actorIdentityId,
                        :actorRole,
                        :targetIdentityId,
                        :action,
                        :outcome,
                        :previousValue,
                        :newValue,
                        :reason,
                        :correlationId,
                        :idempotencyKey
                    )
                    ON CONFLICT (idempotency_key) WHERE idempotency_key IS NOT NULL DO NOTHING
                    """,
            nativeQuery = true)
    int insertIfAbsent(
            @Param("actorIdentityId") UUID actorIdentityId,
            @Param("actorRole") String actorRole,
            @Param("targetIdentityId") UUID targetIdentityId,
            @Param("action") String action,
            @Param("outcome") String outcome,
            @Param("previousValue") String previousValue,
            @Param("newValue") String newValue,
            @Param("reason") String reason,
            @Param("correlationId") String correlationId,
            @Param("idempotencyKey") String idempotencyKey);

    @Query("""
            SELECT e
            FROM AuditEvent e
            WHERE e.idempotencyKey = :idempotencyKey
            """)
    Optional<AuditEvent> findByIdempotencyKey(@Param("idempotencyKey") String idempotencyKey);

    @Query("""
            SELECT e
            FROM AuditEvent e
            WHERE e.eventId = :eventId
            """)
    Optional<AuditEvent> findById(@Param("eventId") UUID eventId);

    @Query("""
            SELECT e
            FROM AuditEvent e
            ORDER BY e.occurredAt ASC, e.eventId ASC
            """)
    List<AuditEvent> findAll();
}
