---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '19'
wave: 12
status: complete
subsystem: chat-moderation-ui
technical_commit: 51c5f28c465dca069f4d6c4c9d07da417e02fb4a
completed: '2026-10-07'
requires: [07-18, 07-15, 07-08]
provides: [scoped-moderation-console, account-sanction-review, restricted-role-case-lock]
affects: [07-31, 07-32]
requirements-completed: [CHAT-05, CHAT-06, TEST-03, UX-01, UX-02]
actuals:
  tasks: 2
  files: 18
  commits: 2
tech-stack:
  added: []
  patterns: [generated-api-adapter, signal-store, private-session-epoch, explicit-confirmation]
---

# Plan 07-19 Summary

Authenticated console links now lead to scoped community moderation and the
account's own sanctions. The generated ChatService adapter disables transfer
cache for private requests. The Signal store clears private state on logout,
expiry and destruction, and ignores late responses from earlier epochs.

Scoped moderators inspect their queue, explicitly request audited original
evidence, remove messages and forward cases. Only a successful administrator-only
pending-review request enables administrator controls. General administrators
confirm the current sanction suggestion and maintain or annul reviews. The
backend remains the authority for every action; links do not grant permissions.

Owners see reason, period and review status, including while writing is blocked.
Review requests do not suspend sanctions automatically. Confirmation dialogs
explain the operation, require a reason, support Tab/Shift+Tab and Escape, and
restore triggering focus. Recoverable retries preserve the command UUID and body.
Original evidence is absent from queue HTML and fetched only after confirmation.

## Scope and authority

Continuous owner Phase7 authority covers inline master implementation, tests,
self-healing, stage, two local commits and advancement. Base69dc381. No push,
worktree, Antigravity, dependency update or owner service/data/seed/volume change.
Fifty-seven protected baseline hashes and the owner's absent manual-testing
document state were preserved. Applied migrations were not edited.

The UI slice has15 paths. A separate three-path prerequisite slice, including
its shared approval record, corrected ChatModerationRepository and extended
ChatModerationIT. The union contains17 technical paths plus supplemental PLAN
bookkeeping. Accepted slice hashes are in .git/gsd/phase7/07-19-backend-slice.json.

## Verification

| Evidence | Result |
| --- | --- |
| Literal approval gate and route boundary | PASS |
| Restricted-role SQL regression | RED permission denied for chat_channel; GREEN6/6 |
| Real isolated browser | 2/2,21.4s,exit0;desktop and320px;owned resources removed |
| Angular aggregate | 505/505;81 files;zero failures |
| Node aggregate | 944/944;9 suites;zero fail/cancel/skip/todo;582156.3049ms |
| Maven clean then literal verify | BUILD SUCCESS, exit0; 377 Surefire/38 classes + 771 Failsafe/149 classes = 1148; zero failures/errors/skips |
| Generator check, lint and production build | PASS;existing unrelated style-budget warnings |
| Scope, protected hashes and frozen inline review | PASS;18 paths |

Real browser roles prove forwarding survives refresh and replay, administrators
alone sanction, third-party evidence is denied, third-party review returns404
without revealing the sanction, owners cannot write during suspension, reviews
can be annulled, an already-loaded message receives its removed marker via STOMP,
credits/ranking remain identical and logged-out queue access returns401. No
browser pageerrors or horizontal overflow appeared.

## Self-healing and review

- Runtime PostgreSQL grants deny UPDATE on immutable chat_channel. A joined
  FOR UPDATE attempted to lock that table. FOR UPDATE OF c now locks only the
  mutable moderation case, preserving grants and concurrency serialization.
  The discriminating test executes the actual repository query with a restricted
  role and exact migration grant blocks; no migration or permission was weakened.
- Added explicit dialog focus cycling after real browser Tab escaped to browser
  chrome. Escape closes the native dialog before deferred triggering focus.
- The animated agenda link uses the same explicit click as the certified read
  journey. Chat is scrolled into its real deferred viewport before assertions.
- Corrected the test's third-party review expectation to the existing404 privacy
  contract. Logout now waits for its HTTP response, the provider confirmation and
  the visitor page before checking401; redirected response bodies are not read.
- One pre-existing logout unit test timed out under load; an unchanged repeat
  passed. Docker API500/502 and one OIDC discovery startup failure recovered in
  owned isolated reruns. Maven/browser Docker workloads were separated.
- Stale compiled preference and privacy classes required cleaning backend/target.
  The final aggregate evidence comes from a full rebuild, not stale reports.
- Final verify rejected only comment indentation; spotless:apply corrected whitespace
  without changing FOR UPDATE OF c. Both accepted prerequisite hashes were
  reconciled explicitly and the literal verify was repeated.
- RepoWise found no new findings in nine tracked code paths. New untracked
  components, adapter/store and browser spec were additionally reviewed inline;
  the tool's tracked-file verdict does not cover them automatically.

## Limits and continuation

This verifies the bounded moderation/account journey. Full phase integration,
load, privacy preference export composition and final certification remain later
plans. Keyboard/mobile checks do not constitute a complete manual WCAG audit.
The UI-SPEC's proposed /operacao/chat alias was not published; the exact guarded
route is /operacao/comunidade/moderacao. Protected owner documents remain intact.

Phase7 is28/35;global163/170. Continue07-34 in Wave12 under continuous authority.
Legal, institutional and public-pilot gates remain separate. The approval is
historical after HEAD advances and is not silently rebound.
