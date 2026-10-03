---
phase: 06-resultados-confirmados-rankings-e-grupos
status: passed
updated: 2026-10-03
---

# Phase 6 Validation

Technical commit: 0a5f02587e77535e42d43c0e407e380249bb41d8.
Base: bf31d518a69333d85636f0169952cc42069fedc9; direct inline master.
All mandatory final gates passed; earlier failed attempts are superseded.

## Final Gates

| Command / gate | Literal result |
|---|---|
| `node scripts/maven.mjs -f backend/pom.xml clean verify` | BUILD SUCCESS; Surefire 361/361, 37 classes; Failsafe 613/613, 123 classes; zero failures/errors/skips |
| `node scripts/maven.mjs -f backend/pom.xml "-Dtest=Phase6ConcurrencyIT,Phase6CertificationIT,Phase6PrivacyIT,OddsReplayCorrectionTest,ModulithArchitectureTests" test` | Final focal 20/20 XML; zero failures/errors/skips |
| Maven spotless:apply then spotless:check | PASS; final check repeated after lifecycle |
| `npm --prefix frontend test -- --watch=false` | Final repeat 455/455 in 75 files |
| Angular group-page focal | 9/9; three new native-submit tests failed before FormsModule fix |
| `npm --prefix frontend run generate:api:check` | Final repeat PASS; no generated output change |
| `npm --prefix frontend run lint` | Final repeat PASS |
| `npm --prefix frontend run build:production` | Final repeat PASS; existing CSS-budget warnings |
| `node --test scripts/*.test.mjs` | 812/812, 9 suites; zero failures/cancelled/skipped/todo |
| `node --test scripts/phase6-e2e.test.mjs scripts/validate-phase6.test.mjs` | Final repeat 47/47: 16 isolation, 31 source regression/mutation |
| `node scripts/validate-phase6.mjs` | PASS: five closed schemas, two specs plus setup scan, V33-V38 normalized hashes, route boundaries; source proof only |
| `node scripts/phase6-e2e.mjs` | 16/16 real Chrome, desktop/320px; zero skips/retries; 52.0 seconds excluding setup |
| Scoped Prettier and `git diff --check` | PASS; LF/CRLF warnings are not whitespace errors |

Focal: concurrency 3, certification 2, privacy 2, odds 10, Modulith 3.
Maven wrapper repeats count lines; XML aggregate is 974, not 1948.
Final clean verify includes formatting, tests and packaging.

## Real Browser Matrix

| Journey / state | Desktop | 320px | Evidence |
|---|---|---|---|
| Arena link -> populated lifetime ranking -> refresh/back/forward | PASS | PASS | Nonempty rows, public field allowlist, retained inactive participant |
| Account -> completed history -> calculation/ledger -> achievements/rules -> profile | PASS | PASS | Genuine owner facts required, discoverable links |
| Superseded fact -> spent-payout recovery -> pending correction -> revoked award -> refresh | PASS | PASS | Actual API-produced facts, no empty-state substitute |
| Create group -> separate nonmember404 -> invalid/valid invite -> join -> metrics -> cancel/close | PASS | PASS | Actual POST, two OIDC sessions, lifetime-equivalent metrics and restored Escape focus |
| Anonymous history -> contextual login; API401; focused404 -> Arena | PASS | PASS | Real backend denial and frontend routing |
| Network throttle -> loading/aria-busy -> populated table | PASS | PASS | Actual Chrome transport, no response substitution |
| Offline -> error -> online reload | PASS | PASS | Real network failure/recovery |
| Isolated Redis expiration -> API401 -> refresh/login | PASS | PASS | Private history removed; not natural eight-hour waiting |

Global setup also verifies empty ranking at both widths before producing
results, exact-version authorization UI with Escape cancellation, missing
version409, nonadministrator403, identical authorization retry with unchanged
balance, actual grant/revocation and spent-payout pending recovery. A proposing
administrator gets self-approval403; a distinct administrator approves through
genuine browser-session HTTP, twice with one credit effect. Setup assertions
are not extra test cases added to the 16-count.

## Requirement Evidence Index

All Phase 6 IDs assigned in ROADMAP are accounted for. Predecessor tests are
included in the final clean verify; not every state has a separate browser case.

| Requirements | Evidence |
|---|---|
| CRED-07, PRED-09, SETL-09 | SettlementPresentationIT/ControllerIT, Angular history and real populated history/calculation/ledger references/refresh |
| CRED-09 | CreditCorrectionIT/EvidenceIT/SecurityIT/ConcurrencyIT/ControllerIT; real distinct administrators, self-denial and one-effect approval retry |
| SETL-01, SETL-02, SETL-03 | SettlementContractTest/SchemaIT/ExecutionIT/ControllerIT, Phase6CertificationIT exact/null/stale/permission; real exact-version authorization and retry |
| SETL-04, SETL-05, SETL-06, SETL-07, SETL-08, SETL-10 | SettlementExecutionIT/RecoveryIT/ConcurrencyIT, SettlementLedgerIT, CreditCorrectionIT/ConcurrencyIT, Phase6ConcurrencyIT; refund/future-credit recovery and preserved WON history |
| SETL-11, ODDS-15 | OddsReplayCorrectionTest 10/10 including exact cancellation cutoff and corrected future replay; real corrected history, ranking recomposition and revocation |
| RANK-01, RANK-02, RANK-03, RANK-04, RANK-05, RANK-06, RANK-07, RANK-08 | RankingSchemaIT/CalculationIT/PrivacyIT/ControllerIT, concurrent rebuild/refund exclusion; populated public ranking and retained inactive legitimate win |
| POOL-01, POOL-02, POOL-03, POOL-04 | PrivateGroupIT/AuthorizationIT/MetricsIT, GroupControllerIT, Phase6PrivacyIT; real invite/join/close, nonmember404, shared metrics and no group credit effects |
| ACHV-01, ACHV-02, ACHV-03 | AchievementIT/IdempotencyIT/RevocationIT/ControllerIT; concurrent one-lineage grant/revoke, nonempty DTO serialization and real profile/rules journey |
| TEST-04 | Four-worker PostgreSQL races and aggregate settlement/ledger/prediction invariants; not production launch-load/SLO certification |

REQUIREMENTS.md checkboxes/Pending cells were outside the documentary allowlist
and were not edited. Later scoped reconciliation should update tracking without
rewriting requirements or claiming a load benchmark.

## Guards, Preservation And Reproduction

Mutation tests reject added PII fields, permissive schemas, missing routes,
guards/endpoints/migrations, migration drift, mocked responses, injected auth,
credential/UUID literals and skipped cases, including hidden setup shortcuts.
Isolation tests reject shared/external volumes, host networks, owner ports,
public binds, writable host mounts and runtime ownership-label mismatches.
These finite source patterns are not a complete AST/privacy proof.

The runner seeds only substrate and genuine Keycloak users on fresh owned
resources; business facts are made through real APIs. An isolated enrollment
update makes the athlete inactive without altering predictions/settlements.
No SQL-filled WON/REVOKED/correction business outcome was used.

Final run betufu-phase6-e2e-ab539bbc8557f51c cleaned its containers/network/volumes
and generated private configuration. Owner query: V38, predictions2 and
settlements/corrections/awards/groups0, with seven healthy owner services.
No labelled isolated runtime resources remain. Fourteen image tags from seven
runs remain; tmp/CLI artifacts and owner resources were preserved.

Reproduce sequential Maven clean verify -> Angular production build ->
node scripts/phase6-e2e.mjs. Prebuilt JAR/dist, approved dependencies/Chrome,
Docker and free loopback ports4300/8281 are required. No owner-volume reset.

No natural eight-hour idle wait, external pentest, launch-load benchmark or
comprehensive WCAG/screen-reader certification is claimed. Correction operations
are HTTP-tested, not a new editor UI. Read caps, projection costs, script
complexity and CSS budgets remain maintenance/scale risks. No push, dependency
update, historical migration, OpenAPI/generated edit, worktree or Antigravity.
