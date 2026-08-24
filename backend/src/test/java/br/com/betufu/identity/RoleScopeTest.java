package br.com.betufu.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import br.com.betufu.identity.internal.LocalIdentity;
import br.com.betufu.identity.internal.LocalIdentityRepository;
import br.com.betufu.identity.internal.OperationalIdentityLookupService;
import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.security.BetufuRoleAuthorityContributor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

class RoleScopeTest {

    private static final String SCOPE_OPENID = "SCOPE_openid";
    private static final String ISSUER = "https://idp.betufu.local";
    private static final String SUBJECT = "user-123";

    @Test
    @DisplayName("ADMINISTRADOR_GERAL global (sem escopo) concede ROLE_ADMINISTRADOR_GERAL")
    void globalAdminGrantConfersAuthority() {
        OperationalIdentityLookup lookup = mock(OperationalIdentityLookup.class);
        UUID identityId = UUID.randomUUID();
        when(lookup.lookupIdentity(ISSUER, SUBJECT)).thenReturn(Optional.of(
                new OperationalIdentityLookup.OperationalIdentity(
                        identityId,
                        Set.of(new OperationalIdentityLookup.GrantedRole("ADMINISTRADOR_GERAL", null)))));

        BetufuRoleAuthorityContributor contributor = new BetufuRoleAuthorityContributor(lookup);
        List<String> authorities = names(contributor.mapAuthorities(createOidcAuthorities(ISSUER, SUBJECT, null)));

        assertThat(authorities).contains("ROLE_ADMINISTRADOR_GERAL");
        assertThat(authorities).contains(SCOPE_OPENID);
    }

    @Test
    @DisplayName("ADMINISTRADOR_GERAL com escopo NÃO concede ROLE_ADMINISTRADOR_GERAL (defesa em profundidade OPER-02)")
    void scopedAdminGrantDoesNotConferGlobalAuthority() {
        OperationalIdentityLookup lookup = mock(OperationalIdentityLookup.class);
        UUID identityId = UUID.randomUUID();
        UUID scope = UUID.randomUUID();
        when(lookup.lookupIdentity(ISSUER, SUBJECT)).thenReturn(Optional.of(
                new OperationalIdentityLookup.OperationalIdentity(
                        identityId,
                        Set.of(new OperationalIdentityLookup.GrantedRole("ADMINISTRADOR_GERAL", scope)))));

        BetufuRoleAuthorityContributor contributor = new BetufuRoleAuthorityContributor(lookup);
        List<String> authorities = names(contributor.mapAuthorities(createOidcAuthorities(ISSUER, SUBJECT, null)));

        assertThat(authorities).doesNotContain("ROLE_ADMINISTRADOR_GERAL");
        assertThat(authorities).contains(SCOPE_OPENID);
    }

    @Test
    @DisplayName("ADMINISTRADOR_COMPETICAO com escopo concede ROLE_ADMINISTRADOR_COMPETICAO")
    void scopedCompetitionAdminGrantConfersAuthority() {
        OperationalIdentityLookup lookup = mock(OperationalIdentityLookup.class);
        UUID identityId = UUID.randomUUID();
        UUID scope = UUID.randomUUID();
        when(lookup.lookupIdentity(ISSUER, SUBJECT)).thenReturn(Optional.of(
                new OperationalIdentityLookup.OperationalIdentity(
                        identityId,
                        Set.of(new OperationalIdentityLookup.GrantedRole("ADMINISTRADOR_COMPETICAO", scope)))));

        BetufuRoleAuthorityContributor contributor = new BetufuRoleAuthorityContributor(lookup);
        List<String> authorities = names(contributor.mapAuthorities(createOidcAuthorities(ISSUER, SUBJECT, null)));

        assertThat(authorities).contains("ROLE_ADMINISTRADOR_COMPETICAO");
        assertThat(authorities).contains(SCOPE_OPENID);
    }

    @Test
    @DisplayName("ADMINISTRADOR_COMPETICAO sem escopo NÃO concede autoridade (papel escopado exige escopo)")
    void competitionAdminWithoutScopeDoesNotConferAuthority() {
        OperationalIdentityLookup lookup = mock(OperationalIdentityLookup.class);
        UUID identityId = UUID.randomUUID();
        when(lookup.lookupIdentity(ISSUER, SUBJECT)).thenReturn(Optional.of(
                new OperationalIdentityLookup.OperationalIdentity(
                        identityId,
                        Set.of(new OperationalIdentityLookup.GrantedRole("ADMINISTRADOR_COMPETICAO", null)))));

        BetufuRoleAuthorityContributor contributor = new BetufuRoleAuthorityContributor(lookup);
        List<String> authorities = names(contributor.mapAuthorities(createOidcAuthorities(ISSUER, SUBJECT, null)));

        assertThat(authorities).doesNotContain("ROLE_ADMINISTRADOR_COMPETICAO");
    }

    @Test
    @DisplayName("MODERADOR com escopo concede ROLE_MODERADOR")
    void scopedModeratorGrantConfersAuthority() {
        OperationalIdentityLookup lookup = mock(OperationalIdentityLookup.class);
        UUID identityId = UUID.randomUUID();
        UUID scope = UUID.randomUUID();
        when(lookup.lookupIdentity(ISSUER, SUBJECT)).thenReturn(Optional.of(
                new OperationalIdentityLookup.OperationalIdentity(
                        identityId,
                        Set.of(new OperationalIdentityLookup.GrantedRole("MODERADOR", scope)))));

        BetufuRoleAuthorityContributor contributor = new BetufuRoleAuthorityContributor(lookup);
        List<String> authorities = names(contributor.mapAuthorities(createOidcAuthorities(ISSUER, SUBJECT, null)));

        assertThat(authorities).contains("ROLE_MODERADOR");
        assertThat(authorities).contains(SCOPE_OPENID);
    }

    @Test
    @DisplayName("MODERADOR sem escopo NÃO concede autoridade (papel escopado exige escopo)")
    void moderatorWithoutScopeDoesNotConferAuthority() {
        OperationalIdentityLookup lookup = mock(OperationalIdentityLookup.class);
        UUID identityId = UUID.randomUUID();
        when(lookup.lookupIdentity(ISSUER, SUBJECT)).thenReturn(Optional.of(
                new OperationalIdentityLookup.OperationalIdentity(
                        identityId,
                        Set.of(new OperationalIdentityLookup.GrantedRole("MODERADOR", null)))));

        BetufuRoleAuthorityContributor contributor = new BetufuRoleAuthorityContributor(lookup);
        List<String> authorities = names(contributor.mapAuthorities(createOidcAuthorities(ISSUER, SUBJECT, null)));

        assertThat(authorities).doesNotContain("ROLE_MODERADOR");
    }

    @Test
    @DisplayName("Preserva ROLE_TECHNICAL do token e acrescenta papéis operacionais do banco simultaneamente")
    void preservesTechnicalRoleAndAddsOperationalRoles() {
        OperationalIdentityLookup lookup = mock(OperationalIdentityLookup.class);
        UUID identityId = UUID.randomUUID();
        when(lookup.lookupIdentity(ISSUER, SUBJECT)).thenReturn(Optional.of(
                new OperationalIdentityLookup.OperationalIdentity(
                        identityId,
                        Set.of(new OperationalIdentityLookup.GrantedRole("ADMINISTRADOR_GERAL", null)))));

        BetufuRoleAuthorityContributor contributor = new BetufuRoleAuthorityContributor(lookup);
        List<String> authorities = names(contributor.mapAuthorities(
                createOidcAuthorities(ISSUER, SUBJECT, List.of("TECHNICAL"))));

        assertThat(authorities).contains("ROLE_TECHNICAL", "ROLE_ADMINISTRADOR_GERAL", SCOPE_OPENID);
    }

    @Test
    @DisplayName("Identidade inexistente devolve apenas autoridades base sem erro")
    void nonExistentIdentityReturnsBaseAuthoritiesWithoutError() {
        OperationalIdentityLookup lookup = mock(OperationalIdentityLookup.class);
        when(lookup.lookupIdentity(ISSUER, SUBJECT)).thenReturn(Optional.empty());

        BetufuRoleAuthorityContributor contributor = new BetufuRoleAuthorityContributor(lookup);
        List<String> authorities = names(contributor.mapAuthorities(createOidcAuthorities(ISSUER, SUBJECT, null)));

        // As autoridades base sao preservadas integralmente: OidcTechnicalAuthorityMapper faz
        // addAll(authorities) por contrato do Gate 1, entao OIDC_USER continua presente. O que
        // esta prova exige e que identidade inexistente nao produza NENHUMA autoridade de papel.
        assertThat(authorities).contains(SCOPE_OPENID);
        assertThat(authorities).noneMatch(authority -> authority.startsWith("ROLE_"));
    }

    @Test
    @DisplayName("Coleção nula ou authorities não-OIDC são tratadas de forma segura")
    void nullAndNonOidcAuthoritiesHandledSafely() {
        OperationalIdentityLookup lookup = mock(OperationalIdentityLookup.class);
        BetufuRoleAuthorityContributor contributor = new BetufuRoleAuthorityContributor(lookup);

        assertThat(contributor.mapAuthorities(null)).isEmpty();

        Collection<? extends GrantedAuthority> nonOidc = Set.of(new SimpleGrantedAuthority("ROLE_CUSTOM"));
        assertThat(names(contributor.mapAuthorities(nonOidc))).containsExactly("ROLE_CUSTOM");
        verify(lookup, never()).lookupIdentity(any(), any());
    }

    @Test
    @DisplayName("Reconhece claim iss como URL e URI da mesma forma que String")
    void handlesIssAsUrlAndUri() throws Exception {
        OperationalIdentityLookup lookup = mock(OperationalIdentityLookup.class);
        UUID identityId = UUID.randomUUID();
        when(lookup.lookupIdentity(ISSUER, SUBJECT)).thenReturn(Optional.of(
                new OperationalIdentityLookup.OperationalIdentity(
                        identityId,
                        Set.of(new OperationalIdentityLookup.GrantedRole("ADMINISTRADOR_GERAL", null)))));

        BetufuRoleAuthorityContributor contributor = new BetufuRoleAuthorityContributor(lookup);

        // Test with URL
        OidcIdToken urlToken = new OidcIdToken(
                "token-url",
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(300),
                Map.of(
                        IdTokenClaimNames.ISS, new URL(ISSUER),
                        IdTokenClaimNames.SUB, SUBJECT));
        List<String> urlAuthorities = names(contributor.mapAuthorities(
                Set.of(new OidcUserAuthority(urlToken, null))));
        assertThat(urlAuthorities).contains("ROLE_ADMINISTRADOR_GERAL");

        // Test with URI
        OidcIdToken uriToken = new OidcIdToken(
                "token-uri",
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(300),
                Map.of(
                        IdTokenClaimNames.ISS, new URI(ISSUER),
                        IdTokenClaimNames.SUB, SUBJECT));
        List<String> uriAuthorities = names(contributor.mapAuthorities(
                Set.of(new OidcUserAuthority(uriToken, null))));
        assertThat(uriAuthorities).contains("ROLE_ADMINISTRADOR_GERAL");
    }

    // -----------------------------------------------------------------------
    // OperationalIdentityLookupService tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("OperationalIdentityLookupService resolve identidade e mapeia papéis com escopo")
    void lookupServiceResolvesIdentityAndMapsRoles() {
        LocalIdentityRepository localIdentityRepository = mock(LocalIdentityRepository.class);
        OperationalRoleQuery operationalRoleQuery = mock(OperationalRoleQuery.class);
        OperationalIdentityLookupService service =
                new OperationalIdentityLookupService(localIdentityRepository, operationalRoleQuery);

        UUID identityId = UUID.randomUUID();
        UUID scopeRef = UUID.randomUUID();
        LocalIdentity localIdentity = mock(LocalIdentity.class);
        when(localIdentity.getIdentityId()).thenReturn(identityId);

        when(localIdentityRepository.findByNaturalKey(ISSUER, SUBJECT)).thenReturn(Optional.of(localIdentity));
        when(operationalRoleQuery.findActiveRoles(identityId)).thenReturn(Set.of(
                new OperationalRoleQuery.GrantedRole(OperationalRole.ADMINISTRADOR_COMPETICAO, scopeRef),
                new OperationalRoleQuery.GrantedRole(OperationalRole.ADMINISTRADOR_GERAL, null)));

        Optional<OperationalIdentityLookup.OperationalIdentity> result = service.lookupIdentity(ISSUER, SUBJECT);

        assertThat(result).isPresent();
        assertThat(result.get().identityId()).isEqualTo(identityId);
        assertThat(result.get().roles()).containsExactlyInAnyOrder(
                new OperationalIdentityLookup.GrantedRole("ADMINISTRADOR_COMPETICAO", scopeRef),
                new OperationalIdentityLookup.GrantedRole("ADMINISTRADOR_GERAL", null));
    }

    @Test
    @DisplayName("OperationalIdentityLookupService devolve vazio quando identidade não existe e não cria registro")
    void lookupServiceReturnsEmptyWhenIdentityDoesNotExist() {
        LocalIdentityRepository localIdentityRepository = mock(LocalIdentityRepository.class);
        OperationalRoleQuery operationalRoleQuery = mock(OperationalRoleQuery.class);
        OperationalIdentityLookupService service =
                new OperationalIdentityLookupService(localIdentityRepository, operationalRoleQuery);

        when(localIdentityRepository.findByNaturalKey(ISSUER, SUBJECT)).thenReturn(Optional.empty());

        Optional<OperationalIdentityLookup.OperationalIdentity> result = service.lookupIdentity(ISSUER, SUBJECT);

        assertThat(result).isEmpty();
        verify(operationalRoleQuery, never()).findActiveRoles(any());
        verify(localIdentityRepository, never()).insertIfAbsent(any(), any());
    }

    @Test
    @DisplayName("OperationalIdentityLookupService devolve vazio para issuer ou subject nulos ou em branco")
    void lookupServiceReturnsEmptyForNullOrBlank() {
        LocalIdentityRepository localIdentityRepository = mock(LocalIdentityRepository.class);
        OperationalRoleQuery operationalRoleQuery = mock(OperationalRoleQuery.class);
        OperationalIdentityLookupService service =
                new OperationalIdentityLookupService(localIdentityRepository, operationalRoleQuery);

        assertThat(service.lookupIdentity(null, SUBJECT)).isEmpty();
        assertThat(service.lookupIdentity(ISSUER, null)).isEmpty();
        assertThat(service.lookupIdentity("  ", SUBJECT)).isEmpty();
        assertThat(service.lookupIdentity(ISSUER, "  ")).isEmpty();

        verify(localIdentityRepository, never()).findByNaturalKey(any(), any());
    }

    // -----------------------------------------------------------------------
    // OperationalRole scope contract invariants
    // -----------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(OperationalRole.class)
    @DisplayName("Invariante D-46/OPER-02: todo papel declara consistentemente se exige escopo")
    void operationalRoleScopeConsistency(OperationalRole role) {
        if (role == OperationalRole.ADMINISTRADOR_GERAL) {
            assertThat(role.isGlobal()).isTrue();
            assertThat(role.requiresScope()).isFalse();
        } else {
            assertThat(role.isGlobal()).isFalse();
            assertThat(role.requiresScope()).isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Collection<? extends GrantedAuthority> createOidcAuthorities(
            String issuer, String subject, Object roleClaimValue) {
        Map<String, Object> claims = roleClaimValue == null
                ? Map.of(IdTokenClaimNames.ISS, issuer, IdTokenClaimNames.SUB, subject)
                : Map.of(IdTokenClaimNames.ISS, issuer, IdTokenClaimNames.SUB, subject, "betufu_roles", roleClaimValue);

        OidcIdToken idToken = new OidcIdToken(
                "token-test",
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(300),
                claims);

        return Set.of(
                new OidcUserAuthority(idToken, null),
                new SimpleGrantedAuthority(SCOPE_OPENID));
    }

    private static List<String> names(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).toList();
    }
}
