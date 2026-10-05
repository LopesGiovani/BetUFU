---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '16'
wave: 7
status: complete
subsystem: moderation
requires: [07-13, 07-07, 07-05]
provides: [scoped-moderation-cases, restricted-evidence-access, V43-moderation-facts]
affects: [07-17, 07-18, 07-19, 07-20]
requirements-completed: []
actuals:
  tasks: 4
  commits: 3
tech-stack:
  added: []
  patterns: [consumer-owned-authority-port, append-only-moderation-facts, scoped-evidence-access]
key-files:
  created:
    - backend/src/main/resources/db/migration/V43__chat_moderation_cases.sql
    - backend/src/main/java/br/com/betufu/chat/ChatModerationAuthorityPort.java
    - backend/src/main/java/br/com/betufu/chat/internal/ChatModerationController.java
    - backend/src/main/java/br/com/betufu/chat/internal/ChatModerationService.java
    - backend/src/main/java/br/com/betufu/chat/internal/ChatModerationRepository.java
    - backend/src/main/java/br/com/betufu/chat/internal/ChatModerationModels.java
    - backend/src/main/java/br/com/betufu/identity/internal/ChatModerationAuthorityAdapter.java
    - backend/src/test/java/br/com/betufu/chat/ChatModerationIT.java
    - backend/src/test/java/br/com/betufu/chat/ChatEvidenceAccessIT.java
  modified:
    - backend/src/main/java/br/com/betufu/chat/internal/ChatRepository.java
key-decisions:
  - "Moderation uses consumer-owned authority through a public chat port; no cross-module internal imports."
  - "Removal preserves the original message and emits an append-only fact, public marker, and change cursor."
  - "Evidence access is limited to authorized scoped moderators and general administrators and is itself audited."
patterns-established:
  - "Historical moderation facts are append-only and protected against UPDATE and DELETE."
  - "Scoped moderation denies out-of-scope and revoked actors at runtime."
coverage:
  - id: scoped-moderation-removal
    description: "A scoped moderator can remove a message while preserving the original and publishing only a marker."
    requirement: CHAT-05
    verification:
      - kind: integration
        ref: "ChatModerationIT"
        status: pass
    human_judgment: false
  - id: restricted-evidence
    description: "Evidence is restricted and every access is recorded as an append-only audit fact."
    requirement: CHAT-06
    verification:
      - kind: integration
        ref: "ChatEvidenceAccessIT"
        status: pass
    human_judgment: false
---

# Plan 07-16 Summary

Scoped moderation cases and restricted evidence now form the V43 backend
foundation. Moderators act only within their granted channel scope, while
general administrators retain the global authority. Removal never deletes the
historical message: it appends a decision fact, exposes a public marker, and
advances the change cursor.

## Authority and scope

- Inline execution on `master`, base `b7c69a8`, with no worktree, Antigravity,
  push, dependency change, volume deletion, or seed reset.
- Technical commits: `f557856` (implementation) and `bba4d4e` (review fixes).
- The approved candidate and governed V43 migration are byte-identical:
  SHA-256 `9FC8EE740832A7D58D1A3E5142E5B9B15ED0E389B54938D8B613C6E8DB27E981`.
- The protected owner documents remained outside the stage.

## Delivered

- V43 stores moderation cases, reports, reviews, decisions, sanctions,
  forwarding facts, and evidence-access audit facts as append-only history.
- `ChatModerationAuthorityPort` is consumer-owned by `chat`; identity supplies
  the scoped authority adapter without importing another module's internal API.
- Removal keeps the original message private and emits only a public marker and
  cursor event. Duplicate commands are idempotent and conflicting reuse fails.
- Evidence DTOs omit internal identifiers and deny revoked or out-of-scope
  actors.

## Verification

| Evidence | Result |
| --- | --- |
| Approval validator | PASS: implementation approved at `b7c69a8` |
| Focal Maven | 8/8; zero failures, errors, or skips |
| Maven verify after final correction | BUILD SUCCESS; XML total 1062 (361 Surefire / 37 classes + 701 Failsafe / 135 classes); zero failures, errors, or skips |
| Node aggregate after final correction | 936/936; 0 failures, cancellations, or skips |
| Candidate/governed V43 digest and byte comparison | PASS |
| `git diff --check` and staged allowlist | PASS; 12 technical paths |

## Deviations and self-healing

- The first governed copy omitted the candidate's UTF-8 BOM. The byte comparison
  caught the mismatch before commit; the governed migration was recopied exactly
  from the approved candidate and all aggregate suites were rerun successfully.
- Read-only review then found a forwarding SQL timestamp mismatch and a removal
  cursor generated from an independent sequence. New tests reproduced both bugs;
  forwarding now reads forwarded_at and removal consumes the existing message
  cursor sequence without modifying V43. Runtime ACL and immutable-trigger tests
  were added to verify the migration's actual protection.
- Queue discovery now reconstructs cases from authorized reports, preserving one
  case per message and never applying automatic punishment. Its regression test
  failed before the correction and passed afterward.
- The initial technical commit preceded that complete review. A separate
  corrective commit preserves its history; final certification includes both.
- Wrapper totals double-count Maven output. Fresh XML totals are authoritative.
- CHAT-05/06 remain phase-level pending: this summary claims only the backend
  foundation, with global sanctions, generated contracts and UI journeys later.

## Next phase readiness

The scoped moderation and evidence foundation is ready for plan `07-17`, which
adds global sanctions and account review. UI journeys and legal, institutional,
public-pilot, load, and restore certifications remain separate gates.

---
*Phase: 07-comunidade-moderada-e-prontid-o-do-piloto*
*Completed: 2026-10-05*
