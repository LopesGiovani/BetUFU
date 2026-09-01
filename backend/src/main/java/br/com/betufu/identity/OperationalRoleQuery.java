package br.com.betufu.identity;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Public query contract for operational roles exposed by the identity module. */
public interface OperationalRoleQuery {

  /** Pair of operational role and nullable scope reference. */
  record GrantedRole(OperationalRole role, UUID scopeRef) {
    public GrantedRole {
      Objects.requireNonNull(role, "role must not be null");
    }

    public GrantedRole(OperationalRole role) {
      this(role, null);
    }
  }

  /**
   * Returns the set of active (vigente) operational role grants (role and scope pairs) for the
   * specified identity.
   *
   * @param identityId the public identifier of the identity
   * @return the set of active granted roles
   */
  Set<GrantedRole> findActiveRoles(UUID identityId);

  /**
   * Checks if the specified identity currently has the given operational role active in any scope
   * (or global).
   *
   * @param identityId the public identifier of the identity
   * @param role the operational role to check
   * @return true if the role is active, false otherwise
   */
  default boolean hasRole(UUID identityId, OperationalRole role) {
    if (identityId == null || role == null) {
      return false;
    }
    if (role.isGlobal()) {
      // Papel global só confere autoridade quando a concessão é global. Aceitar qualquer
      // escopo aqui faria uma concessão escopada de ADMINISTRADOR_GERAL — que o serviço e o
      // banco agora recusam, mas que pode existir em dado legado — valer como autoridade
      // irrestrita, que é exatamente o oposto do que o escopo aparenta.
      return findActiveRoles(identityId).stream()
          .anyMatch(g -> g.role() == role && g.scopeRef() == null);
    }
    return findActiveRoles(identityId).stream().anyMatch(g -> g.role() == role);
  }

  /**
   * Checks if the specified identity currently has the given operational role active in the given
   * scope.
   *
   * @param identityId the public identifier of the identity
   * @param role the operational role to check
   * @param scopeRef the scope reference (or null for global)
   * @return true if the role is active with the given scope, false otherwise
   */
  default boolean hasRole(UUID identityId, OperationalRole role, UUID scopeRef) {
    if (identityId == null || role == null) {
      return false;
    }
    return findActiveRoles(identityId).contains(new GrantedRole(role, scopeRef));
  }
}
