---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '10'
wave: 5
status: complete
subsystem: chat-lifecycle
requires: [07-09, 07-05]
provides: [immutable-settlement-completion, definitive-chat-milestones, permanent-chat-closure]
affects: [07-11, 07-13, 07-21, 07-29, 07-32]
requirements-completed: []
actuals:
  tasks: 4
  commits: 2
tech-stack:
  added: []
  patterns: [consumer-owned-ports, producer-owned-adapters, immutable-history, post-lock-clock]
coverage:
  - id: definitive-chat-lifecycle
    description: Real HTTP/PostgreSQL enforce two-hour closure, correction replay and pending recovery
    verification:
      - kind: integration
        ref: backend/src/test/java/br/com/betufu/chat/ChatLifecycleIT.java
        status: pass
      - kind: integration
        ref: backend/src/test/java/br/com/betufu/chat/ChatLifecycleConcurrencyIT.java
        status: pass
    human_judgment: false
technical_commit: 78f3de9
completed: '2026-10-05'
---

# Plan 07-10 Summary

Event chat rejects new writes at the exact two-hour deadline and records a
permanent, immutable close. A completed correction extends an open window;
a correction at or after the previous deadline never reopens it. Global chat
remains independent. Historical messages and accepted acknowledgements remain.

## Authority and scope

- Direct inline master, base79d815738d63484060a0f08c9eafdefbaefc6e7f.
  Continuous owner execution/self-healing/local-commit authority applies.
  Actual owner decision: cancellation closes after confirmation plus2h;
  pending compensation blocks the definitive milestone until complete.
- Original15 technical paths plus ledger recovery adapter, PLAN execution
  extension and lifecycle decision record:18 changed implementation paths.
  Supplemental adapter scope is explicit in PLAN/approval; no validator weakened.
- Candidate and governed V41 are identical, SHA-256
  820aebe54d305b51db5b2e0c3c607234b7d6b0049f4652357be8f0edf9fb5347.
  Both literal preparation and implementation gates passed. Preparation was
  rerun after delimiter self-healing; no separate human digest reply fabricated.
- All47 recorded protected hashes match, including AGENTS/context/research/
  validation, V1-V40 and the owner's three documents. Owner edits excluded.
  No dependencies, owner database/seeds/volumes, push, worktree or Antigravity.

## Delivered

- Public chat-owned ports consume confirmed sports history, accepted predictions
  across every market, settlement completion and ledger recovery evidence.
  Producer adapters read their own persistence; no cross-module internal import.
- V41 creates immutable processing-completion, lifecycle and close facts, plus
  a rebuildable state projection. Completion insertion requires COMPLETE,
  matching counters and all accepted units. Triggers reject UPDATE/DELETE;
  the distinct runtime role has no UPDATE/DELETE/TRUNCATE grant on facts.
- Settlement records completion in the same transaction as its COMPLETE state,
  after event locking and outcome revalidation. Mutable updated_at is not a
  milestone. Pending financial recoveries block it; the last immutable recovery
  allocation contributes the completion timestamp.
- No predictions uses the confirmed outcome. Cancellation needs no score.
  Replay reconstructs every eligible historical milestone; expiration survives
  missing projections, delayed writes and subsequent corrections.
- Real event and projection locks serialize acceptance and closing. Clock is
  checked after the event wait. Acceptance and the three-second limiter also
  use a refreshed clock, avoiding backdated sends after contention.
- Expected HTTP409 commits closure through the outer write transaction using
  noRollbackFor. No message/receipt is accepted on rejection. Other unexpected
  failures retain ordinary rollback behavior; accepted replay stays idempotent.

## Verification

| Evidence | Final result |
| --- | --- |
| Literal ChatLifecycleIT,ChatLifecycleConcurrencyIT | 14/14;0failure/error/skip |
| Remove outer noRollbackFor, then restore in finally | Expected RED:11tests,4failures,1error; exact boundary loses close fact |
| Backdate acceptance clock, then restore in finally | Expected RED:3tests,1failure;201 instead of429 |
| node --test scripts/*.test.mjs on final production tree | 936/936,9suites;0fail/cancel/skip/todo;312089.1654ms |
| node scripts/maven.mjs -f backend/pom.xml verify | BUILD SUCCESS,exit0; fresh XML361Surefire/37classes +678Failsafe/131classes =1039;0failure/error/skip |
| Angular aggregate | 455/455,75files; final run9.42s |
| Lint, production build, official generator check, Spotless | PASS;13 existing style-budget warnings |
| Frozen stage/hash/approval/diff checks | PASS;18 exact implementation paths |

Final Maven started around15:46:40UTC. The wrapper reports2078 because it
duplicates summaries;1039 comes from fresh XML. Older focal Surefire IT reports
are excluded. Evidence and frozen inline review live under
.git/gsd/phase7/07-10-evidence.json and07-10-frozen.json.

## Self-healing and review

- Replaced initial lifecycle placeholders with real PostgreSQL/HTTP/lock-wait
  proofs. Fixtures use distinct market identities and create the channel before
  holding the event lock, avoiding uniqueness and FK-lock interference.
- JavaScript replacement-string interpretation collapsed SQL dollar delimiters.
  Corrected both V41 copies with apply_patch; Flyway and trigger/grant tests pass.
- Earlier concurrent Maven executions shared target and produced stale/missing
  classes; the earlier clean rebuild repaired those artifacts. Subsequent Maven
  runs were strictly sequential. One aggregate was interrupted for the post-lock
  clock correction; it is not counted as successful certification.
- Angular first timed out in an existing logout test under concurrent load.
  Final complete repeat passed without increasing timeouts or changing its code.
- Frozen inline review found no additional functional defect. RepoWise analysed
  15/18paths, introduced11 health heuristics and worsened1. Complexity16 in
  lifecycle and13 in completion, long fixture helpers and9 constructor dependencies
  remain maintenance costs. Cohesion heuristic conflicts with shared JDBC/helpers.
  This is not an independent security or legal review.

## Limits and continuation

Pre-V41 COMPLETE projections do not contain an immutable completion timestamp.
No backfill invents one from updated_at, job time or restore time. Such legacy
processing is not certified as a definitive milestone; rollout must reconcile
these records before public operation. Deadline presentation and browser writes
belong to later consumers. Migration execution here used disposable databases.

Phase7 is11/35; Wave5 complete, next07-11 then07-13/Wave6 under continuous
authority. CHAT-01/02 and public-pilot/legal/institutional gates remain pending.
