---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '22'
wave: 10
status: complete
subsystem: api-contract
technical_commit: 24033c42ee2ad69102394463b51a6aa7ffb8ed63
completed: '2026-10-06'
requires: [07-03, 07-20, 07-21, 07-18, 07-06, 07-11, 07-14]
provides: [official-emergency-pause-client, minimal-public-availability-contract, private-administrative-receipts]
affects: [07-23, 07-31, 07-32]
requirements-completed: []
actuals:
  tasks: 4
  commits: 2
  primary_paths: 15
  technical_paths: 16
tech-stack:
  added: []
  patterns: [producer-record-reconciliation, separate-public-private-dtos, exact-real-engine-publication]
coverage:
  - id: emergency-pause-official-contract
    description: Five actual producer operations, closed DTO fields, session boundaries and nullable availability revision
    requirement: INTE-05
    verification:
      - kind: unit
        ref: frontend/scripts/generate-api.test.mjs
        status: pass
    human_judgment: false
---

# Plan 07-22 Summary

The official Angular EmergencyPausesService exposes five real Spring operations:
administrator GET/POST at /api/v1/operations/emergency-pauses, public event/market
availability and session-private global chat availability. The client uses
relative routes and sends no manual authorization or cookie headers.

## Authority and preserved scope

- Continuous owner Phase7 inline/master execution, tests, self-healing, local
  stage/commits and advancement authority. Base c981bbde9b0b677415d3603d1ae254b1df62bc16.
- Exactly15 primary paths;16 technical paths including supplemental PLAN
  reconciliation. The unchanged generated index.ts path was replaced by the
  availability model. No validator cap change, dependency, worktree, Antigravity
  or push operation.
- Owner-reverted documents were preserved/excluded. docs/manual-testing.md
  remains absent as left by the owner. All49 protected paths (documents plus
  V1-V45 migration files and existing migration metadata) retained frozen hashes.
  No persistent database, seed or volume mutation.
- Actual candidate/official publication and hashes are recorded in
  docs/phase7-approvals/07-22.json. Authority comes from continuous execution;
  no separate human digest response or public-pilot approval is claimed.

## Delivered and reconciled

- EmergencyPauseCommand matches the strict Java command: clientCommandId, kind,
  targetId, action, reason and optional resumedPauseId. RESUME uses the same root
  POST; the proposed /{id}/resumptions alias was never implemented by producers.
- EmergencyPauseState matches EmergencyPausePort.Receipt: factId, kind, targetId,
  action, reason, nullable resumedPauseId and recordedAt. GET lists current active
  pauses derived from append-only facts; it does not expose the entire audit log.
- EmergencyPauseAvailability contains only state, applicable messagesPaused or
  predictionsPaused flag and nullable revision. It has no administrative reason,
  fact/target/actor/account identity. No synthetic view/status discriminator.
- Prediction availability follows the actual producer route
  /api/v1/me/predictions/markets/{marketId}/availability. Public GET availability
  grants no write authorization; global GET retains the session boundary.
- Real approved Java engine generated all219 TypeScript files. No generated file
  was manually edited. Final manifest digest:
  AE29F51FB42941664FDFB6AC9A1EB5EDA02EAD9529C7782E9F08C58AC6760AA4.
- Candidate snapshots and full inventories prove preservation of governed trees.
  Final header/list-description reconciliation generated identical client bytes;
  the latest inventory is that metadata-only pass. Earlier real-stage evidence is
  preserved under .git/gsd/phase7/07-22-*.json and in the approval provenance.

## Verification

- Literal preparation/implementation gates, candidate command, official generate
  and generate:api:check all passed.
- Generator focal111/111; final emergency-pause focus3/3. Tests compare schema fields
  to actual Java records, generated route/response types and exact field allowlists;
  negative mutations reject session removal, private-field injection and route drift.
- Maven verify BUILD SUCCESS. Fresh Surefire XML37classes/361tests plus fresh
  Failsafe XML145classes/740tests:1101 total, zero failures/errors/skips. Wrapper
  reports2202 because it double-counts summary lines; XML is authoritative.
- Node aggregate937/937 in9suites; zero failures/cancellations/skips/todos.
- Angular79files/483tests, all passed. Lint, production build and final generator
  check passed;13 existing style-budget warnings remain outside this contract scope.
- git diff --check, exact stage scope and frozen/publication hashes passed.
  Frozen review: .git/gsd/phase7/07-22-frozen.json.

## Self-healing and review

- Initial combined schema incorrectly required view/status and omitted actual
  receipt fields. Tests failed against that generated output. Split public and
  private DTOs while retaining the approved primary path cap.
- Actual engine with oneOf produced an enum reference without its namespace.
  The shared availability DTO uses optional domain flags and an explicit documented
  producer invariant; closed schemas and Java-record comparisons preserve fields.
- Corrected PowerShell UTF8 BOM in approval, obsolete digest expectations and
  incomplete generated baseline hashes. Final real candidate and check passed.
- Removed availability response header promises not explicitly emitted by the
  actual controllers. Existing Spring security behavior was unchanged.
- Full inline review covered source, candidate and all generated/untracked paths.
  RepoWise review was partial (4/5 indexed files); its journalPaths complexity
  advisory is outside changed hunks. No additional functional defect was found.
  Browser journeys and phase-level certification belong to downstream plans.

## Next

Phase7 is22/35, global157/170. Wave10 has07-12 and07-22 complete;07-27 is next.
Continuous execution remains authorized. The owner resolved the retention-policy
contradiction through conversation:2026-10-06:owner-retention-90-180:90-day common
messages and180-day closed-case evidence with holds; other-class expurgo remains
blocked. Proceed07-27 inline, preserving append-only facts and owner data.
