---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '01'
wave: 1
status: complete
subsystem: governance
tags: [node, approvals, sha256, fail-closed]
completed: 2026-10-04
technical_commit: 6e62b0ee4c3da2b165f0d33577df4d4a476f4bab
requirements-addressed: [TEST-03, UX-01, UX-02]
requirements-completed: []
provides:
  - Scoped approval validator and historical Wave 1 authorization
  - Decision registry with explicitly pending consumer gates
affects: [phase-07-consumers]
tech-stack:
  added: []
  patterns: [stage-specific-authority, frozen-digests, fail-closed-consumers]
coverage:
  - id: approval-validation
    description: Plan/stage/HEAD/scope/hash and pending-gate validation
    verification:
      - kind: unit
        ref: scripts/validate-phase7-approvals.test.mjs
        status: pass
    human_judgment: false
---

# Plan 07-01 Summary

Delivered scoped execution governance; no chat API, schema or user journey is claimed.

## Authority And Scope

- The owner explicitly requested all Wave 1 plans inline on the current master,
  including file creation, Maven/Node tests, mechanical self-healing, implementation
  and documentary commits; no push, Antigravity or worktrees. Wave 1 contains only 07-01.
- Reference: `conversation:2026-10-04:owner-start-phase7-wave1-inline-master`.
  It supersedes earlier planning-only authority for this wave. Future UI, transport,
  legal, product-policy and operational checkpoints remain pending.
- Base: `c8ffac21d8b800ac912cf899f04a1391dbf0a689`. Two tasks completed: record the
  authorized checkpoint and implement/test the validator. Four technical files,
  followed by this SUMMARY, STATE and ROADMAP. No dependency changes.

## Delivered

- `docs/phase7-approvals/07-01.json` records actual owner authority, its HEAD,
  plan/decision digests, exact task paths/commands and preservation hashes for
  the three owner documents and V1-V38. Local closeout permission is recorded
  separately from material implementation scope.
- `docs/phase7-decisions.json` transcribes D-01..40 and UI-D01..07 and lists the
  applicable pending gates per consumer. Proposals and historical D-40 authority
  remain distinguished from current execution permission and measured evidence.
- `scripts/validate-phase7-approvals.mjs --plan 07-NN --stage
prepare-candidates|implementation` fails closed for missing/revoked/pending
  authority, wrong stage/plan/HEAD, changed plan/decisions/baseline/candidates,
  unapproved files/commands, marked fixtures, unsafe paths and applicable pending
  gates. Approved gates require a human reference and nonempty evidence hashes.
- Stage scope is derived from the actual PLAN tasks. Implementation also requires
  the two aggregate verification commands and every declared candidate digest.
  CLI has no fixture/root/HEAD override; programmatic injection is for isolated tests.
- Bootstrap is only creation of the 07-01 validator. Consumers must stop on a
  nonzero validator exit before mutation. The validator checks authority; it does
  not itself execute commands or prove chat behavior.

## Verification

| Command / evidence                                       | Final result                                                                                        |
| -------------------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| `git status --short`                                     | Initial owner-only three-path dirty baseline identified and preserved                               |
| `node --test scripts/validate-phase7-approvals.test.mjs` | 31/31, zero failures/cancelled/skips/todo                                                           |
| `node --test scripts/*.test.mjs`                         | Final 843/843, 9 suites, zero failures/cancelled/skips/todo; 393.989 seconds                        |
| `node scripts/maven.mjs -f backend/pom.xml verify`       | BUILD SUCCESS; Surefire 361/37 classes, Failsafe 613/123 classes; zero failures/errors/skips        |
| XML freshness                                            | All 160 reports refreshed in this execution; authoritative total 974, not wrapper's duplicated 1948 |
| Real 07-01 CLI before commits                            | APPROVED at the original c8ffac2 HEAD                                                               |
| Real unapproved 07-02 CLI                                | Exit 1, BLOCKED: APPROVAL_FILE; no consumer execution                                               |
| Read-only PLAN compatibility                             | 35 implementation and 16 preparation contracts parsed without errors                                |
| Scoped Prettier / git diff and staged diff checks        | PASS; existing Windows line-ending notices only                                                     |
| Protected bytes                                          | Three owner documents and 38 applied migrations unchanged                                           |

Tests were written before production: initial RED was missing validator. Final
discrimination removes the approval-status guard only in a temporary copy and
requires the pending-approval test to fail. A separate consumer simulation proves
missing validator and denied approval prevent downstream mutation. Temporary test
roots are cleaned. An earlier full Node run passed 842 tests before the additional
mutation proof; the final aggregate includes all 31 new tests.

Maven exercises the existing backend and V1-V38 in isolated Testcontainers. It
does not apply V39 or modify the owner's local seeded database. Backend manifests
already specify Java 25/Boot 4.1/Modulith 2.1; the supplied Java 21/Boot 3 context
was stale. Existing manifests were used without dependency changes.

## Frozen Review

Frozen at `2026-10-04T20:15:22.016Z`, HEAD `c8ffac21d8b800ac912cf899f04a1391dbf0a689`,
empty index. Status contained exactly the four new technical paths plus the three
pre-existing owner-document paths. All four working-copy hashes were rechecked
unchanged before stage/commit; staged scope contained only the four technical paths.

| Working-copy path                            | SHA-256                                                            |
| -------------------------------------------- | ------------------------------------------------------------------ |
| `docs/phase7-approvals/07-01.json`           | `c2748c5c94c7fa9f701ae1251645f90cea1b76315d816a65d7dee40fc64e0f24` |
| `docs/phase7-decisions.json`                 | `6da3080e26da4b88afeba9b6b5d0d46c3d43dfe7dea8469d1fc4afb8086816e0` |
| `scripts/validate-phase7-approvals.mjs`      | `a0a3f7f588206ab7641138dd096ac1a6c04562707d6a6c75d7120d0219e33f46` |
| `scripts/validate-phase7-approvals.test.mjs` | `038d26b2f1cd42a7faf89ee2ffae1a12755980bc7e55a725f752c06cfdf0194f` |

Inline Codex source review found no functional defect. RepoWise change-risk
review was partial: two scripts analysed, two JSON files reviewed manually. Its
nine markers include complexity 17/11, nesting 4, long validation/fixture functions,
per-file reads and synchronous Git lookup. Per-file I/O is inherent to digest/link
checks; Git lookup is a local CLI preflight, not an HTTP request handler. Complexity
remains a maintenance risk. Test reach is inferred, not measured line coverage.
There was no independent reviewer agent or general security certification.

## Mechanical Corrections And Limits

- Nested Node mutation runner inherited NODE_TEST_CONTEXT and initially skipped
  its child suite; removing that variable only from the child fixed the test.
- JSON formatting changed decision bytes; the still-open bootstrap record's
  digest was recalculated before validation/freeze. No approval/HEAD refresh after commits.
- Maven paused while closing historical Hikari pools/Testcontainers. Read-only
  `jcmd` showed cleanup and subsequent progress; no environment reset or code patch.
- Historical planning STATE/CONTEXT HEAD/authority was superseded by the owner's
  explicit current-HEAD request. Historical paragraphs are preserved.
- Local metadata and hashes do not cryptographically authenticate a conversation
  or resist an operator replacing both evidence and digests. Owner references
  require honest review. Marked fixtures cannot authorize real consumers.
- The SUMMARY template's requirement-completion field stays empty deliberately:
  this foundation supports TEST-03/UX-01/UX-02 but delivers no UI/business journey.
  Whole-phase VALIDATION remains draft; this SUMMARY is the plan's actual evidence.

## Git And Next Wave

Implementation: `6e62b0ee4c3da2b165f0d33577df4d4a476f4bab`,
`feat(07-01): add scoped phase 7 approval validation`.
Closeout: the subsequent local commit,
`docs(07-01): finalize plan 07-01 summary and update state`.
No push. Config remains interactive with auto-advance disabled.

Wave 1 is complete, Phase 7 is 1/35. Next Wave 2 comprises 07-02/03/04/28 and is
outside this execution scope. Each consumer needs its own current authority and
applicable gates. Commits intentionally make the original 07-01 HEAD-bound
approval stale for another execution; it is an audit record, never silently renewed.
The three owner documents remain the only pre-existing dirty/untracked baseline.
