package br.com.betufu.verification.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface UfuEnrollmentRepository extends Repository<UfuEnrollment, UUID> {

    @Modifying
    @Query(
            value = """
                    INSERT INTO public.ufu_enrollment (identity_id, matricula_hmac, hmac_version, status)
                    VALUES (:identityId, :matriculaHmac, :hmacVersion, :status)
                    """,
            nativeQuery = true)
    int insert(
            @Param("identityId") UUID identityId,
            @Param("matriculaHmac") byte[] matriculaHmac,
            @Param("hmacVersion") short hmacVersion,
            @Param("status") String status);

    @Query("""
            SELECT enrollment
            FROM UfuEnrollment enrollment
            WHERE enrollment.enrollmentId = :enrollmentId
            """)
    Optional<UfuEnrollment> findById(@Param("enrollmentId") UUID enrollmentId);

    @Query("""
            SELECT enrollment
            FROM UfuEnrollment enrollment
            WHERE enrollment.identityId = :identityId
              AND enrollment.status = 'ATIVO'
            """)
    Optional<UfuEnrollment> findActiveByIdentityId(@Param("identityId") UUID identityId);

    @Query("""
            SELECT CASE WHEN count(enrollment) > 0 THEN true ELSE false END
            FROM UfuEnrollment enrollment
            WHERE enrollment.status = 'ATIVO'
              AND enrollment.matriculaHmac IN :digests
            """)
    boolean existsActiveByMatriculaHmacIn(@Param("digests") Collection<byte[]> digests);

    @Query("""
            SELECT enrollment
            FROM UfuEnrollment enrollment
            WHERE enrollment.status = 'ATIVO'
              AND enrollment.matriculaHmac IN :digests
            """)
    List<UfuEnrollment> findActiveByMatriculaHmacIn(@Param("digests") Collection<byte[]> digests);

    @Query("""
            SELECT enrollment
            FROM UfuEnrollment enrollment
            WHERE enrollment.identityId = :identityId
            ORDER BY enrollment.createdAt DESC
            """)
    List<UfuEnrollment> findAllByIdentityId(@Param("identityId") UUID identityId);
}
