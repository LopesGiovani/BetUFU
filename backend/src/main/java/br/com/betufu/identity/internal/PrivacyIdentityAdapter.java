package br.com.betufu.identity.internal;

import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.privacy.PrivacySubjectPort;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Produces the privacy module's neutral identity projection from its public port. */
@Component
@Profile("runtime | integrated")
public class PrivacyIdentityAdapter
    implements PrivacySubjectPort.Identity, br.com.betufu.privacy.PrivacyRestrictedRecordsPort {
  private final OperationalIdentityLookup identities;
  private final org.springframework.jdbc.core.JdbcTemplate jdbc;

  public PrivacyIdentityAdapter(
      OperationalIdentityLookup identities, org.springframework.jdbc.core.JdbcTemplate jdbc) {
    this.identities = identities;
    this.jdbc = jdbc;
  }

  @Override
  public Optional<UUID> findAuthenticatedIdentity(String issuer, String subject) {
    return identities.findIdentityId(issuer, subject);
  }

  @Override
  public boolean isGeneralAdministrator(UUID identityId) {
    return identities.hasGlobalRole(identityId, "ADMINISTRADOR_GERAL");
  }

  @Override
  public java.util.Set<DataClass> supportedClasses() {
    return java.util.Set.of(DataClass.IDENTITY);
  }

  @Override
  public java.util.List<RestrictedRecord> export(Subject subject, DataClass type) {
    if (type != DataClass.IDENTITY) throw new IllegalArgumentException("Unsupported class");
    if (subject.identityId() == null) return java.util.List.of();
    return jdbc.query(
        "SELECT identity_id,nickname FROM betufu_profile WHERE identity_id=?",
        (rs, row) ->
            new RestrictedRecord(rs.getString(1), java.util.Map.of("nickname", rs.getString(2))),
        subject.identityId());
  }
}
