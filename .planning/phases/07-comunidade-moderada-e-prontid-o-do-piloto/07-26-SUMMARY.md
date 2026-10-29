---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '26'
wave: 14
status: complete
subsystem: private-rights-ui
technical_commit: 19c746a000589283d6568b8f6b2253066b92e7ce
completed: '2026-10-29'
requires: [07-25, 07-19, 07-08, 07-23]
provides: [private-holder-rights-journey, administrator-rights-responses, restricted-runtime-case-lock]
affects: [07-35, 07-30, 07-31, 07-32]
requirements-completed: [PRIV-05, TEST-03, UX-01, UX-02]
actuals:
  tasks: 2
  technical_paths: 18
  commits: 2
tech-stack:
  added: []
  patterns: [official-generated-client, session-epoch, native-dialog, advisory-transaction-lock]
---

# Plan 07-26 Summary

The account links to a private rights page with six request kinds, bounded own-case
pagination, historical status, operator responses and restricted JSON downloads.
The operational panel links to administrator-only case lookup, analysis, replies,
partial exports and the ratified externally verified athlete-without-account procedure.
Requests never automatically delete facts; incomplete exports cannot fulfill ACCESS.

Signals and epoch checks hide private data immediately on session replacement,
cancel outstanding reads and stop follow-up lookups after late mutations. Native
dialog confirmation supports cancellation, Escape and restored focus. Only the
official generated client is used, with transferCache disabled.

## Authorization and scope

Continuous owner authority covers inline master execution, tests/self-healing,
local staging, both commits and advancement. Approval: docs/phase7-approvals/07-26.json.
Ten planned paths plus eight bounded support paths reconcile the actual eighteen:
operations HTML, closed route validation and tests, isolated runner and tests,
privacy repository and two SQL/concurrency regression paths. No plan cap or
validator was weakened. No API/schema/dependency change was required.

## Verification

- Literal real isolated browser command: 2/2 desktop/320px, explicit Node exit0.
  Account-paused holder requests, private list, forbidden third party/moderator,
  analysis/reply, real partial export with identity/messages, forbidden completion,
  private download, NOTICE completion, verified athlete procedure and logout all passed.
  Every confirmation asserts no native navigation.
- Angular focal: 8/8; ranking plus privacy repeat: 17/17; aggregate: 525/525 in83 files.
- Node aggregate: 946/946,9 suites; final runner/route focal:209/209. No skips/failures.
- Literal Maven verify after clean:1136 fresh XML tests:361 Surefire/37 classes plus
  775 Failsafe/150 classes; zero failures/errors/skips, timeout=false,flakes=0.
  Wrapper2272 is duplicated aggregation; XML counts above are authoritative.
- Generator anti-drift, lint, production build, Spotless and git diff --check passed.
- Eighteen paths frozen and60 protected hashes retained in
  .git/gsd/phase7/07-26-frozen.json. Inline read-only review found no remaining defect.

## Self-healing and limits

The browser initially could not retrieve a completed POST body through DevTools.
The tracer now verifies the HTTP status, rendered protocol and independent real GET,
with explicit no-navigation assertions; no business response is mocked.
Real browser testing then exposed FOR UPDATE on immutable case facts. PostgreSQL
requires UPDATE privilege for that lock, correctly absent from the runtime role.
A per-case advisory transaction lock now serializes decisions without extra privileges.
The new restricted-role regression failed with SQLSTATE42501 before the fix and passed
afterward; existing concurrency/revocation tests now block on the same transaction lock.
Focal privacy/repository/export tests passed21 actual tests (wrapper42).

The first aggregate Angular run timed out in four unchanged ranking cases during
concurrent startup/test work. Focal and full repeats passed without timeout/source
weakening. PowerShell treated build stderr warnings as an error; the final literal
browser run captured explicit process exit0. Existing style-budget warnings remain.

V1-V47, append-only history, owner documents and absent manual-testing.md state,
owner seeds and Docker volumes are preserved. Only owned disposable browser resources
were removed. No push, worktree, Antigravity or new dependency. RepoWise MCP tools
were unavailable; source and evidence review was performed inline.

The rules-preference boolean still needs composing into restricted exports during
final integration; its accessor exists. This is not authorization for public launch:
institutional/legal/cloud/load gates remain separate.

Phase7 is31/35; global166/170. Continue07-35 in Wave14 under continuous authority.
