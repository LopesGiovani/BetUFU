---
phase: 03-experi-ncia-esportiva-do-piloto
plan: 25
wave: 25
status: complete-pending-git
completed: 2026-09-04
subsystem: sports-event-lifecycle
tags: [sports, events, lifecycle, audit, flyway, openapi, angular]

requires:
  - phase: 03-experi-ncia-esportiva-do-piloto
    provides: 03-24 temporal roster contract and 03-23 competition-scoped authorization
provides:
  - immutable append-only sports event lifecycle history from creation onward
  - competition-scoped lifecycle query with minimal operational DTO
  - governed OpenAPI candidate and generated Angular lifecycle client
affects: [sports-events, sports-tracer, sports-results, phase-3]

actuals:
  tasks: 6
  commits: 2
  changed_technical_paths: 21

tech-stack:
  added: []
  patterns:
    - append-only lifecycle rows share the event transaction and retain sequence and lock metadata
    - event service owns lifecycle writes while legacy tracer entrypoints delegate through the public seam
    - operational history derives competition scope from the event and uses a bounded ordered query
    - governed Angular output is generated only after the approved OpenAPI digest is fixed

key-files:
  created:
    - backend/src/main/resources/db/migration/V24__create_event_lifecycle_history.sql
    - frontend/src/app/api/generated/model/sports-event-lifecycle-entry.ts
    - frontend/target/api-candidate/03-25/inventory.json
    - frontend/target/api-candidate/03-25/digest.sha256
    - frontend/target/api-candidate/03-25/governed-before.sha256
    - frontend/target/api-candidate/03-25/governed-after.sha256
  modified:
    - backend/openapi/betufu.yaml
    - backend/src/main/java/br/com/betufu/sports/internal/event/SportsEventModels.java
    - backend/src/main/java/br/com/betufu/sports/internal/event/SportsEventRepository.java
    - backend/src/main/java/br/com/betufu/sports/internal/event/SportsEventService.java
    - backend/src/main/java/br/com/betufu/sports/internal/event/SportsEventResultController.java
    - backend/src/main/java/br/com/betufu/sports/internal/tracer/SportsTracerRepository.java
    - backend/src/main/java/br/com/betufu/sports/internal/tracer/SportsTracerService.java
    - frontend/scripts/generate-api.mjs
    - frontend/src/app/api/generated/api/sports-event-result.service.ts
    - frontend/src/app/api/generated/model/models.ts
    - backend/src/test/java/br/com/betufu/sports/SportsEventResultIT.java
    - backend/src/test/java/br/com/betufu/sports/SportsEventResultConcurrencyIT.java
    - backend/src/test/java/br/com/betufu/sports/SportsTracerIT.java
    - backend/src/test/java/br/com/betufu/platform/persistence/FlywayMigrationIT.java

key-decisions:
  - C1 fixed `GET /api/v1/operations/sports/events/{eventId}/lifecycle` with default limit 100, accepted range 1-500 and stable sequence/time/id ordering.
  - D1 keeps `SportsEventService` as the transaction owner; `SportsTracerService` delegates event creation and publication without duplicating lifecycle writes.
  - The initial `NULL -> RASCUNHO` entry is written in the creation transaction, while legacy events begin at their first observed transition.
  - The approved OpenAPI candidate digest is `5D6400EF390D0D459B05A0D3CB4C94774271928E16D7DB618968100D59809D9B`.

patterns-established:
  - "V24 stores previous/new status, opaque actor identity, source/reason, lock versions, sequence and timestamp without replacing V16 result versions."
  - "Database triggers reject UPDATE and DELETE on lifecycle history; the event current row remains mutable while history is preserved."
  - "The lifecycle endpoint derives the competition from the event, returns no PII, maps invalid limits and scope failures through the existing API error contract."
  - "OpenAPI inventory and generated client outputs are checked against governed before/after hashes."

requirements-completed: [SPRT-03, SPRT-04, SPRT-05, SPRT-06, SPRT-07, SPRT-10, SPRT-11, SPRT-13, SPRT-15, SPRT-16]

coverage:
  - id: D1
    description: "V24 persists an immutable lifecycle entry for creation and every valid event transition, including NULL -> RASCUNHO, while rejecting direct updates and deletes."
    requirement: SPRT-11
    verification:
      - kind: integration
        ref: "node scripts/maven.mjs -f backend/pom.xml -Dtest=SportsEventResultIT,SportsEventResultConcurrencyIT,SportsTracerIT,FlywayMigrationIT test"
        status: pass
    human_judgment: false
  - id: D2
    description: "The operational lifecycle query is scoped by the event competition, bounded by limit and ordered by sequence, timestamp and lifecycle id without PII."
    requirement: SPRT-11
    verification:
      - kind: integration
        ref: "SportsEventResultIT.lifecycleEndpointValidatesLimitAndScope"
        status: pass
      - kind: manual_procedural
        ref: "approved candidate digest 5D6400EF390D0D459B05A0D3CB4C94774271928E16D7DB618968100D59809D9B"
        status: pass
    human_judgment: true
    rationale: "The owner had to approve the exact governed OpenAPI candidate before client generation."
  - id: D3
    description: "Legacy tracer creation and publication use the event service seam, preserving lifecycle writes in the same transaction and exposing stale-lock conflicts."
    requirement: SPRT-11
    verification:
      - kind: integration
        ref: "SportsTracerIT plus SportsEventResultConcurrencyIT from the focal Maven command"
        status: pass
    human_judgment: false
  - id: D4
    description: "The generated Angular service, model and barrel expose the approved lifecycle endpoint without manual client edits or contract drift."
    requirement: SPRT-15
    verification:
      - kind: other
        ref: "node frontend/scripts/generate-api.mjs --check"
        status: pass
      - kind: unit
        ref: "node --test frontend/scripts/generate-api.test.mjs"
        status: pass
      - kind: unit
        ref: "npm --prefix frontend run test -- --watch=false --include src/app/api/generated-client.contract.spec.ts"
        status: pass
    human_judgment: false

## Phase 3: Experiencia Esportiva do Piloto - Plan 03-25 Summary

**Historico append-only de lifecycle de eventos esportivos, consulta operacional escopada e cliente Angular governado**

## Performance

- **Duration:** multi-session
- **Completed:** 2026-09-04
- **Tasks:** 6
- **Files modified:** 21 technical paths

## Accomplishments

- Added V24 with a foreign-keyed lifecycle history table, status and version checks, per-event sequence ordering and database protection against UPDATE/DELETE.
- Recorded `NULL -> RASCUNHO` on event creation and previous/new status for publish, suspend, postpone and cancel transitions in the event transaction; result version history remains separate.
- Routed legacy tracer creation/publication through `SportsEventService` under D1, so tracer operations cannot bypass lifecycle history or create a second transaction boundary.
- Added `GET /api/v1/operations/sports/events/{eventId}/lifecycle` with derived competition scope, default limit 100, valid limit range 1-500, stable ordering and a minimal DTO containing no PII.
- Updated the governed OpenAPI candidate and regenerated the Angular service/model output after approval of digest `5D6400EF390D0D459B05A0D3CB4C94774271928E16D7DB618968100D59809D9B`.

## Verification Evidence

- `node scripts/maven.mjs -f backend/pom.xml -Dtest=SportsEventResultIT,SportsEventResultConcurrencyIT,SportsTracerIT,FlywayMigrationIT test`: `35/35`, `BUILD SUCCESS` (`FlywayMigrationIT` 16/16, `SportsEventResultIT` 5/5, `SportsEventResultConcurrencyIT` 3/3, `SportsTracerIT` 11/11).
- `node --test frontend/scripts/generate-api.test.mjs`: `72/72` passed.
- `npm --prefix frontend run test -- --watch=false --include src/app/api/generated-client.contract.spec.ts`: `12/12` passed.
- `node frontend/scripts/generate-api.mjs --check`: passed; candidate digest and governed-before/governed-after artifacts are present and consistent.
- `git diff --check`: passed; only expected Windows LF/CRLF normalization warnings were emitted before the technical commit.

## Decisions and Deviations

- C1 and D1 were approved explicitly. No cursor pagination, new catalog association or shared recorder module was introduced.
- The generator's internal model/file and endpoint allowlists were missing the newly approved lifecycle schema and route; they were extended within the plan allowlist so regeneration is deterministic.
- One authentication integration assertion now sends `Accept: application/json` so the expected unauthorized response is `401` rather than a browser redirect; this keeps the test focused on the API contract.
- `SportsTracerModels.java`, `SportsTracerController.java`, the generated `api.ts`/`index.ts` barrels and the generated-client contract spec were allowed paths but required no content change.
- RepoWise was queried first. Its index was fresh at `8a85cec64c7c` for the primary checkout but did not include this worktree's uncommitted changes; live files, reports and Git remained authoritative.
- No dependency, infrastructure, external service, integration, cleanup or push operation was performed.

## Issues Encountered

- Git reported expected LF-to-CRLF normalization warnings on Windows; no content error or `git diff --check` failure resulted.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 03-25 is implemented, verified and committed locally on the dedicated worktree.
- The broader preserved-capability certification remains assigned to plan 03-27.
- Integration into `master`, worktree cleanup and push remain separate operations and were not performed.

## Git

```yaml
status: complete-pending-git
base_sha: 844767899bba002f8068c09f407e4e0eca230711
branch: worktree-agent-03-gap-exec
worktree: C:/Users/giova/Desktop/BetUFU-worktrees/worktree-agent-03-gap-exec
implementation_commit: b07ac9fad668bc57269462bcb24eab8abec60358
documentation_commit: current documentation commit (hash intentionally omitted)
integration: not performed
cleanup: not performed
push: not performed
```

---
*Phase: 03-experi-ncia-esportiva-do-piloto*
*Plan: 03-25*
*Completed: 2026-09-04*
