---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '27'
wave: 10
status: complete
subsystem: privacy-retention
technical_commit: 3a397f2
completed: '2026-10-07'
requires: [07-02, 07-16, 07-17, 07-24]
provides: [V46-privacy-retention, restricted-exports, suppression-register]
affects: [07-15, 07-25, 07-29, 07-33, 07-32]
requirements: [PRIV-05, CHAT-06, TEST-03]
actuals:
  tasks: 4
  files: 23
  commits: 2
tech-stack:
  added: []
  patterns: [consumer-owned-privacy-port, append-only-retention-facts, shared-input-exclusive-apply-lock, current-suppression-redaction]
coverage:
  - id: retention-and-suppression
    description: Common messages expire after 90 days and moderation evidence 180 days after case closure; open/reopened cases, pending review and holds remain blocked.
    requirement: PRIV-05
    verification:
      - kind: integration
        ref: backend/src/test/java/br/com/betufu/privacy/PrivacyRetentionIT.java
        status: pass
      - kind: integration
        ref: backend/src/test/java/br/com/betufu/privacy/PrivacyExportIT.java
        status: pass
  - id: restricted-partial-export
    description: Owner and general administrator access is session-bound; missing producers remain explicitly blocked and incomplete exports cannot satisfy access approval.
    requirement: PRIV-05
    verification:
      - kind: integration
        ref: backend/src/test/java/br/com/betufu/privacy/PrivacyExportIT.java
        status: pass
  - id: modular-append-only-foundation
    description: V46 protects immutable facts with triggers and runtime grants while chat consumes privacy through public producer-owned ports.
    requirement: TEST-03
    verification:
      - kind: integration
        ref: ModulithArchitectureTests
        status: pass
      - kind: other
        ref: cross-module internal import audit
        status: pass
---

# Plan 07-27 Summary

V46 adds the privacy retention foundation and independently current suppression
register. The implementation records immutable suppression and hold facts,
restricts runtime access, and exposes a dry-run inventory with a stable digest.
Only `COMMON_MESSAGES` and `MODERATION_EVIDENCE` have an approved policy:
90 days from message creation and 180 days from case closure respectively.
Sanctions, progression and minimum audit history remain preserved, while all
other classes stay explicitly `POLICY_BLOCKED`.

## Authority and scope

- Direct inline execution on `master`, with continuous owner authority for
  Phase 7 implementation, tests, self-healing, two local commits and wave
  advancement. No push, worktree or Antigravity was used.
- Technical commit `3a397f2`; this summary and state/roadmap update form the
  documentary commit.
- Twenty-three paths were reconciled: fourteen primary paths and nine
  supplemental policy/serving links. The candidate and governed V46 are
  byte-identical, SHA-256
  `BF7C08C2AFB762F6A17F257368A2D282D36051245194D61E9584E02F738691A5`.
- Protected documents and migrations V1-V45 retain their baseline hashes.
  No dependency, seed, owner database, persistent volume or external service
  was changed.

## Delivered

- `PrivacyRestrictedRecordsPort` is privacy-owned; identity, sports,
  verification, ledger, settlement and chat provide minimum restricted records
  through consumer-owned adapters without cross-module internal imports.
- V46 contains immutable suppression, hold and restricted-export facts plus a
  rebuildable chat serving projection. UPDATE/DELETE triggers protect history;
  runtime has no retention-role membership and no automatic apply scheduler.
- `dryRun` calculates deadlines, holds, pending cases and policy blocks. `apply`
  requires a current general administrator, an existing segregated retention
  role, an exact digest and eligible targets in one transaction.
- Input facts use shared advisory locks; retention apply uses the exclusive
  advisory lock. This preserves the reversed-commit chat delivery behavior
  while keeping the apply window consistent.
- Exports are restricted, partial when unsupported classes have no producer,
  and re-redacted against current suppressions/removals at download time.
  `AUDIT`, `LOGS` and `BACKUPS` remain blocked until their policies/producers
  exist. Physical erasure is not enabled.

## Verification

- Approval validator: `APPROVED: 07-27 implementation`.
- Privacy/architecture focal: 82 tests, 0 failures, 0 errors, 0 skips.
- Literal privacy focal: 46 tests, 0 failures, 0 errors, 0 skips.
- Chat push self-healing focus: 734 tests, 0 failures, 0 errors, 0 skips.
- Maven `verify`: 2,248 tests, 0 failures, 0 errors, 0 skips; Flyway applied
  V46 in disposable Testcontainers databases.
- Node aggregate: 937 tests in 9 suites, 0 failures, skips or todos.
- `git diff --check`, path reconciliation, protected hashes, candidate/governed
  equality and cross-module import audit passed.

## Self-healing and residuals

- The first aggregate run exposed a chat fixture timeout caused by exclusive
  advisory locks on chat input inserts. Input triggers now use shared locks,
  while retention apply retains the exclusive lock; focused and aggregate runs
  both passed.
- An earlier architecture run exposed a public export digest field. The hash
  remains in the immutable export fact, while the HTTP DTO no longer exposes
  it. No architecture rule or existing test was changed.
- Legal review, institutional identity, public-pilot approval and policies for
  blocked data classes remain pending by design.

## Next

Wave 10 is complete. The DAG now advances to Wave 11, beginning with 07-15,
followed by 07-25, 07-29 and 07-33.
