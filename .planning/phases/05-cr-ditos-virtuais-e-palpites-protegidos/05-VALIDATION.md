# Phase 5 Validation

Date: 2026-09-14
Snapshot HEAD: `d263f0b63cf36c20dcecb671ddd236d56cc71813`
Branch: `worktree-agent-05-05-recert`

## Automated Evidence

| Gate | Command | Result |
|---|---|---|
| Repository Maven | `node scripts/maven.mjs -f backend/pom.xml clean verify` | PASS. Authoritative XML: Surefire 327/327 and Failsafe 534/534, total 861/861; 0 failures, 0 errors, 0 skipped. The Maven lifecycle completed through Spotless with `BUILD SUCCESS`. |
| Backend recertification focus | `node scripts/maven.mjs -f backend/pom.xml "-Dtest=Phase5CertificationIT,Phase5PrivacyIT,Phase5ConcurrencyIT,CreditLedgerIT,CreditLedgerConcurrencyIT,CreditBalanceControllerIT,PredictionAcceptanceIT,PredictionConcurrencyIT,PredictionControllerIT,OddsCertificationIT,OddsForPredictionAdapterIT,OddsMarketLifecycleIT,OddsMigrationIT,OddsPersistenceConcurrencyIT,OddsPublicViewIT,FlywayMigrationIT,PrivacyArchitectureTest,ModulithArchitectureTests" test` | PASS. Authoritative Surefire XML: 88/88 across 18 classes; 0 failures, 0 errors, 0 skipped. The wrapper printed 176 because it aggregates duplicate Maven summary lines. |
| Modulith boundary | Included in the backend focus and repository Maven commands | PASS 3/3 in `ModulithArchitectureTests`; no cross-module internal-package violation. |
| Repository Java format | `node scripts/maven.mjs -f backend/pom.xml spotless:apply` followed by `node scripts/maven.mjs -f backend/pom.xml spotless:check` | PASS. The configured Google Java Format rules normalized exactly 33 Java files; all 33 are named in the Plan 05-05 allowlist. |
| Negative harness | `node scripts/validate-phase5-negative-mutations.mjs` | PASS 7/7. Negative balance, ledger deletion, selection deletion, grant filter, divergent receipt, source linkage and competition-scope mutations all produced the expected red signature. V29, V30, V32 and `PredictionService` were restored to their pre-run SHA-256 values, and the restored source/bytecode certification passed. |
| Node aggregate | `node --test scripts/*.test.mjs` | PASS 747/747; 9 suites; 0 failed, cancelled, skipped or todo. This includes the negative-harness unit coverage. |
| OpenAPI generator unit | `node --test frontend/scripts/generate-api.test.mjs` | PASS 73/73; 0 failed, cancelled, skipped or todo. |
| OpenAPI client drift | `node frontend/scripts/generate-api.mjs --check` | PASS. Candidate generation matched the governed TypeScript client without replacing the versioned tree. |
| Angular full | `npm --prefix frontend run test -- --watch=false` | PASS 384/384 across 62 files. The first run completed 383/384 because an unrelated spec timed out; that spec passed 3/3 in isolation and the clean full repetition passed 384/384. The count increased from the earlier 382 after adding two stale-refresh fail-closed tests. |
| Angular lint | `npm --prefix frontend run lint` | PASS. |
| Angular production build | `npm --prefix frontend run build` | PASS. `auto-event-hero` is 7.99 kB and the public agenda is below the fatal 8 kB threshold. Existing 4 kB component-style warnings remain non-fatal. |
| Playwright Chromium + Firefox | `npm --prefix frontend exec playwright test -- --config frontend/playwright.prediction.config.ts --project=phase5-chromium --project=phase5-firefox` with `BETUFU_FIREFOX_EXECUTABLE` set to the local Playwright Firefox 155 executable | PASS 14/14. Both browsers cover desktop and 320 px, visitor, own ticket submit/reload, missing enrollment, stale reconfirmation and generic 403 behavior. JUnit report: 14 tests, 0 failures, 0 errors, 0 skipped. |
| Diff and scope | `git diff --check` plus allowlist reconciliation | PASS. 70 changed/untracked paths are covered by the approved final Plan 05-05 allowlist; stage is empty. LF-to-CRLF messages are checkout warnings, not whitespace errors. |

## Corrected Certification Gaps

- The authenticated Angular adapter uses generated `CreditLedgerService` and
  `PredictionService`, preserves cookies/XSRF and sends `Idempotency-Key`.
- Public odds expose the authoritative market version, publication, algorithm,
  deadline and complete two-way/three-way selection vectors.
- Public odds resolve publications only along the ancestry of
  `current_version_id`; a newer publication on a divergent branch is ignored,
  while cycles or multiple publications at the nearest ancestral depth make the
  view unavailable. Both regression tests failed against the prior query and
  pass after the correction.
- Stale submission performs a safe public refresh and requires a new gesture
  only when the same market remains OPEN and eligible. A different `marketId`,
  failed refresh, CLOSED market or SUSPENDED market fails closed without a
  second POST.
- Prediction acceptance revalidates market, competition, deadline, lineage,
  selection, stake and balance atomically, rejects divergent idempotency and
  links `predictionId` bidirectionally to the ledger debit.
- PostgreSQL/Testcontainers proves append-only history, numeric scale/HALF_UP,
  pessimistic locking, rollback and absence of negative balances under races.
- Privacy checks isolate identities and inspect HTTP metadata, bodies, DOM,
  console and Web Storage without exposing PII, tokens or third-party tickets.
- The production build budgets and repository-wide Spotless lifecycle blockers
  identified in the prior snapshot are resolved.

## Requirement Matrix

| Requirements | Status | Evidence and limitation |
|---|---|---|
| CRED-01, CRED-02, CRED-03, CRED-04, CRED-05, CRED-06, CRED-08, CRED-10 | PASS | PostgreSQL/Testcontainers proves grant/replay, fixed scale, HALF_UP, all-in, pessimistic locking, append-only facts and non-negative balance under concurrency. |
| PRED-01, PRED-02, PRED-03, PRED-04, PRED-05, PRED-06, PRED-07, PRED-08 | PASS | Acceptance, deadline/market revalidation, divergent idempotency, competition scope, traceability, rollback and own-ticket replay are covered by backend and browser evidence. |
| INTE-01, INTE-02, INTE-03, INTE-04 | PASS | Prediction and debit persist atomically with shared `predictionId`/`sourceId`; failures and retries leave no partial effects. |
| DATA-03, DATA-04, DATA-05 | PASS | Ledger and prediction facts remain append-only, privacy checks isolate identities, and bidirectional traceability is asserted. |
| ODDS-14 | PASS | Unit and browser evidence prove `409 -> refreshed odd/version -> confirmation cleared -> explicit reconfirmation -> new POST`, with fail-closed handling for invalid refreshes and unavailable markets. |
| API-03 | PASS | OpenAPI anti-drift passes; generated services are used by the SPA; authenticated controller ITs cover the BFF contract. |
| VERI-10 | PASS | All automated repository gates pass, and the responsible developer approved human checkpoint `05-05/T3` on 2026-09-14. |

## Human Checkpoint

There are no remaining automated or human blockers in this snapshot. The
responsible developer approved checkpoint `05-05/T3` on 2026-09-14 after the
corrected snapshot received independent read-only review `PASS`. Phase 5 is
complete. No stage, commit, integration, cleanup or push is implied by this
approval.

## Environment Deviation

The first Angular aggregate run completed 383/384 because an unrelated spec
timed out. The failing spec passed 3/3 alone and the clean aggregate repetition
passed 384/384, so no product failure remains in the frozen snapshot.

The first combined Playwright attempt used a conventional Mozilla installation
path that was absent, so Chromium passed 7/7 while Firefox could not launch.
No product test failed. The local Playwright cache contained Firefox 155; a
Firefox-only run passed 7/7 and the final clean combined run passed 14/14.
