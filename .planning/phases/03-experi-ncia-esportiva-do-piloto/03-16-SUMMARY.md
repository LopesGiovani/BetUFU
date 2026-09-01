---
phase: 03-experi-ncia-esportiva-do-piloto
plan: 16
wave: 17
status: complete-pending-git
completed: 2026-09-01
subsystem: sports-operational-console
tags: [spring-boot, security, angular, signals, capabilities, routing, authorization]

requires:
  - phase: 03-experi-ncia-esportiva-do-piloto
    provides: secure athlete media and governed OpenAPI client from 03-15
provides:
  - server-derived operational capabilities with exact persisted scope matching
  - fail-closed sports workspace scope selection and O1-O8 navigation
  - ConsoleShell sports navigation with capability-filtered destinations
  - operational panel entry backed only by the BFF capability contract
affects: [sports-console, operational-panel, authorization, phase-03]

actuals:
  tasks: 5
  commits: 0
  changed_technical_paths: 17

tech-stack:
  added: []
  patterns:
    - server capability projection without exposing role names to the client
    - exact UUID scope comparison with fail-closed unknown and malformed payload handling
    - route canMatch revalidation against the authorized BFF workspace
    - scope state reset before reload to prevent privileged stale data
    - capability-filtered ConsoleShell navigation with post-route focus

key-files:
  created:
    - frontend/src/app/features/sports/operations/sports-operations.routes.ts
    - frontend/src/app/features/sports/operations/sports-operations.routes.spec.ts
  modified:
    - backend/src/main/java/br/com/betufu/platform/operations/OperationalCapabilitiesService.java
    - backend/src/main/java/br/com/betufu/platform/operations/OperationalPanelService.java
    - backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java
    - backend/src/test/java/br/com/betufu/platform/operations/OperationalCapabilitiesIT.java
    - frontend/src/app/app.routes.ts
    - frontend/src/app/features/operations/operations-api.adapter.ts
    - frontend/src/app/features/operations/operations-panel-page.component.ts
    - frontend/src/app/features/operations/operations-panel-page.component.html
    - frontend/src/app/features/operations/operations-panel-page.component.scss
    - frontend/src/app/features/operations/operations-panel-page.component.spec.ts
    - frontend/src/app/features/operations/operations.models.ts
    - frontend/src/app/features/sports/operations/scope/sports-scope.store.ts
    - frontend/src/app/layout/console-shell.component.ts
    - frontend/src/app/layout/console-shell.component.html
    - frontend/src/app/layout/console-shell.component.scss

key-decisions:
  - The server remains the authority: the frontend receives only a closed capability list and never maps role names.
  - A scoped grant matches a requested competition only when the persisted UUID is exactly equal; an absent URL scope is only a workspace listing hint.
  - O1-O8 are lazy routes guarded by capability and scope revalidation; a URL scopeRef is never accepted as proof.
  - The sports route module is placed before the legacy bridge in the seam so the guarded O1-O8 routes win while the legacy bridge remains intact for 03-19/03-20.
  - Capability caching was intentionally removed from the adapter because a replayed capability result could survive logout and expose a prior user's menu.

requirements-completed: []

coverage:
  - id: C1
    description: Backend capabilities are projected from persisted roles and reject divergent competition UUIDs.
    requirement: SPRT-01
    verification:
      - kind: integration
        ref: backend/src/test/java/br/com/betufu/platform/operations/OperationalCapabilitiesIT.java (focal scopeA_doesNotAuthorizeScopeB)
        status: pass
      - kind: other
        ref: Maven verify Surefire 266/266 and Failsafe 441/441
        status: pass
    human_judgment: false
  - id: C2
    description: The panel and ConsoleShell expose sports navigation only from BFF capabilities and fail closed on malformed responses.
    requirement: SPRT-01
    verification:
      - kind: unit
        ref: operations panel and sports route Angular focal specs (21/21)
        status: pass
      - kind: unit
        ref: npm run test -- --watch=false (354/354 across 57 files)
        status: pass
    human_judgment: false
  - id: C3
    description: Scope reload clears previous privileged state and rejects a deep link from scope B when only scope A is authorized.
    requirement: SPRT-01
    verification:
      - kind: unit
        ref: sports-operations.routes.spec.ts UUID A/B and zero/multiple-scope cases
        status: pass
      - kind: other
        ref: route canMatch implementation and SportsScopeStore reset-before-reload
        status: pass
    human_judgment: false
  - id: C4
    description: Angular builds and browser smoke remain green for the current repository surface.
    requirement: SPRT-01
    verification:
      - kind: other
        ref: development, homolog and production builds
        status: pass
      - kind: browser
        ref: playwright.sports.config.ts (3/3)
        status: pass
      - kind: other
        ref: node --test scripts/*.test.mjs (625/625)
        status: pass
      - kind: other
        ref: npm run generate:api:check
        status: pass
    human_judgment: false

validation:
  - RepoWise was queried first for capability, panel, scope-store and route dependencies; its graph is stale at commit b84e3db, so live source was authoritative for changed symbols.
  - Backend full verification passed with Surefire 266/266, Failsafe 441/441 and Spotless clean.
  - Angular focal specs passed 21/21; the complete suite passed 354/354 across 57 files after one unrelated transient timeout was repeated successfully.
  - Angular development, homolog and production builds completed successfully; no component exceeded the 8 kB error budget.
  - Node complete suite passed 625/625; OpenAPI generated-client check passed.
  - Playwright sports suite passed 3/3 in Chromium.
  - Lint passed and Prettier passed for every changed technical file; four pre-existing 03-15 files remain outside this allowlist and are not reformatted.
  - git diff --check passed.

deviations:
  - "The live route-boundary validator still rejects app.routes.ts because its textual contract predates the sportsOperationsRoutes module. Updating that validator is explicitly assigned to 03-19 and remains outside this plan allowlist; the fixture-based validator suite passed 173/173."
  - "Angular emitted existing NG8102 diagnostics and CSS budget warnings, including operations-panel 7.06 kB and profile-page 6.49 kB; all remain below the 8 kB error budget and are recorded for the planned Console convergence waves."
  - "Maven/Testcontainers emitted Redis/PostgreSQL teardown warnings after green tests; the process exited with BUILD SUCCESS."
  - "The first complete Angular run had one unrelated athlete-link-proposal timeout; the immediate repeat passed 354/354."

issues:
  - "No known product, authorization, privacy or scope-isolation issue remains in the certified implementation snapshot."
  - "The route-boundary live validator requires the planned 03-19 semantic contract update before the next route-topology change."

git:
  status: complete-pending-git
  base_sha: 32c6af3b20576001b6d406905bb763df29810a8a
  branch: worktree-agent-03-16
  worktree: C:/Users/giova/Desktop/BetUFU-worktrees/worktree-agent-03-16
  implementation_commit: pending
  documentation_commit: pending
  integration: pending
  cleanup: pending
  push: not-authorized

next-phase-readiness:
  - "The technical snapshot is ready for read-only review after the final hash freeze."
  - "The next technical plan is 03-19/W18, which owns the semantic route-boundary validator update and personal ConsoleShell variant."
  - "The subsequent 03-20/W19 owns the legacy operational cut and AuthenticatedLayout retirement."
---

# Fase 3 Plano 03-16: Console operacional esportivo

**Capabilities derivadas do servidor, isolamento de escopo e navegação O1-O8 em um ConsoleShell sem exposição local de papéis.**

## Accomplishments

- The backend now projects sports capabilities from persisted operational grants and exposes the capabilities endpoint behind authentication.
- The frontend adapter accepts only the closed generated capability enum, while the operations panel and ConsoleShell render navigation from that contract.
- SportsScopeStore clears old scope data before revalidation, and route guards reject missing capabilities, malformed payloads and UUID A/B deep-link mismatches.
- The canonical O1-O8 module covers overview, catalog, organizations/teams, athletes/rosters, CSV/XLSX import, events/results, bracket/standings and link review.
- The legacy route bridge remains available for the next personal and legacy-console waves; the new guarded module is ordered first in the seam.

## Verification Evidence

- Backend: Surefire `266/266`, Failsafe `441/441`, Spotless clean, `BUILD SUCCESS`.
- Frontend: focal `21/21`; complete `354/354` across `57` files; lint passed.
- Builds: development, homolog and production completed successfully with no 8 kB component-style error.
- Node: `625/625`; OpenAPI generator check passed.
- Browser: Playwright sports Chromium `3/3`.
- Route fixture suite: `173/173`; live route validator update is deferred to 03-19 by allowlist.
- `git diff --check` passed and all changed technical paths remain within the 03-16 allowlist.

## Self-Check: PASSED

- All five plan tasks, including the human navigation checkpoint, are represented in the snapshot.
- Scope isolation, capability filtering and stale-state cleanup have direct tests.
- The capability cache was removed before freezing to avoid cross-session privilege replay.
- No Git mutation has been performed yet; implementation and documentation commits follow in the authorized Git block.

## Operacoes Git

Stage, implementation commit, documentation commit, fast-forward integration and exact worktree/branch cleanup are authorized and pending. Push remains outside the user's authorization.

---
*Phase: 03-experi-ncia-esportiva-do-piloto*
*Plan: 03-16*
*Wave: 17*
