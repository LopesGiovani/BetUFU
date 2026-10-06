---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '18'
wave: 9
status: complete
subsystem: api-contract
technical_commit: a67f357facef44159b220d91e03947daa66d12f7
completed: '2026-10-06'
requires: [07-03, 07-16, 07-17, 07-14, 07-06, 07-11]
provides: [official-moderation-contract, restricted-evidence-client, private-sanction-review-client]
affects: [07-19, 07-31, 07-32]
requirements-completed: []
actuals:
  tasks: 4
  execution_slices: 4
  commits: 2
  files: 24
tech-stack:
  added: []
  patterns: [consumer-contract-reconciliation, exact-candidate-publication, private-dto-allowlists]
coverage:
  - id: official-moderation-api-client
    description: Eleven real Spring operations and twelve exact private request/response models generated officially
    verification:
      - kind: unit
        ref: frontend/scripts/generate-api.test.mjs
        status: pass
    human_judgment: false
---

# Plan 07-18 Summary

The official Angular Chat service now exposes cases, removal, forwarding,
restricted evidence, global sanctions and private sanction reviews. The governed
contract follows the tested Spring controllers from07-16/17.

## Authority and scope

- Continuous owner Phase7 inline/master implementation, self-healing, tests,
  local code/documentation commits and advancement authority applies. Base
  b1a6f7cbf453321d03e2462a488d0788d8b29c2d. No fabricated human digest response.
- Four sequential slices contain15/14/14/13 primary paths. Accepted hashes and
  complete inventories are preserved in .git/gsd/phase7/07-18-slice1..4.json.
  The final union is24 technical paths: four source/approval paths, five candidate
  artifacts, fourteen generated paths and supplemental PLAN bookkeeping.
  The approval records every slice and the entire union. Cap15 is unchanged.
- Existing Chat tag/service avoids a duplicate service and unchanged API barrels.
  All twelve models, service and model barrel came from the approved real engine;
  no generated file was edited manually.
- Final 215-file generated digest: E0F2DF3EC108FDEA1B380B300612B2D1704B936635445D40924CA696C21285E3.
  Candidate/governed source SHA256: 8DB1D1BA742AD597F6497B63488BB5761E6B8015FC0967912B7D75155F7C7582.
  Fifteen accumulated publication hashes and46 protected hashes match.
- No new dependency or migration, persistent database change, volume/seed reset,
  push, worktree or Antigravity. Owner documents and V1-V44 remain untouched.

## Delivered

- GET/POST cases and POST case/remove match actual producer requests and distinct
  CaseReceipt responses. Queue status is OPEN/IN_REVIEW/RESOLVED/ESCALATED;
  event/competition are nullable for global context. Actor and scope come from session.
- POST case/forward returns its actual forwarding receipt. Obsolete message-removals
  and escalations aliases are absent. Forwarding does not imply punishment.
- GET case/evidence returns restricted audited JSON and requires a nonblank reason.
  Original text appears only in its dedicated evidence model; queue and report
  models omit reporter identity. Every successful evidence access is audited by
  the existing backend; the generator is not an authorization enforcement layer.
- Own sanctions remain private, including inactive enrollment. General administrators
  exclusively suggest/confirm24h,7d or ban sanctions. Ban expiry is nullable.
  These commands do not modify credits, predictions or ranking.
- Owners request private review; administrators read pending reviews and append
  MAINTAIN/ANNUL decisions. Each request/response has exact fields, canonical
  command UUID, bounded nonblank reason, closed enums and no supplied actor/scope.
- Successful responses are private/no-store. Contract errors declare400/401/403/404/409.
  Evidence reason uses the producer's280 UTF-16-unit limit explicitly in its description.

## Verification

| Evidence | Result |
| --- | --- |
| Discriminating RED against old client | Three new moderation tests failed as expected |
| Literal candidate/implementation gates, official generation and checks | PASS in all four slices |
| Final generator focus | 108/108;0fail/cancel/skip/todo;11862.4691ms |
| Angular aggregate | 455/455;75 files;21.41s |
| Lint and production build | PASS;14 warnings in unchanged style files |
| Node aggregate | 936/936;9 suites;0fail/cancel/skip/todo;305602.731ms |
| Maven literal verify | BUILD SUCCESS,exit0;1084/1084: 361 Surefire/37 classes + 723 Failsafe/139 classes;0fail/error/skip |
| Full union stage, exact bytes/hashes and protected data | PASS;24 paths,46 protected hashes |

Fresh Maven XMLs are authoritative; the wrapper duplicates totals. Regression
tests compare generated models with Java record fields, mandatory evidence reason,
exact paths/methods/body/return types, privacy and authority documentation. Mutants
remove security, change routes/receipts, add private fields, remove evidence reason,
broaden administrator authority and add an invalid review decision; each is rejected.

## Self-healing and review

- Preparation initially lacked the explicit governed YAML baseline inherited from
  the prior approval. Added its current hash; the unchanged generator then passed.
- One authority mutant initially survived because the same wording remained in
  a second description. The test now checks the exact summary declaration and
  rejects that mutant. No runtime authority was weakened.
- An inherited full-tree digest literal was updated to the measured final tree;
  complete generator focus passed108/108 after the correction.
- Readonly RepoWise analysis covered3/18 filtered code paths; fifteen generated
  or non-analyzable paths require manual review. The new test helper's complexity16
  and nested assertions are a maintenance cost. journalPaths was attributed by
  file change but its body is unchanged. Exact inventories, producer source and
  discriminating tests cover those skipped paths. No functional defect found.
- Evidence, frozen status/hashes and partial review live under
  .git/gsd/phase7/07-18-{evidence,frozen,review}.json.

## Limits and continuation

This is contract/client delivery. Operator and account browser journeys remain
07-19/31/32; CHAT-05/06 and TEST-03 are not claimed fully certified here.
The inherited cookieSession references still lack a formal securitySchemes
declaration; actual session authorization belongs to the certified backend.
Legal, institutional, public-pilot, load and restore gates remain separate.

Phase7 is18/35, global153/170. Continue07-21, then07-24 in Wave9 under continuous
authority. Approval becomes historical after HEAD advances; it is not rebound.
