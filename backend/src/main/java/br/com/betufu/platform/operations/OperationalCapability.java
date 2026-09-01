package br.com.betufu.platform.operations;

/**
 * Allowlist fechada e ordenada das capacidades operacionais da BETUFU (OPER-01, OPER-02, OPER-04).
 *
 * <p>A ordem canônica declarada governa a serialização da resposta do BFF:
 *
 * <ol>
 *   <li>{@link #VIEW_OPERATIONAL_PANEL}
 *   <li>{@link #MANAGE_ROLES}
 *   <li>{@link #CORRECT_ENROLLMENT}
 * </ol>
 *
 * <p>As capacidades orientam exclusivamente a visibilidade da interface e a navegação no cliente
 * (S7); os endpoints de negócio e os respectivos 403 do BFF continuam sendo a fonte de autorização
 * autoritativa.
 */
public enum OperationalCapability {
  VIEW_OPERATIONAL_PANEL,
  MANAGE_ROLES,
  CORRECT_ENROLLMENT,
  MANAGE_SPORTS_CATALOG,
  BOOTSTRAP_SPORTS_COMPETITION
}
