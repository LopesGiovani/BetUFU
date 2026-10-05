---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '09'
wave: 4
status: complete
subsystem: api
requires: [07-04, 07-05]
provides: [eligible-chat-writes, immutable-idempotency-receipts, actor-wide-rate-limit]
affects: [07-10, 07-11, 07-12, 07-17, 07-20]
requirements-completed: []
actuals:
  tokens: 24678
  tasks: 4
  commits: 2
tech-stack:
  added: []
  patterns: [consumer-owned-eligibility-port, actor-row-lock, immutable-receipt, postcommit-push]
coverage:
  - id: authenticated-idempotent-writing
    description: Real HTTP, PostgreSQL and Redis enforce eligible, serialized chat acceptance
    verification:
      - kind: integration
        ref: backend/src/test/java/br/com/betufu/chat/ChatWriteIT.java
        status: pass
      - kind: integration
        ref: backend/src/test/java/br/com/betufu/chat/ChatWriteConcurrencyIT.java
        status: pass
    human_judgment: false
technical_commit: 63bd92977a16e9232683fae6f05501fae6c44972
completed: '2026-10-05'
---

# Plan 07-09 Summary

Authenticated POSTs accept chat messages only with current ATIVO enrollment.
V40 records immutable actor/key/message receipts and separates them from the
mutable actor-wide limiter. Accepted retries return the original acknowledgement.

## Authority and scope

- Continuous owner reference: conversation:2026-10-04:owner-continuous-phase7-wave2-through-completion.
  Direct master, base9196453f4119b99be426e7d473452f2ed16e2b7a; local stage,
  implementation/documentary commits and progression authorized. No push.
- Fifteen technical paths reconciled in PLAN/approval. Unicode code points and
  a three-second interval across all actor channels are delegated executor choices;
  no separate human answer is asserted. Candidate and published V40 are identical:
  1c4fd6192ca7ff0192de2c56f31de8bc3543e5436cb505fa6429cf0b6f1a79f4.
- All245 protected hashes match. V1-V38, owner documents, generated client/API,
  owner database/volumes/seeds and dependency manifests preserved.
- Order deviation: test authoring/first RED preceded advancing the record from
  prepare-candidates. Continuous owner scope already authorized tests; stage and
  validator were reconciled before governed publication/production work.

## Delivered

- POST /api/v1/chat/global/messages and /events/{eventId}/messages require an
  actual current Redis OIDC session, exact issuer/subject and ATIVO enrollment.
  Administrative roles do not replace enrollment. Actor is never supplied by body.
  Verification owns its adapter to the chat-owned public eligibility port.
- Exact JSON text plus canonical UUID Idempotency-Key. Up to280 Unicode code
  points, without normalization/trimming; blank, controls, excess and unpaired
  surrogates refused. Text including XSS syntax is stored as plain historical text;
  safe browser rendering belongs to the consumer.
- PostgreSQL actor locking serializes keys and channels. Accepted replay precedes
  current enrollment/availability checks but follows current session revalidation.
  Different text/channel with the same key conflicts. Session checked after the
  actor lock and again immediately before insertion; no Redis/PostgreSQL atomicity
  across substrates is claimed.
- Exactly one new acceptance per actor per3seconds. Authoritative429 ProblemDetail
  includes retryAt and rounded Retry-After. Reconstructing a missing limiter recovers
  the last timestamp from immutable history. Failure/rollback writes no receipt,
  message or limiter debit; push is delivered only after commit.
- Receipt UPDATE/DELETE statement triggers and runtime TRUNCATE/mutation denial.
  Message immutability remains in V39. ChatWriteGuard exposes future transaction
  checks for lifecycle, sanctions and pauses; an empty guard list does not certify them.
- Successful writes renew valid idle activity. Bounded64 local stripes serialize
  same-session chat handling and response-triggered Redis saves. PostgreSQL protects
  cross-session actor integrity; stripes are single-instance coordination only.

## Verification

| Evidence | Final result |
| --- | --- |
| Literal candidate/implementation approval and candidate hash commands | APPROVED; exact digest before publication |
| Initial discriminating RED | 9tests:8failures,1error; absent routes/tables |
| Literal ChatWriteIT,ChatWriteConcurrencyIT after final nesting/clean | 13/13,0fail/error/skip |
| Combined chat,Flyway,Odds,architecture,smoke after clean | 45/45,0fail/error/skip |
| node --test scripts/*.test.mjs | 916/916,9suites,0fail/cancel/skip/todo;266398.1994ms |
| node scripts/maven.mjs -f backend/pom.xml verify | BUILD SUCCESS,exit0; fresh Surefire361/37classes + Failsafe662/129classes =1023,0fail/error/skip |
| Scoped formatting, approval, staged diff/hash/baseline checks | PASS; exact15paths |

Final Maven began2026-10-05T04:09:38Z, session65400. Four older focal Surefire
reports are excluded; their classes have fresh Failsafe reports. Wrapper2046
double-counts summaries. Evidence/freeze: .git/gsd/phase7/07-09-evidence.json and
07-09-frozen.json. OIDC principal creation is a fixture, not a live IdP/browser proof.

## Self-healing and review

- Reproduced same-session Redis optimistic-save conflict under six concurrent
  retries; bounded response/activity serialization resolved it. Six identical
  acknowledgements yield one fact; distinct devices/channels yield201+429.
- Reproduced Odds expected40/actual41 and five Flyway stale total counters.
  Total assertions now measure pending migrations while preserving minimum40,
  historical per-version assertions, integrity, idempotency and concurrency.
  No applied SQL changed. First full verify was intentionally interrupted after
  those five confirmed failures; only its validated task-owned process tree stopped.
  It is not counted as a completed aggregate. Final clean/focal/full suite passed.
- PostgreSQL stats snapshot cleared in the race fixture to observe real lock waits.
  Session deletion or enrollment release while blocked refuses acceptance; deleted
  session is not resurrected. Rollback emits no notification.
- Ack and independently injected static JDBC repository are nested in the controller
  and service, keeping15paths with migration self-healing/bookkeeping. Clean removed
  the obsolete standalone repository bytecode before final verification.
- Frozen inline review found no functional defect. RepoWise analyzed12/15paths,
  introduced16 health heuristics and worsened0. The critical conditional marker is
  the session guard's seven boolean operators; null/short-circuit checks inspected.
  Cohesion heuristics conflict with actual shared fields/calls. Complexity and nested
  persistence organization remain maintenance costs, not independent security approval.

## Limits and continuation

No lifecycle/sanction/pause certification, distributed session coordination,
browser writing journey, production capacity/SLO or public-pilot/legal approval.
Phase7 is9/35. Wave4 complete; continue07-08 then07-10/Wave5 under continuous
authority. CHAT-01/02 and RT-01 remain phase-level pending.
