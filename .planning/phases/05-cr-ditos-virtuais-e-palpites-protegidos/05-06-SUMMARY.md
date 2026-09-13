---
phase: 05-cr-ditos-virtuais-e-palpites-protegidos
plan: "06"
status: complete
completed: 2026-09-13
---

# Plan 05-06 Summary

Implemented complete immutable odds publications and the authoritative snapshot
consumed by prediction acceptance.

## Delivered

- V31 creates `odds_publication_selection` with closed HOME, DRAW and AWAY keys,
  positive `numeric(20,10)` prices and append-only database protection.
- Legacy publications are backfilled fail-closed. The legacy publication column
  remains authoritative for HOME, while DRAW and AWAY come from the persisted
  calculation snapshot.
- Deferred constraint triggers reject commits containing missing, partial,
  excessive, duplicated, misordered or divergent selection vectors.
- `OddsMarketRepository` appends a publication and its full vector in one
  transaction without changing the existing publication method contract.
- `OddsForPredictionAdapter` locks `odds_market` with `FOR UPDATE`, resolves the
  nearest published version through `supersedes_version_id`, and exposes the real
  `competitionId`, head version, publication, algorithm version and selections.
- The public `OddsForPredictionPort` remains the only boundary consumed by the
  betting module; no cross-module `internal` import was introduced.

## Verification

- Focal Maven/Testcontainers command: BUILD SUCCESS.
- Surefire XML: 38 tests, 0 failures, 0 errors and 0 skipped across the eight
  requested classes; `ModulithArchitectureTests` passed 3/3.
- The Maven wrapper displayed 76 because its summary aggregation duplicated the
  class count; the individual Surefire XML reports are the authoritative count.
- Scoped Google Java Format check passed for all eight changed Java files.
- Repository-wide Spotless remains blocked by 24 pre-existing files outside the
  plan allowlist; none was modified to hide that condition.
- `git diff --check` passed and independent read-only review returned PASS with
  no material findings.

## Migration Integrity

- V25 SHA-256: `216FC7DCD426A08EE391B5F3DA7D23303BFE93268143DE4F53ED7D1C1D18850F`.
- V29 SHA-256: `D84F77372EAF859E25B09EE976FA52AB16596EA65DC522C349AF8714ECA64E61`.
- V30 SHA-256: `BCB9731DAC52FE362EE4381FF58AA784AF73B6F1C3536B29A2CC0443B0482C4D`.
- V31 SHA-256: `1DB6E1CE197DFF9F12ED9F293B78956BBFD9DE8E6E17772B07BC66662A4AB204`.
- V25, V29 and V30 remained byte-identical to the approved base.

## Git

- Base: `a98364d0f06a43084ede8bf6b095151356509d09`.
- Technical commit: `67f0aed8f70973b7169caf07b3e7f6a0f8e9bb32`.
- The documentation commit carries this summary; its SHA is intentionally not
  embedded here because a commit cannot contain its own final hash.
- Technical and documentation commits were integrated linearly into `master`.
- Worktree `05-06`, branch `worktree-agent-05-06` and the approved integration
  stash were removed after integration.
- No push was performed.

## Next

Plan 05-07 owns V32, the retroactive initial grant, canonical prediction
idempotency and fully atomic prediction/ledger acceptance.
