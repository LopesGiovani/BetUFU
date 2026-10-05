---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '14'
wave: 7
status: complete
subsystem: api-contract
requires: [07-03, 07-13, 07-11, 07-06]
provides: [official-report-contract, official-account-mute-contract, generated-community-client]
affects: [07-15, 07-16, 07-18, 07-19]
requirements-completed: []
actuals:
  tasks: 4
  commits: 2
tech-stack:
  added: []
  patterns: [candidate-generation, private-dto-allowlist, etag-versioning]
coverage:
  - id: generated-community-contract
    description: Official generator publishes private report and account mute operations
    verification:
      - kind: unit
        ref: frontend/scripts/generate-api.test.mjs
        status: pass
    human_judgment: false
technical_commit: 8954ef5
completed: '2026-10-05'
---

# Plan 07-14 Summary

The generated Angular client now exposes the V42 report and account mute
contracts. Reports use a closed reason enum and minimal private receipts; mute
commands use account versions and quoted If-Match/ETag values.

## Authority and scope

- Inline master, base a3588a4a5b54b61ba03c94448ef5bfabe3e21558. Continuous owner
  Phase7 execution, self-healing, tests and local commits apply; no push.
- The existing Chat service was extended instead of creating a duplicate service.
  Six generated deltas plus governed YAML, generator lists/tests and five
  candidate metadata files were reconciled to sixteen technical paths.
- Real engine generated203 files. Full generated digest:
  9BB66884F67583582824EED26BA105B3A8776CE87F26092C0E361BAA4DF19FAF.
  Candidate/governed source SHA256:
  6D206BEA204E2D7AF1CA97270E30E0CDBF146CF8F33123F177FC2AC849D03045.
- All252 protected baselines and seven publication hashes match. Owner documents
  and deltas excluded; no dependency, migration, seed, volume, push, worktree or
  Antigravity operation.

## Delivered

- POST `/api/v1/chat/messages/{messageId}/reports` is session-authenticated and
  accepts only DISCURSO_DE_ODIO, SPAM, OFENSA, ASSEDIO or FRAUDE. The actor is
  derived from the session; no reporter, original text or sanction fields enter
  the public DTO. Retry uses the canonical Idempotency-Key.
- GET mute list/target and PUT/DELETE target are private account operations.
  PUT carries expectedVersion; DELETE carries quoted If-Match; responses expose
  version and participantIds with ETag. No account owner id is request data.
- Error contracts retain 400/401/403/404/409 semantics. Generated paths remain
  relative and do not add Authorization or Cookie headers.
- Generator allowlists and complete-tree digest are updated through the existing
  generator; generated TypeScript is never edited manually.

## Verification

| Evidence | Result |
| --- | --- |
| Generator before publication | RED105 tests,3 expected failures |
| Final generator focus | 105/105;0fail/cancel/skip/todo;13117.3863ms |
| Literal prepare/implementation gates and generate:api:check | PASS |
| Angular focused retry | 3/3 ranking tests |
| Angular aggregate | 455/455,75 files;8.55s |
| Lint and production build | PASS; one existing diagnostic style warning (86 bytes) |
| Node aggregate | 936/936,9 suites;0fail/cancel/skip/todo;370705.1297ms |
| Maven verify | BUILD SUCCESS,exit0;361 Surefire/37classes +693 Failsafe/133classes =1054;0fail/error/skip |
| Candidate/public hashes, scope, frozen index and diff checks | PASS;16 exact paths |

The Maven wrapper reports2108 because it duplicates the XML totals. Fresh XMLs
from this run are authoritative. Evidence and frozen review are in
`.git/gsd/phase7/07-14-evidence.json` and `.git/gsd/phase7/07-14-frozen.json`.

## Self-healing and limits

- The original generated-service path count was stale after adding five valid
  community operations. The test was changed to assert each read path directly;
  final generator focus passed 105/105.
- An unrelated Angular ranking test timed out once under aggregate load; its
  isolated 3/3 run and the complete repeat passed without changing its timeout.
- RepoWise found one medium conditional marker in an unchanged generator helper;
  generated and skipped files were checked by inventory and exact hashes. No
  functional finding remained.
- The formal cookieSession OpenAPI declaration gap remains inherited. This plan
  proves contract generation, not browser writing, public/legal or institutional
  certification.

Phase7 is14/35. Wave7 continues with07-16; moderation cases, UI journeys and
public/legal/institutional gates remain pending.
