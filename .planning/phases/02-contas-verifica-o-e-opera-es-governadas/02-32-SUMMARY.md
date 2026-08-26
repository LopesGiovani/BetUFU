---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 32
subsystem: operational-capabilities
tags: [spring, oidc, roles, openapi, angular-client, security]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: painel, papeis PostgreSQL e contrato de correcao pelos Planos 02-10, 02-17 e 02-25
provides:
  - GET autenticado /api/v1/operations/capabilities
  - Matriz fail-closed de papeis PostgreSQL para tres capacidades operacionais
  - Payload minimo sem identidade, papel, escopo ou PII
  - Contrato OpenAPI e cliente TypeScript gerado com inventario e digest aprovados
affects: [02-26, 02-27, OPER-01, OPER-02, OPER-03, OPER-04, IDEN-05]

actuals:
  tokens: not-recorded
  tasks: 3
  commits: 2

tech-stack:
  added: []
  patterns:
    - Contrato BFF de capacidades derivado exclusivamente do PostgreSQL pelo par OIDC exato
    - Enum fechado e ordenado para resposta deterministica
    - Endpoint de descoberta que nao substitui a autorizacao dos endpoints de negocio
    - Cliente gerado governado por inventario e digest literais

key-files:
  created:
    - backend/src/main/java/br/com/betufu/platform/operations/OperationalCapability.java
    - backend/src/main/java/br/com/betufu/platform/operations/OperationalCapabilitiesResponse.java
    - backend/src/main/java/br/com/betufu/platform/operations/OperationalCapabilitiesService.java
    - backend/src/main/java/br/com/betufu/platform/operations/OperationalCapabilitiesController.java
    - backend/src/test/java/br/com/betufu/platform/operations/OperationalCapabilitiesResponseTest.java
    - backend/src/test/java/br/com/betufu/platform/operations/OperationalCapabilitiesIT.java
    - frontend/src/app/api/generated/api/operational-capabilities.service.ts
    - frontend/src/app/api/generated/model/operational-capability.ts
    - frontend/src/app/api/generated/model/operational-capabilities-response.ts
  modified:
    - backend/openapi/betufu.yaml
    - frontend/scripts/generate-api.mjs
    - frontend/src/app/api/generated/api/api.ts
    - frontend/src/app/api/generated/model/models.ts
    - scripts/validate-angular-route-boundary.test.mjs

key-decisions:
  - "Capacidades sao derivadas de papeis vigentes no PostgreSQL pelo issuer e subject exatos, nunca de claims ou roles do Keycloak."
  - "O Angular recebe somente VIEW_OPERATIONAL_PANEL, MANAGE_ROLES e CORRECT_ENROLLMENT; nomes de papel, escopos e identidade nao atravessam o payload."
  - "Capabilities orientam navegacao, mas os matchers e 403 do backend continuam autoritativos."
  - "O cliente so foi aprovado depois do inventario de 50 arquivos e do digest literal BDF0CA8BDBC4118DA74FA0E5B0FFF47D4C0FF636DC9309751F550161F8EE4E71."

requirements-completed: [OPER-01, OPER-02, OPER-03, OPER-04, IDEN-05]
---

# Plano 02-32 - Contrato BFF de capacidades operacionais

## Verification Evidence

- Backend completo com `clean verify`: Surefire `251/251` em 23 classes e Failsafe
  `332/332` em 43 classes, sem falhas, erros ou ignorados.
- Angular completo da arvore combinada: `196/196` em 18 arquivos.
- Validadores Node: `617/617` em 8 suites.
- Build Angular de producao: sucesso, somente com avisos SCSS preexistentes.
- Contrato e gerador: inventario aprovado de `50` arquivos e digest literal
  `BDF0CA8BDBC4118DA74FA0E5B0FFF47D4C0FF636DC9309751F550161F8EE4E71`.
- A arvore gerada foi produzida pelo gerador oficial; nenhum arquivo gerado foi editado manualmente.
- Reconciliacao final: 14/14 paths do plano coincidiram por hash de blob com o commit
  tecnico durante a revisao combinada.
- Revisao final do snapshot combinado: `PASS`, sem P0, P1 ou P2.

## What Was Built

- `GET /api/v1/operations/capabilities` le o ator OIDC autenticado e consulta a identidade
  operacional pelo par `issuer + subject` exato.
- A matriz entrega as tres capacidades ao Administrador Geral e somente visualizacao do
  painel a Administrador de Competicao ou Moderador; papel ou escopo invalido falha fechado.
- A resposta possui um unico campo, `capabilities`, e nao publica identidade, nome de papel,
  escopo, apelido, PII, claim, token ou MFA.
- OpenAPI e cliente Angular gerado materializam somente o contrato aprovado. O frontend nao
  infere autoridade: cada operacao continua sujeita ao endpoint e ao 403 do backend.

## Deviations from Plan

- Nenhuma dependencia, migration ou configuracao de seguranca foi alterada.
- A allowlist com glob da arvore gerada materializou 14 paths reais, todos reconciliados.
- O `npm ci --offline` aprovado auditou 479 pacotes com zero vulnerabilidades; cinco scripts
  de instalacao permaneceram bloqueados e nao foram executados.

## Next Phase Readiness

- O Plano 02-26 esta desbloqueado: pode consumir o cliente aprovado e a rota S6 sem mapear
  papeis no Angular.
- O Plano 02-27 permanece como checkpoint humano final depois da implementacao do 02-26.

## Self-Check: PASSED

- Quatorze paths reais reconciliados com a allowlist declarada.
- Commit tecnico presente e historico linear sobre o 02-20.
- Inventario, digest, suites agregadas e revisao final registrados acima.

---
*Completed: 2026-08-26*
