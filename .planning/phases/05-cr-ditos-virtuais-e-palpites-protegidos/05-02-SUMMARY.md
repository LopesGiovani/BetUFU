---
phase: 05-cr-ditos-virtuais-e-palpites-protegidos
plan: "02"
status: complete
completed: 2026-09-13
---

# Plan 05-02 Summary

Implemented the first atomic prediction-acceptance slice.

## Delivered

- V30 creates immutable `prediction` and `prediction_selection` records with
  identity/market and idempotency uniqueness, decimal checks, stable snapshots,
  and append-only triggers.
- `OddsForPredictionPort` exposes only the market facts betting needs; its JDBC
  adapter remains inside `odds.internal`.
- `PredictionAcceptancePort` and `PredictionTicket` are public Modulith
  contracts. `PredictionEligibilityPolicy`, repository, and service remain
  internal to `betting`.
- Acceptance validates enrollment, administrative conflicts, stale odds,
  deadline, selection and stake; computes return/profit with scale 2 HALF_UP;
  debits the real ledger and replays the same idempotency key.

## Verification

- `node scripts/maven.mjs -f backend/pom.xml -Dtest=PredictionAcceptanceIT test` - 2/2.
- `node scripts/maven.mjs -f backend/pom.xml -Dtest=PredictionConflictIT test` - 2/2.
- `node scripts/maven.mjs -f backend/pom.xml -Dtest=ModulithArchitectureTests test` - 6/6.
- `git diff --check` - clean.

## Git

- Implementation commit: `2b94335`.
- Documentation commit: pending in this block.
