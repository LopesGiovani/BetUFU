package br.com.betufu.platform.operations;

import java.util.List;

/**
 * Resposta imutável da consulta de capacidades operacionais atuais (OPER-01, OPER-02, OPER-04,
 * IDEN-05).
 *
 * <p>O payload contém exclusivamente o campo {@code capabilities}, omitindo identityId, nomes de
 * papel, escopos, apelidos, PII, claims, tokens, HMAC ou MFA para preservar o modelo de menor
 * privilégio e não vazar dados privados ao navegador (T-02-32-03).
 */
public record OperationalCapabilitiesResponse(List<OperationalCapability> capabilities) {

  public OperationalCapabilitiesResponse {
    capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
  }
}
