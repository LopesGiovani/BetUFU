package br.com.betufu.platform.operations;

import br.com.betufu.platform.identity.OperationalIdentityLookup;
import br.com.betufu.platform.identity.OperationalIdentityLookup.GrantedRole;
import br.com.betufu.platform.identity.OperationalIdentityLookup.OperationalIdentity;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Serviço de resolução fail-closed de capacidades operacionais a partir dos papéis PostgreSQL
 * vigentes (D-46 / OPER-01 / OPER-02 / OPER-04 / IDEN-05).
 *
 * <p>Consome exclusivamente o contrato consumidor {@link OperationalIdentityLookup} pertencente ao
 * módulo {@code platform}. Nenhum tipo do módulo {@code br.com.betufu.identity} é importado.
 *
 * <p>Regras de projeção fail-closed:
 *
 * <ul>
 *   <li>{@code ADMINISTRADOR_GERAL} global (com {@code scopeRef == null}) concede, nesta ordem:
 *       {@link OperationalCapability#VIEW_OPERATIONAL_PANEL}, {@link
 *       OperationalCapability#MANAGE_ROLES}, {@link OperationalCapability#CORRECT_ENROLLMENT}.
 *   <li>{@code ADMINISTRADOR_COMPETICAO} e {@code MODERADOR} escopados (com {@code scopeRef !=
 *       null}) concedem somente {@link OperationalCapability#VIEW_OPERATIONAL_PANEL}.
 *   <li>{@code ADMINISTRADOR_GERAL} com {@code scopeRef != null}, papéis escopados com {@code
 *       scopeRef == null}, papéis desconhecidos ou identidades não resolvidas não concedem nenhuma
 *       capacidade.
 *   <li>Múltiplos papéis válidos produzem a união sem duplicatas na ordem canônica do enum {@link
 *       OperationalCapability}.
 * </ul>
 */
@Service
@Profile("runtime | integrated")
public class OperationalCapabilitiesService {

  private static final String GENERAL_ADMIN = "ADMINISTRADOR_GERAL";
  private static final String COMPETITION_ADMIN = "ADMINISTRADOR_COMPETICAO";
  private static final String MODERATOR = "MODERADOR";

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
    return getCapabilities(issuer, subject, null);
  }

  /**
   * Resolve capacidades considerando, quando informado, o escopo exato da competicao.
   *
   * <p>O UUID recebido aqui nunca e aceito como prova de autoridade: ele apenas restringe uma
   * concessao persistida que ja precisa conter o mesmo {@code scopeRef}.
   */
  public OperationalCapabilitiesResponse getCapabilities(
      String issuer, String subject, UUID requestedCompetitionId) {
    if (issuer == null || subject == null || issuer.isBlank() || subject.isBlank()) {
      return new OperationalCapabilitiesResponse(List.of());
    }

    Optional<OperationalIdentity> identityOpt = identityLookup.lookupIdentity(issuer, subject);
    if (identityOpt.isEmpty()) {
      return new OperationalCapabilitiesResponse(List.of());
    }

    Set<GrantedRole> roles = identityOpt.get().roles();
    boolean isGlobalAdmin = roles.stream().anyMatch(grant -> isGlobalGrant(grant, GENERAL_ADMIN));
    boolean isScopedCompAdmin =
        roles.stream()
            .anyMatch(
                grant ->
                    COMPETITION_ADMIN.equals(grant.roleName())
                        && scopeMatches(grant, requestedCompetitionId));
    boolean isScopedModerator =
        roles.stream()
            .anyMatch(
                grant ->
                    MODERATOR.equals(grant.roleName())
                        && scopeMatches(grant, requestedCompetitionId));

    List<OperationalCapability> capabilities = new ArrayList<>();
    if (isGlobalAdmin || isScopedCompAdmin || isScopedModerator) {
      capabilities.add(OperationalCapability.VIEW_OPERATIONAL_PANEL);
    }
    if (isGlobalAdmin) {
      capabilities.add(OperationalCapability.MANAGE_ROLES);
      capabilities.add(OperationalCapability.CORRECT_ENROLLMENT);
      capabilities.add(OperationalCapability.MANAGE_SPORTS_CATALOG);
      capabilities.add(OperationalCapability.BOOTSTRAP_SPORTS_COMPETITION);
    } else if (isScopedCompAdmin) {
      capabilities.add(OperationalCapability.MANAGE_SPORTS_CATALOG);
    }
    if (isGlobalAdmin || isScopedCompAdmin || isScopedModerator) {
      capabilities.add(OperationalCapability.VIEW_ODDS_MARKET);
    }
    if (isGlobalAdmin || isScopedCompAdmin) {
      capabilities.add(OperationalCapability.MANAGE_ODDS_MARKET);
      capabilities.add(OperationalCapability.PROPOSE_ODDS_OVERRIDE);
      capabilities.add(OperationalCapability.APPROVE_ODDS_OVERRIDE);
    }
    if (isGlobalAdmin) {
      capabilities.add(OperationalCapability.AUTHORIZE_SETTLEMENT);
      capabilities.add(OperationalCapability.MANAGE_EMERGENCY_PAUSES);
    }

    return new OperationalCapabilitiesResponse(capabilities);
  }

  /**
   * Compara o escopo persistido com a competicao solicitada sem normalizacao ou fallback.
   *
   * <p>Quando nenhuma competicao e solicitada, uma concessao escopada continua sendo elegivel para
   * a listagem do workspace; a lista de competicoes autorizadas continua vindo do BFF.
   */
  public static boolean scopeMatches(GrantedRole grant, UUID requestedCompetitionId) {
    return grant != null
        && grant.scopeRef() != null
        && (requestedCompetitionId == null || grant.scopeRef().equals(requestedCompetitionId));
  }

  private static boolean isGlobalGrant(GrantedRole grant, String roleName) {
    return grant != null && roleName.equals(grant.roleName()) && grant.scopeRef() == null;
  }
}
