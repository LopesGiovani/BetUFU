---
phase: 03-experiencia-esportiva-do-piloto
plan: 01
subsystem: database
tags: [postgresql, flyway, sports, versioning, favorite-organization]

requires:
  - phase: 02-contas-verificacao-e-operacoes-governadas
    provides: perfil BETUFU, identidade local, papeis escopados e auditoria
provides:
  - ER/DDL inicial multi-esporte aprovado antes da primeira migration esportiva
  - identidade imutavel confirmedVersionId e supersessao aditiva aprovadas
  - estrategia preservadora para favorite_team_id sem associacao heuristica
affects: [03-02, 03-05, 03-10, phase-6-settlement]

actuals:
  tokens: 6000
  tasks: 2
  commits: 0

tech-stack:
  added: []
  patterns:
    - competition_id como raiz administrativa multi-esporte
    - identidade estavel, lock otimista e versao publicada separados
    - migration condicionada por inventario e forward-fix

key-files:
  created:
    - .planning/phases/03-experi-ncia-esportiva-do-piloto/03-DDL-DECISION.md
    - .planning/phases/03-experi-ncia-esportiva-do-piloto/03-RESULT-VERSION-DECISION.md
    - .planning/phases/03-experi-ncia-esportiva-do-piloto/03-01-SUMMARY.md
  modified: []

key-decisions:
  - "competition_id e a raiz de escopo e admite varios esportes por competicao"
  - "confirmedVersionId identifica uma versao imutavel, nunca o lockVersion do rascunho"
  - "favorito so e convertido por relacao autoritativa unica; ausencia de prova produz NULL"
  - "atores de registro, confirmacao e autorizacao futura permanecem separados"

patterns-established:
  - "Publicacao append-only: correcao cria nova versao e preserva a anterior"
  - "Favorite migration: inventariar primeiro e escolher rota zero-legado ou compatibilidade"

requirements-completed: [SPRT-01, SPRT-10, SPRT-11, SPRT-15]

coverage:
  - id: D1
    description: "ER/DDL inicial, IDs publicos, constraints e fronteiras Modulith aprovados"
    requirement: SPRT-01
    verification:
      - kind: other
        ref: "node decision gate: approved, competition_id, lock_version, forward-fix"
        status: pass
    human_judgment: true
    rationale: "Decisao arquitetural one-way aprovada pelo responsavel; a implementacao futura ainda deve ser confrontada com o documento."
  - id: D2
    description: "Evento minimo do tracer possui escopo persistido, participantes ordenados, horario, zona, fonte e local a definir"
    requirement: SPRT-10
    verification:
      - kind: other
        ref: "03-DDL-DECISION.md#sports_event e #sports_event_slot"
        status: pass
    human_judgment: true
    rationale: "A decisao fisica foi aprovada; o comportamento sera provado pelo SportsTracerIT no plano 03-02."
  - id: D3
    description: "Identidade imutavel confirmedVersionId, supersessao e atores separados aprovados"
    requirement: SPRT-11
    verification:
      - kind: other
        ref: "node decision gate: approved, confirmedVersionId, supersed, NULL"
        status: pass
    human_judgment: true
    rationale: "A decisao historica foi aprovada, mas sua realizacao sera provada pelos testes de evento e resultado do plano 03-10."
  - id: D4
    description: "Migracao preservadora do favorito definida sem inferencia por nome ou slug"
    requirement: SPRT-15
    verification:
      - kind: manual_procedural
        ref: "checkpoint interativo do responsavel em 2026-08-27"
        status: pass
    human_judgment: true
    rationale: "O inventario de dados do banco alvo e a migration real pertencem ao plano 03-05."

duration: 20min
completed: 2026-08-27
status: complete
---

# Fase 3 Plano 01: Portas irreversiveis do dominio esportivo

**Contrato fisico multi-esporte, versoes confirmadas append-only e migracao preservadora do favorito aprovados antes da V9**

## Performance

- **Duration:** 20 min
- **Started:** 2026-08-27T14:39:00Z
- **Completed:** 2026-08-27T14:59:00Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments

- Fixou `competition_id` como raiz administrativa e permitiu varias modalidades na mesma competicao.
- Separou UUID do evento, `lockVersion` concorrente e `confirmedVersionId` historico.
- Definiu correcao por supersessao aditiva e atores operacionais separados.
- Protegeu o perfil contra conversao falsa de `favorite_team_id` para organizacao.

## Task Commits

Nenhum commit foi criado. Stage e commit aguardam aprovacao Git especifica do responsavel.

## Files Created/Modified

- `03-DDL-DECISION.md` - ER, IDs, FKs, constraints, fronteiras e forward-fix aprovados.
- `03-RESULT-VERSION-DECISION.md` - versao confirmada e migracao do favorito aprovadas.
- `03-01-SUMMARY.md` - rastreabilidade das duas decisoes e verificacoes.

## Decisions Made

- A hierarquia visual de D-53 nao limita a competicao a um esporte; a competicao e a raiz escopada multi-esporte.
- V9 sera uma fatia permanente pequena, sem criar antecipadamente todo o schema futuro.
- `confirmedVersionId` referencia exatamente uma versao confirmada e imutavel do resultado.
- Registro, confirmacao e autorizacao futura para liquidacao usam atores e instantes separados.
- O favorito utiliza duas rotas: rename/FK somente com inventario vazio; caso contrario, coluna nova e legado preservado.
- Nome, slug e similaridade textual nunca provam identidade para migracao.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- A hierarquia conceitual poderia ser lida como uma competicao de esporte unico. A decisao explicitou a cardinalidade multi-esporte necessaria para a Olimpiada UFU.
- `favorite_team_id` aceita UUID arbitrario e nao possui FK. A estrategia passou a depender de inventario real antes da migration, sem presumir que todos os ambientes estejam vazios.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- O plano 03-02 pode propor a V9 e o tracer backend usando o ER aprovado.
- O plano 03-05 possui a politica de migracao do favorito.
- O plano 03-10 possui a identidade historica consumida futuramente pela Fase 6.
- A implementacao continua bloqueada ate os documentos serem formalizados e integrados por operacoes Git aprovadas separadamente.

---
*Phase: 03-experiencia-esportiva-do-piloto*
*Completed: 2026-08-27*
