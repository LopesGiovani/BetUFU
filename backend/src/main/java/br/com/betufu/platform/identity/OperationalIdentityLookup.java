package br.com.betufu.platform.identity;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Contrato consumidor somente leitura, pertencente ao módulo platform, que resolve o par
 * {@code (issuer, subject)} em {@code identityId} e papéis vigentes.
 *
 * <p>A assinatura usa exclusivamente tipos neutros (String, UUID, projeções imutáveis) para
 * preservar a arquitetura modular: nenhuma classe do platform importa tipos de {@code br.com.betufu.identity}.
 *
 * <p>A busca é estrita e a chave OIDC é comparada byte a byte, sem normalização. Identidade inexistente
 * devolve vazio ({@link Optional#empty()}) e nunca cria cadastro.
 */
public interface OperationalIdentityLookup {

    /**
     * Projeção imutável de papel operacional concedido e referência de escopo opcional.
     */
    record GrantedRole(String roleName, UUID scopeRef) {
        public GrantedRole {
            Objects.requireNonNull(roleName, "roleName must not be null");
            if (roleName.isBlank()) {
                throw new IllegalArgumentException("roleName must not be blank");
            }
        }

        public GrantedRole(String roleName) {
            this(roleName, null);
        }
    }

    /**
     * Projeção imutável da identidade operacional resolvida e seus papéis vigentes.
     */
    record OperationalIdentity(
            UUID identityId,
            Set<GrantedRole> roles) {
        public OperationalIdentity {
            Objects.requireNonNull(identityId, "identityId must not be null");
            roles = roles == null ? Set.of() : Set.copyOf(roles);
        }
    }

    /**
     * Resolve a identidade operacional e os papéis vigentes a partir do par exato {@code (issuer, subject)}.
     *
     * @param issuer emissor OIDC exato
     * @param subject sujeito OIDC exato
     * @return a identidade operacional se existente, ou {@link Optional#empty()} caso contrário
     */
    Optional<OperationalIdentity> lookupIdentity(String issuer, String subject);

    /**
     * Resolve o identificador público da identidade a partir do par exato {@code (issuer, subject)}.
     */
    default Optional<UUID> findIdentityId(String issuer, String subject) {
        return lookupIdentity(issuer, subject).map(OperationalIdentity::identityId);
    }

    /**
     * Devolve os papéis vigentes para o par exato {@code (issuer, subject)}.
     */
    default Set<GrantedRole> findActiveRoles(String issuer, String subject) {
        return lookupIdentity(issuer, subject)
                .map(OperationalIdentity::roles)
                .orElseGet(Set::of);
    }
}
