---
phase: 06-resultados-confirmados-rankings-e-grupos
plan: "03"
status: complete
completed: 2026-10-02
technical_commit: 9351f04eb3b1df21afcb82229e51dce81916c8b4
---

# Plan 06-03 Summary

Implemented audited credit corrections, recovery of spent payouts and financial reliquidation without modifying historical predictions, settlements or ledger entries.

## Delivered

- V35 adds append-only correction cases, separate maker-checker decisions, FIFO recovery allocations and constrained `CORRECTION_CREDIT`/`CORRECTION_RECOVERY` ledger entries. It extends the audit action constraint without editing V26 or another applied migration.
- An administrative proposal requires a typed reference to an existing ledger entry, prediction or settlement. Producer-owned lookups verify the monetary fact, owner, amount and correction direction. Eligibility corrections additionally verify enrollment ownership; an enrollment alone cannot justify a monetary adjustment.
- A distinct general administrator approves or rejects the proposal. Database checks also reject self-approval and duplicate approval of the same monetary fact, including references through different types. The owner's view exposes amounts, status, reason and sanitized references, not administrative actors or enrollment details.
- Recovery consumes available balance without going negative. Future grants, payouts, refunds and correction credits settle pending cases oldest first before increasing available balance. Pending debt blocks new predictions but not balance or history reads.
- A corrected result after an earlier payout/refund creates a compensating recovery case and a superseding settlement. If the previous credit was spent, the unrecovered amount remains visible as pending; retries do not duplicate effects. Public `platform.operations` and consumer-owned `settlement` ports keep module internals private.

## Verification

- Focal correction, ledger, settlement, migration, audit, privacy and Modulith tests passed during implementation. A final `node scripts/maven.mjs -f backend/pom.xml clean verify` passed 360/360 Surefire tests across 37 classes and 573/573 Failsafe tests across 105 classes, with zero failures, errors or skipped. The wrapper's 1,866 count duplicates Maven summaries; 933 XML cases are authoritative.
- `node --test scripts/*.test.mjs`: 759/759 tests across 9 suites; zero failures or skipped.
- `node scripts/maven.mjs -f backend/pom.xml spotless:check` and `git diff --check` passed. V35 was applied only to isolated Testcontainers PostgreSQL, not the owner's persistent local database.

## Deviations And Remaining Work

- An aggregate run exposed missing correction actions in the database audit constraint. V35 now adds them, and the V26-specific vocabulary test checks only its historical odds actions; the V35 test checks the complete current vocabulary.
- A non-clean aggregate run encountered stale `.class` files for sports tests moved to `sports.internal.*`. `clean verify` removed generated artifacts, rediscovered the correct classes and passed. No source files outside the 06-03 allowlist were changed for this issue.
- RepoWise marked the ledger change as high-impact because it has many consumers. The full backend suite passed, but per-test measured line coverage is unavailable. Controller HTTP, OpenAPI and Angular correction journeys remain in 06-07; ranking reconstruction belongs to 06-04.
- `STATE.md` milestone counters were stale before this plan. They now match the explicit ROADMAP rows: 134 known plans through Phase 6, 129 completed including 06-03; Phase 7 plan count is still TBD.
- The technical commit `9351f04eb3b1df21afcb82229e51dce81916c8b4` includes 30 backend paths and the ratified plan. The owner's `docs/development.md`, `docs/local-seed.md` and `docs/manual-testing.md` remain untouched and excluded. No push, Docker reset, local seed or real prediction occurred; documentary stage and commit require separate approval.
