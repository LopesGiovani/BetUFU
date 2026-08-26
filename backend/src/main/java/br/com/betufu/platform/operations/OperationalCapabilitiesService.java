package br.com.betufu.platform.operations;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.identity.OperationalIdentityLookup.GrantedRole;
import br.com.betufu.platform.identity.OperationalIdentityLookup.OperationalIdentity;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Serviço de resolução fail-closed de capacidades operacionais a partir dos papéis PostgreSQL vigentes
 * (D-46 / OPER-01 / OPER-02 / OPER-04 / IDEN-05).
 *
 * <p>Consome exclusivamente o contrato consumidor {@link OperationalIdentityLookup} pertencente ao
 * módulo {@code platform}. Nenhum tipo do módulo {@code br.com.betufu.identity} é importado.
 *
 * <p>Regras de projeção fail-closed:
 * <ul>
 *   <li>{@code ADMINISTRADOR_GERAL} global (com {@code scopeRef == null}) concede, nesta ordem:
 *       {@link OperationalCapability#VIEW_OPERATIONAL_PANEL},
 *       {@link OperationalCapability#MANAGE_ROLES},
 *       {@link OperationalCapability#CORRECT_ENROLLMENT}.</li>
 *   <li>{@code ADMINISTRADOR_COMPETICAO} e {@code MODERADOR} escopados (com {@code scopeRef != null})
 *       concedem somente {@link OperationalCapability#VIEW_OPERATIONAL_PANEL}.</li>
 *   <li>{@code ADMINISTRADOR_GERAL} com {@code scopeRef != null}, papéis escopados com {@code scopeRef == null},
 *       papéis desconhecidos ou identidades não resolvidas não concedem nenhuma capacidade.</li>
 *   <li>Múltiplos papéis válidos produzem a união sem duplicatas na ordem canônica do enum
 *       {@link OperationalCapability}.</li>
 * </ul>
 */
@Service
@Profile("runtime | integrated")
public class OperationalCapabilitiesService {

    private final OperationalIdentityLookup identityLookup;

    public OperationalCapabilitiesService(OperationalIdentityLookup identityLookup) {
        this.identityLookup = Objects.requireNonNull(identityLookup, "identityLookup must not be null");
    }

    /**
     * Resolve as capacidades operacionais atuais para o par exato {@code (issuer, subject)}.
     *
     * @param issuer emissor OIDC exato sem normalização
     * @param subject sujeito OIDC exato sem normalização
     * @return resposta imutável com as capacidades concedidas na ordem canônica
     */
    public OperationalCapabilitiesResponse getCapabilities(String issuer, String subject) {
        if (issuer == null || subject == null || issuer.isBlank() || subject.isBlank()) {
            return new OperationalCapabilitiesResponse(List.of());
        }

        Optional<OperationalIdentity> identityOpt = identityLookup.lookupIdentity(issuer, subject);
        if (identityOpt.isEmpty()) {
            return new OperationalCapabilitiesResponse(List.of());
        }

        Set<GrantedRole> roles = identityOpt.get().roles();
        boolean isGlobalAdmin = roles.stream()
                .anyMatch(grant -> "ADMINISTRADOR_GERAL".equals(grant.roleName()) && grant.scopeRef() == null);
        boolean isScopedCompAdmin = roles.stream()
                .anyMatch(grant -> "ADMINISTRADOR_COMPETICAO".equals(grant.roleName()) && grant.scopeRef() != null);
        boolean isScopedModerator = roles.stream()
                .anyMatch(grant -> "MODERADOR".equals(grant.roleName()) && grant.scopeRef() != null);

        List<OperationalCapability> capabilities = new ArrayList<>();
        if (isGlobalAdmin || isScopedCompAdmin || isScopedModerator) {
            capabilities.add(OperationalCapability.VIEW_OPERATIONAL_PANEL);
        }
        if (isGlobalAdmin) {
            capabilities.add(OperationalCapability.MANAGE_ROLES);
            capabilities.add(OperationalCapability.CORRECT_ENROLLMENT);
        }

        return new OperationalCapabilitiesResponse(capabilities);
    }
}
