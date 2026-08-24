package br.com.betufu.platform.security;

import java.net.URI;
import java.net.URL;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import br.com.betufu.platform.identity.OperationalIdentityLookup;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

/**
 * Contribui com autoridades de papéis operacionais da BETUFU para o {@link org.springframework.security.core.context.SecurityContext}.
 *
 * <p>Consome exclusivamente o contrato consumidor {@link OperationalIdentityLookup} pertencente ao
 * módulo platform. Nenhuma classe do módulo {@code identity} é importada.
 *
 * <p>Preserva integralmente as autoridades originais e a autoridade técnica concedida pelo
 * {@link OidcTechnicalAuthorityMapper}, acrescentando as autoridades operacionais (prefixo {@code ROLE_})
 * obtidas a partir do par exato {@code (issuer, subject)}.
 */
public final class BetufuRoleAuthorityContributor implements GrantedAuthoritiesMapper {

    public static final String ROLE_PREFIX = "ROLE_";

    private final ObjectProvider<OperationalIdentityLookup> identityLookupProvider;
    private final OperationalIdentityLookup directIdentityLookup;
    private final OidcTechnicalAuthorityMapper technicalAuthorityMapper = new OidcTechnicalAuthorityMapper();

    public BetufuRoleAuthorityContributor(ObjectProvider<OperationalIdentityLookup> identityLookupProvider) {
        this.identityLookupProvider = Objects.requireNonNull(identityLookupProvider, "identityLookupProvider must not be null");
        this.directIdentityLookup = null;
    }

    public BetufuRoleAuthorityContributor(OperationalIdentityLookup directIdentityLookup) {
        this.identityLookupProvider = null;
        this.directIdentityLookup = Objects.requireNonNull(directIdentityLookup, "directIdentityLookup must not be null");
    }

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(
            Collection<? extends GrantedAuthority> authorities) {
        if (authorities == null) {
            return Set.of();
        }

        // 1. Preserva autoridades originais e acrescenta autoridade técnica (ROLE_TECHNICAL se claim presente)
        Collection<? extends GrantedAuthority> baseAuthorities =
                technicalAuthorityMapper.mapAuthorities(authorities);
        Set<GrantedAuthority> mapped = new LinkedHashSet<>(baseAuthorities);

        OperationalIdentityLookup lookup = resolveLookup();
        if (lookup == null) {
            return mapped;
        }

        for (GrantedAuthority authority : authorities) {
            if (authority instanceof OidcUserAuthority oidcAuthority
                    && oidcAuthority.getIdToken() != null) {
                OidcIdToken idToken = oidcAuthority.getIdToken();
                String issuer = extractIssuer(idToken);
                String subject = extractSubject(idToken);

                if (issuer != null && subject != null) {
                    Optional<OperationalIdentityLookup.OperationalIdentity> resolved =
                            lookup.lookupIdentity(issuer, subject);

                    if (resolved.isPresent()) {
                        for (OperationalIdentityLookup.GrantedRole grant : resolved.get().roles()) {
                            // Papel global (ADMINISTRADOR_GERAL) exige scopeRef nulo (D-46/OPER-02/RoleGuard)
                            if ("ADMINISTRADOR_GERAL".equals(grant.roleName())) {
                                if (grant.scopeRef() == null) {
                                    mapped.add(new SimpleGrantedAuthority(ROLE_PREFIX + grant.roleName()));
                                }
                            } else if (grant.scopeRef() != null) {
                                // Papéis escopados (ADMINISTRADOR_COMPETICAO, MODERADOR) exigem scopeRef
                                mapped.add(new SimpleGrantedAuthority(ROLE_PREFIX + grant.roleName()));
                            }
                        }
                    }
                }
            }
        }

        return mapped;
    }

    private OperationalIdentityLookup resolveLookup() {
        if (directIdentityLookup != null) {
            return directIdentityLookup;
        }
        if (identityLookupProvider != null) {
            return identityLookupProvider.getIfAvailable();
        }
        return null;
    }

    private static String extractIssuer(OidcIdToken idToken) {
        Object value = idToken.getClaims().get(IdTokenClaimNames.ISS);
        String text = switch (value) {
            case String s -> s;
            case URL u -> u.toString();
            case URI u -> u.toString();
            case null, default -> null;
        };
        return (text != null && !text.isBlank()) ? text : null;
    }

    private static String extractSubject(OidcIdToken idToken) {
        Object value = idToken.getClaims().get(IdTokenClaimNames.SUB);
        return (value instanceof String s && !s.isBlank()) ? s : null;
    }
}
