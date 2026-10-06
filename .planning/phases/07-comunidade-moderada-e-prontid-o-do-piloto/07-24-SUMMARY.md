---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '24'
wave: 9
status: complete
subsystem: privacy-rights
technical_commit: fc9bc80
completed: '2026-10-06'
duration: multi-session
requires: [07-02, 07-17]
provides: [immutable-privacy-cases, owner-private-timeline, audited-administrator-responses]
affects: [07-25, 07-26, 07-27, 07-32]
requirements: [PRIV-05, TEST-03]
requirements-completed: []
actuals:
  tasks: 4
  files: 15
  commits: 2
tech-stack:
  added: []
  patterns: [consumer-owned-ports, append-only-facts, historical-state-reconstruction, post-lock-session-check]
coverage:
  - id: immutable-rights-foundation
    description: V45 facts reject historical mutation through triggers and runtime ACLs
    verification:
      - kind: integration
        ref: backend/src/test/java/br/com/betufu/privacy/PrivacyRightsIT.java#PrivacyRightsIT
        status: pass
    human_judgment: false
  - id: owner-private-requests-and-responses
    description: Real HTTP validates ownership, administrator authority, private responses and concurrent terminal decisions
    verification:
      - kind: integration
        ref: backend/src/test/java/br/com/betufu/privacy/PrivacyRightsIT.java#PrivacyRightsHttpIT
        status: pass
      - kind: integration
        ref: backend/src/test/java/br/com/betufu/privacy/PrivacyRightsIT.java#PrivacyRightsWithoutSessionsIT
        status: pass
    human_judgment: false
---

# Plan 07-24 Summary

V45 introduces immutable privacy requests, responses, export/disposition facts
and a rebuildable state projection. Authenticated owners can request rights and
read their private timeline, including with inactive enrollment or a paused
account. Only a current general administrator can analyze or answer.

## Authority and scope

- Direct inline execution on master, base868d0dee3271ad02863d652b293aafde209b7776.
  Continuous owner authority covers Phase7 implementation, self-healing, tests,
  two local commits per plan and advancement. The owner explicitly ratified the
  administrator-only manual verification procedure for athletes without accounts.
- Fifteen implementation paths: fourteen declared primary paths plus PLAN
  bookkeeping. Exact status/hashes are recorded in the frozen evidence.
  No separate human candidate-digest reply is asserted.
- Candidate and governed V45 are byte-identical, SHA256
  03c95f44919a9b84a6d5cf8c3e833d47895583953ef71e1bb672fb79a68aed33.
- All46 protected hashes match. V1-V44 are unchanged. The owner restored
  development/local-seed and removed manual-testing, then instructed us to ignore
  those documents; baselines were reconciled without modifying or recreating them.
- No dependency change, owner database migration, seed/volume reset, worktree,
  Antigravity or push. Flyway application occurred in disposable test databases.

## Delivered

- Account ownership comes from the real OIDC session, never a supplied owner ID.
  Athlete requests require a general administrator, an existing sports athlete,
  an externally verified channel and a restricted proof reference.
- The privacy-owned public SubjectPort is implemented by identity/sports
  adapters. There is no cross-module internal import.
- Cases, events, exports and dispositions have UPDATE/DELETE triggers and no
  runtime UPDATE/DELETE/TRUNCATE grant. Current state is a disposable projection.
- Timeline reads are owner/general-administrator-only and private/no-store.
  Subject, owner and proof references are not returned. Operational response
  text is required, bounded to2000 Java characters and stored in a new event.
- ANALYZE enters UNDER_REVIEW; REJECT enters REJECTED. APPROVE can fulfill only
  NOTICE here. Other rights return409 until07-27 supplies execution/export
  evidence; an administrator's decision alone cannot complete them.
- A real case row lock serializes terminal decisions. Current session and
  administrator grant are checked again after lock waits. Terminal cases reject
  repeated/conflicting responses; immutable history reconstructs state when its
  projection is absent. Event time is sampled after waits with clock_timestamp().
- Missing SessionRepository permits lean test contexts to start through
  ObjectProvider, but every privacy operation still denies401 without a current
  repository-backed session. There is no permissive or memory-session fallback.

## Verification

| Evidence | Final result |
| --- | --- |
| Literal approval validator | APPROVED at the base HEAD |
| Expanded literal Maven focal | 15/15 in4 classes;0fail/error/skip |
| Maven verify | BUILD SUCCESS;361 Surefire/37 classes +740 Failsafe/145 classes =1101;0fail/error/skip |
| Node aggregate | 936/936;9 suites;0fail/cancel/skip/todo;352496.1605ms |
| Policy/approval validator focus | 71/71;0fail/cancel/skip/todo |
| Spotless, diff checks, exact stage and protected hashes | PASS |

Real HTTP/PostgreSQL/Redis tests cover third-party denial, anonymous access,
CSRF, forged owners, invalid rights, restricted athlete proof, paused owners,
private responses, absent projections, premature access completion, revoked
authority/deleted sessions during observed PostgreSQL lock waits, and concurrent
terminal decisions with one200/one409 and exactly one terminal event.

The historical premature-fulfillment mutation failed with expected409/observed200
and the source was restored. Its recorded restoration hash predates later private
response/session changes; it is historical mutation evidence, not the final source
hash. Final focal tests again prove that guard. Maven wrapper totals double-count
summary lines; only fresh XMLs are authoritative.

## Self-healing and review

- Initial HTTP review corrected missing response text, premature fulfillment,
  historical-state reconstruction and authorization rechecks after lock waits.
- A complete aggregate then failed1100 tests with91 context initialization errors
  across9 existing classes, all caused by the newly required SessionRepository
  in contexts explicitly excluding sessions. ObjectProvider plus the dedicated
  no-session HTTP test resolved the regression. Final verify passed1101/1101.
- Two earlier interrupted aggregate attempts are not certification evidence.
  Normal identity/container waits were allowed to finish in the final attempts.
- Eleven implementation-policy evidence references were reconciled after focal
  expansion, together with policy/decision/approval hashes; policy meaning did
  not change. The dependent approval digest initially blocked and was repaired.
- Frozen staged RepoWise analysis covered9/9 Java files, with16 new heuristics and
  none worsened. Compound session checks, response complexity18 and nested lock
  tests remain maintenance costs. Cohesion warnings do not account for shared
  service/JDBC fields visible in the source. Inline review found no additional
  functional defect; this is not independent legal or security certification.
- Evidence lives in.git/gsd/phase7/07-24-{evidence,frozen,review,mutation}.json,
  alongside the failed aggregate and the superseded pre-session-fix snapshot.

## Limits and continuation

This is the backend/schema foundation. Export, disposition, retention and
pseudonymization execution remain07-27; generated privacy contracts/UI remain
07-25/26. Independent legal review, public-pilot authorization and other external
phase gates remain pending. PRIV-05/TEST-03 are not claimed fully certified here.

Phase7 is20/35, global155/170. Wave9 is complete. Continue directly with07-12,
then07-22 and07-27 in Wave10 under the owner's continuous authorization.
