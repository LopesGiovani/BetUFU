package br.com.betufu.platform.session.passwordreset;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data Repository for {@link PasswordResetEventInboxEntity}.
 *
 * <p>Uses minimal repository interface and explicit PostgreSQL native queries with {@code ON CONFLICT DO NOTHING}
 * for concurrency arbitration at the database level.
 */
@Transactional(readOnly = true)
public interface PasswordResetEventInboxRepository extends Repository<PasswordResetEventInboxEntity, UUID> {

    @Transactional
    @Modifying
    @Query(
            value = """
                    INSERT INTO public.password_reset_event_inbox (
                        source_event_id,
                        subject,
                        realm_id,
                        event_type,
                        status,
                        created_at,
                        updated_at,
                        attempt_count
                    )
                    VALUES (
                        :sourceEventId,
                        :subject,
                        :realmId,
                        :eventType,
                        :status,
                        CURRENT_TIMESTAMP(6),
                        CURRENT_TIMESTAMP(6),
                        0
                    )
                    ON CONFLICT (source_event_id) DO NOTHING
                    """,
            nativeQuery = true)
    int insertIfAbsent(
            @Param("sourceEventId") String sourceEventId,
            @Param("subject") String subject,
            @Param("realmId") String realmId,
            @Param("eventType") String eventType,
            @Param("status") String status);

    @Query("""
            SELECT e
            FROM PasswordResetEventInboxEntity e
            WHERE e.sourceEventId = :sourceEventId
            """)
    Optional<PasswordResetEventInboxEntity> findBySourceEventId(@Param("sourceEventId") String sourceEventId);

    @Query("""
            SELECT e
            FROM PasswordResetEventInboxEntity e
            WHERE e.inboxId = :inboxId
            """)
    Optional<PasswordResetEventInboxEntity> findById(@Param("inboxId") UUID inboxId);

    @Query("""
            SELECT e
            FROM PasswordResetEventInboxEntity e
            WHERE e.status = :status
            ORDER BY e.createdAt ASC
            """)
    List<PasswordResetEventInboxEntity> findByStatus(@Param("status") String status);

    @Query("""
            SELECT e
            FROM PasswordResetEventInboxEntity e
            ORDER BY e.createdAt ASC, e.inboxId ASC
            """)
    List<PasswordResetEventInboxEntity> findAll();

    @Transactional
    @Modifying
    @Query(
            value = """
                    UPDATE public.password_reset_event_inbox
                    SET status = 'DELIVERED',
                        delivered_at = CURRENT_TIMESTAMP(6),
                        updated_at = CURRENT_TIMESTAMP(6)
                    WHERE source_event_id = :sourceEventId
                    """,
            nativeQuery = true)
    int markDelivered(@Param("sourceEventId") String sourceEventId);

    @Transactional
    @Modifying
    @Query(
            value = """
                    UPDATE public.password_reset_event_inbox
                    SET status = 'FAILED',
                        last_error = :lastError,
                        updated_at = CURRENT_TIMESTAMP(6)
                    WHERE source_event_id = :sourceEventId
                    """,
            nativeQuery = true)
    int markFailed(
            @Param("sourceEventId") String sourceEventId,
            @Param("lastError") String lastError);
}
