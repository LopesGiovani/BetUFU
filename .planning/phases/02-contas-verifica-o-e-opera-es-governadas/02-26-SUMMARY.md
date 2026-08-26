---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 26
subsystem: authenticated-navigation
tags: [angular, signals, routing, accessibility, security]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: rota S6 do Plano 02-20 e contrato/cliente de capacidades do Plano 02-32
provides:
  - Guard global de completude de perfil sobre todas as rotas autenticadas
  - Navegacao S7 governada pelas tres capacidades fechadas do BFF
  - Limpeza de capacidades em expiracao e logout para impedir estado privilegiado residual
  - Provas de 403 autoritativo, acessibilidade estrutural e fronteira de rotas
affects: [02-27, AUTH-04, PROF-01, PROF-02, OPER-01, OPER-02, OPER-03, OPER-04, IDEN-05]

actuals:
  tokens: not-recorded
  tasks: 1
  commits: 2

tech-stack:
  added: []
  patterns:
    - Uniao fechada de capacidades consumida do BFF sem papel ou PII no Angular
    - Guard global composto por canActivateChild com excecao explicita para S1 e rotas tecnicas
    - Estado privilegiado descartado em toda transicao de sessao
    - Menu nativo como conveniencia de navegacao; autorizacao continua no backend

key-files:
  created:
    - frontend/src/app/core/profile/profile-completion.guard.ts
    - frontend/src/app/core/profile/profile-completion.guard.spec.ts
  modified:
    - frontend/src/app/app.routes.ts
    - frontend/src/app/features/operations/operations-api.adapter.ts
    - frontend/src/app/features/operations/operations-api.adapter.spec.ts
    - frontend/src/app/features/operations/operations-panel-page.component.spec.ts
    - frontend/src/app/features/operations/operations.models.ts
    - frontend/src/app/layout/authenticated-layout.component.ts
    - frontend/src/app/layout/authenticated-layout.component.html
    - frontend/src/app/layout/authenticated-layout.component.scss
    - frontend/src/app/layout/session-layout.spec.ts
    - scripts/validate-angular-route-boundary.mjs
    - scripts/validate-angular-route-boundary.test.mjs

key-decisions:
  - "Capability duplicada ou desconhecida nao e convertida em concessao; o cliente falha fechado."
  - "Expiracao, logout e troca de sessao limpam capacidades, inscricao e latch antes de novo carregamento."
  - "Enquanto o perfil esta incompleto, S1 exibe somente Sair e o guard evita loop ao permitir a propria rota de conclusao."
  - "Ocultar item de menu nao autoriza operacao: o 403 real do BFF permanece a decisao final."

requirements-completed: [AUTH-04, PROF-01, PROF-02, OPER-01, OPER-02, OPER-03, OPER-04, IDEN-05]
---

# Plano 02-26 - Navegacao autenticada governada por capacidades

## Verification Evidence

- Angular completo: `219/219` em 19 arquivos, sem falhas.
- Validadores Node completos: `623/623` em 8 suites; a suite focal da fronteira passou
  `171/171` e o validador direto tambem passou.
- Backend completo com `clean verify`: Surefire `251/251` em 23 classes e Failsafe
  `332/332` em 43 classes. A execucao canonica usou HMAC descartavel somente no processo
  e removeu as variaveis ao terminar.
- Build Angular de producao: sucesso, somente com tres avisos SCSS preexistentes.
- Testes focais do adapter e do layout: `37/37`.
- A mutacao que removeu `canActivateChild` ficou vermelha em
  `PROFILE_GUARD_COMPOSITION`; a restauracao foi conferida por SHA-256 e o validador voltou
  a passar.
- Os 13 paths alterados coincidiram exatamente com a allowlist, sem path externo ou ausente.
- Revisao final do snapshot: `PASS`, sem P0, P1 ou P2.

## What Was Built

- `profileCompletionGuard` protege globalmente a arvore autenticada. Perfil incompleto vai
  para S1 por `UrlTree`; S1 e os caminhos tecnicos necessarios permanecem acessiveis para
  evitar loop.
- O `AuthenticatedLayout` consulta o cliente gerado de capacidades e apresenta apenas os
  destinos permitidos por `VIEW_OPERATIONAL_PANEL`, `MANAGE_ROLES` e
  `CORRECT_ENROLLMENT`. Nenhum papel, identidade ou PII e interpretado no frontend.
- O ciclo de vida da sessao limpa o snapshot anterior antes de carregar outro ator. Uma
  resposta antiga nao pode reaparecer depois de expiracao ou logout.
- A prova integrada percorre cliente gerado real, adapter, layout e pagina, e confirma que
  um cliente defasado ainda encontra o `403` autoritativo do BFF.
- Disclosure, ordem de DOM, `tabindex`, ausencia de `preventDefault` e retorno de foco no
  Escape receberam cobertura estrutural e comportamental em teste Angular.

## Deviations from Plan

- Nenhuma dependencia, arquivo de lock, endpoint ou contrato OpenAPI foi alterado.
- O primeiro `clean verify` do backend falhou corretamente porque as variaveis HMAC
  obrigatorias nao estavam definidas. A repeticao canonica usou uma chave Base64
  descartavel de 32 bytes somente no processo e terminou verde, sem persistir segredo.
- A revisao encontrou lacunas de comportamento que foram corrigidas antes do snapshot final;
  toda correcao invalidou o parecer anterior e a revisao final ocorreu sobre os blobs atuais.
- Movimento fisico de foco por Tab e geometria real em viewport de 320 px nao foram
  reivindicados como provados aqui. Essas duas evidencias permanecem no checkpoint visual
  humano do Plano 02-27.

## Next Phase Readiness

- O Plano 02-27 esta desbloqueado para montar o snapshot final da Fase 2 e executar a
  verificacao humana em navegador real.
- O checkpoint continua bloqueante: teclado fisico, foco, disclosure e geometria em 320 px
  precisam ser observados no mesmo snapshot congelado antes de concluir a fase.

## Self-Check: PASSED

- Treze paths reconciliados com a allowlist e commit tecnico `9ace941` presente.
- Suítes completas, build produtivo, mutacao e revisao final registrados acima.
- Nenhuma afirmacao de evidencia fisica foi antecipada ao Plano 02-27.

---
*Completed: 2026-08-26*
