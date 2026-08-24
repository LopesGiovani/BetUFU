package br.com.betufu.identity.internal;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import br.com.betufu.identity.OperationalRoleQuery;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementação do contrato consumidor {@link OperationalIdentityLookup} no módulo de identidade.
 *
 * <p>Único ponto autorizado a consultar {@link LocalIdentityRepository#findByNaturalKey} e
 * {@link OperationalRoleQuery} para responder ao contrato do platform, preservando a seta
 * arquitetural {@code identity -> platform}.
 *
 * <p>Não cria cadastro nem normaliza a chave OIDC: identidade inexistente devolve {@link Optional#empty()}.
 */
@Service
@Profile("runtime | integrated")
public class OperationalIdentityLookupService implements OperationalIdentityLookup {

    private final LocalIdentityRepository localIdentityRepository;
    private final OperationalRoleQuery operationalRoleQuery;

    public OperationalIdentityLookupService(
            LocalIdentityRepository localIdentityRepository,
            OperationalRoleQuery operationalRoleQuery) {
        this.localIdentityRepository = Objects.requireNonNull(
                localIdentityRepository, "localIdentityRepository must not be null");
        this.operationalRoleQuery = Objects.requireNonNull(
                operationalRoleQuery, "operationalRoleQuery must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OperationalIdentity> lookupIdentity(String issuer, String subject) {
        if (issuer == null || subject == null || issuer.isBlank() || subject.isBlank()) {
            return Optional.empty();
        }

        Optional<LocalIdentity> localIdentityOpt = localIdentityRepository.findByNaturalKey(issuer, subject);
        if (localIdentityOpt.isEmpty()) {
            return Optional.empty();
        }

        UUID identityId = localIdentityOpt.get().getIdentityId();
        Set<OperationalRoleQuery.GrantedRole> grantedRoles = operationalRoleQuery.findActiveRoles(identityId);

        Set<GrantedRole> mappedRoles = new HashSet<>();
        if (grantedRoles != null) {
            for (OperationalRoleQuery.GrantedRole grantedRole : grantedRoles) {
                mappedRoles.add(new GrantedRole(
                        grantedRole.role().name(),
                        grantedRole.scopeRef()));
            }
        }

        return Optional.of(new OperationalIdentity(
                identityId,
                Collections.unmodifiableSet(mappedRoles)));
    }
}
