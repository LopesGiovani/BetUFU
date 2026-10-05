---
phase: 07-comunidade-moderada-e-prontid-o-do-piloto
plan: '06'
wave: 4
status: complete
subsystem: api-contract
requires: [07-03, 07-05]
provides: [official-chat-read-contract, generated-chat-read-client]
affects: [07-08, 07-11]
requirements-completed: []
actuals:
  tasks: 4
  commits: 2
tech-stack:
  added: []
  patterns: [real-engine-generation, exact-candidate-inventory, public-private-read-contract]
coverage:
  - id: generated-chat-read-contract
    description: Four GET operations preserve channel security and minimal projections
    verification:
      - kind: integration
        ref: frontend/scripts/generate-api.test.mjs
        status: pass
    human_judgment: false
started: '2026-10-05T01:55:37.397Z'
technical_commit: 2387cd312f106b2ce96b5808b7ea5a695fe18bfe
---

# Plan 07-06 Summary

The governed OpenAPI contract and officially generated Angular client expose
the four chat reads delivered in 07-05. Event reads require actual publication;
global reads require a session. Read availability grants no write permission.

## Authority and scope

- Base 52105ece9f8aaa548a2adefffed8d59bda9c6810, inline on master.
- Authority: conversation:2026-10-04:owner-continuous-phase7-wave2-through-completion.
  It authorizes implementation, tests/self-healing, local stage/two commits and
  formalization. Candidate inspection uses this authority, not an invented reply.
- Fourteen changed technical paths within the original fifteen-path allowlist;
  generated index.ts is unchanged. Documentary closeout adds SUMMARY/STATE/ROADMAP.
- All239 immutable baseline hashes, including owner documents and V1-V38, match.
  Six publication hashes and five candidate hashes match measured bytes.
- Owner edits excluded from both commits. No dependency changes, data resets,
  push, worktree or Antigravity. Configuration remains interactive.

## Delivery

- GET event history/changes and global history/changes; Chat tag and exactly
  ChatMessage/ChatReadResponse schemas, without a separate Channel schema.
- Event security is public; global declares the existing cookieSession convention.
  Default50/max100; channel-bound opaque history cursor and nonnegative int64
  change cursor. Cache descriptions match private/no-store and Vary Cookie.
- Minimal DTO: public participant UUID/nickname, text, timestamp, removed marker
  and cursors. Removed projections omit original author/text. AVAILABLE means
  readable; deadline remains null until lifecycle delivery. Sequence cursor is
  not a certification of complete moderation or commit-order replay.
- Real approved Java engine generated a complete197-file tree. Exactly five
  generated files changed: Chat service, two models, API and model barrels.
  All other generated/config/engine bytes remain unchanged. No generated edit.
- Actual candidate records every path/status/hash, complete generated manifest,
  before/after bytes for changes and governed before/after preservation.
- Two new contract/client tests failed before publication. They verify GET-only
  relative requests, minimal fields and public/private policy; mutation probes
  remove global security and introduce an email field, and are rejected.

## Verification

| Command/evidence | Result |
| --- | --- |
| Preparation approval and literal real candidate generation | PASS before publication; original source/inventory/digest recorded |
| node scripts/validate-phase7-approvals.mjs --plan 07-06 --stage implementation | APPROVED at original HEAD; repeated before commit |
| npm --prefix frontend run generate:api | PASS; repeated after whitespace-only source amendment |
| npm --prefix frontend run generate:api:check | PASS; repeated after final amendment |
| node --test frontend/scripts/generate-api.test.mjs | Final100/100,0fail/cancel/skip/todo;11218.6038ms |
| node --test scripts/*.test.mjs | 916/916,9suites,0fail/cancel/skip/todo;356217.1596ms |
| npm --prefix frontend test -- --watch=false | Final455/455,75files;9.08s |
| npm --prefix frontend run lint | PASS |
| npm --prefix frontend run build:production | PASS;13 existing style-budget warnings outside scope |
| node scripts/maven.mjs -f backend/pom.xml verify | BUILD SUCCESS;0fail/error/skip |
| Fresh XML since2026-10-05T02:18:08Z | Surefire361/37classes + Failsafe634/125classes =995; older focal XML excluded |
| Scoped git diff --cached --check | PASS after removing inherited whitespace-only candidate line |
| Frozen paths/hashes/stage | All14 match immediately before implementation commit |

Maven wrapper reports1990 because its aggregation duplicates report totals;
the995 tests above come from fresh XML and are authoritative. Final whitespace
amendment changes only YAML formatting and measured metadata; real engine,
generator focal and check repeated afterward. Aggregate checks cover identical
production semantics and generated tree; no second aggregate claim is made.

## Self-healing and review

- Generic candidate test fixtures renamed CandidateChat/CandidateChatMessage
  because Chat is now governed; exact selector expectation extended. Focal
  initially99/100 after publication, then100/100 after this correction.
- Transient Windows EPERM prevented moving the governed generated directory;
  previous bytes remained intact. ACL allowed the owner and Restart Manager
  reported no lock owner. Two exact task-owned stalled diagnostic probes were
  stopped. Native same-parent rename succeeded and was immediately restored;
  next official generation succeeded. Root interference cause is unconfirmed.
  No user process, directory permission or publication algorithm changed.
- Two full Angular runs timed out in different existing tests (ranking retry,
  athlete-link confirmation). Each isolated file passed3/3 rapidly. Final literal
  full run passed455/455 with reduced concurrent load. No timeout increase or
  unrelated test/config edit. This supports load sensitivity, not a proven cause.
- Stage revealed an inherited whitespace-only line when full candidate YAML
  became tracked. Source whitespace was removed from candidate and official
  YAML; measured source hash and YAML after-content/hash in inventory amended,
  preserving actual original before inventory and generated entries. Official
  real engine regeneration/check confirmed identical generated digest. Approval
  retains pre-amendment hashes and reason; no new human response asserted.
- Formatting generator/test introduced cosmetic churn; production semantics
  are selector/manifest additions. RepoWise reviewed4/14files and reports no
  introduced/worsened finding;10 non-analyzable/generated files reviewed inline.
  No functional finding in frozen scoped review. Complexity remains inherited.
- Existing OpenAPI references cookieSession without declaring securitySchemes.
  Chat follows that baseline; this plan does not claim formal full OpenAPI
  security validation. Server authorization is tested in07-05. Declaring a scheme
  would alter broader generated authentication/configuration outside this slice.

## Candidate hashes

- Final source: bab463bc8d4953bfd900821e5496a4bfe244a2d382ad1baada3ceb8574aae574.
- Final inventory: 982d6ee44f6fbbae40b351b3901e931693a055122685cde58520497e7a8e4a6e.
- Full197-file generated digest: CFD9FEDAAA07BE604D87958284470FFEDCD4780E0EA3205305DDDC0955B03A81.
- Original194-file digest: AA0BC4ACFADDD834D15B85E6FA331A4554AC01696135823E40B01DF7BA288ED3.
- Original source:3A42F789CA35FF4E1C674A982D5127B5453065FAD279DD7D0295E4617C31DD0C;
  original inventory:230f3136c2483d74de29cb3566c4a14e8de3a0e0fc7c90f825e3a78a2bc7d468.

## Continuation

Phase7 is7/35plans complete, not certified. Continue07-07 then07-09 in Wave4
under continuous authority. STOMP choice is already recorded in07-03-SUMMARY;
delivery, browser journeys and legal/production gates remain pending.
Approval is historical after HEAD advances; it is not silently rebound.
