---
phase: 06-resultados-confirmados-rankings-e-grupos
plan: "04"
status: complete
completed: 2026-10-02
technical_commit: 452993a6831b58d726be7d0565f08fb96f31574c
---

# Plan 06-04 Summary

Implemented a reconstructible lifetime ranking derived only from the latest complete, decisive prediction settlements.

## Delivered

- V36 creates a mutable ranking read model, not a new financial or sporting fact. A transactional rebuild serializes concurrent rebuilds and replaces its rows from completed settlement decisions; two reads converge to the same metrics. V36 SHA-256: `F35EACC28CE465D5EBDDBECA9522B0112D6B6876B678A06A8E70280787A7D835`.
- Consumer-owned public ports in `ranking` receive current settlement decisions, enrollment validity at acceptance and current status, and the chosen public nickname through producer-side adapters. Ranking production code imports no other module's `internal` package.
- Definitive `WON` and `LOST` states alone determine wins, losses and hit rate. Stake and odds cannot multiply wins. Ordering is wins descending, then exact hit rate descending; remaining ties share competition position (1, 2, 2, 4). Refunded, annulled and incomplete cases do not count. A newer incomplete case hides a superseded result until processing completes.
- Released enrollment retains legitimate historical statistics and position. The public entry contains nickname, counts, rate and a boolean `verificationActive`; it contains no identity UUID, enrollment row, HMAC, email, provider subject or private release reason. The same query port exposes member metrics for later private-group composition; HTTP and Angular UI remain in 06-07.

## Verification

- Requested focal ranking/schema/privacy/Modulith command passed 16/16 after clean compilation. An expanded focal run including identity isolation and privacy architecture passed 50/50.
- `node scripts/maven.mjs -f backend/pom.xml clean verify` passed. XML reports show Surefire 360/360 across 37 classes and Failsafe 578/578 across 108 classes, with zero failures, errors or skipped cases. The wrapper's 1,876 count duplicates Maven summaries; the 938 XML cases are authoritative.
- `node --test scripts/*.test.mjs` passed 759/759 across 9 suites. `spotless:apply`, `spotless:check`, staged `git diff --check` and final diff checks passed. Flyway V36 ran only in isolated Testcontainers PostgreSQL, not the owner's persistent local database.

## Reconciliation And Remaining Work

- The initial plan listed four differently named focal classes, but the owner's execution approval named `RankingSchemaIT`, `RankingCalculationIT` and `RankingPrivacyIT`. The plan's allowlist and verify command were reconciled with those names and with the necessary source-owned adapters.
- Aggregate tests exposed an identity-module test context without JDBC and a privacy vocabulary rule. The nickname adapter now tolerates missing JDBC at context creation but fails if invoked without it, the public boolean is named `verificationActive`, and the identity dependency expectation includes the approved public ranking port. Flyway and odds migration tests were advanced from 36 to 37 applied migrations and explicitly assert V36.
- The ranking rebuild currently runs on read for immediate correction visibility. This favors correctness and simplicity in the MVP but may require batching or an event-driven refresh if the number of predictions grows substantially. Public HTTP, Angular, private groups and achievements remain assigned to 06-05 through 06-07; no later plan was started.
- Technical commit `452993a6831b58d726be7d0565f08fb96f31574c` contains 20 approved technical/plan paths. The owner's `docs/development.md`, `docs/local-seed.md` and `docs/manual-testing.md` remain untouched and excluded. No push, Docker reset, local seed or real prediction occurred.
