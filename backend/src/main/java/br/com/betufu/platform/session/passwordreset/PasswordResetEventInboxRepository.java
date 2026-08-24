package br.com.betufu.platform.session.passwordreset;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
            WHERE e.status = :status
            ORDER BY e.createdAt ASC, e.inboxId ASC
            """)
    Page<PasswordResetEventInboxEntity> findPageByStatus(
            @Param("status") String status,
            Pageable pageable);

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
                        claim_instance_id = NULL,
                        last_error = NULL,
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
                        claim_instance_id = NULL,
                        updated_at = CURRENT_TIMESTAMP(6)
                    WHERE source_event_id = :sourceEventId
                    """,
            nativeQuery = true)
    int markFailed(
            @Param("sourceEventId") String sourceEventId,
            @Param("lastError") String lastError);

    @Query("""
            SELECT e
            FROM PasswordResetEventInboxEntity e
            WHERE e.status = 'PENDING'
               OR (e.status = 'CLAIMED' AND e.claimedAt < :leaseCutoff)
            ORDER BY e.createdAt ASC
            """)
    List<PasswordResetEventInboxEntity> findEligibleForClaim(
            @Param("leaseCutoff") Instant leaseCutoff,
            Pageable pageable);

    @Transactional
    @Modifying
    @Query(
            value = """
                    UPDATE public.password_reset_event_inbox
                    -- updated_at vem do relogio do BANCO, nunca do da aplicacao. created_at nasce
                    -- do DEFAULT do PostgreSQL e a constraint ck_..._timestamp_order exige
                    -- updated_at >= created_at; misturar as fontes quebra a linha sempre que o
                    -- relogio da aplicacao estiver atras do banco. No teste isso e deterministico;
                    -- em producao bastam milissegundos de drift entre os dois hosts.
                    SET status = 'CLAIMED',
                        claimed_at = :now,
                        claim_instance_id = :instanceId,
                        updated_at = CURRENT_TIMESTAMP(6),
                        attempt_count = attempt_count + 1
                    WHERE source_event_id = :sourceEventId
                      AND (
                          status = 'PENDING'
                          OR (status = 'CLAIMED' AND claimed_at < :leaseCutoff)
                      )
                    """,
            nativeQuery = true)
    int claim(
            @Param("sourceEventId") String sourceEventId,
            @Param("instanceId") String instanceId,
            @Param("now") Instant now,
            @Param("leaseCutoff") Instant leaseCutoff);

    @Transactional
    @Modifying
    @Query(
            value = """
                    UPDATE public.password_reset_event_inbox
                    SET status = 'CLAIMED',
                        claimed_at = :now,
                        claim_instance_id = :instanceId,
                        last_error = NULL,
                        updated_at = CURRENT_TIMESTAMP(6),
                        attempt_count = attempt_count + 1
                    WHERE source_event_id = :sourceEventId
                      AND status = 'FAILED'
                    """,
            nativeQuery = true)
    int claimFailed(
            @Param("sourceEventId") String sourceEventId,
            @Param("instanceId") String instanceId,
            @Param("now") Instant now);

    @Transactional
    @Modifying
    @Query(
            value = """
                    DELETE FROM public.password_reset_event_inbox
                    WHERE status = 'DELIVERED'
                      AND delivered_at < :cutoff
                    """,
            nativeQuery = true)
    int deleteDeliveredOlderThan(@Param("cutoff") Instant cutoff);

    @Transactional
    @Modifying
    @Query(
            value = """
                    UPDATE public.password_reset_event_inbox
                    SET status = 'PENDING',
                        claimed_at = NULL,
                        claim_instance_id = NULL,
                        last_error = NULL,
                        updated_at = CURRENT_TIMESTAMP(6)
                    WHERE source_event_id = :sourceEventId
                      AND status = 'FAILED'
                    """,
            nativeQuery = true)
    int replayFailed(@Param("sourceEventId") String sourceEventId);

    @Query("""
            SELECT count(e)
            FROM PasswordResetEventInboxEntity e
            WHERE e.status = :status
            """)
    long countByStatus(@Param("status") String status);
}
