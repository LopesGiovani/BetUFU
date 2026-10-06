---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '21'
wave: 9
status: complete
subsystem: backend-api
tags: [spring-modulith, emergency-pause, chat, predictions, postgres]
requires:
  - phase: 07-20
    provides: append-only emergency pause facts and transaction-bound lock port
  - phase: 07-09
    provides: atomic prediction acceptance and idempotent write boundary
provides:
  - consumer-owned chat and prediction pause ports
  - atomic guards for new chat messages and predictions
  - public pause availability snapshots
affects: [07-22, 07-23, 07-24, 07-31, 07-32]
actuals:
  tokens: 12400
  tasks: 2
  commits: 2
tech-stack:
  added: []
  patterns: [consumer-owned-pause-adapters, transaction-bound-guard, minimal-availability-dto]
key-files:
  created:
    - backend/src/main/java/br/com/betufu/chat/ChatEmergencyPausePort.java
    - backend/src/main/java/br/com/betufu/betting/PredictionEmergencyPausePort.java
    - backend/src/main/java/br/com/betufu/chat/internal/EmergencyChatAdapter.java
    - backend/src/main/java/br/com/betufu/betting/internal/EmergencyPredictionAdapter.java
    - backend/src/test/java/br/com/betufu/chat/ChatPauseIT.java
    - backend/src/test/java/br/com/betufu/betting/PredictionAvailabilityIT.java
  modified:
    - backend/src/main/java/br/com/betufu/chat/internal/ChatWriteService.java
    - backend/src/main/java/br/com/betufu/betting/internal/PredictionService.java
    - backend/src/main/java/br/com/betufu/chat/internal/ChatReadService.java
    - backend/src/main/java/br/com/betufu/betting/internal/PredictionController.java
    - backend/src/main/java/br/com/betufu/platform/security/SecurityConfiguration.java
key-decisions:
  - "Pause checks use consumer-owned ports and the existing operations lock in the same transaction."
  - "Market lookup crosses the odds public port; no betting adapter reads sports or odds internals directly."
  - "Availability responses expose only state, domain pause flag and opaque revision."
patterns-established:
  - "Accepted idempotent replays are resolved before current pause checks."
  - "Resume fixtures append a RESUME fact instead of mutating a pause projection."
requirements-completed: [INTE-05, CHAT-01, TEST-03]
coverage:
  - id: atomic-pause-guards
    description: "Account, event, market and channel pauses block only new commands while preserving historical/replay behavior."
    requirement: INTE-05
    verification:
      - kind: integration
        ref: backend/src/test/java/br/com/betufu/chat/ChatPauseIT.java
        status: pass
      - kind: integration
        ref: backend/src/test/java/br/com/betufu/platform/operations/EmergencyPauseConcurrencyIT.java
        status: pass
      - kind: other
        ref: node scripts/maven.mjs -f backend/pom.xml verify
        status: pass
    human_judgment: true
    rationale: "The foundation is backend-certified; concurrent financial settlement and browser journeys remain downstream gates."
  - id: availability-snapshots
    description: "Chat and prediction consumers expose minimal availability views without pause reasons, actors or account identifiers."
    requirement: CHAT-01
    verification:
      - kind: unit
        ref: backend/src/test/java/br/com/betufu/betting/PredictionAvailabilityIT.java
        status: pass
      - kind: other
        ref: node --test scripts/*.test.mjs
        status: pass
    human_judgment: true
    rationale: "The DTO and routing guards are automated; authenticated multi-persona HTTP/browser proof is scheduled for later plans."
  - id: modular-boundary
    description: "Chat and betting consume public ports and adapters without cross-module internal imports."
    requirement: TEST-03
    verification:
      - kind: integration
        ref: ModulithArchitectureTests
        status: pass
      - kind: other
        ref: cross-module internal import audit
        status: pass
    human_judgment: false
---

# Plan 07-21 Summary

Atomic pause guards now connect the operations-owned emergency facts to chat and betting through consumer-owned ports. New messages and predictions acquire the same PostgreSQL target locks used by pause commands; replayed idempotent commands are returned before the current pause check. Public availability snapshots stay minimal and use producer public ports for market publication.

## Performance

- **Duration:** 61 min
- **Started:** 2026-10-06T03:31:40Z
- **Completed:** 2026-10-06T04:32:20Z
- **Tasks:** 2
- **Files modified:** 16 staged paths plus 1 summary

## Accomplishments

- Added chat and betting pause boundaries with operations adapters and atomic account/event/channel/market guards.
- Added chat and prediction availability endpoints with anonymous/session rules and corrected public security matchers.
- Preserved append-only pause history and Modulith boundaries; no migration, dependency, seed or volume changes.

## Task Commits

1. **Implement foundation/capacity** - `a8ba5ec` (`feat(07-21): add atomic emergency pause guards and availability`)
2. **Formalize plan** - pending documentation commit

## Verification

- Approval validator: PASS (`07-21 implementation`).
- Focal Maven: 16/16, zero failures/errors/skips.
- Maven verify: BUILD SUCCESS, 2178/2178, zero failures/errors/skips.
- Node aggregate: 936/936, zero failures/cancellations/skips/todos.
- Spotless apply/check and `git diff --check`: PASS.
- Cross-module internal import audit: PASS; V1-V44 and owner documents were not staged.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Resume fixture mutated a rebuildable projection.**
- **Found during:** focal ChatPauseIT
- **Fix:** inserted an immutable RESUME fact referencing the pause fact.
- **Verification:** focal 16/16 and full Maven verify.

**2. [Rule 2 - Missing Critical] Market pause adapter read producer tables directly and event guard used competitionId.**
- **Fix:** routed market lookup through `OddsForPredictionPort` and guarded `snapshot.eventId()`.
- **Verification:** focal and full Maven verify.

**3. [Rule 2 - Missing Critical] Availability routes were not fully public in SecurityConfiguration.**
- **Fix:** added the concrete `/me` prediction and event-chat availability matchers; reconciled the plan allowlist and approval digest.
- **Verification:** focal, full Maven verify and Node 936/936.

**Total deviations:** 3 auto-fixed. **Impact:** all fixes were required for the declared atomicity and public-read contracts; no schema or dependency scope was added.

## Issues Encountered

- The first full Maven run found Spotless violations in newly edited Java files; `spotless:apply` corrected them before the final run.
- Existing changes to `docs/development.md`, `docs/local-seed.md` and untracked `docs/manual-testing.md` remain untouched and unstaged per preservation rules.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

07-21 is complete at backend foundation scope. Continue with 07-24; browser, multi-persona, financial in-flight and public-pilot gates remain downstream.

---
*Phase: 07-comunidade-moderada-e-prontid-o-do-piloto*
*Completed: 2026-10-06*
