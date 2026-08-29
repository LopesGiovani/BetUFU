---
phase: 03-experiencia-esportiva-do-piloto
plan: 03
subsystem: sports-public-ui
tags: [spring-boot, openapi, angular, signals, webgl, playwright, accessibility]

requires:
  - phase: 03-experiencia-esportiva-do-piloto
    provides: tracer esportivo publicado e autorização escopada do plano 03-02
  - phase: 03-experiencia-esportiva-do-piloto
    provides: variante V, UI-SPEC e DAG replanejado do plano 03-18
provides:
  - agenda pública paginada com eventos publicados reais e filtros sustentados por V9
  - candidato OpenAPI read-only aprovado e cliente Angular governado pelo digest exato
  - Arena V em Angular nativo com hero automático acessível, ticker e Grainient com fallback
  - rollover temporal reativo que remove eventos vencidos sem nova chamada à API
affects: [03-04, 03-11, 03-17, arena-publica, sports-public-api]

actuals:
  tokens: 56086
  tasks: 5
  commits: 2

tech-stack:
  added: []
  patterns:
    - candidato OpenAPI descartável antes da geração governada
    - Angular Signals para estado, filtros, requisições e fronteiras temporais
    - WebGL progressivo com fallback CSS, pausa e cleanup

key-files:
  created:
    - frontend/src/app/features/sports/public-agenda/public-agenda-page.component.ts
    - frontend/src/app/shared/sports/auto-event-hero.component.ts
    - frontend/src/app/shared/visual/grainient-background.component.ts
    - frontend/e2e/sports-pilot.spec.ts
    - .planning/phases/03-experi-ncia-esportiva-do-piloto/03-03-SUMMARY.md
  modified:
    - backend/openapi/betufu.yaml
    - backend/src/main/java/br/com/betufu/sports/internal/tracer/SportsTracerService.java
    - frontend/scripts/generate-api.mjs
    - frontend/src/styles/_tokens.scss
    - frontend/src/app/app.routes.ts

key-decisions:
  - "A home pública usa somente eventos publicados reais; vazio, erro e ausência de próximo evento nunca recebem fixture."
  - "O cliente OpenAPI só é consumido depois de candidato read-only, aprovação do digest e geração governada."
  - "A paleta final separa superfície profunda, sinal coral, título editorial e creme do hero por tokens semânticos."
  - "Eventos vencidos são removidos na fronteira temporal por Signal e timer único, sem polling nem refetch."
  - "Grainient é aprimoramento progressivo; fallback CSS e reduced motion preservam conteúdo e navegação."

patterns-established:
  - "Public agenda store: request id evita resposta antiga, query params reagem à rota e filtros derivam de dados reais."
  - "Composed pause: preferência manual, hover, foco, visibilidade da aba e viewport controlam hero e ticker."
  - "Accessible carousel: slide inativo é inert, aria-hidden e removido da ordem de foco."

requirements-completed: [AUTH-01, SPRT-12]

coverage:
  - id: D1
    description: "Agenda pública lista somente snapshots publicados, com paginação, filtros e ordem estável."
    requirement: AUTH-01
    verification:
      - kind: integration
        ref: "backend/src/test/java/br/com/betufu/sports/SportsTracerIT.java"
        status: pass
      - kind: integration
        ref: "node scripts/maven.mjs -f backend/pom.xml verify — Surefire 251/251, Failsafe 343/343"
        status: pass
    human_judgment: false
  - id: D2
    description: "Candidato OpenAPI aprovado gera cliente governado sem PII pública nem mutação anterior da árvore."
    requirement: AUTH-01
    verification:
      - kind: unit
        ref: "frontend/scripts/generate-api.test.mjs — 72/72"
        status: pass
      - kind: other
        ref: "node frontend/scripts/generate-api.mjs --check"
        status: pass
    human_judgment: false
  - id: D3
    description: "Arena V responsiva apresenta hero, ticker, filtros e estados reais em desktop e 320 px."
    requirement: SPRT-12
    verification:
      - kind: automated_ui
        ref: "frontend/e2e/sports-pilot.spec.ts — Playwright 2/2"
        status: pass
      - kind: manual_procedural
        ref: "aprovação visual do responsável em 2026-08-29"
        status: pass
    human_judgment: true
    rationale: "Fidelidade visual e qualidade editorial exigem julgamento humano além das provas automatizadas."
  - id: D4
    description: "Hero e ticker removem eventos vencidos na fronteira temporal e preservam fuso, foco e reduced motion."
    requirement: SPRT-12
    verification:
      - kind: unit
        ref: "public-agenda-page.component.spec.ts e auto-event-hero.component.spec.ts — focal 13/13"
        status: pass
      - kind: unit
        ref: "npm --prefix frontend run test -- --watch=false — 241/241"
        status: pass
    human_judgment: false

duration: multi-session
completed: 2026-08-29
status: complete
---

# Fase 3 Plano 03: Agenda pública e Arena V

**Agenda esportiva real ponta a ponta, cliente OpenAPI governado e Arena V acessível em Angular nativo**

## Performance

- **Duration:** execução repartida em múltiplas sessões de implementação, refinamento visual e revisão
- **Started:** 2026-08-28
- **Completed:** 2026-08-29T05:40:14-03:00
- **Tasks:** 5
- **Files modified:** 54 paths do snapshot autorizado, incluindo quatro metadados candidatos ignorados pelo Git e seis artefatos de formalização

## Accomplishments

- Estendeu o tracer com listagem pública paginada, filtrável e limitada a snapshots publicados.
- Criou e aprovou o candidato OpenAPI `0B631F67A415BEE7BC95ECF209BD240C922A93ED47B69385B969B093D464AB95` antes de gerar o cliente Angular.
- Transpôs a variante V para a rota `/` com shell Arena, hero automático, ticker, ribbon, rows e Grainient progressivo.
- Fechou regressões de rota, filtros, concorrência de requests, acessibilidade, mobile e expiração temporal dos eventos.

## Task Commits

- `9399a7c` — `feat(03-03): deliver public sports arena` — implementação e testes, 44 paths técnicos.
- `docs(03-03): formalize public sports arena` — este commit formaliza os seis documentos GSD; seu próprio SHA é registrado somente na passada de estado posterior para evitar autorreferência.
- Integração, limpeza e push aguardam aprovações Git específicas do responsável.

## Files Created/Modified

- `SportsTracerController.java` / `SportsTracerService.java` / `SportsTracerRepository.java` — consulta pública paginada e ordenada.
- `backend/openapi/betufu.yaml` — endpoints e schemas públicos allowlist.
- `frontend/scripts/generate-api.mjs` — candidato read-only, digest e geração governada.
- `public-agenda.store.ts` — estado Signal, filtros, cancelamento lógico e rollover temporal.
- `auto-event-hero.component.ts` — duas composições acessíveis do mesmo evento real.
- `grainient-background.component.ts` — atmosfera WebGL opcional com fallback e lifecycle completo.
- `sports-pilot.spec.ts` — prova de navegador em desktop e 320 px.
- `03-UI-SPEC.md` / `03-VALIDATION.md` — paleta aprovada e evidências finais.

## Decisions Made

- Mantida a fronteira do 03-03: agenda pública mínima e Arena V; expansão operacional O1 continua exclusiva do 03-04.
- O vermelho profundo `#910008` é superfície; `#FF626B` é sinal; `#FFC4C4` é título editorial; creme/off-white sustenta o hero.
- O relógio reativo agenda apenas a próxima fronteira relevante, evitando polling contínuo.
- O rail não inventa modalidades ou destinos que a API atual ainda não sustenta.

## Deviations from Plan

### Auto-fixed Issues

**1. Correções de usabilidade e estado descobertas na auditoria visual**
- **Found during:** Task 5 e revisão fresca.
- **Issue:** deep links, respostas fora de ordem, âncora, filtros combinados, pausa compartilhada, foco mobile e nomes longos tinham lacunas.
- **Fix:** sincronização reativa da rota, request id, scroll de âncora, filtros preservados, máquina de pausa comum e ajustes responsivos/acessíveis.
- **Verification:** Angular completo, Playwright e auditoria final sem P0/P1/P2.

**2. Eventos vencidos permaneciam como próximos**
- **Found during:** auditoria final fresca.
- **Issue:** o estado dependia apenas da resposta da API e não se invalidava quando o relógio ultrapassava o início do evento.
- **Fix:** Signal de relógio com timer na próxima fronteira; `upcomingEvents`, `nextEvent` e ticker excluem eventos passados.
- **Verification:** três regressões focais, Angular `241/241` e Playwright `2/2`.

**3. Allowlist continha `frontend/angular.json` sem mudança real**
- **Found during:** reconciliação documental final.
- **Issue:** o arquivo aparecia modificado por CRLF, mas `git diff` mostrava zero diferença de conteúdo.
- **Fix:** path removido da allowlist; nenhum orçamento foi aumentado.
- **Verification:** `git diff --name-only` e auditoria de allowlist final.

---

**Total deviations:** 3 grupos de correções necessários para fidelidade, acessibilidade e correção temporal; nenhum ampliou O1 ou alterou regra de negócio existente.

## Issues Encountered

- O Maven emitiu ruído de teardown de Redis/Hikari e encerrou a JVM fork após 30 segundos, mas todas as 343 integrações passaram e o build terminou com exit code 0.
- Os SCSS da agenda e do hero ficaram próximos do teto de 8 kB; permanecem abaixo do bloqueio e o orçamento não foi relaxado.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- O 03-04 pode expandir o workspace/catálogo O1 usando a mesma identidade visual e o cliente governado.
- Antes disso, a worktree precisa do stage e commit documentais separados, seguidos por autorizações específicas para integração, limpeza e push.
- `AUTH-01` e `SPRT-12` continuam pendentes no mapa global até as expansões e a certificação final dos planos 03-11/03-17.

---
*Phase: 03-experiencia-esportiva-do-piloto*
*Completed: 2026-08-29*
