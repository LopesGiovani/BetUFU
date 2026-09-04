---
phase: 03-experi-ncia-esportiva-do-piloto
verified: 2026-09-02T21:55:03Z
status: gaps_found
score: "1/5 must-haves verified"
behavior_unverified: 0
overrides_applied: 0
decision_coverage: "48/48"
gaps:
  - truth: "SPRT-01: o catalogo suporta varias modalidades por competicao."
    status: failed
    reason: "V10 e o codigo vivo modelam uma relacao singular entre competicao e esporte."
    artifacts:
      - path: "backend/src/main/resources/db/migration/V10__expand_sports_catalog_and_venues.sql"
        issue: "Adiciona somente sports_competition.sport_id."
      - path: "backend/src/main/java/br/com/betufu/sports/internal/catalog/SportsCatalogRepository.java"
        issue: "assignCompetitionSport e tree consultam uma unica modalidade."
    missing:
      - "Tabela de associacao competicao-modalidade e propagacao coerente para eventos, equipes e importacao."
  - truth: "SPRT-02: administradores operam organizacoes e atletas somente no escopo da competicao."
    status: failed
    reason: "Operacoes de organizacao, atleta e leitura de elenco usam acesso global do catalogo; a importacao tambem nao aplica o contrato fixo D-74/D-75."
    artifacts:
      - path: "backend/src/main/java/br/com/betufu/sports/internal/organization/SportsOrganizationService.java"
        issue: "list/create/update/publish chamam requireCatalogAccess sem derivar competicao."
      - path: "backend/src/main/java/br/com/betufu/sports/internal/athlete/SportsAthleteService.java"
        issue: "list/update/publish chamam requireCatalogAccess sem escopo do recurso."
      - path: "backend/src/main/java/br/com/betufu/sports/internal/roster/SportsRosterService.java"
        issue: "getLatestPublication resolve a equipe, mas exige somente acesso global."
      - path: "backend/src/main/java/br/com/betufu/sports/internal/importtabular/SportsXlsxParser.java"
        issue: "Aceita primeira aba arbitraria, colunas extras e converte matricula numerica para long; fixtures declaradas sao bytes PK-mock, nao OOXML."
    missing:
      - "Autorizacao derivada do recurso em todas as operacoes e parser XLSX com template fixo, aba/cabecalho/colunas/tipos e matricula textual preservando zeros."
  - truth: "SPRT-08: a pessoa consegue alcancar e decidir sua proposta de vinculo."
    status: failed
    reason: "A pagina e o store existem, mas nao ha rota ou importacao da tela owner; somente a fila administrativa O8 esta roteada."
    artifacts:
      - path: "frontend/src/app/features/sports/link-proposal/athlete-link-proposal-page.component.ts"
        issue: "Implementa aceite, rejeicao e contestacao, porem permanece orfa."
      - path: "frontend/src/app/app.routes.ts"
        issue: "Nao importa nem registra AthleteLinkProposalPageComponent."
    missing:
      - "Rota pessoal protegida e navegacao para a proposta pendente."
  - truth: "SPRT-09: alteracao tardia de elenco publicado exige nova publicacao, motivo e auditoria."
    status: failed
    reason: "A importacao reutiliza qualquer roster e faz upsert direto de membros, sem verificar PUBLICADO nem criar correcao auditada."
    artifacts:
      - path: "backend/src/main/java/br/com/betufu/sports/internal/importtabular/SportsTabularImportService.java"
        issue: "Seleciona roster existente e executa INSERT ... ON CONFLICT DO UPDATE sem motivo, ator, publicacao ou versionamento."
      - path: "backend/src/main/java/br/com/betufu/sports/internal/roster/SportsRosterService.java"
        issue: "O fluxo protegido existe, mas e contornado pela importacao."
    missing:
      - "Bloqueio de roster publicado e caminho explicito de correcao com motivo, nova versao e auditoria."
  - truth: "SPRT-11: transicoes de evento preservam historico imutavel e auditoria."
    status: failed
    reason: "Suspensao, adiamento e cancelamento alteram a linha corrente de sports_event; V16 versiona resultados, nao transicoes do evento."
    artifacts:
      - path: "backend/src/main/java/br/com/betufu/sports/internal/event/SportsEventRepository.java"
        issue: "updateEventStatus/postponeEvent fazem UPDATE da linha corrente."
      - path: "backend/src/main/resources/db/migration/V16__create_event_result_versions.sql"
        issue: "Cria sports_result_version e status_reason, sem versao de lifecycle do evento."
    missing:
      - "Registro append-only de cada transicao, ator, fonte/motivo e consulta do historico."
  - truth: "SPRT-12: a CTA da agenda abre a pagina completa do evento."
    status: failed
    reason: "A rota /eventos/:eventId existe, mas o adapter gera fragmento #agenda-event-{id}; o E2E vivo confirma o href incorreto."
    artifacts:
      - path: "frontend/src/app/features/sports/public-agenda/public-agenda-api.adapter.ts"
        issue: "detailHref e um fragmento, nao a rota de evento."
      - path: "frontend/e2e/sports-pilot.spec.ts"
        issue: "Assercao espera explicitamente #agenda-event-{id}."
    missing:
      - "Link/routerLink para /eventos/:eventId e teste de navegacao/deep link."
  - truth: "SPRT-14: o piloto admite outras modalidades no catalogo e preserva seus dados oficiais sem simular regras."
    status: failed
    reason: "A taxonomia e singular por competicao e o fluxo detalhado de evento restringe formato a HEAD_TO_HEAD; portanto outras modalidades nao podem ser representadas de forma generica."
    artifacts:
      - path: "backend/src/main/resources/db/migration/V10__expand_sports_catalog_and_venues.sql"
        issue: "Nao ha associacao N:N entre competicao e modalidade."
      - path: "backend/src/main/java/br/com/betufu/sports/internal/event/SportsEventService.java"
        issue: "validateCreateRequest rejeita formatos diferentes de HEAD_TO_HEAD."
    missing:
      - "Suporte de catalogo/agenda para modalidades nao detalhadas e fronteira explicita para classificacao oficial manual."
---

# Phase 3: Experiencia Esportiva do Piloto - Verification Report

**Phase Goal:** Visitantes conseguem acompanhar um catalogo confiavel e generico de competicoes e edicoes esportivas, comecando pela Olimpiada Universitaria UFU 2026, enquanto administradores mantem seus dados somente nos escopos atribuidos.
**Verified:** 2026-09-02T21:55:03Z
**Status:** `gaps_found`
**Re-verification:** No - initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---:|---|---|---|
| 1 | Visitante navega agenda, filtra e abre evento | FAILED | Agenda/filtros e rota existem, mas `public-agenda-api.adapter.ts:196` gera fragmento em vez de `/eventos/:eventId`. |
| 2 | Administradores mantem dados somente no escopo atribuido | FAILED | Organizacao/atleta/elenco usam `requireCatalogAccess()` em operacoes posteriores; importacao e lifecycle de evento contornam protecoes. |
| 3 | Paginas publicas de organizacao, equipe e atleta exibem dados autorizados e favorito aponta para organizacao | VERIFIED | Rotas/componentes `public-organization`, `public-team` e `public-athlete`, adapters OpenAPI e allowlists publicas consultam dados persistidos; favorito usa organizacao. |
| 4 | Atleta sem conta, HMAC transitorio e proposta explicita | FAILED | HMAC/proposta backend e store existem, mas a pagina owner nao e alcancavel por rota. |
| 5 | Elencos publicados, bracket derivado e outras modalidades sem regras inventadas | FAILED | Roster/bracket/standings existem, mas catalogo singular e formato de evento restrito impedem a parte generica; importacao nao protege roster publicado. |

**Score:** 1/5 verdades verificadas. Requisitos apoiados por evidencia de codigo: 10/17.

### Required Artifacts

| Artifact | Status | Details |
|---|---|---|
| `V10__expand_sports_catalog_and_venues.sql` + catalog service/repository | PARTIAL | Schema e consultas de esporte sao singulares. |
| `SportsOrganizationService.java`, `SportsAthleteService.java`, `SportsRosterService.java` | PARTIAL | Caminhos de escrita/leitura nao derivam sempre a competicao. |
| `AthleteLinkProposalPageComponent` + store | ORPHANED | Aceite/rejeicao/contestacao implementados, sem rota owner. |
| `SportsTabularImportService.java` + `SportsXlsxParser.java` | PARTIAL | Upsert contorna publicacao; contrato D-74/D-75 nao e garantido. |
| `SportsEventService.java` + repository + V16 | PARTIAL | Resultados tem versoes; lifecycle do evento nao. |
| `public-agenda-api.adapter.ts` + `app.routes.ts` | PARTIAL | CTA nao aponta para a rota existente. |
| Paginas publicas organization/team/athlete e APIs | VERIFIED | Dados fluem de queries publicas persistidas para stores/componentes. |

### Key Link Verification

| From | To | Via | Status | Details |
|---|---|---|---|---|
| Catalog migration | Catalog service/repository | `sport_id` | PARTIAL | Relacao singular, nao varias modalidades. |
| Organization/athlete/roster services | Competition scope | `requireCatalogAccess` | NOT_WIRED | A verificacao nao usa o escopo do recurso em todos os comandos. |
| Owner proposal page | Angular routes | lazy route/import | NOT_WIRED | Nenhum registro em `app.routes*.ts`. |
| Tabular import | Roster publication/audit | SQL upsert | NOT_WIRED | Importacao grava membro diretamente. |
| Event transitions | Immutable event history/audit | `UPDATE sports_event` | NOT_WIRED | Nao existe tabela/chamada de historico de lifecycle. |
| Agenda CTA | `/eventos/:eventId` | `detailHref` | NOT_WIRED | Fragmento nao casa com a rota. |

### Data-Flow Trace (Level 4)

| Artifact | Source | Produces real data | Status |
|---|---|---|---|
| Public organization/team/athlete pages | Generated client -> Spring public query -> PostgreSQL | Yes | FLOWING |
| Public agenda cards | Generated client -> tracer query -> PostgreSQL | Yes | FLOWING, CTA disconnected |
| Owner proposal store | Adapter -> athlete-link endpoint -> PostgreSQL | Yes | FLOWING, page unreachable |
| O5 import page | Adapter -> import service -> PostgreSQL | Yes | FLOWING, unsafe roster transition |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| Negative mutation harness discriminates and restores | `node --test scripts/validate-phase3-negative-mutations.test.mjs` | 6/6 passed | PASS |
| Angular route boundary has no forbidden residual | `node scripts/validate-angular-route-boundary.mjs` | `OK` | PASS |
| Server/API and full suites | Maven/Angular/Playwright not started | No server or aggregate suite run in this verification | SKIP |

### Probe Execution

No `scripts/**/probe-*.sh` was found or declared for this phase.

### Requirements Coverage

| Requirement | Status | Evidence |
|---|---|---|
| AUTH-01 | SATISFIED | Public routes/matcher and allowlisted DTOs exist. |
| SPRT-01 | BLOCKED | Single `sport_id` relation; see gap 1. |
| SPRT-02 | BLOCKED | Incomplete competition authorization and D-74/D-75 gaps; see gap 2. |
| SPRT-03 | SATISFIED | Athlete creation is independent of local account. |
| SPRT-04 | SATISFIED | Matricula enters transient fingerprint/HMAC path. |
| SPRT-05 | SATISFIED | Versioned HMAC is persisted without raw matricula column. |
| SPRT-06 | SATISFIED | Unique match publishes a link proposal. |
| SPRT-07 | SATISFIED | Divergence/multiple candidates are blocked for review. |
| SPRT-08 | BLOCKED | Owner proposal UI is orphaned; see gap 3. |
| SPRT-09 | BLOCKED | Import bypasses published-roster correction path; see gap 4. |
| SPRT-10 | SATISFIED | Event creation validates participants, time zone, source and venue. |
| SPRT-11 | BLOCKED | No immutable event-transition history; see gap 5. |
| SPRT-12 | BLOCKED | Agenda CTA target is wrong; see gap 6. |
| SPRT-13 | SATISFIED | Public DTOs/pages expose authorized generic data. |
| SPRT-14 | BLOCKED | Other modalities cannot be represented consistently; see gap 7. |
| SPRT-15 | SATISFIED | Favorite relation targets persistent sports organization. |
| SPRT-16 | SATISFIED | Owner-only profile/media validation and public allowlist exist. |

### Decision Coverage

`check.decision-coverage-verify` found 48/48 trackable decisions honored. This is non-blocking and does not override failed product truths.

### Test Quality Audit

Linked tests showed no disabled tests and no circular expected-value generator. The XLSX fixtures are not valid OOXML files (small `PK-mock-*` payloads); tests create `XSSFWorkbook` in memory, so the fixture matrix does not independently prove the file-format contract.

### Anti-Patterns Found

| File | Pattern | Severity | Impact |
|---|---|---|---|
| `SportsXlsxParser.java:42-48,104-115,160-168` | Flexible first-sheet/extra-column acceptance and numeric matricula coercion | BLOCKER | Violates D-74/D-75 contract and can lose leading zeros. |
| `SportsTabularImportService.java:441-452` | Direct `ON CONFLICT DO UPDATE` into roster members | BLOCKER | Bypasses publication, reason and audit invariants. |
| `public-agenda-api.adapter.ts:196` | Hardcoded fragment instead of route link | BLOCKER | Event CTA cannot complete the user flow. |
| Modified implementation files | `TBD/FIXME/XXX` markers | NONE | No unreferenced debt marker found. |

### Human Verification Required

1. **Judgment-tier prohibitions:** review the DDL/favorite mapping and public DTO/matcher decisions; confirm no institutional claim, wrong favorite association, or private-field exposure.
2. **Visual and browser matrix:** inspect public and operational surfaces at 320 px and desktop, keyboard/focus, contrast and cross-browser behavior.
3. **Governed artifacts and dependencies:** approve OpenAPI inventories/digests and the CSV/XLSX dependency/license tree before any correction or regeneration.

### Gaps Summary

The phase has substantial implemented surfaces and real public data flow, but the goal is not achieved. Seven blocking truths remain: multi-modality catalog, complete competition authorization, reachable owner proposal, safe published-roster correction, immutable event lifecycle history, working agenda deep link, and generic handling of other modalities. D-74/D-75 is additionally unproven because the checked fixtures are mock bytes and the parser is permissive. No gap is deferred to a later roadmap phase; Phase 4 concerns odds, not these missing foundations.

No code, dependency, database, infrastructure, Git stage, commit, push, cleanup or external action was performed. This report is the only file created by this verification block.

---

_Verified: 2026-09-02T21:55:03Z_
_Verifier: gsd-verifier (read-only codebase inspection)_

## Fresh recertification - Plan 03-27 Task 1

O parecer historico acima descreve o estado anterior aos gaps e permanece apenas como baseline historica. A recertificacao pos-03-26 usa o HEAD atual ratificado pelo responsavel: `b6a662a063b754bb7c934e149c5492a47727db2a`, na branch/worktree `worktree-agent-03-gap-exec`.

### Current gate status

| Gate | Status | Evidence |
|---|---|---|
| Task 1 - snapshot, allowlists and contracts | PASS | OpenAPI check `OK`, route boundary `OK`, Node validator `173/173` |
| Task 2 - material certification | PENDING | Maven, Node, Angular, browsers, architecture/privacy and gap focals still to run |
| Task 3 - human snapshot approval | PENDING | Must follow the complete Task 2 evidence and frozen hashes |

### Task 1 evidence

- The six gap plans 03-21 through 03-26 were reconciled against their declared allowlists and hashes; no new runtime or generated path is introduced by this report update.
- `node frontend/scripts/generate-api.mjs --check` exited `0` with `[generate-api] OK: modo check concluido.`
- `node scripts/validate-angular-route-boundary.mjs` exited `0` with `[validate-angular-route-boundary] OK: fronteira Angular segue o contrato aprovado.`
- `node --test scripts/validate-angular-route-boundary.test.mjs` exited `0`: `173` tests, `173` pass, `0` fail, `0` cancelled, `0` skipped, `0` todo.
- `git status --short --untracked-files=all` was empty before this documentation edit; the only intended paths for this plan remain this report and `03-VALIDATION.md`.

### Recertification boundary

The final verdict must cover `AUTH-01` and `SPRT-01` through `SPRT-16`, including the seven bounded gaps: N:N catalog and generic modalities, derived authorization, published-roster correction, owner route and identity authority, append-only event lifecycle, encoded event deep links, and the complete CSV/XLSX/import contract. Historical results from 03-17 cannot satisfy this gate without the fresh material evidence required by Task 2.

**Task 1 verdict:** PASS. **Phase 3 final verdict:** not yet issued; Task 2 and the blocking human checkpoint remain.

## Fresh recertification - Plan 03-27 Task 2

### Material evidence

| Dimension | Verdict | Exact evidence |
|---|---|---|
| Backend aggregate | PASS | Maven `verify`: `1472` tests, `0` failures, `0` errors, `0` skipped; `BUILD SUCCESS` |
| Node aggregate | PASS | `638/638`, `0` failures, `0` skipped |
| Angular aggregate | PASS | `58/58` files, `349/349` tests |
| Browser matrix | PASS | Chromium + Firefox: `28/28` |
| Architecture/privacy | PASS | `46/46` tests |
| Gap backend focals | PASS | `268/268` tests across 16 required classes |
| Gap Angular focals | PASS | `10/10` files, `41/41` tests |
| Generator and route guards | PASS | generator `OK`, route validator `OK`, route suite `173/173`, generator suite `72/72` |
| Negative mutations | PASS | harness `6/6`; M1-M3 red signatures and SHA restoration confirmed |

### Requirements reconciliation

`AUTH-01` and `SPRT-01` through `SPRT-16` now have fresh post-03-26 evidence. The seven gaps are covered by the N:N/GENERIC catalog and public reads, derived authorization focals, fixed tabular import and publication guard, owner route and server-side identity flow, append-only lifecycle and concurrency tests, encoded event destinations, and the complete two-browser/public-surface matrix. No requirement is promoted from the historical 03-17 verdict alone.

### Discriminating corrections

- The Grainient Angular test now destroys its fixture before global mocks are unstubbed; the focused spec is `4/4` and the aggregate Angular gate is `349/349`.
- The public competition Playwright fixture now supplies required `sports` data. The desktop hero test freezes the initial composition with reduced motion, then explicitly resumes automatic rotation; the final Chromium/Firefox matrix is `28/28`.
- Intermediate failures were diagnostic only: missing required `sports` caused the original `2/28` failures, and a Firefox timing race caused a subsequent `1/28` failure. Both are resolved in the final snapshot.

**Task 2 verdict:** PASS. **Phase 3 final verdict:** pending only the blocking human review and frozen snapshot.
