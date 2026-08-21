---
phase: 02-contas-verifica-o-e-opera-es-governadas
plan: 01
subsystem: testing
tags: [postgresql, spring-session, redis, testcontainers, measurements]

requires:
  - phase: 01-fundacao-segura-da-plataforma
    provides: PostgreSQL, Redis, Spring Session e infraestrutura de testes hermetica
provides:
  - Medicoes empiricas A-5, A-7 e A-9 no PostgreSQL 18.4
  - Medicao A-12 do footprint Redis de uma sessao autenticada
  - Decisao OQ-8 pelo repositorio indexado nativo do Spring Session
affects: [02-03, 02-07, 02-11, IDEN-05]

actuals:
  tokens: 10879
  tasks: 3
  commits: 0

tech-stack:
  added: []
  patterns:
    - Comportamento de banco e framework e medido em containers reais antes da implementacao
    - Fixtures Redis fixadas por digest sao compartilhadas entre ITs

key-files:
  created:
    - backend/src/test/java/br/com/betufu/platform/persistence/PostgresSemanticsIT.java
    - backend/src/test/java/br/com/betufu/platform/session/RedisContainerSupport.java
    - backend/src/test/java/br/com/betufu/platform/session/PrincipalNameIndexProbeIT.java
    - .planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-MEASUREMENTS.md
  modified: []

key-decisions:
  - "OQ-8: usar o repositorio indexado nativo do Spring Session; nao construir indice paralelo identidade-sessoes."

patterns-established:
  - "Measurement first: afirmacoes sobre PostgreSQL, Redis e Spring Session exigem prova executavel."
  - "DRY de sessao: preferir a capacidade indexada nativa do Spring Session."

requirements-completed: [PROF-01, VERI-07, IDEN-05, AUDT-02]

coverage:
  - id: D1
    description: PostgreSQL aceita CHECK POSIX ASCII estrito e recusa homoglifo cirilico com constraint identificavel.
    requirement: PROF-01
    verification:
      - kind: integration
        ref: "PostgresSemanticsIT#posixRegexCheckConstraintRejectsHomoglyphAndIdentifiesConstraint"
        status: pass
    human_judgment: false
  - id: D2
    description: Indice unico parcial permite duplicidade fora do predicado e recusa conflito ativo.
    requirement: VERI-07
    verification:
      - kind: integration
        ref: "PostgresSemanticsIT#partialUniqueIndexAllowsDuplicatesAcrossInactiveStatesAndRejectsActiveConflict"
        status: pass
    human_judgment: false
  - id: D3
    description: Trigger de auditoria bloqueia UPDATE e DELETE com falha observavel, ao contrario de RULE DO INSTEAD NOTHING.
    requirement: AUDT-02
    verification:
      - kind: integration
        ref: "PostgresSemanticsIT#triggerBlocksWithObservableExceptionWhileRuleFailsSilently"
        status: pass
    human_judgment: false
  - id: D4
    description: O runtime atual nao grava indice de principal; a decisao indexada ficou registrada para IDEN-05.
    requirement: IDEN-05
    verification:
      - kind: integration
        ref: "PrincipalNameIndexProbeIT"
        status: pass
    human_judgment: false

duration: not-recorded
completed: 2026-08-20
status: complete
---

# Phase 2 Plan 01: Medicoes de PostgreSQL e Spring Session

**Quatro pressupostos de banco e sessao foram substituidos por provas executaveis, e OQ-8 foi decidida pelo repositorio indexado nativo.**

## Performance

- **Duration:** nao cronometrada; execucao retomada em mais de uma sessao
- **Started:** nao registrado
- **Completed:** 2026-08-20T21:21:09-03:00
- **Tasks:** 3
- **Files modified:** 4

## Accomplishments

- Mediu regex POSIX, indice unico parcial e imutabilidade de auditoria no PostgreSQL 18.4 real.
- Mediu o footprint Redis de sessoes autenticada e anonima sob o repositorio vigente.
- Registrou a escolha humana pelo repositorio indexado do Spring Session, evitando indice paralelo.

## Task Commits

Nenhum commit foi criado. `AGENTS.md` exige autorizacao separada para stage e commit; as tres tasks permanecem no working tree verificado.

## Files Created/Modified

- `backend/src/test/java/br/com/betufu/platform/persistence/PostgresSemanticsIT.java` - Provas A-5, A-7 e A-9.
- `backend/src/test/java/br/com/betufu/platform/session/RedisContainerSupport.java` - Fixture Redis hermetica reutilizavel.
- `backend/src/test/java/br/com/betufu/platform/session/PrincipalNameIndexProbeIT.java` - Prova A-12 em sessao autenticada real.
- `.planning/phases/02-contas-verifica-o-e-opera-es-governadas/02-MEASUREMENTS.md` - Resultados e decisao OQ-8.

## Decisions Made

- Usar o repositorio indexado nativo do Spring Session. A decisao segue DRY e transfere a manutencao do indice ao framework.

## Deviations from Plan

None - plan executed as written. Git commits were intentionally withheld by repository governance.

## Issues Encountered

- A medicao confirmou que `repository-type: default` nao grava indice de principal. O checkpoint humano escolheu `indexed`, desbloqueando os planos dependentes.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- A medicao A-7 libera a migration de perfil do Plano 02-03.
- A decisao OQ-8 fica como entrada obrigatoria para o encerramento de sessoes por identidade nos planos posteriores.
- A Wave 1 ainda depende do Plano 02-03.

---
*Phase: 02-contas-verifica-o-e-opera-es-governadas*
*Completed: 2026-08-20*
