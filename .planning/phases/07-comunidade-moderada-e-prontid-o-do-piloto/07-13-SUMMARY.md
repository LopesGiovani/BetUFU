---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '13'
wave: 6
status: complete
subsystem: chat-community
requires: [07-09, 07-07, 07-10]
provides: [private-immutable-reports, account-wide-mutes, versioned-private-resync]
affects: [07-14, 07-15, 07-16, 07-17, 07-20]
requirements-completed: []
actuals:
  tasks: 4
  commits: 2
tech-stack:
  added: []
  patterns: [consumer-owned-ports, canonical-occurrence, immutable-receipts, account-lock, optimistic-version]
coverage:
  - id: private-reports-and-account-preferences
    description: Real HTTP/PostgreSQL/Redis/STOMP enforce privacy, versions, rollback and revocation
    verification:
      - kind: integration
        ref: backend/src/test/java/br/com/betufu/chat/ChatReportIT.java
        status: pass
      - kind: integration
        ref: backend/src/test/java/br/com/betufu/chat/ChatMuteIT.java
        status: pass
    human_judgment: false
technical_commit: 4e261e3b1b40c6d4dc0e62cc4b42a3f5e44b011b
completed: '2026-10-05'
---

# Plan 07-13 Summary

Authenticated channel readers can privately report real messages without write
eligibility. Personal mutes persist across all account channels, synchronize by
owner-only postcommit hints and reject stale device versions.

## Authority and scope

- Inline master, base9b2400b34283d41a658c8b92c380d7ed818be34a. Continuous owner
  Phase7 execution/self-healing/local-commit authority applies. Thirteen primary
  paths plus explicit PLAN/decision-registry bookkeeping: fifteen actual paths.
- Cross-channel mute, synchronization conflicts and canonical report semantics
  are executor choices documented under that authority. No separate human policy
  or candidate digest reply is fabricated. Literal preparation/publication gates
  and candidate hash command passed before governed publication.
- Candidate/governed V42 SHA256:
  720ac40c9febfd112211d228d97e0c18d6d1a61a71a89580342c20797ab90db6.
  All53 protected hashes match, including V1-V41, governed API, manifests and
  the owner's three documents. Owner deltas excluded; no dependencies, persistent
  database/seeds/volume changes, push, worktree or Antigravity.

## Delivered

- V42 creates immutable chat_report and chat_report_receipt facts, indexed and
  protected by UPDATE/DELETE triggers and runtime grants without UPDATE/DELETE/
  TRUNCATE. Composite foreign key binds each receipt to its actual reporter.
  Mutable chat_mute_account/chat_mute preferences are separate from history.
- POST /api/v1/chat/messages/{messageId}/reports accepts only the five closed
  reasons and canonical Idempotency-Key. Session-derived actor, real message and
  current publication access are required. Removed messages remain reportable;
  no original text, reporter identity or reason is returned or pushed.
- One report per reporter/message is canonical. Same reason and fresh key adds
  only an immutable command receipt; accepted retry returns original reportId/
  reportedAt. Changed reason/message under an accepted key returns409. Different
  reporters stay private. Reports never automatically remove or sanction.
- GET /api/v1/chat/me/mutes and /{participantId} return the owner's full private
  version/participantIds list. PUT accepts only expectedVersion; DELETE requires
  quoted canonical If-Match. ETag identifies the returned version. Actual changes
  increment version; no-op retains it. Stale versions return409 and require refetch.
- Preferences cover all chat channels. Target identity is a public participant
  UUID resolved through the existing consumer-owned identity port. No supplied
  owner, internal cross-module imports or cross-module table reads in chat.
- Account locks serialize reports and preference changes. Current issuer/subject/
  session expiry are rechecked after a real lock wait and before mutation returns.
  Reports/mutes do not acquire active enrollment or invoke write guards.
- Preference hints reach only current owner sessions across global/event channels.
  Target/visitor sessions receive nothing; hints include no mute list or identity.
  Rollback leaves no facts/preferences/version debit and sends no notification.

## Verification

| Evidence | Result |
| --- | --- |
| Before producer/schema publication | RED11tests,10failures/1error; routes404 and absentV42 |
| Literal ChatReportIT,ChatMuteIT final | 15/15;8report +7mute;0failure/error/skip |
| Real two-device concurrency and PostgreSQL lock-wait revocation | PASS; one version winner, revoked actor401 |
| Real private STOMP and rollback | PASS; owner sessions only, rollback silent |
| Distinct non-superuser SQL role | PASS; inserts/preference changes accepted, fact UPDATE/DELETE/TRUNCATE refused |
| Node aggregate | 936/936,9suites;0fail/cancel/skip/todo;429199.2146ms |
| Maven verify | BUILD SUCCESS,exit0;361Surefire/37classes +693Failsafe/133classes =1054;0fail/error/skip |
| Spotless, scope, protected/candidate hashes and diff checks | PASS |

Fresh XMLs newer than17:03:40UTC are authoritative; older focal IT reports are
excluded. The wrapper doubles summaries to2108. Evidence and frozen inline review:
.git/gsd/phase7/07-13-evidence.json and07-13-frozen.json.

## Self-healing and review

- Fixed an initial STOMP assertion to the existing resyncRequired field.
- Scoped formatter wildcard was refused by the wrapper; explicit scoped calls
  matched no paths. Normal Spotless applied formatting and changed only the
  declared source files, confirmed by status/baselines. No wrapper weakening.
- Read-only review corrected unbound GET participant UUID and concretized quoted
  If-Match/ETag before final focal/full verification. The intermediate focus also
  passed15/15; final focus passed again after those corrections.
- Scope checker initially stripped the leading porcelain status space with trim,
  then rejected its own malformed path. Corrected to trimEnd; actual paths were
  always within scope. No owner source change or false clean-worktree claim.
- Frozen inline review found no additional functional defect. RepoWise analysed
  12/17working-tree paths,8introduced/0worsened markers; two owner files were in
  that tool scope, not the commit. Session compound condition and controller/
  repository cohesion heuristics were inspected; maintainability costs remain.

## Limits and continuation

HTTP fixtures use owner connections; runtime database permissions were separately
tested with a real distinct non-superuser role. This is backend/schema/private
push foundation, not a browser journey or independent security/legal certificate.
Suspension/account-pause producer integration, UI local overlay and logout cleanup
are dependent-plan work; permission does not depend on their future write guards.
No original message is filtered for other readers by a personal mute.

Phase7 is13/35. Wave6 complete; next07-14 then07-16/Wave7 under continuous
authority. Public/legal/institutional certification remains pending.
