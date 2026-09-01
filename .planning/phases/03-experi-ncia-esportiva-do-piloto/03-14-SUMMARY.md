---
phase: 03-experi-ncia-esportiva-do-piloto
plan: 14
wave: 15
status: complete-pending-git
completed: 2026-08-31
subsystem: sports-profile
tags: [spring-boot, postgresql, flyway, openapi, angular, signals, security]

requires:
  - phase: 03-experi-ncia-esportiva-do-piloto
    provides: athlete links, official sports data, public athlete projection P6
provides:
  - V19 owner-only textual and social athlete profile
  - safe public profile projection without PII or internal identifiers
  - generated OpenAPI client for athlete profile endpoints
  - C3 profile editor and enriched P6 public athlete page
  - lazy route /conta/perfil/esportivo and profile-page link
affects: [public-arena, account-console, sports-profile, phase-03]

tech-stack:
  added: []
  patterns:
    - owner and public profile stores remain separate Signal state boundaries
    - plain-text biography and HTTPS social URL allowlist
    - Flyway append-only profile migration with optimistic versioning
    - governed OpenAPI candidate before client generation

key-files:
  created:
    - backend/src/main/resources/db/migration/V19__create_athlete_public_profiles.sql
    - backend/src/main/java/br/com/betufu/sports/internal/athleteprofile/AthletePublicProfileModels.java
    - backend/src/main/java/br/com/betufu/sports/internal/athleteprofile/AthletePublicProfileRepository.java
    - backend/src/main/java/br/com/betufu/sports/internal/athleteprofile/AthletePublicProfileService.java
    - backend/src/main/java/br/com/betufu/sports/internal/athleteprofile/AthletePublicProfileController.java
    - backend/src/test/java/br/com/betufu/sports/AthletePublicProfileIT.java
    - frontend/src/app/features/sports/athlete-profile/athlete-profile-page.component.ts
    - frontend/src/app/features/sports/athlete-profile/athlete-profile.store.ts
    - frontend/src/app/features/sports/athlete-profile/athlete-profile-api.adapter.ts
    - frontend/src/app/features/sports/public-athlete/public-athlete-profile.store.ts
  modified:
    - backend/openapi/betufu.yaml
    - backend/src/test/java/br/com/betufu/platform/persistence/FlywayMigrationIT.java
    - frontend/scripts/generate-api.mjs
    - frontend/scripts/generate-api.test.mjs
    - frontend/src/app/api/generated/api/api.ts
    - frontend/src/app/api/generated/model/models.ts
    - frontend/src/app/app.routes.development.ts
    - frontend/src/app/app.routes.production.ts
    - frontend/src/app/features/profile/profile-page.component.html
    - frontend/src/app/features/profile/profile-page.component.spec.ts
    - frontend/src/app/features/sports/public-athlete/public-athlete-page.component.ts
    - frontend/src/app/features/sports/public-athlete/public-athlete-page.component.spec.ts
    - scripts/validate-angular-route-boundary.mjs

key-decisions:
  - Only the account linked to the athlete may update or remove editable profile content.
  - Teams, modalities, participations and results remain official domain data and are read-only in the profile.
  - Biography is plain text; social links require HTTPS and an explicit host allowlist.
  - Public DTOs omit enrollment, HMAC, email, authentication and internal identifiers.
  - The approved OpenAPI candidate digest is A519EC31AF3F6CF504DB180A9606B700288A76C2CB6504BEEA3802A4A4726B93.
  - Governed before and after hashes are both 5BC25D0DC503474976AFC5223037464CB66205D4DD29725A796011B78DC0295B.

requirements-completed: [SPRT-08, SPRT-13, SPRT-16]

coverage:
  - id: D1
    description: V19 persists and protects owner/public athlete profile data.
    requirement: SPRT-08
    verification:
      - kind: integration
        ref: backend/src/test/java/br/com/betufu/sports/AthletePublicProfileIT.java (10/10)
        status: pass
      - kind: integration
        ref: backend/src/test/java/br/com/betufu/platform/persistence/FlywayMigrationIT.java (13/13)
        status: pass
    human_judgment: false
  - id: D2
    description: Governed OpenAPI candidate and generated client are deterministic and PII-safe.
    requirement: SPRT-13
    verification:
      - kind: other
        ref: node frontend/scripts/generate-api.mjs --check
        status: pass
      - kind: other
        ref: governed-before.sha256 equals governed-after.sha256
        status: pass
    human_judgment: false
  - id: D3
    description: C3 owner editor and public P6 projection use isolated Signals stores and safe rendering.
    requirement: SPRT-16
    verification:
      - kind: unit
        ref: four 03-14 Angular focal specs (11/11)
        status: pass
      - kind: unit
        ref: npm --prefix frontend run test -- --watch=false (340/340)
        status: pass
    human_judgment: false
  - id: D4
    description: Repository-wide backend and script regressions remain green after V19 and route expansion.
    verification:
      - kind: other
        ref: Maven Surefire 266/266 and Failsafe 432/432
        status: pass
      - kind: other
        ref: node --test scripts/*.test.mjs (625/625)
        status: pass
    human_judgment: false

git:
  status: implementation-pending-git
  implementation_commit: pending authorization
  documentation_commit: pending authorization
  integration: pending authorization
  cleanup: pending authorization
  push: pending authorization
---

# Fase 3 Plano 03-14: perfil esportivo textual/social

**Perfil esportivo owner-only com projecao publica segura, cliente OpenAPI governado e rota de gerenciamento na Conta.**

## Performance

- **Validation window:** 2026-08-31 22:26-22:49 BRT
- **Tasks:** 5 planned tasks completed
- **Changed paths:** 35 paths, all within the original allowlist plus the authorized route expansion and one mechanical validator fix

## Accomplishments

- V19 and its integration tests enforce the athlete-link owner boundary, validation and optimistic version behavior.
- The public projection reuses official sports data while excluding enrollment, HMAC, email, authentication and internal IDs.
- The Angular owner editor and public P6 projection use separate adapters/stores and plain-text/HTTPS-safe rendering.
- `/conta/perfil/esportivo` is lazy-loaded in development and production and is linked from the account profile page.
- The OpenAPI candidate was approved before generation and the generated client passes its deterministic check.

## Validation Evidence

- Maven: Surefire `266/266`, Failsafe `432/432`, total `698`, `BUILD SUCCESS`.
- Node scripts: `625/625`, zero failures.
- Angular focal: `4` files, `11/11` tests.
- Angular full: `55` files, `340/340` tests on the final repetition.
- `git diff --check`: clean.
- Candidate digest: `A519EC31AF3F6CF504DB180A9606B700288A76C2CB6504BEEA3802A4A4726B93`.
- Governed before/after: equal at `5BC25D0DC503474976AFC5223037464CB66205D4DD29725A796011B78DC0295B`.

## Deviations and Issues

### Auto-fixed mechanical issue

The authorized route expansion added a legitimate lazy route that was missing from `scripts/validate-angular-route-boundary.mjs`. The validator consequently produced one primary `DYNAMIC_IMPORT_FORBIDDEN` error and 141 cascaded assertion mismatches in the first Node run. Adding the exact route to the validator allowlist fixed the root cause; the complete Node suite then passed `625/625`. No business rule or security behavior changed.

The first complete Angular run had one historical 5-second timeout in `athlete-link-proposal-page.component.spec.ts` outside this plan. The isolated spec passed `2/2`, and the repeated full suite passed `340/340`; no unrelated source was changed.

The Angular worktree had no local dependencies. A temporary junction to the existing main-checkout `frontend/node_modules` was used for the focal and full suites and removed afterward. The target dependency directory remained intact.

Maven emitted Redis/PostgreSQL connection and Netty teardown warnings while Testcontainers contexts shut down, but exited `0` with `BUILD SUCCESS` and zero test failures.

## Next Phase Readiness

- The implementation is certified and ready for the separate Git review gate.
- Stage, implementation/documentation commits, integration into `master`, worktree cleanup and push remain unperformed pending explicit Git authorization.
- `STATE.md`, `CLAUDE.md`, `HANDOFF.json` and `.continue-here.md` were not edited; `CLAUDE.md`, `HANDOFF.json` and `.continue-here.md` are absent in this worktree, and `STATE.md` remains historically stale as required.

---
*Phase: 03-experi-ncia-esportiva-do-piloto*
*Plan: 03-14*
