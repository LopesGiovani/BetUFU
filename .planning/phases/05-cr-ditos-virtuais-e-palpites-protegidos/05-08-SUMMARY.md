---
phase: 05-cr-ditos-virtuais-e-palpites-protegidos
plan: "08"
status: complete
completed: 2026-09-15
---

# Plan 05-08 Summary

Reconciled the local seed with the immutable odds-publication and initial-credit
contracts introduced by Plans 05-06 and 05-07.

## Delivered

- Every seeded odds calculation now records its complete `publishedOdds`
  vector in `input_snapshot`. Publications use deterministic vectors
  `[1.90, 1.90]` and `[1.73, 2.12]`, while the legacy scalar remains the HOME
  value required by V31.
- Every seeded publication appends its ordered HOME(1) and AWAY(2) rows to
  `odds_publication_selection` in the same transaction.
- Active local enrollments now converge to one `credit_account` and one
  append-only `GRANT_INITIAL` ledger fact of `1000.00`. The balance increment
  consumes only identities returned by a newly inserted ledger fact, so a
  repeated seed cannot duplicate credit.
- Regression tests cover the vector/selection relationship and the
  insert-returning-update idempotency pattern.

## Verification

- `node --test scripts/seed-local.test.mjs`: 9 tests passed, 0 failed.
- `node --test scripts/*.test.mjs`: 749 tests passed, 0 failed.
- `node scripts/maven.mjs -f backend/pom.xml verify`: BUILD SUCCESS. The report
  XML records Surefire 327/327 and Failsafe 534/534, with no failures, errors or
  skips. The wrapper displayed 1722 because it counted the 861 reports twice.
- The Docker reset removed exactly seven BETUFU containers and four BETUFU
  volumes, preserving unrelated Docker resources.
- `node scripts/dev.mjs up full` exited with code 0. Flyway applied 33 migrations
  to schema version V32, and backend, frontend, PostgreSQL, Redis, Keycloak,
  Mailpit and SeaweedFS all became healthy.
- `node scripts/dev.mjs seed full` succeeded twice, each time reporting 671
  matches and 6 personas.
- Live PostgreSQL evidence after the second seed: 5/5 publications have exactly
  two selections, 2 eligible identities have exactly one initial grant, and
  their account balances are `1000.00` each (`2000.00` total).
- The frontend responded with HTTP 200.

## Migration Integrity

- V31 SHA-256: `1DB6E1CE197DFF9F12ED9F293B78956BBFD9DE8E6E17772B07BC66662A4AB204`.
- V32 SHA-256: `850EB3DE83BD308D2B9E301BBC9F1BC6E2485653DF3FC7343B6C548A125F91D7`.
- Both approved migrations remained byte-identical; the correction is confined
  to the post-migration local fixture.

## Execution Notes

- The initial backend failure came from a retained named PostgreSQL volume, not
  from the rebuilt Docker image. Deleting the old container alone therefore did
  not clear the incompatible pre-V31 data.
- One evidence query initially failed because of PowerShell quote escaping; the
  same read-only checks were rerun as simple commands and all passed. No database
  correction was needed.
- The responsible developer explicitly authorized this plan to run inline,
  without Antigravity/`agy-wave.mjs`, including reset, tests and commits. The
  exception is limited to Plan 05-08.

## Git

- Base: `e19586cfd2fce4cefb6b5306ead9f35995a8c2a7`.
- Technical commit: `9a3a016b9ea35b3820b8be45e9692ef2fcb27a83`.
- The documentation commit carries this summary and intentionally cannot embed
  its own final hash.
- Pre-existing changes in `docs/development.md`, `docs/local-seed.md` and
  `docs/manual-testing.md` were preserved and excluded from both commits.
- No push was performed.

## Next

Resume the interactive Phase 6 discussion. Plan 05-08 changes no settlement,
compensation, ranking or privacy decision for that phase.
