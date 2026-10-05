---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '17'
wave: 8
status: complete
technical_commit: b5f66b551ec0964b3b17d7a385cece404acbcbbe
completed: '2026-10-05'
subsystem: chat-sanctions
requires: [07-16, 07-09, 07-10]
provides: [global-chat-sanctions, private-sanction-reviews, serialized-sanction-acceptance]
affects: [07-18, 07-19, 07-21, 07-31, 07-32]
requirements-completed: []
actuals:
  tasks: 2
  commits: 2
tech-stack:
  added: []
  patterns: [consumer-owned-authority, shared-actor-lock, append-only-review-decisions]
key-files:
  created:
    - backend/src/main/java/br/com/betufu/chat/internal/ChatSanctionController.java
    - backend/src/main/java/br/com/betufu/chat/internal/ChatSanctionService.java
    - backend/src/main/java/br/com/betufu/chat/internal/ChatSanctionRepository.java
    - backend/src/main/java/br/com/betufu/chat/internal/ChatReviewController.java
    - backend/src/main/java/br/com/betufu/chat/internal/ChatReviewService.java
    - backend/src/test/java/br/com/betufu/chat/ChatSanctionIT.java
    - backend/src/test/java/br/com/betufu/chat/ChatReviewIT.java
    - backend/src/test/java/br/com/betufu/chat/ChatSanctionConcurrencyIT.java
  modified:
    - backend/src/main/java/br/com/betufu/chat/internal/ChatModerationModels.java
coverage:
  - id: global-chat-sanctions
    description: Administrator confirmation enforces 24h, 7d and ban across chat channels without financial effects
    requirement: CHAT-05
    verification:
      - kind: integration
        ref: ChatSanctionIT
        status: pass
    human_judgment: false
  - id: private-sanction-reviews
    description: Owners including inactive accounts request review; administrator decisions append history
    requirement: CHAT-06
    verification:
      - kind: integration
        ref: ChatReviewIT
        status: pass
    human_judgment: false
  - id: serialized-sanction-acceptance
    description: Real PostgreSQL lock waits serialize confirmations and writes with session and authority revalidation
    requirement: INTE-05
    verification:
      - kind: integration
        ref: ChatSanctionConcurrencyIT
        status: pass
    human_judgment: false
---

# Plan 07-17 Summary

Only a current general administrator can confirm a global chat punishment.
The server suggests 24 hours, seven days, then a chat ban using historical
confirmed occurrences. Expiration never resets that progression, while an
annulled punishment is excluded. Duplicate reports of the same message share
one case and cannot cause a second punishment.

## Authority and scope

- Continuous owner authorization covers inline implementation, tests,
  self-healing, local commits and advancement through Phase 7. The approval
  record references the actual conversation and does not assert a new reply.
- Direct `master`, base `c4e420a9f4b790a8dbc640fa0f12510ac0eceee7`.
- Ten technical paths changed within the twelve-path PLAN allowlist. The
  existing `ChatWriteService` guard collection and public authority port already
  provide the necessary integration and need no replacement.
- No new migration or dependency. Existing V43 facts, triggers and grants are
  reused. Owner documents, seed data, volumes and governed API/client remain
  preserved. Contracts and browser journeys belong to subsequent plans.

## Delivered

- Strict authenticated JSON endpoints accept server-derived actors and targets;
  no caller-supplied identity can select the punishment recipient. Owner and
  administrator responses use private/no-store caching and omit identity IDs.
- Confirmation checks the suggested kind again under the target actor lock.
  A command-actor advisory lock protects command uniqueness across different
  cases; the existing write-limiter row serializes punishments with chat writes.
  Current session and general-administrator authority are checked after waits.
- A confirmed punishment blocks new writing in every chat until the exact
  expiration boundary. Accepted-message retries retain the established original
  receipt behavior. Reading and private punishment consultation remain available.
- An owner can open a private review, including with inactive enrollment. A
  pending review keeps the punishment in effect. An administrator maintains or
  annuls it by appending a new fact; no historical sanction or review is edited.
  There is one canonical review request and resolution per punishment, with
  command replay and conflicting reuse checked explicitly.
- New facts and case projections commit together. A rollback leaves no
  punishment, progression step or write guard effect. No producer writes credit,
  ranking, prediction or historical message data.

## Verification

| Evidence | Result |
| --- | --- |
| Literal approval validator | APPROVED at the base HEAD |
| Initial discriminating RED | 11 tests; 10 failures and one fixture error; absent routes returned 404 |
| Literal focal after corrections | 14/14, zero failures, errors or skips |
| Maven aggregate | BUILD SUCCESS; XML 1076/1076 (361 Surefire / 37 classes +715 Failsafe / 138 classes), zero failures, errors or skips |
| Node aggregate | 936/936; nine suites; zero failures, cancellations or skips; 293916.7347ms |
| Protected hashes, scope and frozen review | PASS; ten technical paths, V1-V43 and owner documents preserved |

The financial preservation test seeds nonempty credit, ledger, ranking and
prediction facts, then compares their complete rows before and after punishment.
Concurrency tests observe actual PostgreSQL lock waits, confirm one punishment
per occurrence, reject two simultaneous confirmations of the same progression
step, deny revoked authority/deleted sessions after a wait, and deny a write
waiting for a punishment transaction to commit.

## Self-healing and review

- Fixture repairs supplied required ranking losses, corrected the ledger primary
  key, used the existing `/global` read endpoint and removed a nonexistent
  enrollment timestamp column. No historical migration was changed.
- Scoped Spotless formatting touched exactly nine Java files. The direct Windows
  wrapper's shell result reported nonzero despite Maven `BUILD SUCCESS`; Java
  warnings appeared on stderr. Aggregate Spotless verification is authoritative.
- RepoWise's initial uncommitted-change review covered only tracked changes,
  excluding new files. It is partial evidence and does not replace full staged
  review or actual tests.
- The final staged Java review analyzed all nine Java files: twelve introduced
  heuristics, none worsened. Cohesion warnings contradict the shared repository,
  JDBC and access fields visible in the source. Confirmation complexity 11 and
  decision complexity 12 are maintenance costs; inline review found no additional
  functional defect. This is not independent security or legal certification.
- XMLs newer than the final run start are authoritative; the wrapper's 2152
  total double-counts the actual 1076. Closed disposable database pool warnings
  did not cause any failure. Evidence is in `.git/gsd/phase7/07-17-evidence.json`
  and `07-17-frozen.json`.
- Clock advances are kept behind real session time to isolate punishment
  expiration from unrelated idle-session expiration; Redis sessions are real.

## Limits and continuation

Backend foundation only. API generation, account/operator UI, paused-account
integration, browser journeys and final phase certification remain downstream.
Legal retention and public-pilot gates remain pending; no historical content is
expired here. CHAT-05/06 and INTE-05 remain phase-level pending.

Next: plan `07-20`, completing Wave 8 with emergency pause/resume facts.
