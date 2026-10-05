---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '07'
wave: 4
status: complete
subsystem: chat-push
requires: [07-05]
provides: [session-guarded-stomp, aftercommit-chat-invalidations]
affects: [07-08, 07-09, 07-10, 07-13, 07-16, 07-17, 07-20]
requirements-completed: []
actuals:
  tasks: 2
  commits: 2
tech-stack:
  added: []
  patterns: [stomp-read-only, aftercommit-invalidation, full-resynchronization]
coverage:
  - id: chat-push-security-delivery
    description: Real WebSocket/filterchain, Redis sessions and disposable PostgreSQL
    verification:
      - kind: integration
        ref: backend/src/test/java/br/com/betufu/chat/ChatPushSecurityIT.java
        status: pass
      - kind: integration
        ref: backend/src/test/java/br/com/betufu/chat/ChatPushDeliveryIT.java
        status: pass
    human_judgment: false
started: '2026-10-05T02:43:36.496Z'
technical_commit: f6a9869
---

# Plan 07-07 Summary

The separate read-only STOMP1.2 endpoint `/api/v1/chat/push` delivers minimal
postcommit invalidations. Existing technical `/ws` remains unchanged. Readers
must fully resynchronize after initial subscription, reconnection and each hint;
PostgreSQL identity sequence allocation does not order transaction commits.

## Authority and scope

- Base cd7775f0a1df81b911e83a6e57b7a4d9e37b781f, inline master.
- Continuous authority: conversation:2026-10-04:owner-continuous-phase7-wave2-through-completion.
  Covers implementation, tests/self-healing, local stage/two commits and docs.
- Protocol choice: conversation:2026-10-04:owner-protocol-stomp, actual owner
  reply recorded in 07-03-SUMMARY. Registry evidence references that summary.
- Ten changed technical paths within amended eleven-path allowlist. Registry
  and PLAN are explicit bookkeeping additions; ValidActivityFilter is unchanged.
- All245 immutable baseline hashes match, including owner documents and V1-V38.
  Owner changes excluded. No dependencies, push, worktree, Antigravity or data reset.
  Approval closeout labels inherited from06 were corrected to07 before freezing.

## Delivery

- Exact configured Origin and no Authorization handshake; SESSION cookie resolves
  through real SessionRepository. CONNECT must match the XSRF cookie even for visitors.
- Global requires current OIDC authentication. Anonymous subscribers can read only
  genuinely published events; aliases, drafts and arbitrary destinations fail closed.
- Read-only CONNECT/SUBSCRIBE/UNSUBSCRIBE/DISCONNECT; SEND, bodies and credentials
  refused. Session expiry/deletion and changed context/authorities cut connections.
  Store reads and transport heartbeat do not save/touch idle activity.
- One subscription,16KiB frames,256 connections,4 per session/anonymous IP,
 256 queued delivery tasks,64KiB output buffer and1s decorator send limit.
  Guard runs separately from outbound I/O; heartbeat work is coalesced.
- Domain producers call changed/changedForAccount inside their transaction;
  callbacks run only after commit. Rollback is silent, accounts/channels isolated.
- Hints contain kind/event/public state/deadline/advisory sequence and
  resyncRequired=true, no message text or private identity.

## Verification

| Command/evidence | Result |
| --- | --- |
| node scripts/validate-phase7-approvals.mjs --plan 07-07 --stage implementation | APPROVED at original HEAD, repeated before commit |
| node scripts/maven.mjs -f backend/pom.xml "-Dtest=ChatPushSecurityIT,ChatPushDeliveryIT" test | Final15/15:9security +6delivery;0fail/error/skip |
| Architecture/smoke additional focal | 7/7,0fail/error/skip |
| node --test scripts/*.test.mjs | 916/916,9suites;255138.9016ms;0fail/cancel/skip/todo |
| node scripts/maven.mjs -f backend/pom.xml verify | BUILD SUCCESS,0fail/error/skip |
| Fresh XML since2026-10-05T03:13:22.588Z | Surefire361/37classes +Failsafe649/127classes =1010 |
| ChatPush XML inside aggregate | Delivery6/6,8.311s;Security9/9,4.964s |
| Scoped spotless apply/check | PASS; exact owned absolute-path regex only |
| Scoped staged diff check and frozen hashes/index | PASS,10paths unchanged through commit |

Wrapper2020 duplicates XML totals;1010 above is authoritative. Initial RED was
a missing-class compilation failure, not a behavioral RED certification.
Tests use real Redis/session/filterchain/WebSocket with explicit fixture OIDC
authentication; they do not certify a live IdP browser login or browser journey.

## Self-healing and frozen review

- Corrected Spring7 HttpHeaders access, fixture approved activity marker and
  actual PUBLICADO status. Production refusal of a draft fixture was correct.
- Early full verify found non-exposed platform.session.SessionPolicy dependency.
  Removed that import and derived expiry from public Spring Session interval.
  Architecture and literal chat focal repeated green; final full verify green.
- Extracted protocol/delivery/guard helpers. Absolute regex spotlessFiles matches
  Windows absolute paths; unsuccessful relative/glob attempts wrote no files.
  Final SecurityConfiguration change normalized five line endings after aggregate
  compilation; standalone spotless:check passed. No semantics changed afterward.
- RepoWise live change review analyzed7/10files, manually reviewed other3.
  Eight new health markers,0worsened: cohesion/complexity, bounded per-frame I/O
  and ignored closeIOException. No functional finding in frozen inline review.
  Its24independent-method LCOM claim does not match actual shared state/calls;
  this is not treated as a functional verdict or a measured health improvement.
  Validation complexity and class size remain maintenance risks.
- Frozen record stored locally at .git/gsd/phase7/07-07-frozen.json;
  HEAD/index/path hashes checked immediately before technical commit.

## Limits and continuation

No durable notification journal, production capacity/SLO, browser teardown flow
or automatic wiring of future producers is certified. Every hint requires full
resync; history APIs alone are not commit-order-safe incremental replay.
AVAILABLE/deadline=null are inherited read-foundation values until07-10.
Anonymous-IP connection cap can affect multiple visitors behind NAT.
Future09/10/13/16/17/20 must invoke domain notification hooks.

Phase7 is8/35plans complete, not certified. Continue07-09 in Wave4 under
continuous authority, then08/10 in Wave5. Legal/institutional/public-operation
gates remain pending. Approval stays historical after HEAD advances.
