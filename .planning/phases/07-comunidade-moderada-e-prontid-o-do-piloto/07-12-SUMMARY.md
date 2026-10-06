---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '12'
wave: 10
status: complete
subsystem: frontend-chat-write
requires: [07-08, 07-11, 07-10, 07-17, 07-21]
provides: [volatile-chat-composer, idempotent-chat-outbox, logout-purge]
affects: [07-22, 07-27, 07-31, 07-32]
requirements-completed: [CHAT-01, CHAT-02, TEST-03, UX-01, UX-02]
actuals:
  tasks: 2
  commits: 2
tech-stack:
  added: []
  patterns: [signals-outbox, generated-api-adapter, real-stomp-write-proof]
---

# Plan 07-12 Summary

The chat now has a composer and a volatile, per-tab outbox. Text is validated
by Unicode code points (up to 280), preserves the original idempotency key,
waits three seconds between sends, and marks a message sent only after a valid
HTTP 201 receipt. Offline writes wait for reconnection; lost acknowledgements
retry the same key; 400/401/403/404/409 stop automatically; 429 follows the
server retry time with a bounded budget. Eligibility is re-read immediately
before each POST and the backend remains the final authorization boundary.

Logout, session replacement, refresh, page close and component destruction
cancel requests, timers, callbacks and pending facts. Session state is cleared
at the start of logout, so a delayed logout response cannot leave a window in
which an old account can send. The existing native WebSocket/STOMP read flow,
draft, focus, scroll anchoring and plain-text rendering remain intact.

## Verification

- `node scripts/validate-phase7-approvals.mjs --plan 07-12 --stage implementation`: approved.
- `npm --prefix frontend test -- --watch=false`: 483/483 tests, 79 files, zero failures.
- `npm --prefix frontend run generate:api:check`: passed.
- `npm --prefix frontend run lint`: passed.
- `npm --prefix frontend run build:production`: passed; existing style-budget warnings remain outside this plan.
- `node --test scripts/*.test.mjs`: 937/937 tests, 9 suites, zero failures/cancellations/skips.
- `node scripts/phase7-e2e.mjs --isolated --spec frontend/e2e/phase7-chat-write.spec.ts`: 4/4 real browser tests passed in desktop and 320px projects; owned resources cleaned.
- `node scripts/maven.mjs -f backend/pom.xml verify`: 361 Surefire + 740 Failsafe = 1101 XML tests, zero failures/errors/skips.
- Mutation proof removed session epoch/early-clear guards and produced the expected two failing race tests; source was restored.

## Self-healing and review

- The isolated runner now builds the production Angular bundle before creating
  its image, preventing stale `dist` output from being certified.
- The write adapter uses the generated enrollment endpoint (`ATIVO`) rather
  than the profile projection (`REGISTRADA`), matching the real contract.
- Docker Desktop briefly returned HTTP 500 while Testcontainers started
  Keycloak; the run-owned seven containers/four volumes were verified and
  removed without touching owner resources, then E2E and Maven were repeated
  sequentially.
- RepoWise found no functional finding. It reported maintainability markers on
  `ChatPanelComponent` (low cohesion and a large constructor); these are
  deferred refactoring costs, not behavioral failures.

## Continuation

Wave 10 continues with 07-22 (official pause and availability contract), then
07-27 (retention, exports and suppressions). Independent legal review and
public-pilot authorization remain separate phase gates.
