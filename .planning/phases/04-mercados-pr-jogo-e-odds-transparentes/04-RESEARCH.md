---
phase: 4
slug: mercados-pr-jogo-e-odds-transparentes
status: research_complete
researched: 2026-09-04
source_head: 720df5fc84474fa88356caffa0b422a18c857dc5
repowise_index: 720df5fc8447
confidence: MEDIUM
---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Provenance:** copied verbatim from the approved Phase 4 context. [VERIFIED: .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-CONTEXT.md:26-118]

As decisoes D-95 a D-100 da Fase 3 continuam preservadas como contrato
herdado. Em especial, a convergencia visual/estrutural nao autoriza alterar
APIs, autorizacao, stores, adapters, validacoes, capabilities ou regras de
negocio. As decisoes abaixo sao o registro da discussao especifica da Fase 4.

### Modelo de forca, recencia e edicoes

- **D-101:** O rating inicial e `1500`, separado por modalidade e categoria,
  configuravel e versionado. Nao existe um rating global que misture esportes
  com escalas ou distribuicoes diferentes.
- **D-102:** Equipes provisoriamente usam `K=32`; depois de um limiar de
  experiencia passam a `K=16`. O limiar exato deve ser escolhido por
  backtest temporal e documentado pelo plano, nao inventado como uma decisao
  de produto.
- **D-103:** O processamento e cronologico e deve ordenar os eventos por
  instante esportivo canonico. Nao sera aplicada uma meia-vida mensal
  arbitraria; qualquer tratamento de recencia precisa ser reproduzivel e
  justificado pelos dados.
- **D-104:** Ao atravessar edicoes, o rating sofre regressao de 50% em direcao
  a `1500`. A regressao e versionada para que um backtest possa ser repetido.
- **D-105:** O rating so consome resultado esportivo confirmado e vigente,
  referenciado pela cadeia de versoes da Fase 3. Uma correcao futura produz
  calculo/versao posterior; nao reescreve historico nem odds ja aceitas.

### Empate, dados escassos e fontes

- **D-106:** Quando o regulamento ou a fase tornam o empate impossivel, o
  mercado usa dois resultados. Quando o empate no tempo regulamentar e
  possivel, usa tres resultados com uma extensao Davidson explicita.
- **D-107:** A tendencia de empate comeca compartilhada e conservadora entre
  pools compativeis. Especializacao por modalidade ou categoria so entra
  depois que houver amostra suficiente e evidencia de backtest.
- **D-108:** Mercados podem ser publicados desde o inicio, mesmo com poucos
  dados, mas a estimativa neutra deve trazer indicador visivel de baixa
  confianca. Baixa confianca nao libera odd manual rotineira e nao aumenta a
  margem automaticamente.
- **D-109:** O W.O. e uma vitoria administrativa sem placar esportivo. Nao se
  deve transformar um W.O. em placar sintetico (por exemplo, `20 x 0`) para
  alimentar Elo ou Davidson; ele fica fora da calibragem esportiva inicial e
  recebe tratamento de estado/settlement separado.
- **D-110:** Cancelamento, W.O. ou abandono sem resultado esportivo valido
  tornam o mercado anulavel e devolvem os creditos quando a camada de apostas
  existir. O evento cancelado nao altera o modelo.
- **D-111:** Adiamento suspende o mercado. Uma remarcacao cria uma nova versao
  do preco/mercado, preservando a versao anterior e a trilha de auditoria.

### Mercado, margem e imutabilidade

- **D-112:** O MVP usa templates simples de confronto direto e abre apenas
  mercados pre-jogo. O volume e a distribuicao das apostas dos usuarios nao
  alteram probabilidades nem odds (`ODDS-05`).
- **D-113:** A margem inicial e fixa em 5%, mas permanece configuravel,
  explicita e versionada. A aplicacao e o arredondamento precisam ser
  deterministas e auditaveis.
- **D-114:** Odds publicadas aparecem com duas casas decimais. Depois de
  publicada, a odd e imutavel; uma aposta futura referencia exatamente o
  valor/versao que o usuario viu e aceitou, sem recalculo retroativo.
- **D-115:** Probabilidade justa (antes da margem) e odd publicada sao
  registros distintos. Isso permite explicar a margem e deixa aberta uma
  evolucao posterior sem esconder o valor estatistico original.

### Governanca, explicacao e confronto direto

- **D-116:** Override e excecao, nao rotina. Antes de propor um override, o
  administrador deve corrigir a fonte ou recalcular; o mercado fica suspenso
  e a proposta inclui motivo, evidencia, comparacao com o modelo,
  probabilidades e delta visivel.
- **D-117:** O override exige dois administradores distintos, auditoria e nova
  versao imutavel. Nao pode ocorrer depois do encerramento definitivo
  (`CLOSED`/`LOCKED`) e nunca altera uma odd ja aceita.
- **D-118:** A explicacao publica tem duas camadas: um resumo simples para
  qualquer pessoa e um detalhe auditavel com algoritmo/versao, entradas,
  probabilidades sem margem, margem, arredondamento, confianca, timestamp e
  historico de alteracoes.
- **D-119:** Confrontos diretos podem aparecer na explicacao como evidencia
  contextual, mas nao recebem bonus automatico na odd do MVP. Um ajuste H2H
  so deve ser considerado apos varias edicoes e backtest que demonstre ganho
  real.

### Dados LAUFU e caminho futuro

- **D-120:** A extracao LAUFU e uma amostra exploratoria para pesquisa e
  backtests, nao uma fonte de producao nem um contrato permanente da BETUFU.
  Os artefatos nao rastreados da worktree principal nao entram nesta fase por
  inferencia e nao devem ser copiados para a worktree de planejamento.
- **D-121:** A direcao futura pode aproximar-se de sportsbooks tradicionais,
  mantendo a probabilidade justa separada e permitindo ajustes comerciais por
  mercado/exposicao somente quando houver liquidez, antifraude, auditoria e
  revisao formal de `ODDS-05`. Esse comportamento fica adiado e nao altera o
  MVP atual.

### the agent's Discretion

**Provenance:** copied verbatim from the approved Phase 4 context. [VERIFIED: .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-CONTEXT.md:120-127]

O agente e o plano podem escolher estruturas de dados, nomes internos,
algoritmos numericamente equivalentes, estrategia de cache e detalhes de UI
que preservem as decisoes acima, os requisitos do roadmap e os limites de
modulos. O limiar que troca `K=32` por `K=16`, a parametrizacao numerica do
Davidson e os testes de calibracao devem ser propostos com evidencia, sem
serem tratados como escolhas silenciosas do responsavel.

### Deferred Ideas (OUT OF SCOPE)

**Provenance:** copied verbatim from the approved Phase 4 context. [VERIFIED: .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-CONTEXT.md:242-256]

- Ajuste H2H com peso positivo na odd somente se varias edicoes e backtests
  comprovarem melhoria.
- Precificacao inspirada em sportsbook que mova o preco por exposicao,
  liquidez ou comportamento dos apostadores; exige revisao formal de
  `ODDS-05`, antifraude e governanca.
- Mercados de varios competidores, acumuladas, estatisticas especificas e
  liquidacao/reliquidacao pertencem a fases posteriores.
- Endurecimento do extrator LAUFU (cache com validade, robots.txt, identidade
  canonica, separacao de placares e testes do parser) deve ser tratado como
  trabalho de dados separado antes de qualquer uso recorrente.

None - no other ideas were accepted into this phase.
</user_constraints>

# Phase 4: Mercados Pre-jogo e Odds Transparentes - Research

<phase_requirements>
## Phase Requirements

The Phase 4 scope is exactly `ODDS-01` through `ODDS-13`; `ODDS-14` belongs to Phase 5 and `ODDS-15` to Phase 6. [VERIFIED: .planning/REQUIREMENTS.md:72-88; .planning/ROADMAP.md:378-394]

| ID | Description | Research support |
|---|---|---|
| ODDS-01 | Create simple pre-game H2H markets, with draw only when applicable. | Requires an explicit template contract and market state machine before schema/API work. [VERIFIED: .planning/REQUIREMENTS.md:74-74] |
| ODDS-02 | Calculate probabilities and odds with a versioned Elo baseline per sport/category. | Use a deterministic pricing kernel and persist the algorithm/config version. [VERIFIED: .planning/REQUIREMENTS.md:75-75] |
| ODDS-03 | Account for recency, edition regression and limited H2H influence. | D-103 rejects arbitrary monthly half-life; D-119 makes H2H contextual only in the MVP. [VERIFIED: .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-CONTEXT.md:43-48,103-106] |
| ODDS-04 | Keep cancellation out of the model and treat W.O. separately. | The current sports taxonomy has cancellation but no explicit W.O./abandonment result type, so a contract checkpoint is required. [VERIFIED: backend/src/main/resources/db/migration/V16__create_event_result_versions.sql:5-15,73-86] |
| ODDS-05 | Do not move price from user betting volume/distribution. | Keep pricing inputs independent from future betting/ledger modules. [VERIFIED: .planning/REQUIREMENTS.md:78-78] |
| ODDS-06 | Apply configurable 5% margin with versioned deterministic rounding. | Formula and rounding mode remain a human decision gate; publication must store both inputs and output. [VERIFIED: .planning/REQUIREMENTS.md:79-79] |
| ODDS-07 | Publish neutral low-confidence estimates when data is insufficient. | Confidence definition, bands and sample threshold require calibration evidence. [VERIFIED: .planning/REQUIREMENTS.md:80-80] |
| ODDS-08 | Record algorithm, inputs, fair probabilities, margin, prices, confidence, rounding and time. | Model calculation and published-price facts separately, both append-only. [VERIFIED: .planning/REQUIREMENTS.md:81-81] |
| ODDS-09 | Expose current price, version history and readable explanation publicly. | Add a public projection under the existing anonymous sports/odds boundary without leaking operational evidence. [VERIFIED: .planning/REQUIREMENTS.md:82-82; backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java:224-233] |
| ODDS-10 | Correct source/recalculate before override and suspend if distortion remains. | Requires a verifiable precondition and stored attempt/evidence, not a UI checkbox. [VERIFIED: .planning/REQUIREMENTS.md:83-83] |
| ODDS-11 | Require reason/evidence and model-vs-proposal comparison. | Persist proposal, fair probabilities and deltas before approval. [VERIFIED: .planning/REQUIREMENTS.md:84-84] |
| ODDS-12 | Require a distinct second administrator and create an immutable audited version. | Reuse server-side scoped identity and append-only audit patterns; add new closed audit vocabulary in Java and DDL. [VERIFIED: .planning/REQUIREMENTS.md:85-85; backend/src/main/java/br/com/betufu/audit/AuditEntry.java:19-65] |
| ODDS-13 | Allow override only while suspended, never after final close, and never rewrite accepted odds. | Market `CLOSED`/`LOCKED` states do not yet exist in the sports event vocabulary and must be defined for the odds domain. [VERIFIED: .planning/REQUIREMENTS.md:86-86; backend/src/main/resources/db/migration/V16__create_event_result_versions.sql:5-7] |
</phase_requirements>

For ODDS-03, “limitar a influência de confrontos diretos” is interpreted under the locked MVP decision as **no automatic H2H bonus**: H2H may be shown as contextual evidence, while any future positive/negative weighting remains deferred until multi-edition backtesting and a new approval. [VERIFIED: .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-CONTEXT.md:103-118]

## Summary

Phase 4 is ready for **plan drafting with blocking decision checkpoints**, but it is not ready for direct product implementation. The product boundary, requirements, inherited sports contracts and existing stack are sufficiently clear to decompose work. The numerical calibration protocol, margin/rounding formula, market lifecycle, sports-to-odds contract and override authorization vocabulary are still open and must be resolved explicitly before their dependent implementation tasks. [VERIFIED: .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-CONTEXT.md:6-22,120-127; .planning/ROADMAP.md:378-394]

The handoff's known index marker was `bedc623fb1a1`, while the live RepoWise metadata reports indexed commit `720df5fc8447` and live HEAD `720df5fc8447`; the live index therefore matches the current HEAD and no `repowise update` was run. The semantic provider returned mock/degraded prose, therefore live files and Git remain the authority for exact values and line references. [VERIFIED: RepoWise metadata observed in this research session; git rev-parse HEAD observed in this research session]

The predecessor is operationally ready: the latest Phase 3 summary records a frozen approved snapshot and complete backend, Node, Angular, browser, architecture and mutation evidence. However, `STATE.md` still records `state_head: bedc623fb1a1e82e10823fd60645bc960c6fff4e` and describes Phase 4 discussion as pending, while the live HEAD is `720df5fc84474fa88356caffa0b422a18c857dc5`. That drift must be acknowledged in the future plan preflight and corrected only under separate approval. [VERIFIED: .planning/phases/03-experi-ncia-esportiva-do-piloto/03-27-SUMMARY.md:13-18,53-72; .planning/STATE.md:4-11]

**Primary recommendation:** create the future `04-PLAN.md` as a gated sequence beginning with a documentary calibration/contract decision plan; do not place schema, API or UI implementation before those gates are approved. [ASSUMED]

## Architectural Responsibility Map

| Capability | Primary tier | Secondary tier | Rationale |
|---|---|---|---|
| Market creation and lifecycle | API / Backend (`odds`) | Database | Business rules and authorization belong server-side; durable states and versions belong in PostgreSQL. [VERIFIED: .planning/PROJECT.md:93-112] |
| Sports evidence and confirmed results | API / Backend (`sports`) | Database | The existing result chain and `confirmed_version_id` are owned by sports and persisted append-only. [VERIFIED: backend/src/main/resources/db/migration/V16__create_event_result_versions.sql:40-86,114-118] |
| Elo/Davidson pricing | API / Backend (`odds`) | Database | Calculation must be reproducible and versioned; the browser only renders results. [VERIFIED: .planning/REQUIREMENTS.md:75-81] |
| Published price/history | Database | API / Backend | Published facts are immutable and public projections expose current/history. [VERIFIED: .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-CONTEXT.md:80-88,96-102] |
| Override maker-checker flow | API / Backend | Database / audit | Distinct actors, scope, suspension and final-state checks must be enforced transactionally. [VERIFIED: .planning/REQUIREMENTS.md:83-86] |
| Public explanation | API / Backend | Browser / Client | Backend selects safe facts; Angular presents summary/detail and confidence. [VERIFIED: .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-CONTEXT.md:99-102] |
| Operational console | Browser / Client | API / Backend | Client capabilities filter navigation only; backend remains authoritative. [VERIFIED: backend/src/main/java/br/com/betufu/platform/operations/OperationalCapability.java:14-16; frontend/src/app/layout/console-shell.component.ts:95-122] |
| Contract generation | API contract / tooling | Browser / Client | OpenAPI is governed and the TypeScript client is generated, never hand-edited. [VERIFIED: frontend/scripts/generate-api.mjs:53-71,1831-1844] |
| Bet acceptance and settlement | Out of scope (Phases 5/6) | Future betting/settlement | Phase 4 stores stable references but does not create credits, bets or payouts. [VERIFIED: .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-CONTEXT.md:17-22] |

## Project Constraints (from AGENTS.md)

- Before every material block, present objective, approach, files, commands, impact, risks and expected result; obtain explicit approval for the exact scope. [VERIFIED: AGENTS.md]
- Product/architecture decisions, dependencies, scaffolds, code, database, infrastructure, external actions and every Git mutation require their own approval; one approval never carries to the next block. [VERIFIED: AGENTS.md]
- Keep GSD interactive with auto-advance disabled; never use `--auto`, `--all` or `--force` without explicit authorization. [VERIFIED: AGENTS.md; .planning/config.json]
- Use RepoWise MCP/LSP before native search, then use live source for exact implementation details or recent branch changes. [VERIFIED: AGENTS.md]
- Use one neutral worktree/branch per plan, execute tasks in the same plan sequentially, and never place two executors in one checkout. [VERIFIED: AGENTS.md]
- Preserve unrelated owner/worker changes, edit only the approved allowlist and stop on branch/HEAD/STATE drift, unexpected changes, dependency needs, security concerns or architecture/product checkpoints. [VERIFIED: AGENTS.md]
- Treat `backend/openapi/betufu.yaml` as governed; inspect a candidate/digest before generation and never edit generated client files manually. [VERIFIED: AGENTS.md]
- Verification must reconcile paths, run focal tests, include discriminating negative proof where required, run aggregate suites before completion, and record exact counts rather than estimates. [VERIFIED: AGENTS.md]
- Explain actions and results pedagogically for a junior Java developer, including the Spring/database concept and practical failure diagnostics. [VERIFIED: AGENTS.md]
- After each approved block, report changes, exact tests, files, deviations/risks and the next separate authorization, then pause. [VERIFIED: AGENTS.md]

## Planning Readiness and Blocking Gates

### Confirmed inputs

- The MVP is H2H and pre-game only; user betting volume cannot influence price. [VERIFIED: .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-CONTEXT.md:75-79]
- Initial Elo is `1500`, provisional `K=32`, mature `K=16`, with `50%` regression toward `1500` between editions. [VERIFIED: .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-CONTEXT.md:34-48]
- Draw markets are conditional and use Davidson; low data may still publish a visibly low-confidence neutral estimate. [VERIFIED: .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-CONTEXT.md:53-64]
- W.O. is administrative and excluded from initial sports calibration; cancellation does not update the model; postponement suspends and rescheduling versions. [VERIFIED: .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-CONTEXT.md:65-73]
- Initial margin is `5%`; fair probability and published odds are distinct; published odds have two decimal places and are immutable. [VERIFIED: .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-CONTEXT.md:80-88]
- Override is exceptional, suspended, evidence-based, approved by two distinct administrators, audited and versioned. [VERIFIED: .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-CONTEXT.md:90-102]

### Decisions that the planner must not make silently

| Gate | Evidence required before approval | Blocks |
|---|---|---|
| Exact threshold for `K=32` to `K=16` | Temporal backtest with declared fixtures/split, baseline and sensitivity comparison. [ASSUMED] | Pricing implementation |
| Reproducible recency treatment | Written formula showing chronological ordering and no arbitrary monthly half-life. [VERIFIED: .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-CONTEXT.md:43-46] | Rating updates/backtest |
| Davidson parameters and conservative shared draw prior | Backtest on compatible pools, leakage controls and sparse-data behavior. [ASSUMED] | Three-outcome pricing |
| Sufficient sample for specialization | Explicit minimum evidence and fallback to shared prior. [ASSUMED] | Per-sport/category draw tuning |
| Confidence metric and bands | Definition, monotonicity expectations, low-data fixtures and public labels. [ASSUMED] | ODDS-07/08/09 UI and API |
| Temporal backtest protocol | Canonical ordering, train/evaluate cutoffs, correction handling, W.O. exclusion and repeatable dataset digest. [ASSUMED] | All calibration claims |
| Margin formula and rounding | Exact formula, scale, rounding mode, sum/overround invariant and golden vectors. [ASSUMED] | ODDS-06 publication |
| Complete market state machine | States, allowed transitions, closing deadline, suspend/resume, postpone, cancel, void, `CLOSED` and `LOCKED`. [ASSUMED] | Persistence, API and override |
| `sports` to `odds` boundary | Minimal public port/event contract, ownership and replay semantics; no access to `sports.internal`. [VERIFIED: backend/src/test/java/br/com/betufu/ModuleDependencyRulesTest.java:34-45] | Backend integration |
| Authorization/capabilities | Exact roles and new navigation capabilities for create/suspend/propose/approve override. [ASSUMED] | Security/API/UI |
| Pre-override correction proof | Machine-verifiable record of source correction/recalculation attempt. [ASSUMED] | ODDS-10/11 |
| W.O./abandonment taxonomy | Sports fact vs market outcome vs future settlement effect; no score inference. [VERIFIED: backend/src/main/resources/db/migration/V16__create_event_result_versions.sql:5-7,73-86] | ODDS-04/lifecycle |
| Replay after corrected result | Define future recalculation and lineage without rewriting published/accepted price versions. [VERIFIED: .planning/phases/03-experi-ncia-esportiva-do-piloto/03-RESULT-VERSION-DECISION.md:93-104] | Phase 6 compatibility |

## Standard Stack

No new dependency is recommended for Phase 4 research or planning. Implementation should use the already approved stack. [VERIFIED: backend/pom.xml:7-32; frontend/package.json:20-44]

### Core

| Library/runtime | Version quoted from source | Purpose |
|---|---|---|
| Java | `"25"` | Backend language/toolchain. [VERIFIED: backend/pom.xml:22-25] |
| Spring Boot | `"4.1.0"` | HTTP, validation, transactions and application runtime. [VERIFIED: backend/pom.xml:7-12,53-89] |
| Spring Modulith | `"2.1.0"` | Enforce the `sports`/`odds` modular boundary. [VERIFIED: backend/pom.xml:22-40] |
| PostgreSQL/Flyway | existing managed dependencies | Authoritative persistence and forward migrations. [VERIFIED: backend/pom.xml:70-93] |
| Angular | `"22.1.0"` | Public and operational UI. [VERIFIED: frontend/package.json:21-27] |
| RxJS | `"7.8.2"` | Async API adapters. [VERIFIED: frontend/package.json:28-29] |
| TypeScript | `"6.0.3"` | Typed Angular code and generated client. [VERIFIED: frontend/package.json:31-44] |

### Supporting validation stack

| Tool | Version quoted from source | Purpose |
|---|---|---|
| ArchUnit | `"1.4.2"` | Cycles and forbidden internal-module access. [VERIFIED: backend/pom.xml:22-29] |
| Vitest | `"4.1.10"` | Angular unit/component tests through the Angular unit-test builder. [VERIFIED: frontend/package.json:31-44; frontend/angular.json:123-125] |
| Playwright | `"1.62.1"` | Public/operational browser flows. [VERIFIED: frontend/package.json:35-44] |
| OpenAPI Generator CLI package | `"2.40.1"` | Invokes the pinned governed generator workflow. [VERIFIED: frontend/package.json:31-36] |
| OpenAPI generator engine | `"7.24.0"` | Pinned `typescript-angular` code generation. [VERIFIED: frontend/scripts/generate-api.mjs:40-71] |

**Installation:** none. A future plan must not add or update packages unless a separate package-legitimacy and owner-approval block is created. [VERIFIED: AGENTS.md]

## Package Legitimacy Audit

Not applicable: this research recommends no external package installation or version change. [VERIFIED: this research artifact]

## Existing Contract Inventory

### Sports facts available today

- Event formats are exactly `"HEAD_TO_HEAD"` and `"GENERIC"`; Phase 4 detailed markets must accept only the former. [VERIFIED: backend/src/main/java/br/com/betufu/sports/internal/event/SportsEventModels.java:18-21]
- Result requests require exactly two scores and scores are non-negative. [VERIFIED: backend/src/main/java/br/com/betufu/sports/internal/result/SportsResultModels.java:18-59]
- A result version exposes `"versionId"`, `"eventId"`, `"competitionId"`, `"editionId"`, `"versionNo"`, `"status"`, source/reason, scores, actor IDs, timestamps, `"supersedesVersionId"` and `"baseLockVersion"`. [VERIFIED: backend/src/main/java/br/com/betufu/sports/internal/result/SportsResultModels.java:61-82]
- Sports event states are exactly `"RASCUNHO"`, `"PUBLICADO"`, `"SUSPENSO"`, `"ADIADO"`, `"CANCELADO"`; result-version states are exactly `"INFORMADO"`, `"AGUARDANDO_REVISAO"`, `"CONFIRMADO"`, `"CORRIGIDO"`. [VERIFIED: backend/src/main/resources/db/migration/V16__create_event_result_versions.sql:5-7,73-83]
- `sports_event.confirmed_version_id` is a foreign key to the exact immutable result version, and result rows/slots reject `UPDATE` and `DELETE`. [VERIFIED: backend/src/main/resources/db/migration/V16__create_event_result_versions.sql:114-118,152-161]
- Event lifecycle history is append-only, ordered by `"sequence_no"`, and rejects `UPDATE`/`DELETE`. [VERIFIED: backend/src/main/resources/db/migration/V24__create_event_lifecycle_history.sql:1-18,52-65]
- The opened source-of-truth vocabularies contain no explicit `"WO"` or `"ABANDONO"` sports result state; those facts cannot be inferred from a numeric score. [VERIFIED: backend/src/main/resources/db/migration/V16__create_event_result_versions.sql:5-7,73-83; backend/src/main/resources/db/migration/V24__create_event_lifecycle_history.sql:27-30]

### Authorization and modularity available today

- Operational roles are exactly `"ADMINISTRADOR_GERAL"`, `"ADMINISTRADOR_COMPETICAO"`, `"MODERADOR"`; general is global and the others require scope. [VERIFIED: backend/src/main/java/br/com/betufu/identity/OperationalRole.java:11-35]
- Existing capabilities are exactly `"VIEW_OPERATIONAL_PANEL"`, `"MANAGE_ROLES"`, `"CORRECT_ENROLLMENT"`, `"MANAGE_SPORTS_CATALOG"`, `"BOOTSTRAP_SPORTS_COMPETITION"`; no odds capability exists yet. [VERIFIED: backend/src/main/java/br/com/betufu/platform/operations/OperationalCapability.java:18-24]
- A client-supplied competition UUID is never proof of authority; the persisted grant must match the requested scope. [VERIFIED: backend/src/main/java/br/com/betufu/platform/operations/OperationalCapabilitiesService.java:62-119]
- `sports` and `odds` are separate `@ApplicationModule` boundaries; architecture tests require the business modules `"identity"`, `"verification"`, `"sports"`, `"odds"`, `"betting"`, `"ledger"`, `"settlement"`, `"ranking"`, `"chat"`, `"audit"`. [VERIFIED: backend/src/main/java/br/com/betufu/sports/package-info.java:1-4; backend/src/main/java/br/com/betufu/odds/package-info.java:1-4; backend/src/test/java/br/com/betufu/ModulithArchitectureTests.java:18-29]
- The repository forbids module cycles and access to `identity.internal` from other modules. [VERIFIED: backend/src/test/java/br/com/betufu/ModuleDependencyRulesTest.java:34-45]

### Audit and API constraints available today

- Audit outcomes are exactly `"PERMITIDO"` and `"BLOQUEADO"`; actions use a closed Java set and a matching database check constraint. New odds actions therefore require coordinated Java, migration and tests. [VERIFIED: backend/src/main/java/br/com/betufu/audit/AuditEntry.java:19-65; backend/src/main/resources/db/migration/V14__harden_athlete_link_proposals.sql:35-54]
- Audit rows are immutable and the runtime role receives only `SELECT, INSERT`. [VERIFIED: backend/src/main/resources/db/migration/V4__create_audit_event.sql:92-106,120-127]
- Anonymous access currently includes only `GET "/api/v1/session"` and `GET "/api/v1/public/sports/**"`; all other `"/api/v1/**"` requests are authenticated unless a narrower matcher says otherwise. [VERIFIED: backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java:224-233,251-275]

## Architecture Patterns

### System architecture diagram

```text
confirmed sports event/result
        |
        v
[approved sports -> odds contract] ---> [chronological rating replay]
                                             |
                          +------------------+------------------+
                          |                                     |
                    draw impossible                       draw possible
                          |                                     |
                    two outcomes                     three outcomes/Davidson
                          +------------------+------------------+
                                             |
                                  [fair probabilities]
                                             |
                                  [margin + rounding]
                                             |
                              [immutable price version]
                                  /                    \
                                 v                      v
                    public explanation/history   operational lifecycle
                                                       |
                                            suspend -> propose -> approve
                                                       |
                                             immutable override version
```

This is a conceptual data-flow recommendation, not an approved schema or class layout. [ASSUMED]

### Recommended project structure

```text
backend/src/main/java/br/com/betufu/odds/
  <public sports-consumer contract or package API>
  internal/model/          # pure rating/pricing rules
  internal/market/         # market lifecycle and orchestration
  internal/override/       # maker-checker workflow
  internal/publicview/     # safe public projection

backend/src/test/java/br/com/betufu/odds/
  <unit, integration, concurrency and security tests>

frontend/src/app/features/odds/
  public-market/           # current/history/explanation
  operations/              # create/suspend/override flows
```

These paths are proposed, not yet present or approved. [ASSUMED]

### Pattern 1: explicit one-way sports-to-odds contract

The odds module must consume a minimal public contract that yields event scope, format, participants, canonical time and exact confirmed result-version facts. It must not import `sports.internal`, and sports must not depend back on odds. Whether this is a synchronous public query port or an event-fed odds projection is an architectural checkpoint; the default recommendation is the smallest synchronous read contract first, because the MVP is a modular monolith and does not require distributed infrastructure. [VERIFIED: backend/src/test/java/br/com/betufu/ModuleDependencyRulesTest.java:21-45] [ASSUMED]

### Pattern 2: pure deterministic pricing kernel

Separate calculation from orchestration: an input snapshot plus algorithm/config version should produce fair probabilities, confidence evidence and pricing outputs without HTTP, database or clock access. The application service then validates lifecycle/authorization and persists an immutable version. This makes golden vectors and replay possible. [ASSUMED]

### Pattern 3: append-only calculation and publication lineage

Preserve stable market identity separately from calculation/version identity. A correction, reschedule, recalculation or override inserts a new fact linked to its predecessor; it never updates a published price row. This mirrors the existing result-version and lifecycle-history contracts. [VERIFIED: backend/src/main/resources/db/migration/V16__create_event_result_versions.sql:40-86,152-161; backend/src/main/resources/db/migration/V24__create_event_lifecycle_history.sql:1-18,55-65]

### Pattern 4: explicit market state machine

Do not reuse sports-event status as market status. Market closure, locking, suspension, voiding and version publication have distinct invariants, while sports events currently expose only `"RASCUNHO"`, `"PUBLICADO"`, `"SUSPENSO"`, `"ADIADO"`, `"CANCELADO"`. Define allowed transitions and reject every unlisted transition in service and database tests. [VERIFIED: backend/src/main/resources/db/migration/V16__create_event_result_versions.sql:5-7] [ASSUMED]

### Pattern 5: transactional maker-checker override

Store proposer and approver as distinct identities, compare them server-side, lock the current market/version, re-check suspension/final state and scope, then insert the approved immutable version and audit fact in one transaction. UI separation alone is not enforcement. [VERIFIED: .planning/REQUIREMENTS.md:83-86; backend/src/main/java/br/com/betufu/sports/internal/result/SportsResultService.java:95-135,408-473] [ASSUMED]

### Pattern 6: two public projections, one source of truth

Expose a concise explanation and an auditable detail projection from the same stored calculation facts. Public DTOs should omit internal operator identity/evidence details while retaining algorithm version, inputs safe for publication, fair probabilities, margin, rounding, confidence and version history. [VERIFIED: .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-CONTEXT.md:99-102; .planning/phases/03-experi-ncia-esportiva-do-piloto/03-RESULT-VERSION-DECISION.md:82-91]

### Anti-patterns to avoid

- Importing `sports.internal` from `odds` or creating a reverse dependency from sports to odds. [VERIFIED: backend/src/test/java/br/com/betufu/ModuleDependencyRulesTest.java:34-45]
- Treating a numeric W.O. score as sports performance or a draw observation. [VERIFIED: .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-CONTEXT.md:65-68]
- Updating a published price row, changing history after correction, or resolving a future bet from the latest price instead of its accepted version. [VERIFIED: .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-CONTEXT.md:83-88]
- Trusting capabilities, route visibility or a client-provided UUID as authorization. [VERIFIED: backend/src/main/java/br/com/betufu/platform/operations/OperationalCapability.java:14-16; backend/src/main/java/br/com/betufu/platform/operations/OperationalCapabilitiesService.java:62-119]
- Allowing low confidence to trigger routine manual price editing or a hidden margin increase. [VERIFIED: .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-CONTEXT.md:61-64]
- Calibrating against the full dataset before a chronological split, which would leak future evidence into earlier prices. [ASSUMED]

## Persistence and Migration Guidance

The latest opened migration is `V24__create_event_lifecycle_history.sql`, while `FlywayMigrationIT` currently expects exactly `25` applied migrations and repeated runs of `0` new migrations. Any Phase 4 schema plan must allocate a forward migration after V24 and update the migration test counts/ordered assertions as part of the same approved block. [VERIFIED: backend/src/main/resources/db/migration/V24__create_event_lifecycle_history.sql:1-65; backend/src/test/java/br/com/betufu/platform/persistence/FlywayMigrationIT.java:92-103,180-197]

At minimum, the schema design should distinguish market identity, market rule/template version, calculation version, fair outcome probabilities, published outcome prices, lifecycle history and override proposal/approval lineage. Exact table names and cardinalities remain an architecture/DDL checkpoint. [ASSUMED]

Database constraints should enforce uniqueness/version monotonicity, distinct override actors, valid state transitions where practical, non-negative probabilities/prices, immutable published facts and exact foreign keys to event/result versions. Service tests alone are insufficient for invariants that must survive concurrency or retries. [ASSUMED]

The domain must remain PostgreSQL-backed: the existing authoritative-storage rule explicitly includes `br.com.betufu.odds..` and rejects dependencies on `org.springframework.data.redis.repository..`. Redis may support auxiliary concerns elsewhere, but it must not become the source of odds, market state or accepted-price history. [VERIFIED: backend/src/test/java/br/com/betufu/platform/persistence/AuthoritativeStorageIT.java:78-94]

The existing Flyway policy requires the literal settings `spring.jpa.hibernate.ddl-auto=validate`, `spring.flyway.enabled=true`, `spring.flyway.clean-disabled=true`, `spring.flyway.clean-on-validation-error=false`, `spring.flyway.baseline-on-migrate=false` and `spring.flyway.locations=classpath:db/migration`. A V25+ plan must preserve and test these guards rather than rely on Hibernate schema generation. [VERIFIED: backend/src/test/java/br/com/betufu/platform/persistence/FlywayPolicyTest.java:12-20]

The test dependencies already include `spring-boot-starter-test`, `spring-security-test`, `spring-boot-testcontainers`, PostgreSQL Testcontainers, Spring Modulith test and ArchUnit; no new test library is needed for the proposed validation. [VERIFIED: backend/pom.xml:137-172]

## OpenAPI and Angular Guidance

The governed client target is `"src/app/api/generated"`, generated with `"typescript-angular"`, Angular `"22.1.0"`, TypeScript range `">=6.0.0 <6.1.0"` and RxJS `"7.8.2"`. The official workflow accepts only modes `"generate"`, `"check"`, `"candidate"`, validates public schema allowlists, and verifies candidate/governed digests. [VERIFIED: frontend/scripts/generate-api.mjs:53-71,1831-1889,1950-2009]

The future plan must use this order: prepare a read-only candidate/inventory, present the literal digest and public-field review, obtain human approval, update the approved manifest, run official generation, then adapt Angular through handwritten adapters/ViewModels. Generated files are never edited manually. [VERIFIED: AGENTS.md; frontend/scripts/generate-api.mjs:1950-2009]

Angular components should remain standalone with `ChangeDetectionStrategy.OnPush`, Signals for local/derived state and generated-service adapters. Existing operational capabilities may hide destinations, but the backend must return 403 for unauthorized commands independently. [VERIFIED: frontend/src/app/layout/console-shell.component.ts:93-122; frontend/src/app/features/operations/operations-api.adapter.ts:42-93; backend/src/main/java/br/com/betufu/platform/operations/OperationalCapability.java:14-16]

The public odds route needs an explicit security decision: either extend the current anonymous `GET /api/v1/public/sports/**` projection or add a narrowly matched public odds path before the authenticated `/api/v1/**` fallback. This must be reviewed for accidental mutation exposure. [VERIFIED: backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java:224-233,272-275] [ASSUMED]

All odds mutations must also preserve the existing CSRF/CORS boundary: the CSRF cookie is non-HttpOnly at path `/` with SameSite `Lax`, CORS allows only the configured public origin (default `http://localhost:4200`), credentials are enabled, and `X-XSRF-TOKEN` is an allowed header. [VERIFIED: backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java:47-77]

If an application event is chosen for confirmed-result propagation, the repository precedent is a public opaque event plus `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)`. No confirmed/corrected-result event currently exists, so introducing one is an explicit architecture decision rather than an assumed integration. [VERIFIED: backend/src/main/java/br/com/betufu/verification/EnrollmentVerified.java:7-17; backend/src/main/java/br/com/betufu/sports/internal/linking/AthleteLinkReconciler.java:46-59] [ASSUMED]

The existing public sports projection reads a result only through the event's `confirmedVersionId` and statuses `"CONFIRMADO"`/`"CORRIGIDO"`; it does not expose internal result services as a Modulith contract. Odds should consume an approved top-level port/event, not `sports.internal.publicview`. [VERIFIED: backend/src/main/java/br/com/betufu/sports/internal/publicview/SportsPublicViewRepository.java:343-391; backend/src/main/java/br/com/betufu/sports/internal/publicview/SportsPublicViewModels.java:148-197] [ASSUMED]

## LAUFU Exploratory Data Risk Register

`scripts/extract-laufu.mjs` must stay outside the Phase 4 implementation allowlist unless a separate data-hardening plan is approved. [VERIFIED: .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-CONTEXT.md:108-118,242-254]

| Risk | Evidence | Planning consequence |
|---|---|---|
| Comment promises `robots.txt`, but the request flow directly fetches target pages and contains no robots fetch/check. | [VERIFIED: scripts/extract-laufu.mjs:8-13,89-129,566-588] | Do not run it as an approved crawler or production ingestion source. |
| `--ano` is parsed, but each match stores `edicao: '2026'`. | [VERIFIED: scripts/extract-laufu.mjs:41-69,381-390] | Non-2026 output is internally inconsistent. |
| Report includes `"CANCELADA"`, but the parser only assigns `"AGENDADA"`, `"WO"`, `"CONCLUIDA"`, `"ADIADA"`. | [VERIFIED: scripts/extract-laufu.mjs:352-375,487-519] | Cancellation counts cannot be trusted. |
| Unknown team IDs silently fall back to `slugify(name)`. | [VERIFIED: scripts/extract-laufu.mjs:148-155,329-343] | Identity resolution is not authoritative and unresolved rows are not marked. |
| Numeric W.O. keeps score fields and participates in the `empate` expression. | [VERIFIED: scripts/extract-laufu.mjs:345-379,404-414] | Direct use can contaminate Elo/Draw calibration. |
| Match URL uses hardcoded `"https://laufu.jliga.club"` instead of the configured base variable. | [VERIFIED: scripts/extract-laufu.mjs:33-39,300-312] | Host/config changes can produce mixed origins. |
| Disk cache has no TTL or freshness metadata. | [VERIFIED: scripts/extract-laufu.mjs:89-99,120-129] | Backtests may unknowingly reuse stale pages. |
| A past date without score is inferred as `"ADIADA"`. | [VERIFIED: scripts/extract-laufu.mjs:369-375] | Missing publication and actual postponement are conflated. |
| Non-dry-run execution performs network requests and writes cache, CSV, JSON and Markdown. | [VERIFIED: scripts/extract-laufu.mjs:89-129,552-633] | Running it is an external/material action requiring a separate approval. |

The counts `671`, `326`, `299`, `46`, and `8` in `127` games are context observations only, not a calibration result. [VERIFIED: .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-CONTEXT.md:227-233]

## Don't Hand-Roll

| Problem | Do not build | Use instead | Why |
|---|---|---|---|
| Authorization | Browser-only capability checks or request UUID trust | Existing `OperationalIdentityLookup`/scope-validation pattern | Server-side persisted scope is authoritative. [VERIFIED: backend/src/main/java/br/com/betufu/platform/operations/OperationalCapabilitiesService.java:62-119] |
| Result history | A second odds-owned copy of score/current result | Exact `confirmed_version_id` and sports public contract | Avoids divergent sports truth. [VERIFIED: backend/src/main/resources/db/migration/V16__create_event_result_versions.sql:114-118] |
| Immutability | Mutable rows plus application convention | Insert-only versions plus DB triggers/privileges | Existing contracts prove DB-enforced immutability. [VERIFIED: backend/src/main/resources/db/migration/V16__create_event_result_versions.sql:152-161; backend/src/main/resources/db/migration/V4__create_audit_event.sql:92-106] |
| Audit actions | Free-form strings accepted only in odds code | Extend closed Java + DDL vocabularies together | Prevents runtime/database drift. [VERIFIED: backend/src/main/java/br/com/betufu/audit/AuditEntry.java:37-65; backend/src/main/resources/db/migration/V14__harden_athlete_link_proposals.sql:35-54] |
| API client | Handwritten or manually edited generated DTO/services | Governed OpenAPI candidate/generator workflow | Preserves contract digest and regeneration. [VERIFIED: frontend/scripts/generate-api.mjs:1831-2009] |
| Data ingestion | Promote the LAUFU regex scraper to production | Governed sports records and separately approved data hardening | Current parser has unresolved identity/state/freshness risks. [VERIFIED: scripts/extract-laufu.mjs:89-129,300-415] |

## Common Pitfalls

### Pitfall 1: implementing before calibration gates

**What goes wrong:** arbitrary thresholds/formulas become product behavior without evidence. **Avoidance:** make the first plan documentary, with golden fixtures, temporal split and literal owner checkpoint. **Warning sign:** a schema or Java constant appears before an approved decision record. [ASSUMED]

### Pitfall 2: conflating sports state, market state and settlement state

**What goes wrong:** cancellation, W.O., abandonment, suspension and voiding are inferred from one overloaded status. **Avoidance:** define three boundaries and their mappings explicitly. **Warning sign:** `sports_event.status` alone decides rating, market validity and future refund. [VERIFIED: backend/src/main/resources/db/migration/V16__create_event_result_versions.sql:5-15] [ASSUMED]

### Pitfall 3: hidden nondeterminism

**What goes wrong:** clock access, unordered queries, implicit numeric rounding or mutable config changes replay results. **Avoidance:** version every config, sort by canonical instant plus stable tie-breaker, and use golden vectors. **Warning sign:** the same input snapshot produces a different digest/output. [ASSUMED]

### Pitfall 4: maker-checker enforced only in UI

**What goes wrong:** the proposer can call the approval endpoint directly or an out-of-scope admin approves. **Avoidance:** compare identities/scopes under row lock and assert no-write on 401/403/409. **Warning sign:** tests cover hidden buttons but not direct HTTP/database state. [ASSUMED]

### Pitfall 5: corrected sports result silently rewrites price history

**What goes wrong:** recomputation changes historical/accepted prices. **Avoidance:** link a new calculation/version to the corrected `confirmedVersionId` and keep old versions readable. **Warning sign:** `UPDATE` targets a published price record. [VERIFIED: .planning/phases/03-experi-ncia-esportiva-do-piloto/03-RESULT-VERSION-DECISION.md:93-104]

### Pitfall 6: public explanation leaks operational evidence

**What goes wrong:** internal actor IDs, raw evidence or sensitive source material becomes anonymous. **Avoidance:** public DTO allowlist plus separate operational projection. **Warning sign:** public OpenAPI schemas reuse administrative entities. [VERIFIED: .planning/phases/03-experi-ncia-esportiva-do-piloto/03-RESULT-VERSION-DECISION.md:82-91]

### Pitfall 7: generated contract drift

**What goes wrong:** handwritten client edits pass locally but disappear on regeneration. **Avoidance:** candidate/digest/manifest workflow and generator tests. **Warning sign:** a task lists `frontend/src/app/api/generated/**` as a manual edit. [VERIFIED: frontend/scripts/generate-api.mjs:1950-2009]

## Code Examples

These are small, verified patterns from the existing codebase; they illustrate the conventions a future Phase 4 plan should preserve, not a final odds implementation.

### Scope and optimistic locking

```java
// Source: backend/src/main/java/br/com/betufu/sports/internal/result/SportsResultService.java:95-106
return transactions.execute(
    status -> {
      EventScope locked =
          repository
              .findEventScopeForUpdate(eventId)
              .orElseThrow(SportsResultService::notFound);
      if (locked.lockVersion() != expectedLockVersion) {
        throw staleVersion();
      }
      if ("RASCUNHO".equals(locked.status()) || "CANCELADO".equals(locked.status())) {
        throw validation();
      }
    });
```

The snippet is the exact guard portion of the existing transaction; the odds service should preserve the same lock/version-check shape for mutable market commands. [VERIFIED: backend/src/main/java/br/com/betufu/sports/internal/result/SportsResultService.java:95-106] [ASSUMED]

### Immutable database fact

```sql
-- Source: backend/src/main/resources/db/migration/V24__create_event_lifecycle_history.sql:55-65
CREATE TRIGGER trg_prevent_sports_event_lifecycle_history_modification
BEFORE UPDATE OR DELETE ON public.sports_event_lifecycle_history
FOR EACH ROW
EXECUTE FUNCTION public.prevent_sports_event_lifecycle_history_modification();
```

The odds tables should use the same database-enforced append-only principle, with their own approved names and constraints. [VERIFIED: backend/src/main/resources/db/migration/V24__create_event_lifecycle_history.sql:55-65] [ASSUMED]

### Angular capability filtering

```typescript
// Source: frontend/src/app/layout/console-shell.component.ts:116-122
readonly operationDestinations = computed(() =>
  this.isPersonal
    ? []
    : SPORTS_OPERATION_DESTINATIONS.filter((destination) =>
        this.capabilities().includes(destination.capability),
      ),
);
```

This is navigation convenience only; server authorization remains authoritative. [VERIFIED: frontend/src/app/layout/console-shell.component.ts:116-122; backend/src/main/java/br/com/betufu/platform/operations/OperationalCapability.java:14-16]

## State of the Art

| Older/unsafe approach | Current BETUFU approach | Evidence and planning impact |
|---|---|---|
| Overwrite the current result or lifecycle row | Append-only version/history plus database immutability triggers | Existing V16/V24 contracts; odds must preserve lineage. [VERIFIED: backend/src/main/resources/db/migration/V16__create_event_result_versions.sql:152-161; backend/src/main/resources/db/migration/V24__create_event_lifecycle_history.sql:55-65] |
| Let the browser or a free UUID decide authorization | Resolve authenticated `(issuer, subject)` and compare persisted scope on the server | Existing sports/capability pattern; reuse for every odds mutation. [VERIFIED: backend/src/main/java/br/com/betufu/platform/operations/OperationalCapabilitiesService.java:62-119; backend/src/main/java/br/com/betufu/sports/internal/result/SportsResultService.java:408-448] |
| Edit generated TypeScript client by hand | Candidate inventory/digest, human approval, official generation and `--check` | Governed generator workflow; new odds endpoints need the same gate. [VERIFIED: frontend/scripts/generate-api.mjs:1831-2009] |
| Couple price to user betting volume in the MVP | Keep model inputs independent from betting/ledger | Locked D-112/ODDS-05; future commercial pricing is deferred. [VERIFIED: .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-CONTEXT.md:75-88,108-118] |

**Deprecated/outdated for this phase:** treating LAUFU scraper output as production truth, synthesizing W.O. scores, and routine unilateral price edits are explicitly rejected. [VERIFIED: .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-CONTEXT.md:65-73,90-118]

## Proposed Plan Decomposition

This is a recommendation for the future planner, not a created or approved `04-PLAN.md`. [ASSUMED]

| Order | Proposed plan | Main output | Blocking checkpoint |
|---:|---|---|---|
| 1 | Calibration and pricing decision gate | Versioned model specification, dataset/fixture inventory, temporal backtest protocol, K/Davidson/confidence/margin/rounding evidence | Human approval of every numerical choice |
| 2 | Sports-to-odds contract and state taxonomy | Public boundary, W.O./abandonment/cancel mapping, market lifecycle/state-transition decision | Architecture + product/DDL approval |
| 3 | Odds persistence foundation | Forward migration, immutable versions, lifecycle and override lineage, Flyway/idempotency tests | Approved schema/allowlist |
| 4 | Deterministic pricing engine | Elo replay, conditional Davidson, confidence and price publication golden vectors | Approved Plan 1 parameters |
| 5 | Market lifecycle and public query | Create/publish/suspend/reschedule/cancel/close/lock plus safe history/explanation API | Approved state machine and security path |
| 6 | Override governance | Correct/recalculate proof, proposal, distinct approval, transaction/concurrency/audit | Approved roles/capabilities/audit vocabulary |
| 7 | OpenAPI and Angular surfaces | Candidate contract, generated client, public market UI, operational console flows | Literal digest approval before generation |
| 8 | Integrated certification | Focal, negative mutation, migration, architecture, OpenAPI, Angular, browser and aggregate evidence | Frozen snapshot + human UAT approval |

Plans 3-7 must not begin until their matching decision checkpoint is recorded. Plans that alter OpenAPI, migration, audit vocabulary or capabilities need separate explicit allowlists and approvals. [VERIFIED: AGENTS.md] [ASSUMED]

## Validation Architecture

Nyquist validation is enabled. [VERIFIED: .planning/config.json]

### Test framework

| Property | Value |
|---|---|
| Backend | JUnit/Spring integration tests through Maven; focal pattern `node scripts/maven.mjs -f backend/pom.xml -Dtest=<classes> test` or `-Dit.test=<ClassNameIT>`. [VERIFIED: .planning/phases/03-experi-ncia-esportiva-do-piloto/03-22-PLAN.md:45-45; .planning/phases/03-experi-ncia-esportiva-do-piloto/03-VALIDATION.md:514-520] |
| Migration | `FlywayMigrationIT` with PostgreSQL and repeat-run idempotency. [VERIFIED: backend/src/test/java/br/com/betufu/platform/persistence/FlywayMigrationIT.java:92-103,180-197] |
| Architecture | `ModulithArchitectureTests`, `ModuleDependencyRulesTest`, `PrivacyArchitectureTest`. [VERIFIED: .planning/phases/03-experi-ncia-esportiva-do-piloto/03-VALIDATION.md:67-70] |
| Frontend | Angular unit-test builder with `--include` focal specs. [VERIFIED: frontend/angular.json:123-125; .planning/phases/03-experi-ncia-esportiva-do-piloto/03-10-PLAN.md:134-134] |
| Contract | `node frontend/scripts/generate-api.mjs --check` and `node --test frontend/scripts/generate-api.test.mjs`. [VERIFIED: .planning/phases/03-experi-ncia-esportiva-do-piloto/03-VALIDATION.md:64-70] |
| Browser | Playwright, serial single-worker two-browser precedent. [VERIFIED: frontend/playwright.sports.config.ts:11-48] |
| Full backend | `node scripts/maven.mjs -f backend/pom.xml verify`. [VERIFIED: AGENTS.md] |
| Full Node | `node --test scripts/*.test.mjs`. [VERIFIED: AGENTS.md] |
| Full Angular | `npm --prefix frontend run test -- --watch=false`. [VERIFIED: .planning/phases/03-experi-ncia-esportiva-do-piloto/03-VALIDATION.md:59-70] |

The closest integration-test analogues are `SportsEventResultIT` for MockMvc, lifecycle, optimistic locking, immutability and scope authorization; `SportsEventResultConcurrencyIT` for races; `AuditEventIT`/`AuditImmutabilityIT` for append-only actions and database privileges; and `FlywayChecksumIT` for migration tamper detection. These are existing patterns to copy in the future plan, not tests run in this research block. [VERIFIED: .planning/phases/03-experi-ncia-esportiva-do-piloto/03-VALIDATION.md:510-524; backend/src/test/java/br/com/betufu/platform/persistence/FlywayMigrationIT.java:92-103]

### Phase requirements to test map

All Phase 4-specific files below are Wave 0 gaps; names are proposed and must be reconciled by the planner. [ASSUMED]

| Requirements | Behavior | Test type | Proposed focal command | Exists? |
|---|---|---|---|---|
| ODDS-01,04,13 | Template eligibility and lifecycle/W.O./cancel/final-state rules | Integration + DB | `node scripts/maven.mjs -f backend/pom.xml -Dtest=OddsMarketIT,OddsMarketLifecycleIT test` | No - Wave 0 |
| ODDS-02,03,07 | Elo/recency/regression/conditional draw/confidence golden vectors | Unit + backtest fixture | `node scripts/maven.mjs -f backend/pom.xml -Dtest=OddsPricingModelTest,OddsCalibrationFixtureTest test` | No - Wave 0 |
| ODDS-05,06,08 | Independence from betting volume, deterministic margin/rounding and recorded inputs | Unit + integration | `node scripts/maven.mjs -f backend/pom.xml -Dtest=OddsPublicationIT,OddsPricingModelTest test` | No - Wave 0 |
| ODDS-09 | Anonymous current/history/explanation with public allowlist | HTTP integration + Angular | backend focal plus `npm --prefix frontend run test -- --watch=false --include src/app/features/odds/public-market/*.spec.ts` | No - Wave 0 |
| ODDS-10,11,12,13 | Correction/recalc prerequisite, suspend, proposal/delta, distinct approver, audit/no-write | Integration + concurrency + mutation | `node scripts/maven.mjs -f backend/pom.xml -Dtest=OddsOverrideIT,OddsOverrideConcurrencyIT test` | No - Wave 0 |
| ODDS-01..13 | Governed DTO/client contract | Node + Angular contract | `node frontend/scripts/generate-api.mjs --check`; `node --test frontend/scripts/generate-api.test.mjs` | Existing harness; new inventory missing |
| ODDS-01..13 | Module cycles/internal access/privacy | Architecture | `node scripts/maven.mjs -f backend/pom.xml -Dtest=PrivacyArchitectureTest,ModulithArchitectureTests,ModuleDependencyRulesTest test` | Existing harness; new assertions may be needed |
| ODDS-09..13 | Public and operational browser journeys | Playwright | future Phase 4 config/spec, Chromium + Firefox | No - Wave 0 |

### Required negative/discriminating proofs

- Mutating the K threshold, edition regression, draw branch or rounding rule must make a named golden-vector test fail. [ASSUMED]
- Removing W.O. exclusion or feeding its numeric score into Elo must fail. [ASSUMED]
- Allowing the same identity to propose/approve, approving from another scope, or approving after final state must return 403/409 as appropriate and leave the database unchanged. [ASSUMED]
- Updating/deleting a published odds version or audit row must fail at the database layer. [ASSUMED]
- Adding a forbidden public field or hand-changing generated output must fail the generator/contract checks. [VERIFIED: frontend/scripts/generate-api.mjs:1055-1068,1878-1888,1986-1996]

### Sampling rate

- Per task: run only the literal focal command approved in that task. [VERIFIED: AGENTS.md]
- Per plan/wave: repeat all affected focals, contract and architecture gates. [VERIFIED: AGENTS.md]
- Phase gate: backend/Node/Angular aggregate suites, required Playwright matrix, migration checks, negative mutations and frozen hashes before human approval. [VERIFIED: AGENTS.md; .planning/phases/03-experi-ncia-esportiva-do-piloto/03-27-SUMMARY.md:53-72]

## Security Domain

Security enforcement is enabled because `.planning/config.json` does not disable it. [VERIFIED: .planning/config.json]

### Applicable ASVS categories

| ASVS category | Applies | Required control |
|---|---|---|
| V2 Authentication | Yes for operational commands | Reuse server session/OIDC identity; no new authentication mechanism. [VERIFIED: .planning/PROJECT.md:100-106] |
| V3 Session Management | Yes | Existing BFF server session and CSRF/session-fixation controls remain authoritative. [VERIFIED: backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java:272-278] |
| V4 Access Control | Yes, critical | Derive competition scope from persisted resources; verify create/suspend/propose/approve separately; deny IDOR with no write. [VERIFIED: backend/src/main/java/br/com/betufu/sports/internal/result/SportsResultService.java:408-448] [ASSUMED] |
| V5 Validation | Yes | Jakarta validation plus service checks, UTF-8 byte limits and DB constraints. [VERIFIED: backend/src/main/java/br/com/betufu/sports/internal/result/SportsResultModels.java:18-59; backend/src/main/java/br/com/betufu/sports/internal/result/SportsResultService.java:365-473] |
| V6 Cryptography | No new Phase 4 primitive | Do not introduce custom cryptography. [ASSUMED] |
| V8 Data Protection | Yes | Public DTO allowlists; do not expose actor IDs, internal evidence, enrollment/HMAC or secrets. [VERIFIED: AGENTS.md] |
| V10 Malicious Code | Yes for exploratory data/tooling | Do not execute/promote LAUFU extraction without separate review/approval. [VERIFIED: scripts/extract-laufu.mjs:89-129,552-633] |
| V13 API | Yes | Narrow HTTP matchers, CSRF for mutations, Problem Details, generated contract and server authorization. [VERIFIED: backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java:224-278; .planning/PROJECT.md:116-126] |

### Threat patterns

| Pattern | STRIDE | Standard mitigation |
|---|---|---|
| IDOR by choosing another competition/event/market ID | Elevation of privilege | Derive scope from persisted resource and authenticated grants; negative two-scope tests. [VERIFIED: backend/src/main/java/br/com/betufu/platform/operations/OperationalCapabilitiesService.java:62-119] |
| Same actor approves own override | Tampering / repudiation | Distinct actor DB/service invariant plus immutable audit. [VERIFIED: .planning/REQUIREMENTS.md:85-86] [ASSUMED] |
| Race between suspend/close/approve/version publish | Tampering | Row lock or optimistic version check, transaction and concurrency tests. [VERIFIED: backend/src/main/java/br/com/betufu/sports/internal/result/SportsResultService.java:95-135,451-473] [ASSUMED] |
| Historical price rewrite | Tampering | Append-only versions and DB immutability triggers. [VERIFIED: backend/src/main/resources/db/migration/V16__create_event_result_versions.sql:152-161] |
| Public operational evidence leak | Information disclosure | Separate public/admin projections and OpenAPI field allowlist. [VERIFIED: AGENTS.md] |
| Manipulated/stale scraped fixture | Tampering / spoofing | Dataset digest, provenance, unresolved-ID quarantine and no production trust. [VERIFIED: scripts/extract-laufu.mjs:89-129,342-343] [ASSUMED] |

## Environment Availability

This block did not execute Java, Maven, Node, npm, Docker, PostgreSQL, Playwright or network probes. Manifests define the required stack, but runtime availability is unverified and must be checked in an approved execution preflight. [VERIFIED: this research session]

| Dependency | Required by | Available | Required/declared version | Fallback |
|---|---|---|---|---|
| Java | Backend tests/build | Not probed | `25` | None; blocking if absent. [VERIFIED: backend/pom.xml:22-25] |
| Maven Wrapper launcher | Backend tests/build | File pattern used historically; runtime not probed | Maven `3.9.16` required | None; blocking if launcher cannot run. [VERIFIED: backend/pom.xml:189-196] |
| Node/npm | Tooling/frontend | Not probed | npm `10.9.8` declared | None; blocking if absent. [VERIFIED: frontend/package.json:20-20] |
| PostgreSQL/Testcontainers/Docker | Migration/integration tests | Not probed | PostgreSQL 18 project contract | No equivalent in-memory proof for DB invariants. [VERIFIED: .planning/PROJECT.md:108-114] |
| Browser engines | Playwright certification | Not probed | Chromium/Firefox precedent | Unit tests are not a replacement for phase browser gate. [VERIFIED: frontend/playwright.sports.config.ts:21-29] |

## Assumptions Log

| # | Assumed claim | Section | Risk if wrong |
|---|---|---|---|
| A1 | Start with a synchronous minimal sports read contract instead of an event projection. | Architecture Pattern 1 | Could constrain replay/decoupling; requires architecture approval. |
| A2 | Use a pure pricing kernel separated from persistence/orchestration. | Architecture Pattern 2 | Different project convention could change task boundaries. |
| A3 | Use temporal backtest metrics/golden fixtures to select K/Davidson/confidence/margin details. | Blocking Gates / Validation | Wrong protocol could approve a poorly calibrated model. |
| A4 | Use distinct market, calculation, publication and override lineage records. | Persistence | Schema may be over/under-normalized; requires DDL review. |
| A5 | Add dedicated Phase 4 test classes/specs and a two-browser config. | Validation Architecture | Exact names and grouping may differ during planning. |
| A6 | Add a narrow public odds matcher or extend public sports projection. | OpenAPI/Security | Wrong boundary could expose mutations or create awkward ownership. |

## Open Questions

1. **Which exact temporal backtest dataset is approved?**
   - Known: LAUFU is exploratory only and unsafe for automatic promotion. [VERIFIED: .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-CONTEXT.md:108-118]
   - Needed: fixture provenance, canonical identities, W.O./cancel labels, chronological split and digest. [ASSUMED]
2. **What exact formulas pass the product gate?**
   - Known: initial Elo/K/regression/margin and conditional Davidson direction are fixed. [VERIFIED: .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-CONTEXT.md:34-64,75-88]
   - Needed: K threshold, Davidson parameterization, confidence bands, margin transform and rounding mode. [ASSUMED]
3. **Who owns the sports-to-odds integration contract?**
   - Known: direct `sports.internal` access and cycles are forbidden. [VERIFIED: backend/src/test/java/br/com/betufu/ModuleDependencyRulesTest.java:34-45]
   - Recommendation: approve a minimal public sports port before schema work. [ASSUMED]
4. **What is the complete market state machine?**
   - Known: `CLOSED`/`LOCKED` are required by ODDS-13 but absent from sports event states. [VERIFIED: .planning/REQUIREMENTS.md:86-86; backend/src/main/resources/db/migration/V16__create_event_result_versions.sql:5-7]
   - Needed: close deadline, lock semantics, resume/reschedule/version and void mapping. [ASSUMED]
5. **Which roles/capabilities can create, suspend, propose and approve?**
   - Known: three operational roles exist; no odds capability exists. [VERIFIED: backend/src/main/java/br/com/betufu/identity/OperationalRole.java:11-20; backend/src/main/java/br/com/betufu/platform/operations/OperationalCapability.java:18-24]
   - Needed: exact matrix and cross-scope behavior, with distinct proposer/approver. [ASSUMED]
6. **How is correction/recalculation attempt proven before override?**
   - Known: ODDS-10 requires it. [VERIFIED: .planning/REQUIREMENTS.md:83-83]
   - Needed: persisted command/result/evidence contract and failure semantics. [ASSUMED]
7. **When is `STATE.md` reconciled?**
   - Known: live HEAD and recorded `state_head` differ, and status text predates the completed discussion. [VERIFIED: .planning/STATE.md:4-11]
   - Needed: separate approval before editing state/roadmap. [VERIFIED: AGENTS.md]

## Sources

### Primary - HIGH confidence local sources

- `.planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-CONTEXT.md` - locked scope and decisions D-101 through D-121.
- `.planning/REQUIREMENTS.md:72-88` - ODDS-01 through ODDS-15 and phase boundary.
- `.planning/ROADMAP.md:378-394` - Phase 4 goal, dependency and success criteria.
- `.planning/phases/03-experi-ncia-esportiva-do-piloto/03-27-SUMMARY.md:13-18,53-72` - latest predecessor readiness evidence.
- `.planning/phases/03-experi-ncia-esportiva-do-piloto/03-RESULT-VERSION-DECISION.md:20-104` - exact result-version contract inherited by future consumers.
- `backend/src/main/resources/db/migration/V16__create_event_result_versions.sql:1-161` - event/result states, confirmed-version FK and immutability.
- `backend/src/main/resources/db/migration/V24__create_event_lifecycle_history.sql:1-65` - append-only event lifecycle.
- `backend/src/main/java/br/com/betufu/sports/internal/result/SportsResultService.java:57-135,365-473` - authorization, scope, locking and errors.
- `backend/src/test/java/br/com/betufu/ModulithArchitectureTests.java:18-72` and `backend/src/test/java/br/com/betufu/ModuleDependencyRulesTest.java:21-45` - module boundaries.
- `backend/src/test/java/br/com/betufu/platform/persistence/AuthoritativeStorageIT.java:78-94` and `backend/src/test/java/br/com/betufu/platform/persistence/FlywayPolicyTest.java:12-20` - PostgreSQL authority, Redis prohibition and migration policy.
- `backend/src/test/java/br/com/betufu/audit/AuditEventIT.java` and `backend/src/test/java/br/com/betufu/audit/AuditImmutabilityIT.java` - existing audit/idempotency/privilege test analogues.
- `frontend/scripts/generate-api.mjs:53-71,1831-2009` - governed OpenAPI/client workflow.
- `scripts/extract-laufu.mjs:1-633` - exploratory extractor risks.

### Secondary - MEDIUM confidence

- RepoWise structural metadata at indexed/live commit `720df5fc8447`; semantic answer text was mock-provider output and was not used as source truth. [VERIFIED: RepoWise metadata observed in this research session]

### External sources

None. Network/documentation research was intentionally not executed in this approval block; unresolved model choices remain explicit gates rather than unverified claims. [VERIFIED: this research session]

## Metadata

**Confidence breakdown:**

- Existing stack and contracts: HIGH - read directly from manifests, Java and SQL source-of-truth files. [VERIFIED: Sources section]
- Architecture mapping: MEDIUM - constrained by verified module rules, but the sports-to-odds contract needs human approval. [ASSUMED]
- Pricing/calibration details: LOW until the approved backtest/decision plan is completed. [ASSUMED]
- Validation strategy: MEDIUM - existing harnesses are verified; Phase 4 files/commands are proposed Wave 0 work. [ASSUMED]
- LAUFU risk assessment: HIGH for code-observed defects; LOW for any statistical inference from the extracted sample. [VERIFIED: scripts/extract-laufu.mjs:1-633] [ASSUMED]

**Research date:** 2026-09-04
**Valid until:** the repository HEAD, Phase 4 context, requirements or predecessor contracts change. [ASSUMED]
