---
phase: 07-comunidade-moderada-e-prontidao-do-piloto
plan: '08'
wave: 5
status: complete
subsystem: frontend-e2e
requires: [07-06, 07-07, 07-09]
provides: [arena-chat-reading, real-stomp-browser-proof]
affects: [07-10, 07-11, 07-17, 07-20]
requirements-completed: []
actuals:
  tasks: 2
  commits: 2
tech-stack:
  added: []
  patterns: [angular-signals-read-store, native-stomp-read-client, owned-real-browser-run]
coverage:
  - id: arena-chat-read
    description: Public event and authenticated global chat reads render real server data with history and safe plaintext.
    verification:
      - kind: e2e
        ref: frontend/e2e/phase7-chat-read.spec.ts
        status: pass
      - kind: automated_ui
        ref: node scripts/phase7-e2e.mjs --isolated --spec frontend/e2e/phase7-chat-read.spec.ts
        status: pass
    human_judgment: false
  - id: stomp-reconnect-and-logout
    description: Real WebSocket/STOMP HTTP 101 connection resynchronizes after offline recovery and tears down on logout in desktop and 320px viewports.
    verification:
      - kind: e2e
        ref: frontend/e2e/phase7-chat-read.spec.ts
        status: pass
      - kind: integration
        ref: backend/src/test/java/br/com/betufu/chat/ChatPushSecurityIT.java
        status: pass
    human_judgment: false
started: '2026-10-05'
completed: '2026-10-05'
technical_commit: 953ecc4
documentation_commit: pending
---

# Plan 07-08 Summary

The Arena now exposes a Community route and embeds a read-only chat panel on
published event pages. The Signal store reads the generated API, preserves
keyset history position, sanitizes the public projection, and uses the native
read-only STOMP endpoint for invalidation and full resynchronization. It clears
state on logout and destruction, reconnects after real offline recovery, and
does not poll.

The browser proof used only run-owned Docker resources, genuine Keycloak
personas, real PostgreSQL chat facts, and desktop plus 320px Playwright projects.
It passed both projects in 24.6 seconds. HTTP 101 upgrades, live post commits,
history paging, safe text rendering, keyboard discovery, scroll preservation,
offline reconnect, contextual login, logout cleanup, and no post-logout
reconnect were observed.

## Verification

- `node scripts/phase7-e2e.mjs --isolated --spec frontend/e2e/phase7-chat-read.spec.ts`: 2/2 passed; owned resources cleaned.
- `node --test scripts/*.test.mjs`: 936/936, 9 suites, no failures/errors/skips.
- `npm --prefix frontend test -- --watch=false`: 455/455 across 75 files.
- `npm --prefix frontend run lint`, `npm --prefix frontend run generate:api:check`, and production build: passed.
- `node scripts/maven.mjs -f backend/pom.xml verify`: BUILD SUCCESS; fresh XMLs 361 Surefire + 664 Failsafe = 1,025, no failures/errors/skips.
- Baseline: 253/253 protected hashes unchanged; owner documents and V1-V38 preserved.

## Deviations and risks

- Self-healing added the exact anonymous-session handshake correction and its two security tests in `ChatPushEndpoint.java` and `ChatPushSecurityIT.java`; the plan's 15-path validator remains unchanged and the supplemental paths are recorded in the evidence.
- RepoWise review reported maintainability heuristics for compound fail-closed validation and the large isolated runner; no consumer break or functional defect was found, and all discriminating tests passed.
- Legal, institutional, public-operation, backup-provider, and pilot-capacity gates remain pending by phase policy.

## Continuation

Wave 5 continues with 07-10, which establishes the immutable definitive-result
milestone and permanent event-chat deadline before later write and moderation
features.
