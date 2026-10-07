---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '15'
wave: 11
status: complete
subsystem: frontend-chat-community
technical_commit: 840a663
completed: '2026-10-07'
requires: [07-12, 07-14, 07-08]
provides: [private-account-mute-controls, private-report-dialog, real-multi-session-community-proof]
affects: [07-19, 07-31, 07-32]
requirements-completed: [CHAT-03, CHAT-04, TEST-03, UX-01, UX-02]
actuals:
  tasks: 2
  files: 15
  commits: 2
tech-stack:
  added: []
  patterns: [consumer-ui-adapter, optimistic-signal-state, session-epoch-cancellation, native-modal-dialog]
coverage:
  - id: account-private-mute
    description: Account preferences filter all chats, synchronize the owner's sessions and leave another account intact
    verification:
      - kind: unit
        ref: frontend/src/app/features/chat/chat-personal-mute.store.spec.ts
        status: pass
      - kind: e2e
        ref: frontend/e2e/phase7-chat-community.spec.ts
        status: pass
    human_judgment: false
  - id: accessible-private-report
    description: Authenticated inactive users can report through a closed reason catalog; visitors receive contextual login
    verification:
      - kind: unit
        ref: frontend/src/app/features/chat/chat-panel.component.spec.ts
        status: pass
      - kind: e2e
        ref: frontend/e2e/phase7-chat-community.spec.ts
        status: pass
    human_judgment: false
---

# Plan 07-15 Summary

The Arena chat now offers private reports and account-wide personal mute
controls. Mutes apply optimistically, roll back visibly on failure, refetch
the authoritative version before retry and synchronize the owner's other
sessions through the existing WebSocket/STOMP hints. Another account's
preferences and message view remain intact. Unmute sends quoted If-Match.

Reports use the generated API, a closed reason enum and a retained
idempotency key on retry. The native dialog contains keyboard focus,
supports Escape without a POST, restores the triggering focus and works
at 320px. Reporting remains independent of message-writing eligibility;
the real inactive actor received HTTP201. Removed markers retain report
controls, and visitors receive login back to the relevant channel.

Logout, account replacement and destruction cancel pending operations and
discard private IDs, report state and delayed callbacks. No private browser
storage or generated-file manual edit was introduced.

## Authority and scope

- Continuous owner authorization covers inline master execution, self-healing,
  tests, stage, two local commits and advancement. Base84b9c26; no push.
- Fourteen technical paths plus PLAN reconciliation were frozen and staged.
  The supplemental runner registration, route seam and route-validator tests
  support the declared separate CommunityPageComponent.
- Fifty protected paths, including applied migrations and the owner's document
  state, remain intact. No dependency, owner database, seed, volume, worktree
  or Antigravity change. Docker resources belonged only to the browser run.

## Verification

| Evidence | Final result |
| --- | --- |
| Approval validator | APPROVED07-15 implementation |
| Angular | 500/500;80 files;zero failures |
| Node aggregate | 938/938;9 suites;zero fail/cancel/skip/todo;316379.7161ms |
| Maven verify | BUILD SUCCESS;361 Surefire/37classes +763 Failsafe/147classes =1124;zero failures/errors/skips |
| Literal isolated browser | 2/2;desktop and320px;20.4s;explicit process exit0;owned resources removed |
| Generator check, lint and production build | PASS;existing unrelated style-budget warnings |
| Scope, protected hashes, frozen review and diff checks | PASS;15 exact paths |

The Maven wrapper duplicates XML summary counts and printed2248. Fresh XMLs
are authoritative. Java/schema remained identical after the frontend-only
retry correction; Angular, Node, browser, lint and build were recertified.
Evidence is in `.git/gsd/phase7/07-15-frozen.json` and
`.git/gsd/phase7/07-15-review.json`.

## Self-healing and review

- JSDOM lacks native dialog methods; a test-only shim models attributes while
  real Playwright verifies modal focus behavior. Explicit Tab/Shift+Tab cycling
  prevents focus leaving for browser chrome.
- The event page defers chat until its placeholder enters the viewport. The
  browser proof scrolls to that real section before expecting a connection.
- A new reason on an already-reported occurrence correctly conflicts; the
  inactive reporting scenario uses a distinct real occurrence.
- Logout follows the real Keycloak confirmation before inspecting the returned
  visitor page. Explicit exit capture resolved an ambiguous terminal result.
- Moving the Community page required exact route-validator/fixture updates;
  alternate modules/components remain rejected. The runner focus passed21/21.
- Final review found a queued-refresh retry race. A delayed-response test was
  RED1/12 on the old behavior and GREEN12/12 after replay was prioritized
  ahead of accumulated push hints with a session epoch check.
- Four unrelated group tests timed out under concurrent build load. Their
  isolated9/9 and final complete500/500 runs passed without source or timeout
  changes. PowerShell UTF16 logs are decoded explicitly in the evidence reader.
- RepoWise reviewed13/15 paths; PLAN/approval metadata were checked directly.
  Complexity/cohesion and the long sequential E2E remain maintenance costs;
  no functional finding remains. This is not a complete WCAG or launch audit.

## Continuation

Phase7 is24/35;global159/170. Wave11 continues with07-25, then07-29 and07-33.
Legal review, institutional identity, blocked-class retention policies and
public-pilot authorization remain separate gates.
