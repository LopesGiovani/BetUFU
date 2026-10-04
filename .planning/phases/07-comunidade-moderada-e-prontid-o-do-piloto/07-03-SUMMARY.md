---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '03'
wave: 2
status: complete
subsystem: api-tooling
requires: [07-01]
provides: [real-approved-candidate-source-pipeline, exact-generated-change-inventory]
affects: [07-06, 07-07, 07-11, 07-14, 07-18, 07-22, 07-25, 07-34]
requirements-completed: []
actuals:
  tokens: 20227
  tasks: 2
  commits: 2
tech-stack:
  added: []
  patterns: [frozen-input-snapshot, scoped-candidate-authority, governed-baseline-preservation]
coverage:
  - id: candidate-source-pipeline
    description: Real engine generates candidates, inventories bytes and refuses unauthorized scope/drift
    verification:
      - kind: integration
        ref: frontend/scripts/generate-api.test.mjs
        status: pass
    human_judgment: false
started: '2026-10-04T21:20:42.383Z'
completed: '2026-10-04T22:04:23.326Z'
technical_commit: be45ed1edc8e13b441f7da79d85e57947d26e2ec
---

# Plan 07-03 Summary

The existing transactional generator accepts an approved candidate source and
produces reviewable evidence without replacing the governed API or client.

## Authority and scope

- Base 264ff28cf89df52ae17f9ab2da0cd8945ecd6338, master, direct inline execution.
- Owner reference: conversation:2026-10-04:owner-continuous-phase7-wave2-through-completion.
  It covers implementation, tests/self-healing, local stage/commits and closeout.
- Exactly docs/phase7-approvals/07-03.json, frontend/scripts/generate-api.mjs and
  frontend/scripts/generate-api.test.mjs; closeout adds this file, STATE and ROADMAP.
- Owner response: "Manter WebSocket/STOMP conforme RT-01". Reference:
  conversation:2026-10-04:owner-protocol-stomp. It resolves the transport conflict
  and supersedes the SSE proposal for 07-07; transport implementation is pending.
- No push, worktree, Antigravity, installation or dependency changes.

## Delivery

- CLI --candidate <dir> --candidate-source <file> --candidate-approval <JSON>;
  source/approval flags are paired and restricted to candidate mode. Legacy
  candidate/check/generate behavior and governed selectors/manifests are retained.
- Canonical approval passes the central preparation gate. Exact source hash,
  new tags/models, changed/deleted paths, metadata scope, complete governed file
  baseline and PLAN allowlist are checked before actual generation.
- Candidate input is frozen in owned scratch; existing lock, engine verification,
  materialization and cleanup are reused. Links, nonfiles, path escape, overlap,
  stale/pending/synthetic approval and changed source/approval/baseline fail closed.
- Inventory v2 covers the full normalized tree and every added/modified/deleted/
  unchanged generated path, including real before/after content and SHA-256 for
  changes, proposed governed YAML bytes, endpoint/schema/operationId inventories,
  source/config/engine hashes. Unexpected removals fail. Candidate YAML survives
  metadata refresh; scratch remains ephemeral and is not a hidden persistent client.
- Real Java engine proof extends a full official YAML in an isolated fixture,
  generates Chat service/model and the api/model barrels. index.ts is unchanged;
  exact changed scope contains four paths. Official repository bytes stay intact.
- Synthetic fixtures are confined to disposable test roots; they never supply
  production owner/legal authorization. No chat HTTP, STOMP or Angular journey is
  claimed by this tooling foundation; TEST-03/05 remain certification-level work.

## Verification

| Command/evidence | Result |
| --- | --- |
| node scripts/validate-phase7-approvals.mjs --plan 07-03 --stage implementation | APPROVED before edits and before commit at original HEAD |
| node --test frontend/scripts/generate-api.test.mjs | Final 98/98, 0 failures/cancelled/skips/todo; 17.856 seconds |
| Real approved Java engine test | PASS; 8.869 seconds in final focal run |
| node --test scripts/*.test.mjs | Final 883/883, 9 suites, 0 failures/cancelled/skips/todo; 508.756 seconds |
| node scripts/maven.mjs -f backend/pom.xml verify | BUILD SUCCESS; Surefire 361/37 classes, Failsafe 613/123 classes; 0 failures/errors/skips |
| Fresh report XML | All 160 refreshed since this execution; authoritative total 974, wrapper duplicates to 1948 |
| Scoped git diff --cached --check | PASS; exact three technical paths staged |
| Protected documents/migrations/API/config/engine/generated tree | SHA-256 baselines preserved |

Initial TDD RED: eight candidate behavior tests failed with the old generator,
which ignored the new source/approval options. Final tests add 25 cases to the
existing 73. Negative cases cover omitted/extra scope, missing baseline, invalid
selectors, escaped/missing/directory/junction inputs, source/config/API/client/
approval drift, unexpected removals and CLI misuse. Explicit removal approval
checks deleted and modified content separately. No harness-only result substitutes
for the real Java test.

## Self-healing and review

- Real generator also modifies api/api.ts and model/models.ts. The fixture's exact
  inventory was corrected to include those changes and omit unchanged index.ts.
- Review found concentrated authorization complexity and a growing executor;
  extracted approval, path, selector, baseline, initialization, drift and candidate
  completion helpers. Repeated focal and Node aggregate pass after this refactor.
- RepoWise change review still reports 12 medium/low advisory markers (bounded
  filesystem repetition, conditions, function size and one test callback). Its
  inherited journalPaths attribution is not an edit to that function. The JSON
  approval skipped by static health analysis was reviewed manually and validated.
  No functional finding; coverage recommendations are inferred, not measured.
- Python executable was unavailable during a local extraction attempt; it made no
  edit. Existing Node runtime performed the extraction; no installation required.
- Successful Maven wrapper removes its temporary log. A later newest-log lookup
  selected an old failed run from 2026-10-03; session exit 0/BUILD SUCCESS and fresh
  XML are the evidence for this run. No unrelated formatter repair was performed.

## Frozen implementation snapshot

HEAD before commit: 264ff28cf89df52ae17f9ab2da0cd8945ecd6338. Final working hashes:

| Path | SHA-256 |
| --- | --- |
| docs/phase7-approvals/07-03.json | 62b78adf166aebd7b7a069542965bc1494eb62f7e18c4de994809df0dda61881 |
| frontend/scripts/generate-api.mjs | dae88a518f56f43870803250a69d004c766296475d618e3ca262b1c61a3b79cd |
| frontend/scripts/generate-api.test.mjs | bde8a71fa1c99448b637d094640054a6175d779f28a8919ffd055d5b67a27c73 |

Hashes and exact stage were rechecked before technical commit be45ed1edc8e13b441f7da79d85e57947d26e2ec.
Owner-document changes remain outside commits. Approval becomes historical after
HEAD advances; it is not silently rebound. Documentary commit is this SUMMARY's
commit and cannot contain its own hash.

## Continuation

Phase 7 is 3/35 plans complete, not certified. Continue 07-04, then 07-28 in Wave 2,
under the same owner authority. Preserve pending legal/operational evidence limits.
