package br.com.betufu.identity.internal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface LocalIdentityRepository extends Repository<LocalIdentity, UUID> {

    @Modifying
    @Query(
            value = """
                    INSERT INTO public.local_identity (issuer, subject)
                    VALUES (:issuer, :subject)
                    ON CONFLICT (issuer, subject) DO NOTHING
                    """,
            nativeQuery = true)
    int insertIfAbsent(@Param("issuer") String issuer, @Param("subject") String subject);

    @Query("""
            SELECT identity
            FROM LocalIdentity identity
            WHERE identity.issuer = :issuer
              AND identity.subject = :subject
            """)
    Optional<LocalIdentity> findByNaturalKey(
            @Param("issuer") String issuer, @Param("subject") String subject);
}
