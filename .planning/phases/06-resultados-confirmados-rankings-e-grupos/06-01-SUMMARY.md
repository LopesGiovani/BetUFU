---
phase: 06-resultados-confirmados-rankings-e-grupos
plan: "01"
status: complete
completed: 2026-10-01
technical_commit: 942461533d5f2c2488614d2ffe93ebeda4097faf
---

# Plan 06-01 Summary

Created the durable settlement foundation without changing prediction, odds, sports-result or credit-ledger history.

## Delivered

- V33 adds append-only authorization, processing case and per-prediction settlement facts with unique idempotency keys, versioned case identity, money in `numeric(20,2)` and UPDATE/DELETE rejection on all three tables.
- Authorization records a distinct technical actor, decision and time. A database trigger requires the current published sports outcome and, for sporting results, its exact confirmed version. A rejected authorization cannot create a case.
- Public consumer-owned ports expose only the current sports result and the minimal accepted-prediction snapshot. Producer adapters remain in `sports` and `betting`; no production import crosses into another module's `internal` package.
- `SettlementRepository` inserts immutable facts and reads units by idempotency key. The settlement motor, ledger effects and operational lease belong to 06-02/V34 and are not claimed by this plan.
- Test fixtures that assumed V32 was the final migration now account for V33; V32 replay tests stay targeted at V32.

## Verification

- Focal Maven test (settlement schema/contract/Modulith and affected migration suites): 38/38 Surefire tests, 0 failures, 0 errors, 0 skipped.
- Aggregate `node scripts/maven.mjs -f backend/pom.xml verify`: Surefire 392/392 across 41 classes; Failsafe 542/542 across 95 classes; 0 failures, errors or skipped. The wrapper's 1,802 count duplicates Maven summary lines; XML totals are authoritative.
- `node --test scripts/*.test.mjs`: 759/759 tests across 9 suites.
- `node scripts/maven.mjs -f backend/pom.xml spotless:check` and `git diff --check`: PASS.
- Flyway applied all 34 migrations through V33 in isolated PostgreSQL Testcontainers. No migration was run against the owner's persistent local database.

## Deviations And Risks

- The interrupted first attempt left stale build artifacts; a `clean test` rebuilt the current tree and passed. Recreated files were staged only after rechecking the worktree.
- The V33 addition broke seven hard-coded migration counts and one V32 replay test. The fixes are limited to three historical test files and preserve the original migration assertions.
- Aggregate Testcontainers startup was intermittently slow in an unrelated session test. The final complete `verify` passed without Docker reset or changing infrastructure.
- Authorization-role validation, payout/refund effects, durable progress and lease recovery remain 06-02 responsibilities. No user-facing settlement flow exists yet.

## Git And Scope

- Base HEAD: `f20b32ab3074a437315ec4aa85fec365255af0b8` on `master`.
- Technical commit: `942461533d5f2c2488614d2ffe93ebeda4097faf`.
- The owner's changes in `docs/development.md`, `docs/local-seed.md` and `docs/manual-testing.md` were excluded. No push was performed.
