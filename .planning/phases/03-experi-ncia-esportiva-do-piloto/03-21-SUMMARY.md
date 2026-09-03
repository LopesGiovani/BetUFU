---
phase: 03-experi-ncia-esportiva-do-piloto
plan: 21
wave: 21
status: complete-pending-git
completed: 2026-09-03
subsystem: sports-catalog-association
tags: [sports, catalog, flyway, openapi, angular]

requires:
  - phase: 03-experi-ncia-esportiva-do-piloto
    provides: phase 3 sports modules and the 03-17 certification baseline
provides:
  - forward-only V21 N:N competition-to-sport association
  - competition-scoped catalog reads for operational and public sports views
  - approved OpenAPI candidate and governed Angular client update
affects: [sports-catalog, tracer, standings, bracket, public-view, phase-3]

actuals:
  tasks: 4
  commits: 1
  changed_technical_paths: 28

tech-stack:
  added: []
  patterns:
    - additive Flyway migration with idempotent legacy backfill
    - association table as the authority; V10 sport_id retained for compatibility
    - OpenAPI candidate approval before governed client generation

key-files:
  added:
    - backend/src/main/resources/db/migration/V21__associate_competitions_and_sports.sql
    - frontend/target/api-candidate/03-21/inventory.json
    - frontend/target/api-candidate/03-21/digest.sha256
    - frontend/target/api-candidate/03-21/governed-before.sha256
    - frontend/target/api-candidate/03-21/governed-after.sha256
  modified:
    - backend/src/main/java/br/com/betufu/sports/internal/catalog/SportsCatalogRepository.java
    - backend/src/main/java/br/com/betufu/sports/internal/catalog/SportsCatalogService.java
    - backend/src/main/java/br/com/betufu/sports/internal/tracer/SportsTracerRepository.java
    - backend/src/main/java/br/com/betufu/sports/internal/standings/SportsStandingsRepository.java
    - backend/src/main/java/br/com/betufu/sports/internal/standings/SportsStandingsService.java
    - backend/src/main/java/br/com/betufu/sports/internal/bracket/SportsBracketRepository.java
    - backend/src/main/java/br/com/betufu/sports/internal/bracket/SportsBracketService.java
    - backend/src/main/java/br/com/betufu/sports/internal/publicview/SportsPublicViewRepository.java
    - backend/src/main/java/br/com/betufu/sports/internal/publicview/SportsPublicViewModels.java
    - backend/openapi/betufu.yaml
    - frontend/scripts/generate-api.mjs
    - frontend/src/app/api/generated/model/sports-competition-summary.ts
    - frontend/src/app/api/generated/model/public-competition-detail.ts
    - frontend/src/app/features/sports/public-competition/public-competition-page.component.ts

## Accomplishments

- Added `sports_competition_sport` with composite primary key, non-destructive foreign keys, reverse sport index and idempotent backfill from V10.
- Switched catalog, tracer, standings, bracket and public-view reads to resolve sports through the competition association.
- Expanded the OpenAPI contract to expose a sports collection while preserving the legacy database column for compatibility only.
- Regenerated the affected Angular contract models after the literal approval of candidate digest `D53E38AE2F7CE539B0610694480E158535697ED5E83FB52C4A4C2309B72228DC`.
- Added migration, ordering, duplicate, scope and regression coverage in the focused integration tests.

## Verification Evidence

- Maven focal command from 03-21: `51/51`, `BUILD SUCCESS`.
- `node frontend/scripts/generate-api.mjs --check`: passed.
- `node --test frontend/scripts/generate-api.test.mjs`: `72/72`.
- Angular focal command: `13/13`.
- Candidate guard: all four files exist; governed-before and governed-after both equal `1BE6BAB88EA2F208A38BE115E3CB4D9B0DA2EBB8E04FBF038FD122E5A2B43F27`.
- `git diff --check`: passed; only expected Windows LF/CRLF warnings were emitted.

## Decisions and Deviations

- N:N association and idempotent backfill were approved before V21 implementation.
- The generated client was consumed only after the literal candidate approval.
- `frontend/src/app/features/sports/operations/events-results/sports-event-result-page.component.spec.ts` is an authorized mechanical fixture correction outside the original allowlist; it replaces legacy `sportId`/`sportName` fixture fields with `sports: []` and does not change production behavior.
- `frontend/node_modules` was restored with `npm ci` from existing manifests because the checkout lacked dependencies; no manifest or lockfile changed. npm reported two audit vulnerabilities and pending scripts; no audit fix or script approval was performed.
- RepoWise was queried first, but its indexed commit (`94c1a0b22bbb`) differed from this worktree base; live files and Git were authoritative.

## Git

```yaml
status: complete-pending-git
base_sha: fba033754a7fee17d9f6da0a826c6cd15871c876
branch: worktree-agent-03-gap-exec
worktree: C:/Users/giova/Desktop/BetUFU-worktrees/worktree-agent-03-gap-exec
implementation_commit: 7b6c8de9bf673db27764738b98a226d038535102
documentation_commit: pending (this formalization commit)
integration: not performed
cleanup: not performed
push: not performed
```

## Next-Phase Readiness

- 03-21 is implemented, verified and locally committed.
- The next required human checkpoint is the 03-22 decision to add the `GENERIC` event format and migration V22.
- Plans 03-22 through 03-27 remain untouched and are not authorized by this summary.
