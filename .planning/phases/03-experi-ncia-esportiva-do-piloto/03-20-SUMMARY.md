---
phase: 03-experi-ncia-esportiva-do-piloto
plan: 20
wave: 19
status: complete
completed: 2026-09-02
subsystem: legacy-operational-console
tags: [angular, routing, accessibility, capabilities, playwright]

requires:
  - phase: 03-experi-ncia-esportiva-do-piloto
    provides: personal ConsoleShell convergence and semantic route contract from 03-19
provides:
  - operational ConsoleShell convergence for the three legacy destinations
  - retirement of AuthenticatedLayoutComponent after the six product routes were cut over
  - semantic development/production route validation and consolidated operational browser proof
affects: [operational-console, routing, authorization, phase-03]

actuals:
  tasks: 5
  commits: 1
  changed_technical_paths: 14

tech-stack:
  added: []
  patterns:
    - one ConsoleShell with explicit operational and personal variants
    - capability-filtered operational navigation remains a convenience layer over server authorization
    - tracer and diagnostic remain development-only technical routes
    - semantic route validation replaces brittle full-route snapshots
    - legacy operational browser coverage lives in sports-pilot.spec.ts

key-files:
  modified:
    - frontend/src/app/app.routes.development.ts
    - frontend/src/app/app.routes.production.ts
    - frontend/src/app/layout/console-shell.component.ts
    - frontend/src/app/layout/console-shell.component.html
    - frontend/src/app/layout/console-shell.component.spec.ts
    - frontend/src/app/features/profile/profile-page.component.spec.ts
    - frontend/e2e/sports-pilot.spec.ts
    - scripts/validate-angular-route-boundary.mjs
  deleted:
    - frontend/src/app/layout/authenticated-layout.component.ts
    - frontend/src/app/layout/authenticated-layout.component.html
    - frontend/src/app/layout/authenticated-layout.component.scss
    - frontend/src/app/layout/session-layout.spec.ts
    - frontend/e2e/operations-panel.spec.ts
    - frontend/playwright.operations.config.ts

## Accomplishments

- Routed the six product surfaces under `/conta/...` and `/operacao/...` through the ConsoleShell personal/operational variants in development and production.
- Preserved capability-derived operational navigation, server-side authorization, guards, stores, adapters, APIs, validation, and business rules.
- Retired AuthenticatedLayoutComponent only after equivalent session, focus, navigation, route, and absence-of-residual-reference proofs were in place.
- Kept `tracer` and `diagnostic` as development-only technical routes; production remains free of both.
- Consolidated the old operations browser lane into `sports-pilot.spec.ts` under `@03-20-legacy-operations`.

## Decisions D-95 to D-100

- D-95: this is a visual/structural migration only; no API, HTTP contract, adapter, Signal store, guard, validation, permission, capability, security rule, or business rule changes are allowed.
- D-96: the six personal and operational routes are mandatory Wave 19 convergence scope before Phase 3 certification.
- D-97: account routes use the personal variant of the same ConsoleShell, with shared tokens, focus, navigation, and states.
- D-98: operational routes use the operational variant; capabilities filter navigation, while the backend remains the authorization authority.
- D-99: the old authenticated layout can be removed only after all six product routes and equivalent tests are migrated; tracer/diagnostic stay development-only.
- D-100: 03-19 owns personal convergence, 03-20 owns operational convergence and shell retirement, and 03-17/W20 depends on this wave; no new visual dependency or fixture is introduced.

## Verification Evidence

- `npm --prefix frontend ci`: 558 packages installed, zero vulnerabilities.
- Task 1 Angular focal: 22/22; Task 2 Angular focal: 35/35.
- Angular complete suite: 342/342 across 57 files.
- Node complete suite: 625/625.
- Route-boundary tests: 173/173; live route validator: `OK`.
- Production build passed with no component over the 8 kB error threshold.
- Playwright legacy-operations focal: 4/4 at desktop and 320 px.
- `git diff --check`: passed.

## Deviations and Limitations

- Angular retained five pre-existing NG8102 diagnostics.
- Style warnings remain below the 8 kB error threshold; `operations-panel` is 7.06 kB and `role-admin` is 4.02 kB, so both warnings are recorded rather than hidden.
- Five npm scripts remained blocked by the repository `allow-scripts` policy; no manual script approval was granted.
- RepoWise was fresh at `8e1b893`, but its index predates this implementation commit; live Git files are authoritative for the final snapshot.

## Issues

- No known product, authorization, privacy, route-isolation, or business-rule regression remains in the verified implementation snapshot.
- The 03-17/W20 certification is the next technical front now that this wave is integrated, cleaned, and pushed.

## Git

```yaml
status: complete
base_sha: 8e1b8937363003f4d007f12d97182838748a7b5a
branch: worktree-agent-03-20
worktree: C:/Users/giova/Desktop/BetUFU-worktrees/worktree-agent-03-20
implementation_commit: fa2985d1c9b94c5c79491f58212f81c4c36bb45a
documentation_commit: 88eb9646af08bf7061a3dd4fcda9e27d1701fe1e
integration: fast-forward complete
cleanup: exact worktree and branch removed
push: Wave 19 published; confirm live refs after the non-self-referential maintenance fold
```

## Next-Phase Readiness

- Task 5 human checkpoint was approved with `aprovar Console convergido 03-20`.
- The technical snapshot was isolated, verified, integrated, cleaned, and pushed.
- 03-17/W20 can proceed as the Phase 3 certification front; no new visual dependency or fixture is introduced.
