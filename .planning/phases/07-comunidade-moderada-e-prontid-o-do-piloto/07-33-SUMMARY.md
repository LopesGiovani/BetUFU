---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '33'
wave: 11
status: complete
subsystem: chat-rules-preference
technical_commit: 9e7fdec
completed: '2026-10-07'
requires: [07-01, 07-05, 07-27]
provides: [V47-chat-rules-dismissal, private-account-preference, privacy-boolean-accessor]
affects: [07-34, 07-35, 07-31]
requirements: [CHAT-01, TEST-03, UX-01, UX-02]
actuals:
  files: 11
  commits: 2
tech-stack:
  added: []
  patterns: [append-only-account-preference, server-resolved-owner, idempotent-insert]
---

# Plan 07-33 Summary

V47 stores the first chat-rules dismissal per account. The private GET and POST
return only `dismissed`; closing the popup never grants chat write permission,
changes mute state or constitutes legal consent.

## Authority and scope

- Inline master under the owner's continuous Phase 7 implementation, test,
  self-healing and two-local-commit authority. No push, worktree or Antigravity.
- Ten planned paths plus the existing decision registry entry for the required
  rules-preference gate. Decision registry reconciliation is governance only;
  no dependency, persistent owner service, volume or seed was changed.
- Frozen evidence: `.git/gsd/phase7/07-33-frozen.json`.
- Approval validator passed against base `0031845e596a7b07b8a8ee3ed2c97e08bb2cae41`.
  Protected baseline hashes remained unchanged, including V1/V46 and owner docs.

## Delivered behavior

- V47 follows V46, with identity FK, account primary key, immutable timestamp,
  append-only trigger and runtime SELECT/INSERT grants. Runtime UPDATE, DELETE
  and TRUNCATE are revoked. Candidate and governed SHA256:
  `3e1578fa50dea880d250d525295b17316a188a7dabf888c4bb4baa0aab220823`.
- Session identity is resolved by existing chat access ports. No account ID is
  accepted from a request. Anonymous access is denied and POST requires CSRF.
- Missing preference reads false; concurrent/repeated POSTs return true while
  retaining one row and the initial timestamp. A second session reads the
  persisted result; another account remains false.
- Responses use private/no-store cache protection. ChatPrivacyAdapter reads
  the minimal boolean via ChatRulesPreferencePort, with isolated test coverage.
- Preference classes are physically under internal/preferences but declare
  the chat.internal package to reuse package-private session access within the
  approved file scope. No other module's internal package is imported.

## Verification

| Evidence | Result |
| --- | --- |
| Literal focal Maven command | 16/16, zero failures/errors/skips |
| Full Maven verify | BUILD SUCCESS; XML Surefire397 + Failsafe770 =1167, zero failures/errors/skips |
| Wrapper log summary | 2262 reported; XML counts above are authoritative |
| Final Node aggregate | 943/943,9 suites; zero fail/cancel/skip;355062.8451ms |
| Angular | 500/500,80 files; zero failed tests |
| Generator/lint/production build | PASS; existing component style-budget warnings remain |
| Approval, protected hashes and diff checks | PASS |

## Self-healing and review

- Spotless initially rejected the two new tests; official spotless:apply fixed
  formatting. Full Maven was repeated after all final changes.
- An additional timestamp assertion needed an explicit RowMapper<Instant> to
  avoid Java overload ambiguity; focal tests then compiled.
- A Vary first-value assertion incorrectly assumed Cookie was the first header
  value emitted with Spring's origin filter. It was removed; the private/no-store
  assertion, session isolation and CSRF checks remain discriminating.
- Native review covered new files and the exact diff. RepoWise review was
  partial and raised a cohesion advisory for the existing privacy adapter;
  its methods share JdbcTemplate and no functional/security defect was established.

## Limits and next work

This is the backend foundation. OpenAPI/client publication is 07-34 and the
actual popup with browser evidence is 07-35. The privacy adapter exposes the
boolean seam; full restricted-export composition of this preference is not
claimed by this slice and must be checked during final integration. Other-class
expurgo remains blocked by the ratified retention policy. Continue Wave12 with
07-19, followed by the DAG successors; public-pilot approval remains separate.
