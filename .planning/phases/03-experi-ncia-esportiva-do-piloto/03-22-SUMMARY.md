---
phase: 03-experi-ncia-esportiva-do-piloto
plan: 22
wave: 22
status: complete-pending-git
completed: 2026-09-03
subsystem: sports-generic-event-format
tags: [sports, generic, flyway, openapi, angular]

requires:
  - phase: 03-experi-ncia-esportiva-do-piloto
    provides: 03-21 competition-to-sport association and phase-3 sports baseline
provides:
  - forward-only V22 format constraint accepting GENERIC
  - generic event, tracer and public-view support without fabricated detailed data
  - governed OpenAPI candidate and regenerated Angular contract for GENERIC
affects: [sports-event, tracer, public-view, standings, bracket, phase-3]

actuals:
  tasks: 4
  commits: 1
  changed_technical_paths: 20

tech-stack:
  added: []
  patterns:
    - additive Flyway migration with an explicit format CHECK
    - GENERIC is accepted for catalog and agenda while detailed derivation remains guarded
    - OpenAPI candidate approval before governed client generation

key-files:
  added:
    - backend/src/main/resources/db/migration/V22__allow_generic_event_format.sql
    - frontend/src/app/features/sports/public-agenda/public-agenda-api.adapter.spec.ts
    - frontend/target/api-candidate/03-22/inventory.json
    - frontend/target/api-candidate/03-22/digest.sha256
    - frontend/target/api-candidate/03-22/governed-before.sha256
    - frontend/target/api-candidate/03-22/governed-after.sha256
  modified:
    - backend/openapi/betufu.yaml
    - backend/src/main/java/br/com/betufu/sports/internal/event/SportsEventModels.java
    - backend/src/main/java/br/com/betufu/sports/internal/event/SportsEventService.java
    - backend/src/main/java/br/com/betufu/sports/internal/tracer/SportsTracerModels.java
    - backend/src/main/java/br/com/betufu/sports/internal/tracer/SportsTracerService.java
    - backend/src/main/java/br/com/betufu/sports/internal/publicview/SportsPublicViewModels.java
    - backend/src/main/java/br/com/betufu/sports/internal/bracket/SportsBracketRepository.java
    - backend/src/main/java/br/com/betufu/sports/internal/bracket/SportsBracketService.java
    - backend/src/main/java/br/com/betufu/sports/internal/result/SportsResultRepository.java
    - backend/src/main/java/br/com/betufu/sports/internal/result/SportsResultService.java
    - backend/src/test/java/br/com/betufu/platform/persistence/FlywayMigrationIT.java
    - backend/src/test/java/br/com/betufu/sports/SportsBracketIT.java
    - backend/src/test/java/br/com/betufu/sports/SportsEventResultIT.java
    - backend/src/test/java/br/com/betufu/sports/SportsPublicViewIT.java
    - backend/src/test/java/br/com/betufu/sports/SportsTracerIT.java
    - frontend/scripts/generate-api.mjs
    - frontend/src/app/api/generated/model/sports-event-format.ts
    - frontend/src/app/features/sports/public-agenda/public-agenda-api.adapter.ts

## Accomplishments

- Added `GENERIC` to the event format contract and persisted/publicly projected generic events through V22.
- Added the forward-only database CHECK and migration coverage without rewriting legacy rows.
- Kept `HEAD_TO_HEAD` as the only detailed format and blocked only derivations that actually exist for standings/results/brackets; manual official CRUD/publication remains available.
- Regenerated the Angular model through the governed generator after approval of candidate digest `D8DDE8402F6D66D279B46E66DAF5000619A010ED28C5CA2B4000A6287B527E3D`.
- Updated the public agenda adapter so `GENERIC` preserves the event title instead of fabricating a `team A x team B` label; unknown formats are rejected.

## Verification Evidence

- Maven focal command from task 2: `55/55`, `BUILD SUCCESS`.
- `node frontend/scripts/generate-api.mjs --check`: passed.
- `node --test frontend/scripts/generate-api.test.mjs`: `72/72`.
- Angular generated-contract and public-agenda adapter focal command: `15/15`.
- Candidate guard: all four files exist; governed-before and governed-after both equal `D53E38AE2F7CE539B0610694480E158535697ED5E83FB52C4A4C2309B72228DC`.
- `git diff --check`: passed; only expected Windows LF/CRLF warnings were emitted.

## Decisions and Deviations

- The responsible owner approved the additive `GENERIC` enum, V22 CHECK and manual D-65 boundary before implementation.
- The candidate OpenAPI digest was explicitly approved before client generation.
- Four paths were added to the plan allowlist after implementation because the live derivation paths required explicit `GENERIC` guards: `SportsBracketRepository.java`, `SportsBracketService.java`, `SportsResultRepository.java` and `SportsResultService.java`. The owner ratified this deviation as the more solid behavior; no new product surface or automatic calculation was introduced.
- RepoWise was queried first, but its index tracked `origin/master` and was treated as orientation only; live files and Git were authoritative.
- No stage, commit, integration, cleanup or push was performed.

## Git

```yaml
status: complete-pending-git
base_sha: 5cfaa4ca517dd082b8b89ec85ba0fc95f5548325
branch: worktree-agent-03-gap-exec
worktree: C:/Users/giova/Desktop/BetUFU-worktrees/worktree-agent-03-gap-exec
implementation_commit: 4bea05a96c92d8730f83f7a75ed67250306a9421
documentation_commit: pending
integration: not performed
cleanup: not performed
push: not performed
```

## Notes

The worktree also contains the previously completed 03-21 implementation and its approved candidate artifacts. Those paths are preserved and are not reclassified as 03-22 changes here.
