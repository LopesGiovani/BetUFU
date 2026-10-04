---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '02'
subsystem: privacy
status: complete
scope: documentary-foundation-only
requires: [07-01]
provides: [fail-closed-policy-validation, honest-pending-legal-institutional-publication-records]
affects: [07-24, 07-27, 07-29, 07-30, 07-32]
requirements-completed: []
actuals:
  tokens: 9522
  tasks: 2
  commits: 2
tech-stack:
  added: []
  patterns: [evidence-digest-validation, fail-closed-launch-gates]
coverage:
  - id: P7-policy-foundation
    description: Pending, proposed or incomplete policy and altered evidence block release
    verification:
      - kind: unit
        ref: scripts/validate-phase7-policy.test.mjs
        status: pass
    human_judgment: false
  - id: P7-real-legal-evidence
    description: Real legal review, institutional identity and publication inspection
    verification: []
    human_judgment: true
    rationale: Real documents remain pending; tests cannot supply legal or institutional evidence
started: '2026-10-04T20:54:07.634Z'
completed: '2026-10-04T21:20:11.408Z'
duration: 27min
---

# Plan 07-02: Privacy and publication policy foundation

Prepared three honest pending records and a validator that rejects missing review,
retention authority, identity, verified-holder procedures or publication evidence.
Development preparation is complete; PRIV-03/04/05 and public launch are not certified.

## Authority and scope

- Owner continuously authorized all remaining Phase 7 plans/waves, inline master,
  Maven/Node tests, mechanical self-healing, two local commits per plan and
  closeout. Reference: conversation:2026-10-04:owner-continuous-phase7-wave2-through-completion.
- Base: 8bc193df1dabd7e4308c991d0bacd6556f3affbf. Technical commit: 5bbee7e884d10e1e862805a324dd4d201425cb24.
- Six technical paths only, plus SUMMARY/STATE/ROADMAP closeout. No push,
  Antigravity, worktrees, dependency changes or external legal/operational calls.
- Execution authorization does not constitute legal review or approval of
  proposed periods, institutional identity or public identifiers/exceptions.

## Delivery

- Common messages: proposed 90 days. Moderation originals: proposed 180 days
  after case closure, held during open case/review/legal hold. No automatic purge.
- Minimum sanction/progression audit has a separate pending period; active
  sanctions and progression must survive content expiration.
- Classes cover athlete without account, ledger, audit, logs and backups.
  Rights cover notice, access, correction, objection, deletion and pseudonymization.
  Verified-holder procedures and response-operator authority remain pending.
- BETUFU records no official UFU affiliation; legal identity, domain, email and
  infrastructure operator require real evidence. Publication checklist includes
  pages, builds, images, documents, metadata and public history.
- Validator verifies evidence bytes/SHA-256 and path containment, rejects links,
  non-files, unresolved states, synthetic authority and missing policies. Passing
  documentary validation alone explicitly does not authorize launch.

## Verification

- Literal git status and implementation-approval CLI passed before materiality.
- RED: first focal failed with missing production module before its creation.
- Final focal: 40 tests, 40 pass, zero failures/cancelled/skipped/todo.
- Discriminating mutation disables nested approval guards: pending review is then
  accepted; the original implementation rejects it. Temporary mutation removed.
- Full Node: 883 tests in 9 suites, all passed; zero failures/skips/cancellations.
- Maven verify: BUILD SUCCESS. Fresh Surefire XML: 361 tests/37 classes; fresh
  Failsafe XML: 613 tests/123 classes. Total 974; zero failures/errors/skips,
  no stale XML. Wrapper's 1948 count double-counts summaries; XML is authoritative.
- Real policy CLI intentionally returns nonzero because review remains pending.
- Scope, protected baseline hashes, formatting and staged diff checks passed.

## Review and deviations

- Frozen inline review of HEAD/index/paths and the hashes below found no functional
  defect. RepoWise analysed both scripts and identified five inherent filesystem
  reads in loops; its recommendations did not require changing them. This is
  bounded CLI work, not a high-throughput service. JSON was manually reviewed.
- Formatting initially switched quote style and broke the mutation's source
  selector; restored the established single-quote style and re-ran focal tests.
  The final aggregate also includes all 40 new tests. No business rule changed.
- Local authority records provide traceability; an operator rewriting both
  evidence and digest can forge provenance. Hashes do not authenticate a legal
  review or replace inspection by an authorized reviewer.
- Owner documents, V1-V38 migration bytes and existing volumes/seed preserved.
  Config stays interactive/auto_advance=false; conversational authority controls
  continuous execution. No backend implementation or frontend journey delivered.

## Frozen technical hashes

| Path | SHA-256 |
| --- | --- |
| `docs/phase7-approvals/07-02.json` | `1929b517ca3d316d607a1b77d60163abe60a38f6d675128ef2caa9971852d3ce` |
| `docs/phase7-privacy-policy.json` | `16a98f628af30d151a965ced772e50835b31608e212c0f3d394939bb6c6c62fd` |
| `docs/phase7-institutional-identity.json` | `5147b0f89cb3d2c4c471bdbc3ca3f07f06eeb3c9d754a468912307bbba205dd6` |
| `docs/phase7-publication-checklist.json` | `3cfe4c2240bc7beeafbdeb6a7fb439020d6fb95868148f31d35c289c513455fe` |
| `scripts/validate-phase7-policy.mjs` | `7670b4fa991fa07057437e5a4361d61a2bc13b049e3776452ea2a426f628a83c` |
| `scripts/validate-phase7-policy.test.mjs` | `7bcefd98f3e4c7d8f6bacddcf4bdb969469e101b9a71cf6e35dc98293509724e` |

## Next

Continue 07-03 directly on master under the same authority. Protocol choice is
pending: RT-01 requires STOMP while the Phase 7 research proposes SSE; an async
question is open before the dependent 07-07 work. Legal/identity/operational gates
remain explicit and no Phase 7 requirement is marked complete by this foundation.
