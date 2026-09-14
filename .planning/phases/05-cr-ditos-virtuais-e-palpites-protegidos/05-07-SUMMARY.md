---
phase: 05-cr-ditos-virtuais-e-palpites-protegidos
plan: "07"
status: complete
completed: 2026-09-13
---

# Plan 05-07 Summary

Implemented retroactive virtual-credit grants and deterministic, atomic
prediction acceptance under retries and concurrency.

## Delivered

- V32 creates missing credit accounts for active enrollments, appends the
  established `1000.00` initial grant exactly once and increments only balances
  whose ledger fact was newly inserted. No enrollment value or other PII is
  copied into the ledger.
- `PredictionCommandDigest` produces the ratified lowercase SHA-256 over the
  NUL-separated UTF-8 command payload, excluding the idempotency key and
  normalizing the stake to two decimal places.
- Prediction retries are serialized by a transaction-scoped PostgreSQL advisory
  lock. An identical replay returns its persisted ticket before mutable market
  checks; a divergent payload fails with zero new effects.
- New acceptance locks and revalidates the authoritative market snapshot,
  competition scope, eligibility, lineage, rule, deadline, selection, stake and
  account balance in a fixed order.
- Prediction, selection and ledger debit commit in one Spring transaction. The
  preallocated `predictionId` is also the debit `sourceId`, and failures roll
  back all new facts.
- PostgreSQL concurrency tests prove one receipt for identical retries, one
  winner for divergent/same-market races and no negative balance for concurrent
  markets sharing an account.

## Verification

- Focal Maven/Testcontainers command completed with BUILD SUCCESS.
- Authoritative Surefire XML count: 36 tests, 0 failures, 0 errors and 0 skipped
  across `FlywayMigrationIT`, prediction acceptance/conflict/concurrency,
  ledger integration/concurrency and `ModulithArchitectureTests`.
- The Maven wrapper displayed 72 because its streaming summary counted the same
  Surefire results twice; the XML reports are the authoritative count.
- Scoped `spotless:apply` and `spotless:check` completed with BUILD SUCCESS.
- `node --test scripts/*.test.mjs` and `git diff --check` passed.
- Repository-wide `maven verify` executed 327 tests with two failures, both the
  preexisting `PrivacyArchitectureTest` rule against the unchanged and
  out-of-allowlist `PredictionEligibilityPolicy`. Running that guard on base
  `ecf7cb7` reproduces exactly 2 failures in 15 tests, proving it is not a
  regression introduced by this plan.

## Migration Integrity

- V29 SHA-256: `D84F77372EAF859E25B09EE976FA52AB16596EA65DC522C349AF8714ECA64E61`.
- V30 SHA-256: `BCB9731DAC52FE362EE4381FF58AA784AF73B6F1C3536B29A2CC0443B0482C4D`.
- V32 SHA-256: `850EB3DE83BD308D2B9E301BBC9F1BC6E2485653DF3FC7343B6C548A125F91D7`.
- V29 and V30 remained byte-identical to the approved base.

## Issues Resolved During Execution

- PostgreSQL rejects NUL characters in `text`, so the idempotency lock uses the
  two-integer `pg_advisory_xact_lock` overload over separate identity/key hashes
  instead of concatenating the digest preimage.
- A stale-selection fixture originally used a published AWAY selection and was
  corrected to DRAW, which is intentionally absent from that two-way snapshot.

## Git

- Base and recorded plan commit: `ecf7cb749332263b421cb19cc598311697eba879`.
- Technical commit: `f8d9510`.
- The documentation commit carries this summary; its SHA is intentionally not
  embedded because a commit cannot contain its own final hash.
- No dependency, OpenAPI, generated client, frontend or infrastructure file was
  changed. No push was performed.

## Next

Plan 05-05 final certification and human UAT remain the last Phase 5 gate. The
repository-wide privacy guard debt should be handled in a separately approved
scope because its only finding is outside the 05-07 allowlist.
