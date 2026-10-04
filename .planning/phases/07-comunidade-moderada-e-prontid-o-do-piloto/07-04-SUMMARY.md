---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '04'
wave: 2
status: complete
subsystem: database
requires: [07-01]
provides: [chat-channel-message-schema, runtime-append-only-integrity, stable-keyset-foundation]
affects: [07-05, 07-09, 07-10, 07-13, 07-16]
requirements-completed: []
actuals:
  tokens: 18334
  tasks: 4
  commits: 2
tech-stack:
  added: []
  patterns: [immutable-facts, rebuildable-projection, runtime-grants-plus-triggers]
coverage:
  - id: chat-schema-runtime-integrity
    description: Runtime PostgreSQL enforces unique channels, immutable text facts and stable pagination
    verification:
      - kind: integration
        ref: backend/src/test/java/br/com/betufu/chat/ChatSchemaIT.java
        status: pass
    human_judgment: false
started: '2026-10-04T22:03:00Z'
completed: '2026-10-04T23:03:31.029Z'
technical_commit: fef7ce5fc20469e0065c606da9a5548ce89bea4f
---

# Plan 07-04 Summary

V39 separates immutable chat channels/messages from a rebuildable visibility
projection and proves database integrity through a distinct runtime user.

## Authority and scope

- Base 906d852d4edc6fdc6ff926a06ca3f5bc21738c95, inline master, no worktree/Antigravity.
- Owner continuously authorized Phase 7 files, V39+, tests, self-healing and two
  local commits. Reference: conversation:2026-10-04:owner-continuous-phase7-wave2-through-completion.
- Stage preparation validated before candidate creation; candidate digest validated
  and bound before implementation. Exact SQL copied to governed V39. Tests use
  disposable PostgreSQL containers only; persistent owner database/volumes untouched.
- Original five technical paths extended to seven for reproduced mechanical
  OddsMigrationIT count failure and its PLAN scope record. The actual authorization,
  original plan hash and reason are in the approval record; no invented owner response.
- No edits to V1-V38 or protected documents; no push or dependency changes.

## Delivered

- chat_channel distinguishes GLOBAL without event from EVENT with a valid event;
  partial unique indexes enforce one global and one channel per event. Global seeded
  once. Channel/message IDs are opaque UUIDs, not derived from event or author.
- chat_message preserves original text and timestamp, author/channel references and
  an identity change cursor. Channel keyset index orders timestamp plus UUID;
  incremental cursor index is channel-specific. Cursor is a technical sequence,
  not a claim of commit-order streaming completeness; consumer concurrency work remains.
- UPDATE/DELETE triggers protect channel/message facts even if mutation grants return;
  runtime SELECT/INSERT only, no UPDATE/DELETE/TRUNCATE, sequence USAGE/SELECT without
  setval. No cascade deleting history. Owner-only disposable resets remain possible.
- chat_message_projection stores visibility only, with VISIBLE/REMOVED states and a
  fact reference. Mutable grants allow reconstruction; original text stays in facts.
  Moderation facts/retention consumers later provide authoritative removal replay.
- Flyway count40 includes existing V5.1; newest applied version39 checked. No business
  Unicode/limiter, lifecycle, sanctions, HTTP/STOMP or Angular authorization is claimed.
  CHAT-01/02 and RT-01 are not certified complete by schema-only tests.

## Verification

| Command/evidence | Result |
| --- | --- |
| node scripts/validate-phase7-approvals.mjs --plan 07-04 --stage prepare-candidates | APPROVED before candidate creation |
| Get-FileHash -Algorithm SHA256 -LiteralPath candidate V39 | E0B9A9005E8DFE13F1D7786B4D145DFA50412928C12804E7EED436EC65041A0B |
| node scripts/validate-phase7-approvals.mjs --plan 07-04 --stage implementation | APPROVED before governed copy/tests and before closeout |
| node scripts/maven.mjs -f backend/pom.xml "-Dtest=ChatSchemaIT,FlywayMigrationIT" test | Final 29/29 XML: ChatSchemaIT9 + FlywayMigrationIT20; zero failures/errors/skips |
| node scripts/maven.mjs -f backend/pom.xml "-Dtest=OddsMigrationIT" test | 5/5, zero failures/errors/skips after reproduced count repair |
| node scripts/maven.mjs -f backend/pom.xml spotless:apply / spotless:check | Final PASS; only the three scoped Java files changed |
| node --test scripts/*.test.mjs | Final 883/883, 9 suites, zero failures/cancelled/skips/todo; 374.894 seconds |
| node scripts/maven.mjs -f backend/pom.xml verify | Final BUILD SUCCESS; fresh Surefire361/37 + Failsafe622/124, total983, zero failures/errors/skips |
| git diff --cached --check; frozen hashes/index/baselines | PASS; exact seven paths; protected owner docs/V1-V38/API/client/config/engine unchanged |

Freshness start for final aggregate: 2026-10-04T22:41:50.579Z. All 161 aggregate
XMLs refreshed. Three old Surefire IT reports from focal commands are excluded
(the same ITs are fresh in Failsafe). Wrapper totals1966 double-count summary lines.

Nine discriminating schema tests: runtime not owner/superuser; global singleton
and type aliases; unique event and real FK; exact nonempty body/author/channel;
grants including TRUNCATE; triggers after accidental UPDATE/DELETE regrant;
projection mutation/rebuild preserving originals; keyset equal-time/boundaries
and channel isolation; cursor/index/sequence/explicit identity restrictions.

## Self-healing and review

- TDD RED before V39: all nine new schema tests errored on missing tables; existing
  Flyway passed. GREEN after exact candidate copy: all29 focal tests passed.
- OddsMigrationIT RED expected39/actual40 reproduced after V39; corrected one count
  assertion and recorded scope extension under explicit continuous self-healing.
  Wrapper disallows method-selector #; complete class5tests used instead.
- Initial Spotless path filter returned success without formatting selected paths.
  First verify had all983tests green but formatting failed on ChatSchemaIT and two
  existing tests. Standard approved formatter changed only these scoped paths;
  isolated format check, focal, Node aggregate and literal full verify repeated green.
- Frozen review rerun after formatting. RepoWise analyzed5/7 files with no new health
  findings; PLAN/approval reviewed manually. Static result is partial, not global
  clean evidence. Focal/aggregate results and exact SQL review supply behavioral proof.
- SQL review confirms explicit PUBLIC/runtime revocations and append-only statement
  triggers. Mutable projection is separated from historical originals. No cross-module
  Java internal imports were introduced.

## Final frozen hashes

| Path | SHA-256 |
| --- | --- |
| 07-04-PLAN.md | a54be8d2be0f8686b0e9f931c5b8cdfe21a4e38375e3fc0d5683c51758dce7ca |
| Candidate and governed V39 | e0b9a9005e8dfe13f1d7786b4d145dfa50412928c12804e7eed436ec65041a0b |
| ChatSchemaIT.java | 29fbc93b55816547849d13ac181d5984512284b6964e7d9b30aa4aa4e1f51287 |
| FlywayMigrationIT.java | a52f53903e789a3445aa7aacb4b39396f87f96c60f1d18825a9f3684e69bb012 |
| OddsMigrationIT.java | 74169a4d88f37c590918aba9135b21e1b4fcc077a268814dc06c63b3dae687aa |
| docs/phase7-approvals/07-04.json | 0492e469bbd76b8c10a1e86af95020640a4f4612b56722ffeb32f4877bf69d34 |

Exact index and hashes rechecked before fef7ce5fc20469e0065c606da9a5548ce89bea4f. Authority becomes historical
once HEAD advances; documentary commit cannot cite its own hash.

## Continuation

Phase7 is4/35, not pilot-certified. Next07-28 in Wave2, then07-05 in Wave3 under
continuous authority. WebSocket/STOMP remains the owner's chosen RT-01 transport.
