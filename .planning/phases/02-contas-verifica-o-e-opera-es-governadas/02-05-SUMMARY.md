---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 05
subsystem: ui
tags: [angular, signals, profile, routing, accessibility]

requires:
  - phase: 02-contas-verifica-o-e-opera-es-governadas
    provides: API de perfil próprio e cliente TypeScript aprovado pelos Planos 02-03 e 02-14
provides:
  - Gate Angular de perfil obrigatório com estados seguros e acessíveis
  - Primeira rota de produto lazy-loaded no build de produção
  - Fronteira de rotas baseada em allowlist fechada e mutações discriminantes
affects: [02-15, PROF-01, PROF-02]

actuals:
  tokens: 19448
  tasks: 3
  commits: 1

tech-stack:
  added: []
  patterns:
    - Adapter consome somente o cliente OpenAPI gerado
    - Store Angular usa sinais privados e seletores computados
    - Rotas de produção são validadas por propriedade e allowlist fechada

key-files:
  created:
    - frontend/src/app/features/profile/profile.models.ts
    - frontend/src/app/features/profile/profile-api.adapter.ts
    - frontend/src/app/features/profile/profile.store.ts
    - frontend/src/app/features/profile/profile-gate-page.component.ts
    - frontend/src/app/features/profile/profile-gate-page.component.html
    - frontend/src/app/features/profile/profile-gate-page.component.scss
  modified:
    - frontend/src/app/app.routes.production.ts
    - frontend/src/app/app.routes.development.ts
    - scripts/validate-angular-route-boundary.mjs
    - scripts/validate-angular-route-boundary.test.mjs

key-decisions:
  - "O sucesso do gate solicita /conta/perfil; a rota S2 real permanece responsabilidade do Plano 02-15."
  - "Erros visuais usam status e ApiErrorCode estáveis; detail bruto do servidor nunca é renderizado."
  - "Produção aceita somente as seis rotas de produto aprovadas e mantém tracer e diagnóstico proibidos."

patterns-established:
  - "Privacy by presentation: dados privados e detail não chegam ao DOM."
  - "Route boundary by property: validar autenticação, unicidade, lazy loading e allowlist, não um snapshot vazio."

requirements-completed: [PROF-01]
requirements-partial: [PROF-02]

coverage:
  - id: D1
    description: Pessoa autenticada conclui o gate de perfil sem perder o apelido em recusa ou falha.
    requirement: PROF-01
    verification:
      - kind: unit
        ref: "profile-api.adapter.spec.ts e profile-gate-page.component.spec.ts"
        status: pass
      - kind: automated_ui
        ref: "playwright:02-05-s1-mobile-320-fixed.png e 02-05-s1-desktop-fixed.png"
        status: pass
    human_judgment: false
  - id: D2
    description: A apresentação do gate não expõe e-mail, matrícula, HMAC, papéis, MFA ou detail bruto.
    requirement: PROF-02
    verification:
      - kind: unit
        ref: "profile-gate-page.component.spec.ts"
        status: pass
    human_judgment: false
  - id: D3
    description: Produção publica o gate sob layout autenticado e continua sem tracer ou diagnóstico.
    requirement: PROF-01
    verification:
      - kind: other
        ref: "node --test scripts/validate-angular-route-boundary.test.mjs"
        status: pass
      - kind: other
        ref: "npm --prefix frontend run build -- --configuration production"
        status: pass
    human_judgment: false

duration: not-recorded
completed: 2026-08-21
status: complete
---

# Phase 2 Plan 05: Gate de perfil e fronteira de produção

**O primeiro acesso autenticado agora apresenta um gate de perfil acessível, enquanto o build de produção publica somente rotas de produto explicitamente aprovadas.**

## Performance

- **Duration:** não cronometrada; execução acompanhada em múltiplas interações
- **Started:** não registrado
- **Completed:** 2026-08-21T16:26:42-03:00
- **Tasks:** 3
- **Files modified:** 15

## Accomplishments

- Implementou modelo, adapter, store e página S1 com foco inicial, validação acessível, cópias estáveis e preservação do apelido.
- Publicou `/conta/perfil/completar` por lazy loading sob o layout autenticado em development e production.
- Substituiu regras que fixavam a lista de produção vazia por propriedades duráveis, cobertas por quatro mutações negativas novas.
- Preservou integralmente a proibição de tracer e diagnóstico em produção.

## Task Commits

- `4f9f3d4` — `feat(02-05): deliver profile gate and production route boundary` — consolida as três tasks em 15 arquivos de implementação e testes.

Este summary, o plano ajustado, `02-VALIDATION.md`, `STATE.md` e `ROADMAP.md` formarão o commit documental subsequente após aprovação específica.

## Files Created/Modified

- `frontend/src/app/features/profile/*` — fatia Angular completa do gate S1 e seus testes.
- `app.routes.production.ts` e `app.routes.development.ts` — registro lazy-loaded sob autenticação.
- `validate-angular-route-boundary*.mjs` — allowlists e mutações negativas da fronteira de build.
- `authenticated-layout.component.scss` e `session-layout.spec.ts` — contenção responsiva do gate e prova estrutural do layout.
- `infra/compose.yaml` — suporte local estritamente necessário à jornada visual autenticada.

## Decisions Made

- O gate navega contratualmente para `/conta/perfil`, mas não cria uma S2 temporária; a integração completa será provada no 02-15.
- A UI nunca confia em `detail` de Problem Details para texto visível; somente códigos e estados conhecidos determinam a cópia.
- A fronteira de produção usa um conjunto fechado de rotas aprovadas e exige layout autenticado, unicidade e lazy loading.

## Deviations from Plan

### Auto-fixed Issues

**1. Fixtures antigas de mutação do tracer não formavam uma base válida**
- **Found during:** Task 3, revisão Codex
- **Fix:** as fixtures passaram a combinar tracer e gate válidos antes de aplicar cada mutação.
- **Verification:** 158/158 testes focais do validador e 529/529 testes Node agregados.

**2. O layout precisava conter a nova superfície em 320 px**
- **Found during:** Task 2, verificação visual
- **Fix:** estilos do layout autenticado foram ajustados e o contrato estrutural ganhou teste.
- **Verification:** screenshots móvel e desktop sem overflow, com foco inicial no apelido.

**3. A jornada visual local exigia a configuração autorizada do serviço de apoio**
- **Found during:** Task 2
- **Fix:** `infra/compose.yaml` recebeu o ajuste mínimo para executar a jornada autenticada local.
- **Verification:** serviços permaneceram saudáveis e os resíduos de apelido usados no teste foram removidos.

**Total deviations:** 3 correções necessárias à prova do contrato, sem dependência nova e sem ampliar as rotas publicadas.

## Issues Encountered

- O build recebeu `--configuration production` duas vezes porque o script npm já fixa essa opção; foi apenas aviso e o build terminou dentro dos budgets.
- Maven exibiu avisos conhecidos de Logback, agente dinâmico do Mockito, open-in-view e reconexão Redis durante teardown; nenhuma suíte falhou.

## Evidências de validação

- Validador focal: 158/158.
- Node agregado: 529/529.
- Angular: 69/69 em 9 arquivos.
- Maven agregado: 164 testes Surefire e 143 Failsafe, total 307/307.
- Build production: sucesso; bundle inicial de 257,75 kB e chunk lazy do gate de 51,15 kB.
- Quatro mutações negativas novas observaram vermelho e foram restauradas.
- Bloco `PRODUCTION_TRACER_FORBIDDEN` preservado byte a byte; SHA-256 `038c5b913af99859bf0988dbd59aa93f39ffa1a4abf56ded19757ab996c7a306`.
- `git diff --check`: sem erros; somente avisos normais de LF/CRLF no Windows.

## Partial Requirement: PROF-02

O gate cumpre a parcela de privacidade e apresentação segura de `PROF-02`. A página pública S2 e os demais dados públicos continuam no Plano 02-15 e em fases posteriores; por isso o requisito não é declarado integralmente concluído.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- O Plano 02-15 pode reutilizar o adapter/store, publicar S2 e fechar a borda `salvar S1 → renderizar S2`.
- A Wave 3 continua parcial até a conclusão e integração dos Planos 02-09 e 02-21.
- Os artefatos `.playwright-cli/` permanecem locais e fora do commit documental.

---
*Phase: 02-contas-verifica-o-e-opera-es-governadas*
*Completed: 2026-08-21*
