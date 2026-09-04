---
phase: 03-experi-ncia-esportiva-do-piloto
plan: 26
wave: 26
status: complete-pending-git
completed: 2026-09-04
subsystem: sports-owner-navigation
tags: [sports, angular, routing, owner, agenda, playwright]

requires:
  - phase: 03-experi-ncia-esportiva-do-piloto
    provides: 03-25 event lifecycle history and the existing owner proposal flow
provides:
  - static personal route for the current identity's athlete-link proposal
  - Console navigation to the owner proposal without proposalId in the URL
  - canonical encoded event links shared by public agenda surfaces
  - four Playwright scenarios proven in the material Chromium lane
affects: [sports-link-proposal, public-agenda, console-routing, phase-3]

actuals:
  tasks: 3
  commits: 2
  changed_technical_paths: 13

tech-stack:
  added: []
  patterns:
    - static owner route delegates authorization to the current server-side identity
    - development and production route manifests stay structurally equivalent
    - agenda cards consume one canonical encoded event detail URL
    - unauthorized owner responses fail closed without proposal data in the DOM

key-files:
  created: []
  modified:
    - frontend/src/app/app.routes.development.ts
    - frontend/src/app/app.routes.production.ts
    - frontend/src/app/app.routes.spec.ts
    - frontend/src/app/layout/console-shell.component.ts
    - frontend/src/app/layout/console-shell.component.spec.ts
    - frontend/src/app/features/sports/link-proposal/athlete-link-proposal-page.component.spec.ts
    - frontend/src/app/features/sports/public-agenda/public-agenda-api.adapter.ts
    - frontend/src/app/features/sports/public-agenda/public-agenda-api.adapter.spec.ts
    - frontend/src/app/features/sports/public-agenda/public-agenda-page.component.spec.ts
    - frontend/src/app/shared/sports/auto-event-hero.component.spec.ts
    - frontend/src/app/shared/sports/broadcast-ticker.component.spec.ts
    - frontend/e2e/sports-pilot.spec.ts
    - scripts/validate-angular-route-boundary.mjs

key-decisions:
  - The personal route is exactly `/conta/vinculo-atleta`, guarded by `profileCompletionGuard` in development and production manifests.
  - The route accepts no `proposalId`; `AthleteLinkProposalStore.load()` keeps the authenticated server identity authoritative.
  - Event destinations use `/eventos/${encodeURIComponent(eventId)}` instead of document fragments.
  - The material Playwright run is the certification evidence for the owner and deep-link flows.

patterns-established:
  - "Personal owner URLs identify the feature, never the proposal whose ownership must be decided by the server."
  - "Every agenda presentation consumes the adapter's canonical detailHref instead of inventing a parallel destination."
  - "401 and 403 responses clear the proposal view and render no sensitive proposal fields."

requirements-addressed: [SPRT-08, SPRT-12]
requirements-preserved: [AUTH-01, SPRT-03, SPRT-04, SPRT-05, SPRT-06, SPRT-07, SPRT-10, SPRT-13, SPRT-15, SPRT-16]

coverage:
  - id: D1
    description: "The owner route exists in both build manifests, is guarded, is reachable from the Console and loads without a proposal identifier."
    requirement: SPRT-08
    verification:
      - kind: unit
        ref: "Angular Task 1 focal command"
        status: pass
      - kind: other
        ref: "route-boundary live validator and Node suite"
        status: pass
    human_judgment: false
  - id: D2
    description: "Public agenda CTAs use the encoded full event route across the adapter and shared cards."
    requirement: SPRT-12
    verification:
      - kind: unit
        ref: "Angular Task 2 focal command"
        status: pass
    human_judgment: false
  - id: D3
    description: "Browser scenarios cover CTA navigation, direct refresh, owner identity lookup and fail-closed 401/403 behavior."
    requirement: SPRT-08
    verification:
      - kind: e2e
        ref: "npm --prefix frontend exec playwright test -- --config playwright.sports.config.ts --project=phase3-chromium --grep @03-26-owner-event"
        status: pass
    human_judgment: false

## Phase 3: Experiencia Esportiva do Piloto - Plan 03-26 Summary

**Rota pessoal da proposta owner e deep links canonicos para eventos esportivos**

## Performance

- **Duration:** multi-session
- **Implementation completed:** 2026-09-04
- **Tasks:** 3
- **Files modified:** 13 technical paths

## Accomplishments

- Registered `/conta/vinculo-atleta` in the development and production route manifests under `profileCompletionGuard` and exposed it from the personal Console navigation.
- Kept the owner query free of `proposalId`, so the browser cannot choose another person's proposal and the backend identity remains authoritative.
- Replaced agenda fragments with encoded `/eventos/:eventId` destinations while preserving the same accessible links in event row, hero and ticker presentations.
- Added four Playwright cases for agenda navigation and refresh, owner navigation and refresh, and fail-closed `401`/`403` responses without proposal data leakage.

## Verification Evidence

- Angular Task 1 focal suite: `3/3` files and `15/15` tests passed.
- Angular Task 2 focal suite: `4/4` files and `18/18` tests passed.
- `node scripts/validate-angular-route-boundary.mjs`: `OK`.
- `node --test scripts/validate-angular-route-boundary.test.mjs`: `173/173` passed, zero failures.
- Playwright material lane: `4/4` passed in Chromium (`16.7s`).

## Decisions and Deviations

- No backend endpoint, authorization rule, generated client, dependency, database object or infrastructure configuration changed.
- `app.routes.ts`, the owner page/store and shared event components were allowed by the plan but required no content change after the build-specific manifests and canonical adapter were reconciled.
- The first material attempt exposed a test-only heading-level mismatch; changing the expected error heading from level 1 to level 2 was validated by the final `4/4` run.
- Browser evidence is green, so 03-26 is certified locally and Phase 3 advances to `26/27` plans.
- RepoWise was queried first. Its index was fresh at primary-checkout HEAD `8a85cec64c7c`, while this authorized worktree was at `1dd0e96`; live Git and files were authoritative for this formalization.
- Concurrent changes in Maven-output tooling and `docs/development.md` belong to another agent and remain outside this amend.

## Issues Encountered

- The first invocation from the worktree root resolved the config path incorrectly on Windows; rerunning from `frontend` used the same plan config and passed.
- No product, architecture, security or dependency decision was needed to finish the implementation.

## User Setup Required

None - the required Chromium browser evidence is green.

## Next Phase Readiness

- The 03-26 implementation and material browser evidence are committed locally.
- Plan 03-26 is certified locally and advances the phase count to `26/27`.
- Plan 03-27 remains blocked on that certification and owns the broader preserved-capability recertification.
- Integration into `master`, worktree cleanup and push remain separate operations and were not performed.

## Git

```yaml
status: complete-pending-git
base_sha: 40f90057f4dd33aea2e56b9ae205e358b83150fc
branch: worktree-agent-03-gap-exec
worktree: C:/Users/giova/Desktop/BetUFU-worktrees/worktree-agent-03-gap-exec
implementation_commit: 1dd0e96c9cbf610c684679d57ba554b6b56c478f
documentation_commit: current documentation commit (hash intentionally omitted)
integration: not performed
cleanup: not performed
push: not performed
```

---
*Phase: 03-experi-ncia-esportiva-do-piloto*
*Plan: 03-26*
*Completed: 2026-09-04*
