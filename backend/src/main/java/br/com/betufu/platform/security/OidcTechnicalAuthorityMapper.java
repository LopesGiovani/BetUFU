package br.com.betufu.platform.security;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority;

/**
 * Converte a claim de papel aprovada no Gate 1 do 01-03 em autoridade Spring.
 *
 * <p>Allowlist positiva e fechada: apenas o valor exato {@code TECHNICAL}, dentro da claim {@code
 * betufu_roles} do ID token, produz {@link #TECHNICAL_AUTHORITY}. Nada mais concede autoridade —
 * nem e-mail, nem {@code preferred_username}, nem grupo, nem {@code realm_access}, nem claim
 * alternativa. O contrato aprovado em 2026-08-10 é literal: "Claim {@code betufu_roles} como array
 * de strings somente no ID token; handoff allowlistado TECHNICAL -&gt; ROLE_TECHNICAL".
 *
 * <p><strong>Por que a claim é lida do ID token e não do UserInfo.</strong> O realm declara o
 * mapper {@code betufu-client-roles} com {@code id.token.claim=true}, {@code
 * access.token.claim=false} e {@code userinfo.token.claim=false}. O Plano 01-07 já desabilitou o
 * UserInfo por bean explícito, então o ID token é a única fonte existente.
 *
 * <p><strong>Por que a leitura é defensiva quanto ao tipo.</strong> O Plano 01-07 descobriu que
 * {@code iss} chega como {@link java.net.URL} depois do decode real, invisível para 74 testes
 * verdes que usavam tokens construídos à mão. {@code betufu_roles} é claim custom e portanto não
 * tem conversor registrado — {@code OidcTechnicalAuthorityMapperTest} mede isso em vez de deduzir
 * —, mas a leitura aceita somente {@link Collection} de {@link String} e ignora silenciosamente
 * qualquer outra forma. Um valor inesperado nunca vira autoridade.
 *
 * <p>As autoridades originais do provedor são sempre preservadas: este mapper acrescenta, nunca
 * substitui.
 */
public final class OidcTechnicalAuthorityMapper implements GrantedAuthoritiesMapper {

  /** Nome da claim aprovada. Definido pelo realm, não pelo backend. */
  static final String ROLES_CLAIM = "betufu_roles";

  /** Único valor de papel reconhecido, comparado byte a byte. */
  static final String TECHNICAL_ROLE = "TECHNICAL";

  /** Única autoridade que este mapper pode emitir. */
  static final String TECHNICAL_AUTHORITY = "ROLE_TECHNICAL";

  @Override
  public Collection<? extends GrantedAuthority> mapAuthorities(
      Collection<? extends GrantedAuthority> authorities) {
    Set<GrantedAuthority> mapped = new LinkedHashSet<>();
    if (authorities == null) {
      return mapped;
    }
    mapped.addAll(authorities);

    for (GrantedAuthority authority : authorities) {
      // Somente a autoridade OIDC carrega o ID token. Qualquer outra origem de authority
      // — inclusive um OAuth2UserAuthority sem ID token — e ignorada de proposito.
      if (authority instanceof OidcUserAuthority oidcAuthority
          && oidcAuthority.getIdToken() != null
          && grantsTechnical(oidcAuthority.getIdToken().getClaims().get(ROLES_CLAIM))) {
        mapped.add(new SimpleGrantedAuthority(TECHNICAL_AUTHORITY));
      }
    }

    return mapped;
  }

  /**
   * Decide se o valor bruto da claim concede o papel técnico.
   *
   * <p>Exige coleção; um {@link String} isolado com o texto {@code TECHNICAL} nao concede, porque o
   * contrato aprovado fala em array. A comparacao e {@link String#equals}, nunca {@code
   * equalsIgnoreCase}, {@code contains} ou {@code startsWith}: {@code technical}, {@code
   * NOT_TECHNICAL} e {@code TECHNICAL_ADMIN} sao papeis distintos e nao aprovados.
   */
  private static boolean grantsTechnical(Object claimValue) {
    if (!(claimValue instanceof Collection<?> values)) {
      return false;
    }
    for (Object value : values) {
      if (value instanceof String role && TECHNICAL_ROLE.equals(role)) {
        return true;
      }
    }
    return false;
  }
}
