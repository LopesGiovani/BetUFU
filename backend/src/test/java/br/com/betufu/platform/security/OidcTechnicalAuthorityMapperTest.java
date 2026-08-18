package br.com.betufu.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.authentication.OidcIdTokenDecoderFactory;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

/**
 * Prova positiva e negativa do handoff Keycloak para Spring.
 *
 * <p>O contrato aprovado no Gate 1 do 01-03 e literal: claim {@code betufu_roles} como array de
 * strings somente no ID token, com handoff allowlistado {@code TECHNICAL -> ROLE_TECHNICAL}.
 */
class OidcTechnicalAuthorityMapperTest {

    private static final String SCOPE_OPENID = "SCOPE_openid";

    private final OidcTechnicalAuthorityMapper mapper = new OidcTechnicalAuthorityMapper();

    // -----------------------------------------------------------------------
    // Medicao empirica, nao deducao
    // -----------------------------------------------------------------------

    /**
     * Mede se o Spring converte a claim de papel antes de a entregar ao mapper.
     *
     * <p>Este teste existe por causa do P0 do Plano 01-07: {@code iss} chegava como
     * {@link java.net.URL} depois do decode real, e 74 testes verdes nao viram, porque todos
     * usavam tokens construidos a mao. A licao foi que o tipo de uma claim apos o decode e
     * afirmacao sobre framework e exige medicao.
     *
     * <p>A medicao e direta: os conversores padrao sao indexados pelo nome da claim. Se
     * {@code betufu_roles} nao estiver entre eles, nenhum conversor se aplica e o valor chega ao
     * mapper na forma produzida pelo parser do JWT. Se esta assercao um dia falhar, o Spring
     * passou a converter a claim e o mapper precisa ser reavaliado antes de qualquer outra coisa.
     */
    @Test
    @DisplayName("betufu_roles nao possui conversor padrao, logo chega ao mapper sem conversao")
    void roleClaimHasNoDefaultConverter() {
        Map<String, ?> converters = OidcIdTokenDecoderFactory.createDefaultClaimTypeConverters();

        assertThat(converters).doesNotContainKey(OidcTechnicalAuthorityMapper.ROLES_CLAIM);
        // Controle: iss TEM conversor, e foi ele que produziu o P0 do 01-07. Sem esta linha a
        // assercao acima passaria mesmo se o mapa viesse vazio por outro motivo.
        assertThat(converters).containsKey(IdTokenClaimNames.ISS);
    }

    // -----------------------------------------------------------------------
    // Prova positiva
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("array com TECHNICAL concede ROLE_TECHNICAL e preserva as authorities originais")
    void exactTechnicalRoleGrantsAuthority() {
        List<String> mapped = names(map(List.of("TECHNICAL")));

        assertThat(mapped).contains(OidcTechnicalAuthorityMapper.TECHNICAL_AUTHORITY);
        assertThat(mapped).contains(SCOPE_OPENID);
    }

    @Test
    @DisplayName("TECHNICAL entre outros papeis concede apenas TECHNICAL")
    void technicalAmongOtherRolesGrantsOnlyTechnical() {
        List<String> mapped = names(map(List.of("OUTRO", "TECHNICAL")));

        assertThat(mapped).contains(OidcTechnicalAuthorityMapper.TECHNICAL_AUTHORITY);
        assertThat(mapped).doesNotContain("ROLE_OUTRO", "OUTRO");
    }

    // -----------------------------------------------------------------------
    // Prova negativa, allowlist fechada
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("claim ausente nao concede autoridade")
    void missingClaimGrantsNothing() {
        assertThat(names(mapWithClaims(Map.of(IdTokenClaimNames.SUB, "technical"))))
                .doesNotContain(OidcTechnicalAuthorityMapper.TECHNICAL_AUTHORITY);
    }

    @Test
    @DisplayName("array vazio nao concede autoridade")
    void emptyArrayGrantsNothing() {
        assertThat(names(map(List.of())))
                .doesNotContain(OidcTechnicalAuthorityMapper.TECHNICAL_AUTHORITY);
    }

    @Test
    @DisplayName("String isolada nao concede: o contrato aprovado exige array")
    void bareStringGrantsNothing() {
        assertThat(names(map("TECHNICAL")))
                .doesNotContain(OidcTechnicalAuthorityMapper.TECHNICAL_AUTHORITY);
    }

    @Test
    @DisplayName("tipo errado dentro do array nao concede")
    void wrongElementTypeGrantsNothing() {
        assertThat(names(map(List.of(123))))
                .doesNotContain(OidcTechnicalAuthorityMapper.TECHNICAL_AUTHORITY);
    }

    @Test
    @DisplayName("caixa divergente nao concede: a comparacao e byte a byte")
    void differentCaseGrantsNothing() {
        for (String role : List.of("technical", "Technical", "TECHNICAl")) {
            assertThat(names(map(List.of(role))))
                    .as("papel %s", role)
                    .doesNotContain(OidcTechnicalAuthorityMapper.TECHNICAL_AUTHORITY);
        }
    }

    @Test
    @DisplayName("papel que apenas contem TECHNICAL como substring nao concede")
    void substringRolesGrantNothing() {
        for (String role : List.of("NOT_TECHNICAL", "TECHNICAL_ADMIN", "XTECHNICALX", "TECHNICA")) {
            assertThat(names(map(List.of(role))))
                    .as("papel %s", role)
                    .doesNotContain(OidcTechnicalAuthorityMapper.TECHNICAL_AUTHORITY);
        }
    }

    @Test
    @DisplayName("papel administrativo ou de dominio nao concede")
    void unknownRolesGrantNothing() {
        assertThat(names(map(List.of("ADMIN", "USER", "ROLE_TECHNICAL"))))
                .doesNotContain(OidcTechnicalAuthorityMapper.TECHNICAL_AUTHORITY);
    }

    @Test
    @DisplayName("e-mail, username, grupo e realm_access com valor TECHNICAL nao concedem")
    void otherClaimsNeverGrant() {
        Map<String, Object> claims = Map.of(
                IdTokenClaimNames.SUB, "technical",
                "preferred_username", "TECHNICAL",
                "email", "TECHNICAL@betufu.local",
                "groups", List.of("TECHNICAL"),
                "realm_access", Map.of("roles", List.of("TECHNICAL")));

        assertThat(names(mapWithClaims(claims)))
                .doesNotContain(OidcTechnicalAuthorityMapper.TECHNICAL_AUTHORITY);
    }

    @Test
    @DisplayName("authority sem ID token nao concede")
    void authorityWithoutIdTokenGrantsNothing() {
        Collection<? extends GrantedAuthority> mapped =
                mapper.mapAuthorities(Set.of(new SimpleGrantedAuthority(SCOPE_OPENID)));

        assertThat(names(mapped)).containsExactly(SCOPE_OPENID);
    }

    @Test
    @DisplayName("colecao nula de authorities nao explode nem concede")
    void nullAuthoritiesGrantNothing() {
        assertThat(mapper.mapAuthorities(null)).isEmpty();
    }

    // -----------------------------------------------------------------------
    // Fixtures
    // -----------------------------------------------------------------------

    private Collection<? extends GrantedAuthority> map(Object roleClaimValue) {
        return mapWithClaims(Map.of(
                IdTokenClaimNames.SUB, "technical",
                OidcTechnicalAuthorityMapper.ROLES_CLAIM, roleClaimValue));
    }

    private Collection<? extends GrantedAuthority> mapWithClaims(Map<String, Object> claims) {
        OidcIdToken idToken = new OidcIdToken(
                "token-de-teste",
                Instant.EPOCH,
                Instant.EPOCH.plusSeconds(300),
                claims);

        return mapper.mapAuthorities(Set.of(
                new OidcUserAuthority(idToken, null),
                new SimpleGrantedAuthority(SCOPE_OPENID)));
    }

    private static List<String> names(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).toList();
    }
}
