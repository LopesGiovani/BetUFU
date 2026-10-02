---
phase: 06-resultados-confirmados-rankings-e-grupos
plan: "06"
status: complete
completed: 2026-10-02
technical_commit: 5345c73eec65c3e53be7256e6ca7a2d63aeed388
---

# Plan 06-06 Summary

Implemented derived achievements without a wallet, bonus or credit side effect.

## Delivered

- V38 creates a versioned four-rule catalog, unique awards by person/rule/fact lineage, and immutable revocation audit. An award can only transition from AWARDED to REVOKED; its source, edition and original timestamp cannot be rewritten or deleted. V38 SHA-256: `F7CB6126F937F78A3091C4CE16929AB5C5F24DDE80C173D74BF972EC1A12EAFC`.
- The v1 rules cover three consecutive definitive wins, a winning accepted odd of at least 3.00, three wins in distinct events of one phase, and a winning selection for the favorite organization. Only complete current settlements for predictions accepted during valid verification qualify. Round and streak awards retain the supporting settlement IDs in their origin key.
- Reconciliation grants each origin once. A correction that removes eligibility marks the award REVOKED with a reason and separate immutable record; a later qualifying correction creates a new award without reactivating the old one. Changing the current favorite alone does not revoke an award already earned while its winning settlement remains current.
- `AchievementQueryPort` returns name, description, rule, catalog edition, sporting edition, award time and explicit revocation state for a profile caller. HTTP/OpenAPI/Angular binding remains in 06-07.

## Boundary Reconciliation

- The initial plan placed lookup interfaces in the producing `identity` and `sports` modules and had `ranking` import `SettlementReadPort`. The focal Modulith test exposed dependency cycles because those producers already depend on ranking or settlement. The final design puts public consumer-owned ports in `ranking`, with adapters in `identity`, `sports` and `settlement`; the settlement adapter reads its own module's extended `SettlementReadPort`. No production ranking class imports another module's `internal` package.
- The approved plan allowlist now records these bridge files and the mechanical V38 migration-count updates in `OddsMigrationIT` and `FlywayMigrationIT`. Historical migrations V1-V37 were not modified.

## Verification And Remaining Work

- Final focal `AchievementIT`, `AchievementIdempotencyIT`, `AchievementRevocationIT` and `ModulithArchitectureTests`: 16/16. Targeted migration tests: 54/54. Final Maven `verify` XML reports: Surefire 372/372 across 43 classes and Failsafe 590/590 across 114 classes, with zero failures, errors or skips. Node tests: 759/759. Spotless and staged diff checks passed.
- V38 was applied only in isolated Testcontainers PostgreSQL. No persistent local migration, Docker reset, seed, real prediction or push occurred. `docs/development.md`, `docs/local-seed.md` and `docs/manual-testing.md` were not staged or edited.
- Favorite-organization eligibility is evaluated against the profile's current favorite when first reconciled; the profile has no historical favorite-at-bet snapshot. This temporal limitation should be decided before public UI copy promises attribution at prediction time. Read-time reconciliation scans current facts; ranking metrics are fetched once per batch, but a refresh strategy may be needed at pilot scale.
