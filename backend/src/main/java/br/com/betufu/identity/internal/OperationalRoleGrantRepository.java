package br.com.betufu.identity.internal;

import br.com.betufu.identity.OperationalRole;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface OperationalRoleGrantRepository extends Repository<OperationalRoleGrant, UUID> {

  @Modifying
  @Query(
      value =
          """
                    INSERT INTO public.operational_role_grant (identity_id, role_name, scope_ref, status)
                    VALUES (:identityId, :roleName, :scopeRef, 'VIGENTE')
                    ON CONFLICT DO NOTHING
                    """,
      nativeQuery = true)
  int insertGrant(
      @Param("identityId") UUID identityId,
      @Param("roleName") String roleName,
      @Param("scopeRef") UUID scopeRef);

  @Query(
      """
            SELECT g
            FROM OperationalRoleGrant g
            WHERE g.identityId = :identityId
              AND g.status = 'VIGENTE'
            """)
  List<OperationalRoleGrant> findActiveByIdentityId(@Param("identityId") UUID identityId);

  @Query(
      """
            SELECT g
            FROM OperationalRoleGrant g
            WHERE g.identityId = :identityId
              AND g.roleName = :roleName
              AND (:scopeRef IS NULL AND g.scopeRef IS NULL OR g.scopeRef = :scopeRef)
              AND g.status = 'VIGENTE'
            """)
  Optional<OperationalRoleGrant> findActiveGrant(
      @Param("identityId") UUID identityId,
      @Param("roleName") OperationalRole roleName,
      @Param("scopeRef") UUID scopeRef);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      """
            SELECT g
            FROM OperationalRoleGrant g
            WHERE g.roleName = :roleName
              AND g.status = 'VIGENTE'
            """)
  List<OperationalRoleGrant> findActiveRolesForUpdate(@Param("roleName") OperationalRole roleName);

  @Modifying
  @Query(
      value =
          """
                    UPDATE public.operational_role_grant
                    SET status = 'REVOGADO',
                        revoked_at = :revokedAt
                    WHERE grant_id = :grantId
                      AND status = 'VIGENTE'
                    """,
      nativeQuery = true)
  int revokeGrant(@Param("grantId") UUID grantId, @Param("revokedAt") Instant revokedAt);

  @Query(
      value = "SELECT subject FROM public.local_identity WHERE identity_id = :identityId",
      nativeQuery = true)
  Optional<String> findSubjectByIdentityId(@Param("identityId") UUID identityId);

  @Query(
      value = "SELECT count(*) > 0 FROM public.local_identity WHERE identity_id = :identityId",
      nativeQuery = true)
  boolean existsIdentityById(@Param("identityId") UUID identityId);
}
