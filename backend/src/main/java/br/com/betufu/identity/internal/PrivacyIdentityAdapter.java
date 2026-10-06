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
public class PrivacyIdentityAdapter implements PrivacySubjectPort.Identity {
  private final OperationalIdentityLookup identities;

  public PrivacyIdentityAdapter(OperationalIdentityLookup identities) {
    this.identities = identities;
  }

  @Override
  public Optional<UUID> findAuthenticatedIdentity(String issuer, String subject) {
    return identities.findIdentityId(issuer, subject);
  }

  @Override
  public boolean isGeneralAdministrator(UUID identityId) {
    return identities.hasGlobalRole(identityId, "ADMINISTRADOR_GERAL");
  }
}
