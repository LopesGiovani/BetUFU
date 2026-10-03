---
phase: 06-resultados-confirmados-rankings-e-grupos
plan: "07"
status: complete
completed: 2026-10-03
technical_commit: 27224b5ef7e77d503e03b5bfeb16fbc2b3e99c2f
---

# Plan 06-07 Summary

Connected Phase 6 domains to HTTP contracts and discoverable Angular journeys.

## Delivered

- Five controllers publish owner-scoped settlements, corrections, achievements and groups, a sanitized public lifetime ranking, and general-administrator settlement authorization. CSRF remains mandatory on commands; nonmember and missing-group responses are indistinguishable. Raw domain errors and achievement revocation details are not exposed.
- Settlement composes accepted tickets through its public consumer-owned prediction port and current sports results through its sports port. Its JDBC repository reads only its own settlement facts. History distinguishes the current pending/partial case from preserved superseded facts, including accepted stake/odd/return, rules, calculation and related ledger references.
- The HTTP authorization requires the exact outcome fact plus the exact nullable sporting version, revalidated under the event lock. Missing sporting version cannot bypass validation; cancellation facts legitimately have no sporting version. Candidates include durable case progress, allowing refresh without pretending partial work is complete.
- `/ranking` is public and linked from the Arena. `/conta/historico`, `/conta/grupos` and `/conta/conquistas` are discoverable in the personal console; achievements also appear in the profile. `/operacao/liquidacoes` is exposed only with `AUTHORIZE_SETTLEMENT`; backend authorization remains authoritative.
- Standalone OnPush components use Signals, stores and generated-service adapters. Private API 401 responses reset session state and redirect to contextual login; 403 does not trigger automatic replay. Groups offer creation, secure invitations, management and accessible destructive confirmations without financial effects. Only the owner sees rename/close controls; an admin can invite.
- The OpenAPI client was generated exclusively through the official script. Final candidate digest: `AA0BC4ACFADDD834D15B85E6FA331A4554AC01696135823E40B01DF7BA288ED3`. Candidate generation preserved the governed tree; the final official anti-drift check passed.

## Exact Verification

| Command / evidence | Result |
|---|---|
| `node scripts/maven.mjs -f backend/pom.xml clean verify` | Surefire 360/360 in 37 classes; Failsafe 606/606 in 120 classes; zero failures/errors/skips |
| Focal five ControllerIT classes, SettlementPresentationIT and ModulithArchitectureTests | 19/19 XML cases, zero failures/errors/skips |
| `npm --prefix frontend test -- --watch=false` | 452/452 in 75 files |
| `node --test scripts/*.test.mjs` | 765/765, nine suites, zero failures/skips |
| `node --test frontend/scripts/generate-api.test.mjs` | 73/73 |
| Official generator check, Angular lint and production build | Passed; existing component-style budget warnings remain |
| Spotless apply/check, scoped Prettier and `git diff --check` | Passed |
| Controlled-response Playwright preview | Five surfaces at 1280 and 320 px; exactly one main and no page overflow; internally scrollable readable tables; Tab/Shift+Tab/Escape, cancellation and restored focus; 409 blocks resubmission; 503 reload recovers |

The Maven wrapper repeats test-summary counts; XML counts above are authoritative.
Controller tests use the real Spring Security filter chain with domain-port doubles;
settlement presentation and existing domain integration tests use PostgreSQL
Testcontainers. Neither these tests nor the controlled-response preview prove the
real business browser E2E assigned to 06-08.

Measured text/muted/danger/focus contrast against the surface token was respectively
17.35:1, 10.55:1, 7.44:1 and 13.72:1. These measurements are not a full WCAG audit.
The preview issued zero backend writes; its expected 409/503 responses explain the
browser resource-error entries. Preview browser and local static-server process
were stopped after checks.

## Reconciliation And Review

- The plan records the approved public-port/adapter expansion, capability wiring, shared HTTP test support, private session recovery, actual generated DTO inventory and validator/generator regression tests. Technical commit contains exactly 103 files. No previous-plan or owner documentation was staged.
- Aggregate testing caught capability expectations pinned at nine instead of the approved ten, and stale compiled sports classes. Expectations were updated; clean compilation removed the stale artifacts. The final aggregate run passed without modifying sports source for those stale errors.
- Browser testing caught focus escaping a native dialog and mobile tables wrapping words into individual letters. Both were corrected and repeated; lint additionally required a focusable dialog. A review aligned owner-only rename UI with the existing backend rule.
- The generated authorization DTO import names initially resembled an authentication header to the route scanner. Only the three exact approved import paths are exempted; the new negative test still rejects a literal manual Authorization header. New route mutations reject missing guards, wrong shell/path and missing public ranking.
- Read-only review used RepoWise and the frozen technical snapshot. The manifest is sorted relative paths plus uppercase SHA-256, joined by LF with one final LF; its digest is `6426F50BCFCFE19F1EF1E56C3754965903C279FBD373A81E5F64BF3D950FCA63`. The baseline was `123576b4c115dca82943de730badbbf6acaa24a8`; code stayed frozen from review through stage. No further blocking functional finding remained.

## Preserved Files And Remaining Work

- Owner file SHA-256 remained unchanged: development `DEAF6470ACF46C158D53EC66784916DD021CD8464336FF583EE780E0F9898FE8`; local-seed `EF1DA4213B92E1DB908CCF370390C24E412C0891E3EF7F8D17C5D2092A925FCC`; manual-testing `D4E1DF426A38B6C680A0A864997F01D5692B687308FD944F8EE6161491F3307F`. These files remain excluded from both commits.
- No dependency update, migration edit, persistent database mutation, Docker reset, seed, real prediction, worktree, Antigravity call or push occurred. Transient candidate metadata and preview evidence remain unstaged; they are not production artifacts.
- History/candidate reads currently cap at 100 items and read-time projections can become expensive as data grows. Pagination, refresh strategy and service/style maintainability remain pilot-scale risks, not evidence of phase certification. Correction proposal/approval is published via HTTP; the planned UI presents owner corrections rather than a new administrative correction editor.
- Phase 6 is 7/8. Plan 06-08 remains pending separate approval for real business E2E, phase-level concurrency/privacy, future odds replay and final certification. No `06-VERIFICATION.md` with `status: passed` was created and no phase requirements were prematurely marked complete.
