---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '05'
wave: 3
status: complete
subsystem: api
requires: [07-04]
provides: [consumer-owned-chat-ports, public-event-private-global-reads, scoped-keyset-pagination]
affects: [07-06, 07-07, 07-09]
requirements-completed: []
actuals:
  tokens: 21627
  tasks: 2
  commits: 2
tech-stack:
  added: []
  patterns: [consumer-owned-ports, producer-owned-adapters, removal-projection]
coverage:
  - id: secure-chat-reads
    description: Actual filters and PostgreSQL enforce public published-event and private global reads
    verification:
      - kind: integration
        ref: backend/src/test/java/br/com/betufu/chat/ChatReadIT.java
        status: pass
    human_judgment: false
started: '2026-10-05T00:55:55.949Z'
completed: '2026-10-05T01:53:14.847Z'
technical_commit: 3400b4f7aa2df12009183c1067330fbbb81f0fff
---

# Plan 07-05 Summary

Four read endpoints expose published-event chat publicly and global chat only
to an OIDC session. Domain modules communicate through public consumer-owned
ports; the chat repository reads only its own tables.

## Authority and scope

- Base 092afc49039dd13f4bebc2564c5128f7cc681f3d, direct inline master.
- Owner reference: conversation:2026-10-04:owner-continuous-phase7-wave2-through-completion.
  Implementation, tests, self-healing, local stage/two commits and closeout authorized.
- Fifteen technical paths, including the two exact self-healing additions to
  IdentityModuleTest and PLAN. No dependency, protected-doc or V1-V38 changes.
- All 242 baseline hashes intact. Owner changes excluded from both commits.
  No owner database, seed, volume, push, worktree or Antigravity action.
- Started metadata is the recorded approval/preflight timestamp.

## Delivered

- GET /api/v1/chat/events/{eventId}, /events/{eventId}/changes, /global,
  and /global/changes. Only event GET patterns permit anonymous access.
- Sports owns its publication adapter: actual publication required; draft or
  a status-only false publication returns404. Legacy channel creation is
  idempotent and four concurrent reads converge on one channel.
- Global401 includes requests without Accept, text/html, history/changes and
  the global UUID used as an event alias. Authenticated alias returns404.
  Authenticated inactive enrollment may read global. No write route delivered.
- Global private/no-store, event no-store, Vary Cookie. Minimal DTO contains
  public participant UUID/nickname, exact text, timestamp and removal marker.
  Removed projection strips original text and author before materialization;
  response also omits their values while preserving the immutable message fact.
- Descending timestamp/UUID keyset with channel-bound canonical base64 cursor,
  default50/max100 and next-page evidence. Invalid/cross-channel cursors fail400.
  Changes read ascending technical sequence and include removed markers.
- Identity owns its nickname/public-ID adapter. Optional JdbcTemplate provider
  supports isolated module construction; an actual query without DB fails explicitly.

## Verification

- Initial TDD RED: ChatReadIT10 tests,8 expected failures,0errors before production.
- Final literal focal15/15:12 chat +3 architecture; repeated after corrections
  and after cleaning invalid compiled artifacts. Wrapper30 double-counts summaries.
- Combined identity/chat/architecture focus17/17:12+3+2, all green.
- Final Node aggregate916/916 in9suites,0fail/cancel/skip/todo,294552.6864ms.
- Final literal Maven verify BUILD SUCCESS, exit0. Fresh XML Surefire361/37classes
  + Failsafe634/125classes =995,0fail/error/skip. One older focal Surefire
  ChatReadIT report excluded. Wrapper1990 double-counts summaries.
- Final verify started around2026-10-05T01:30:42Z, completed before01:51:42Z. Its temporary
  success log is removed by design; no old failed log used as final evidence.
- Central approval, exact staged scope, frozen hashes and diff check passed
  immediately before implementation commit. Scoped Java formatted with Spotless.

## Self-healing and review

- First aggregate exposed the isolated identity context's absent DataSource and
  obsolete future-chat module assertion. Reused existing optional-provider
  pattern and removed chat alone from the future list; PLAN/approval reconcile scope.
- Next aggregate had3errors from invalid old .class copies in sports, beside
  the correct internal-package classes. Source declarations and javap proved
  package divergence. Maven clean removed backend/target only; literal focal
  and full verify then passed. No source edit or data reset needed for this cleanup.
- Frozen inline review: no functional finding. RepoWise analyzed13/15paths;
  PLAN/approval manually reviewed. Cohesion heuristics contradict actual shared
  fields/calls; residual cursor complexity12, five parameters and existing shared
  security-filter complexity18 documented. No independent security certification.

## Limits and next work

- AVAILABLE is read availability, not write authorization. Deadline remains null
  until07-10. No Angular journey, STOMP stream, rate limiting or moderation API yet.
- Technical sequence is not complete commit-order/moderation replay certification;
  07-07/16 and later certification must address delivery/recovery behavior.
- Requirements remain phase-level pending; this foundation does not complete
  CHAT-01/02 or RT-01. Continue07-06 in Wave4 under continuous authority.
