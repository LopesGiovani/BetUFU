package br.com.betufu.identity.internal;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface BetufuProfileRepository extends Repository<BetufuProfile, UUID> {

  @Modifying
  @Query(
      value =
          """
                    INSERT INTO public.betufu_profile (identity_id, nickname)
                    VALUES (:identityId, :nickname)
                    ON CONFLICT (identity_id) DO NOTHING
                    """,
      nativeQuery = true)
  int insertIfAbsent(@Param("identityId") UUID identityId, @Param("nickname") String nickname);

  @Query(
      """
            SELECT profile
            FROM BetufuProfile profile
            WHERE profile.identityId = :identityId
            """)
  Optional<BetufuProfile> findByIdentityId(@Param("identityId") UUID identityId);
}
