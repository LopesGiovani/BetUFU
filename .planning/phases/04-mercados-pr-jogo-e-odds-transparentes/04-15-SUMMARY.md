---
phase: 04-mercados-pr-jogo-e-odds-transparentes
plan: "15"
wave: 1
status: complete
completed: 2026-09-07
subsystem: sports-source-governance
tags: [sports, odds, source-authority, temporal-replay, validation]

requires:
  - phase: 04-mercados-pr-jogo-e-odds-transparentes
    provides: 04-01 calibration evidence boundary and fail-closed exploratory-source gate
provides:
  - approved source-authority candidate with one current outcome head per event
  - explicit sporting, administrative and lifecycle taxonomy with score prohibitions
  - immutable sport_time/known_at replay rules, legacy quarantine and correction lineage
  - fail-closed validator and negative tests for the candidate contract
affects: [04-02, 04-03, 04-16, 04-17, 04-18, 04-19, odds, sports]

actuals:
  tokens: 0
  tasks: 2
  commits: 3
  changed_technical_paths: 3
  changed_documentation_paths: 3

tech-stack:
  added: []
  patterns:
    - candidate JSON remains non-runtime and requires an explicit human checkpoint
    - append-only facts plus a transactional compare-and-set head prevent dual authority
    - as-of replay orders immutable sport_time, known_at and stable fact identity
    - negative mutation tests prove fail-closed rules without changing production code

key-files:
  created:
    - scripts/sports-odds-source-candidate.json
    - scripts/validate-sports-odds-source-candidate.mjs
    - scripts/validate-sports-odds-source-candidate.test.mjs
    - .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-15-SUMMARY.md
  modified:
    - .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-15-PLAN.md
    - .planning/phases/04-mercados-pr-jogo-e-odds-transparentes/04-VALIDATION.md

key-decisions:
  - The owner approved option A: separate administrative facts and one current head per event.
  - SPORTING_RESULT references exact V16 score slots; administrative and lifecycle classes never carry a score payload.
  - sport_time is captured on the fact, while known_at controls as-of replay; current head and MAX(version_no) are forbidden shortcuts.
  - H2H is contextual evidence only; automatic bonuses and unapproved Elo/Davidson parameters remain gated by backtest.
  - The T2 approval is bound to the canonical pre-approval digest `1E7DB9D05625CD36CBE083144B95E2429C2523909155498C2CF25336CC17B867`.

requirements-completed: []
requirements-addressed: [ODDS-01, ODDS-02, ODDS-03, ODDS-04, ODDS-08, ODDS-13]

coverage:
  - id: D1
    description: "Source authority, five-class taxonomy, temporal lineage and legacy quarantine are captured as a non-runtime candidate."
    requirement: ODDS-01
    verification:
      - kind: unit
        ref: "node --test scripts/validate-sports-odds-source-candidate.test.mjs"
        status: pass
      - kind: manual_procedural
        ref: "04-15/T2 owner approval and digest review"
        status: pass
    human_judgment: true
    rationale: "The candidate is a governance decision; automation can validate shape but cannot replace owner ratification."
  - id: D2
    description: "The validator rejects runtime promotion, score leakage, hindsight replay, dual heads, silent legacy classification and unsafe approval mutations."
    requirement: ODDS-13
    verification:
      - kind: unit
        ref: "node --test scripts/validate-sports-odds-source-candidate.test.mjs"
        status: pass
      - kind: other
        ref: "node scripts/validate-sports-odds-source-candidate.mjs --check"
        status: pass
      - kind: other
        ref: "node scripts/validate-sports-odds-source-candidate.mjs --require-approval"
        status: pass
    human_judgment: false

---

# Plan 04-15 Summary

**Source authority and temporal replay candidate approved for the next odds plans, with runtime and physical schema decisions still gated.**

## Accomplishments

- Implemented the three T1 allowlisted scripts: candidate JSON, fail-closed validator and 36-case negative-test suite.
- Closed 04-15/T2 with the literal owner response recorded as `aprovar source 14-15`, normalized to plan `04-15`.
- Preserved one outcome authority per event, exact V16 score references, separate V24 lifecycle facts, append-only correction lineage and explicit roster/migration gates.
- Reconciled the plan and phase validation documents to base snapshot
  `0963c965e8fced9af426727cecf9a0ed9306dbe0` and recorded technical HEAD
  `e162a173393fe365006eb831ecede0119a03ae2d`.

## Verification Evidence

- `node --check` for both modules: `2/2` successful.
- Focal Node suite: `36/36` passed, zero failures, errors, skips or cancellations.
- Candidate validator `--check`: `414` checks, exit `0`, `T2=approved`.
- Candidate validator `--require-approval`: `414` checks, exit `0`, `T2=approved`.
- Node aggregate: `674/674` passed.
- Maven aggregate: Surefire `282/282` plus Failsafe `454/454`, total distinct
  `736/736`, with `BUILD SUCCESS`, using only process-local test values for the
  two required matricula HMAC variables. The wrapper's `1472` is a raw sum of
  repeated markers, not a distinct-test count.
- `git diff --check`: exit `0`; LF/CRLF messages are checkout normalization warnings only.
- Normalized LF SHA-256 after the portability fix: candidate `6D0B8E183D6B8D9AD646C5119BE684BC124C17BD37037A029E6283643D2BD40C`, validator `DA0031058F674653E8AC9C202817F1B792D5AC888FC4FBEDF753D02688FF5F2C`, tests `FCFB00E8A5AF5324E184B4F689FBB363D4F9B8074726805732D26678CC977AE9`.

## Approval and Scope

- Approved by `LopesGiovani` at `2026-09-07T00:41:36.751Z`.
- Canonical candidate snapshot digest: `1E7DB9D05625CD36CBE083144B95E2429C2523909155498C2CF25336CC17B867`.
- The approval covers authority, taxonomy and temporal invariants. Envelope names, constraints/triggers, roster atomicity, recovery details and literal migration filenames remain pending in their own plans.
- No Java, SQL migration, OpenAPI, generated client, Angular, dependency, infrastructure or external-data path changed.
- `04-01/T2` remains fail-closed because the exploratory LAUFU capture is not calibration-eligible; this plan does not set `wave_0_complete` to true.

## Deviations and Auto-fixes

- The validator import was corrected to `fileURLToPath` from `node:url`, a local mechanical ESM fix.
- The path assertion was corrected to use platform-native `join(...)`; the
  local technical correction is recorded in commit `e162a173` and keeps the
  suite valid on Windows and POSIX.
- The candidate digest and approval hash were recomputed after the approved snapshot corrections; the final digest is recorded above.
- Historical validation numbers (`28/28`, `416`, pending approval) remain in `04-VALIDATION.md` as explicitly labeled evidence from the pre-approval snapshot.
- A Maven attempt without the required process environment produced `454`
  Failsafe tests with `67` context errors because Spring retained the HMAC
  placeholder literally. The cause was reproduced as an absent environment,
  not a source regression; the same aggregate command was rerun with
  process-local test values and passed with `282/282` Surefire plus `454/454`
  Failsafe (`736/736` distinct). No persistent secret or
  non-allowlisted file was introduced.
- An auxiliary unquoted PowerShell `-Dit.test` probe was discarded after the
  quoted form passed; it is not part of the plan evidence.

## Git State and Next Step

- Worktree: `C:/Users/giova/Desktop/BetUFU-worktrees/worktree-agent-04-15`.
- Branch: `worktree-agent-04-15`.
- Base SHA: `0963c965e8fced9af426727cecf9a0ed9306dbe0`.
- Implementation commits: `0c486a1a2e288a83b19edcaeb83ef01674a0f4a6` and
  `e162a173393fe365006eb831ecede0119a03ae2d`.
- Documentation commit: current documentation commit (hash intentionally omitted).
- Integration and cleanup are the authorized closing operations after this
  documentation commit; push remains disabled by owner instruction.
- The next phase plans may consume the approved source invariants only after satisfying their own human gates.

---
*Phase: 04-mercados-pr-jogo-e-odds-transparentes*
*Plan: 04-15*
*Completed: 2026-09-07*
