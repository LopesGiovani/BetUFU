# Phase 5 Validation

Date: 2026-09-13

## Automated Evidence

| Gate | Command | Result |
|---|---|---|
| Ledger | `node scripts/maven.mjs -f backend/pom.xml -Dtest=CreditLedgerIT test` | PASS 4/4 |
| Ledger concurrency | `node scripts/maven.mjs -f backend/pom.xml -Dtest=CreditLedgerConcurrencyIT test` | PASS 4/4 |
| Prediction acceptance | `node scripts/maven.mjs -f backend/pom.xml -Dtest=PredictionAcceptanceIT test` | PASS 2/2 |
| Prediction conflicts | `node scripts/maven.mjs -f backend/pom.xml -Dtest=PredictionConflictIT test` | PASS 2/2 |
| Controllers | `node scripts/maven.mjs -f backend/pom.xml "-Dtest=CreditBalanceControllerIT,PredictionControllerIT" test` | PASS 4/4 |
| Modulith | `node scripts/maven.mjs -f backend/pom.xml -Dtest=ModulithArchitectureTests test` | PASS 6/6 |
| Phase 5 backend | `node scripts/maven.mjs -f backend/pom.xml "-Dtest=Phase5CertificationIT,Phase5PrivacyIT,Phase5ConcurrencyIT" test` | PASS 6/6 |
| Negative harness | `node scripts/validate-phase5-negative-mutations.mjs` | PASS |
| Negative harness test | `node --test scripts/validate-phase5-negative-mutations.test.mjs` | PASS 1/1 |
| Angular focal | `npm --prefix frontend run test -- --watch=false --include ...` | PASS 6/6 |
| Angular full | `npm --prefix frontend run test -- --watch=false` | PASS 366/366 |
| Angular lint | `npm --prefix frontend run lint` | PASS |
| OpenAPI client drift | `node frontend/scripts/generate-api.mjs --check` | BLOCKED: GENERATOR_FAILED |
| Playwright Chromium | `npm --prefix frontend exec playwright test -- --config frontend/playwright.prediction.config.ts --project=phase5-chromium` | BLOCKED: browser executable missing |
| Playwright Firefox | same command with `--project=phase5-firefox` | BLOCKED: app connection refused |

## Requirement Matrix

- CRED-01/02/03/04/05/06/08: PASS by ledger ITs, migration checks, and phase certification.
- PRED-01..08 and INTE-01..04: PARTIAL; acceptance contracts and replay are covered, browser and full real-controller flows remain blocked.
- API-03: PARTIAL; BFF YAML/controllers pass, generated-client anti-drift blocked by missing generator completion.
- ODDS-14/VERI-10/DATA-03..05: PARTIAL; public contracts exist, full cross-module/browser certification awaits services and generated client.

## Known Blockers

1. Approved OpenAPI generator exits with `GENERATOR_FAILED`; generated Angular files were not edited manually.
2. Playwright Chromium executable is not installed and Firefox cannot connect to an application server at `127.0.0.1:4200`.
3. `frontend/test-results/` contains untracked Playwright diagnostics and must be cleaned before any final snapshot.
