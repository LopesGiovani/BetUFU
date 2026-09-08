package br.com.betufu.odds.internal.governance;

import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.identity.OperationalIdentityLookup.GrantedRole;
import br.com.betufu.platform.identity.OperationalIdentityLookup.OperationalIdentity;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Server-side authorization for odds commands; menu capabilities are not used as proof. */
@Service
@Profile("runtime | integrated")
public class OddsAuthorizationService {

  public static final String VIEW_ODDS_MARKET = "VIEW_ODDS_MARKET";
  public static final String MANAGE_ODDS_MARKET = "MANAGE_ODDS_MARKET";
  public static final String PROPOSE_ODDS_OVERRIDE = "PROPOSE_ODDS_OVERRIDE";
  public static final String APPROVE_ODDS_OVERRIDE = "APPROVE_ODDS_OVERRIDE";

  private static final String GENERAL_ADMIN = "ADMINISTRADOR_GERAL";
  private static final String COMPETITION_ADMIN = "ADMINISTRADOR_COMPETICAO";
  private static final String MODERATOR = "MODERADOR";
  private static final Set<String> CLOSED_CAPABILITIES =
      Set.of(VIEW_ODDS_MARKET, MANAGE_ODDS_MARKET, PROPOSE_ODDS_OVERRIDE, APPROVE_ODDS_OVERRIDE);

  private final OperationalIdentityLookup identityLookup;

  public OddsAuthorizationService(OperationalIdentityLookup identityLookup) {
    this.identityLookup = Objects.requireNonNull(identityLookup, "identityLookup must not be null");
  }

  /** Returns a decision based on the current identity lookup and persisted resource scope. */
  public AuthorizationDecision authorize(
      String issuer, String subject, String capability, UUID persistedCompetitionId) {
    if (issuer == null || issuer.isBlank() || subject == null || subject.isBlank()) {
      return AuthorizationDecision.denied("IDENTITY_KEY_MISSING");
    }
    if (!CLOSED_CAPABILITIES.contains(capability)) {
      return AuthorizationDecision.denied("UNKNOWN_CAPABILITY");
    }
    if (persistedCompetitionId == null) {
      return AuthorizationDecision.denied("PERSISTED_SCOPE_MISSING");
    }

    Optional<OperationalIdentity> identity = identityLookup.lookupIdentity(issuer, subject);
    if (identity.isEmpty()) {
      return AuthorizationDecision.denied("IDENTITY_NOT_FOUND");
    }

    for (GrantedRole grant : identity.get().roles()) {
      if (grant == null || grant.roleName() == null) {
        continue;
      }
      if (GENERAL_ADMIN.equals(grant.roleName())
          && grant.scopeRef() == null
          && isAdminCapability(capability)) {
        return AuthorizationDecision.permitted(identity.get().identityId(), grant.roleName());
      }
      if (COMPETITION_ADMIN.equals(grant.roleName())
          && persistedCompetitionId.equals(grant.scopeRef())
          && isAdminCapability(capability)) {
        return AuthorizationDecision.permitted(identity.get().identityId(), grant.roleName());
      }
      if (MODERATOR.equals(grant.roleName())
          && persistedCompetitionId.equals(grant.scopeRef())
          && VIEW_ODDS_MARKET.equals(capability)) {
        return AuthorizationDecision.permitted(identity.get().identityId(), grant.roleName());
      }
    }
    return AuthorizationDecision.denied("ROLE_OR_SCOPE_NOT_AUTHORIZED");
  }

  public boolean isAuthorized(
      String issuer, String subject, String capability, UUID persistedCompetitionId) {
    return authorize(issuer, subject, capability, persistedCompetitionId).permitted();
  }

  public void requireAuthorized(
      String issuer, String subject, String capability, UUID persistedCompetitionId) {
    AuthorizationDecision decision = authorize(issuer, subject, capability, persistedCompetitionId);
    if (!decision.permitted()) {
      throw new OddsAuthorizationException(decision.reason());
    }
  }

  private static boolean isAdminCapability(String capability) {
    return Set.of(
            VIEW_ODDS_MARKET, MANAGE_ODDS_MARKET, PROPOSE_ODDS_OVERRIDE, APPROVE_ODDS_OVERRIDE)
        .contains(capability);
  }

  public record AuthorizationDecision(
      boolean permitted, UUID identityId, String role, String reason) {
    public static AuthorizationDecision permitted(UUID identityId, String role) {
      return new AuthorizationDecision(true, identityId, role, "PERMITTED");
    }

    public static AuthorizationDecision denied(String reason) {
      return new AuthorizationDecision(false, null, null, reason);
    }
  }

  public static final class OddsAuthorizationException extends RuntimeException {
    public OddsAuthorizationException(String reason) {
      super(reason);
    }
  }
}
