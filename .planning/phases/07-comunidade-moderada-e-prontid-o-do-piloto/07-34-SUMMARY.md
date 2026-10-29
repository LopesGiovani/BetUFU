---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '34'
wave: 12
status: complete
subsystem: chat-rules-api-contract
technical_commit: fada9f67cee9142eb36fd80c21a5353719bb505d
completed: '2026-10-28'
requires: [07-03, 07-25, 07-33]
provides: [private-rules-preference-contract, officially-generated-angular-client]
affects: [07-35, 07-31, 07-32]
requirements: [CHAT-01, TEST-03, UX-01, UX-02]
requirements-completed: []
actuals:
  tasks: 4
  files: 13
  commits: 2
tech-stack:
  added: []
  patterns: [exact-candidate-publication, session-derived-owner, closed-boolean-dto]
coverage:
  - id: private-preference-contract
    description: Session-private parameterless GET/POST and closed required boolean DTO match the certified Java producer
    verification:
      - kind: unit
        ref: frontend/scripts/generate-api.test.mjs
        status: pass
    human_judgment: false
---

# Plan 07-34 Summary

The governed contract and official Angular client expose the current account's
chat-rules dismissal preference. GET and POST return only dismissed:boolean;
account ownership remains session-derived and dismissal is not legal consent.

## Authority and delivery

- Owner explicitly ratified the prepared inventory/digests and authorized
  implementation, official generation, tests/self-healing, local stage/two
  commits and continuous advancement through Waves12-17. Recorded in the approval.
- Inline master, base2a2fda0; no worktree, Antigravity, dependency change or push.
- Exact candidate YAML published, two generator allowlists extended, two generated
  types added and two barrels regenerated. index.ts remained byte-identical.
- All230 generated entries match ratified bytes and sizes. Tree SHA256:
  72A2ACF318B8A1DBD1785F035AF322F136F761BA24167D00ED58077EB5790F38.
  Source SHA256:99808BA4CAEC6DA7595FCB828DCB9F77A44911017A92D2E95E98012CE1217F53.
- Responses retain private/no-store and Vary:Cookie; no supplied account/body.
  Runtime session/CSRF/ownership proofs belong to07-33; popup browser proof is07-35.

## Verification

| Check | Result |
| --- | --- |
| Literal implementation approval gate | PASS before mutation and before freeze |
| Official generation / generate:api:check | PASS; all230 entries match candidate |
| Generator suite |116/116; zero fail/skip |
| Node aggregate |944/944; zero fail/cancel/skip |
| Angular |505/505 in81 files |
| Maven verify |BUILD SUCCESS; fresh XML361 Surefire/37 classes +771 Failsafe/149 classes =1132; zero failure/error/skip |
| Lint / production build / diff check |PASS; pre-existing style-budget warnings |

Two new rules-preference tests discriminated prepublication behavior (RED2/2)
and passed after official generation; six contract mutations fail as required.
Existing candidate tests reject hash, baseline, path and authority drift.

## Reconciliation and review

- Generator initially rejected an out-of-order model allowlist entry; sorting it
  restored generation without weakening the guard. An old literal digest test
  was updated to the ratified tree digest and the full116-test suite repeated.
- Maven wrapper reports2264 by duplicate aggregation. Fresh XML1132 is authoritative;
  an old Surefire PasswordResetSessionTerminationIT report was excluded, not counted.
- Local evidence reader required UTF-16LE for PowerShell-redirected logs; no test rerun
  or production change was needed for that decoding correction.
-13 paths frozen and reviewed inline;237 protected hashes retained. Applied V1-V47
  and owner documents unchanged, including owner-removed manual-testing.md remaining absent.
- Frozen evidence:.git/gsd/phase7/07-34-frozen.json. RepoWise MCP was unavailable;
  source/producer/diffs, actual generated entries and test evidence were reviewed inline.
- No owner database, seed or volume change. The07-33 restricted export composition
  seam remains a final-integration check; external legal/public-pilot gates are separate.

## Next

Wave12 complete. Phase7 is29/35, global164/170; continue07-23/Wave13 under the
owner's standing authorization.
